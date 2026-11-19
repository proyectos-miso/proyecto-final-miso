# HOTFIX · Gestión de socios

| Field | Value |
|---|---|
| ID | HOTFIX |
| Title | Gestión de socios |
| User story | none: mejora del feature FE-WEB-05 sin historia en el tablero, acordada el 2026-11-14 |
| Owner | Juan Camilo Peña |
| Status | IN_PROGRESS |
| Created | 2026-11-14 |
| Security profile | HIGH |

## 1. Requirements

### User story

Mejora (no es una historia del tablero). FE-WEB-05 pide que el equipo de alianzas de Solventa dé de alta socios
B2B, los promueva de sandbox a producción y controle sus credenciales. Hoy solo existen los endpoints de registro
(`POST /quote-edge/partners/register`, restringido a Solventa por HOTFIX-correcciones-de-hu-web-12) y de
aprobación, sin pantalla, sin forma de revocar ni de rotar un secreto, y el sandbox no tiene ningún efecto.

**Como** analista de operaciones de Solventa **quiero** registrar, aprobar, rotar las credenciales y revocar a los
socios desde el portal **para** incorporar socios sin tocar la base y cortar el acceso de uno comprometido de inmediato.

Qué ya existe: registro en sandbox con cuota de 1000 por hora; aprobación a producción; `get_current_partner`
vuelve a leer el estado del socio en cada pedido; eventos de seguridad del socio en `solventa.security.partner`.

### Problem and outcome

Problema: el alta y la aprobación solo se hacen con llamadas a la API; un secreto filtrado no se puede cambiar ni
un socio revocar sin tocar la base, y un socio en sandbox emite pólizas reales. Resultado: la pestaña «Socios» del
portal, solo para el equipo de Solventa, con alta, aprobación, rotación y revocación; el sandbox solo cotiza.

### Goals and non-goals

Objetivos: listado de socios sin secretos; revocación idempotente que corta los tokens vivos; rotación del secreto
entregado una sola vez; la aprobación no reactiva a un revocado; el sandbox no emite pólizas; `GET
/experience/operator` para que el portal sepa si mostrar la pestaña «Socios»; pestaña «Socios» con su guard; ningún
canal emite, cobra ni renueva en nombre de un socio en sandbox, revocado o inexistente.

Fuera de alcance: reactivar a un socio revocado (se registra uno nuevo); editar el nombre, el correo o la cuota de
un socio activo; portal de autoservicio para el socio; varias credenciales por socio o con vencimiento.

### Affected paths

| Path | Change |
|---|---|
| backend/app/modules/rating/partners.py | Servicio de gestión: alta con id ASCII, listado con aviso de truncado, revocación, rotación, aprobación, enmascarado, auditoría con `operator_ref` y la regla `ensure_partner_can_issue` |
| backend/app/bff/experience/operations_router.py | `GET /experience/operator`, `GET /experience/partners`, `POST .../revoke`, `POST .../credentials`; la aprobación usa el servicio y da 409 a un revocado; el id de la ruta acepta `[\w-]`; la renovación del analista responde 403 si la regla bloquea al socio de la póliza |
| backend/app/bff/quote_edge/router.py | El registro usa `partners.register` y responde `no-store`; la emisión del socio responde 403 si la regla lo bloquea |
| backend/app/bff/experience/router.py | La emisión y el cobro del asesor responden 403 si la regla bloquea al socio |
| backend/app/modules/policy/service.py | La emisión del asesor (socio de la cotización y del asesor) y la del socio aplican `ensure_partner_can_issue` solo al crear una póliza nueva; repetir la emisión devuelve la existente (RC-02) |
| backend/app/modules/policy/operations.py | `renew` aplica `ensure_partner_can_issue` al socio de la póliza antes de cambiar nada |
| backend/app/bff/insured/router.py | La renovación desde la app responde 403 si la regla bloquea al socio de la póliza |
| backend/app/modules/payments/service.py | Un cobro nuevo aplica `ensure_partner_can_issue` al socio del asesor y al de la póliza |
| backend/app/core/security.py | `log_partner_security_event` acepta `operator_ref` |
| backend/tests/integration/test_partner_management_api.py | Pruebas de los endpoints, la revocación, la rotación, el sandbox, la auditoría y las rutas de emisión y cobro |
| backend/tests/integration/test_policy_renewal_api.py, backend/tests/integration/test_insured_claims_api.py | La renovación de la póliza de un socio revocado o en sandbox da 403 y la póliza sigue vencida |
| backend/tests/integration/test_payments_partner.py, backend/tests/unit/test_partner_policy.py | Siembran el socio que antes no existía: ahora la regla exige que exista y esté activo |
| web/src/app/core/models/partner.model.ts | Contrato de los socios |
| web/src/app/core/partners/partner.service.ts | Llamadas de la gestión de socios |
| web/src/app/core/operator/operator.service.ts | Si el analista es de Solventa; conserva la respuesta al renovar el token de la misma persona y descarta las respuestas de una sesión anterior |
| web/src/app/core/guards/solventa.guard.ts | Guard de la pestaña «Socios» |
| web/src/app/core/interceptors/jwt.interceptor.ts | No envía el token del analista a `/openapi.json` ni intenta renovar la sesión si esa petición falla |
| web/src/app/features/socios/partners.component.ts | Pantalla «Socios» |
| web/src/app/app.routes.ts | Ruta `/socios` |
| web/src/app/app.component.ts | Consulta `/experience/operator` con la sesión del analista |
| web/src/app/app.component.html | Pestaña «Socios» |
| web/proxy.conf.json | El servidor de desarrollo reenvía `/openapi.json` al backend |
| web/src/app/**/*.spec.ts | Pruebas del portal |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Analista de Solventa | Registrar un socio | 1. Abre «Socios». 2. Escribe nombre y correo. 3. Copia el `client_id` y el `client_secret` del panel. 4. Cierra el panel | Correo inválido: 422 y el aviso; sale de la pestaña: el panel se borra |
| UC-02 | Analista de Solventa | Aprobar a producción | 1. Pulsa «Aprobar a producción». 2. Confirma. 3. El socio queda en producción con 50.000 por hora | Revocado: 409 «Socio revocado.» |
| UC-03 | Analista de Solventa | Rotar credenciales | 1. Pulsa «Rotar credenciales». 2. Confirma. 3. Copia el secreto nuevo | Revocado: 409 |
| UC-04 | Analista de Solventa | Revocar | 1. Pulsa «Revocar». 2. Confirma. 3. El socio queda revocado y sus tokens dejan de servir | Ya revocado: 200 sin cambios |
| UC-05 | Socio en sandbox | Probar la integración | 1. Pide token. 2. Cotiza. 3. Intenta emitir | Emite: 403 «Un socio en sandbox no emite pólizas reales»; un asesor que emite su cotización también recibe 403; renovar la póliza de un socio bloqueado da 403 |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | El analista de un socio lista, revoca, rota o aprueba socios | Analista de socio | THR-01 | 403 en cada endpoint; sin sesión 401; un asesor 403 | TC-02 |
| AB-02 | Leer un secreto o el correo completo desde el listado, el navegador o los registros | Analista o atacante con acceso al navegador | THR-02 | El listado no trae hash, secreto ni correo completo; el secreto se ve una vez, con `no-store`, nunca en storage | TC-01, TC-04, TC-06 |
| AB-03 | Un socio revocado sigue usando un token ya emitido o se reactiva al aprobarlo | Socio revocado | THR-03 | 401 con el token vivo; aprobar o rotar da 409 | TC-03 |
| AB-04 | Registrar un socio duplicado o forzar la reutilización de identificadores | Analista de Solventa | THR-04 | Cada alta genera `partner_id` y `client_id` con sufijo aleatorio; el `client_id` es único en la base | TC-01 |
| AB-05 | Un secreto filtrado sigue sirviendo después de rotarlo | Atacante con el secreto anterior | THR-02 | El secreto anterior da 401; el nuevo sirve | TC-03 |
| AB-06 | Un socio en sandbox, revocado o inexistente emite o cobra, por su canal o por el del asesor | Socio en sandbox o revocado, asesor | THR-05 | 403 y evento de seguridad; no queda póliza ni cobro | TC-05 |
| AB-07 | Un socio dado de alta con tildes queda sin gestión porque su id no pasa la validación | Analista de Solventa | THR-03 | El alta genera ids ASCII y la ruta acepta los ids con tildes ya creados | TC-03 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | THE portal SHALL listar los socios con entorno, estado, cuota, fecha de alta y contacto enmascarado, sin secretos | STATED |
| AC-02 | WHEN el analista registra un socio o rota sus credenciales, THE portal SHALL mostrar el `client_secret` una sola vez con «Copiar» y el aviso de que no se volverá a mostrar | STATED |
| AC-03 | WHEN el analista revoca un socio, THE backend SHALL rechazar sus tokens vigentes y nuevas autenticaciones | STATED |
| AC-04 | WHEN se rota el secreto, THE secreto anterior SHALL dejar de servir y el nuevo SHALL obtener token | STATED |
| AC-05 | IF el socio está revocado, THEN aprobarlo o rotarlo SHALL responder 409 sin cambiar su estado | DERIVED: revocar es definitivo; la aprobación ya no reactiva |
| AC-06 | WHILE el socio está en sandbox, THE backend SHALL dejarlo cotizar con su cuota y SHALL rechazar con 403 toda emisión nueva, todo cobro nuevo y toda renovación en su nombre, por el canal del socio, el del asesor, el del analista o la app (socio de la cotización, de la póliza o del asesor), sin cambiar la póliza; IF el socio está revocado o no existe, THEN SHALL responder 403 «Socio revocado o inexistente»; repetir una emisión ya hecha SHALL devolver la misma póliza (RC-02) | DERIVED: FE-WEB-05 pide que el sandbox no emita; la regla vale en toda ruta de emisión, cobro y renovación y también para un socio revocado; renovar sin poder cobrar dejaría una póliza pendiente que nadie puede pagar |
| AC-07 | THE pestaña «Socios» SHALL verse solo si `GET /experience/operator` dice `solventa: true`, y su ruta SHALL devolver a `/gestion` al analista de un socio | STATED |
| AC-08 | Cada acción de aprobar, rotar o revocar SHALL pedir confirmación | STATED |
| AC-09 | IF hay más socios de los que trae el listado, THEN THE backend SHALL marcarlo con `truncated: true` y el portal SHALL avisarlo | DERIVED: el listado tiene un límite de 200 |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | ACCESSIBILITY | Pantalla «Socios» | Tabla con `caption` y `th scope`; campos con etiqueta; mensajes en `aria-live`; el foco va al panel de confirmación y al de credenciales y vuelve al botón que abrió la acción o, si ya no existe, al encabezado; contorno de foco sólido `#0f6d94` (contraste de al menos 3:1); WCAG 2.2 AA |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | Correo de contacto del socio |
| Sensitive data (special categories, biometrics, health, financial, minors) | NO | No aplica |
| Authentication, sessions, credentials or one-time codes | YES | Secretos OAuth2 de los socios: rotación y revocación |
| Authorization or access to other users' resources | YES | Solo el analista de Solventa gestiona socios |
| Payments or money movement | YES | El cobro de la prima se rechaza si el socio del asesor o el de la póliza está en sandbox, revocado o no existe |
| Third-party services or integrations | YES | Los socios B2B consumen el canal quote-edge |
| Infrastructure, network, IAM, CI/CD or secrets | NO | Sin infraestructura ni secretos de plataforma nuevos; los secretos de los socios son credenciales de la aplicación (trigger de autenticación) |
| Multiple tenants, partners or organizations | YES | Estado y entorno de cada socio |
| Storage or processing on a mobile device | NO | Es el portal |
| Web UI or data exposed in the browser | YES | El secreto se muestra una vez en el portal |
| New or updated dependencies | NO | Sin dependencias nuevas |
| Critical paths (sdd/security/critical-paths.md) | YES | web/src/app/core/guards/** |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | Solo el analista de Solventa (`partner_id` nulo, leído de la base) usa los endpoints de gestión; el portal solo oculta | THR-01 | CTL-01 | HIGH |
| SR-02 | El secreto se guarda solo como hash bcrypt, se entrega una vez con `no-store` y no queda en el navegador, el listado ni los registros; el correo se enmascara | DATA-01, DATA-02, THR-02 | CTL-02, CTL-03, CTL-08 | HIGH |
| SR-03 | Revocar corta los tokens vigentes y es definitivo para la aprobación y la rotación | THR-03 | CTL-04 | HIGH |
| SR-04 | Ninguna ruta emite, cobra ni renueva en nombre de un socio en sandbox, revocado o inexistente: emisión nueva del asesor y del socio, cobro nuevo del asesor y del socio, renovación del analista y de la app; la idempotencia de la emisión y del cobro se conserva | THR-05 | CTL-05 | HIGH |
| SR-05 | Cada alta, rotación, revocación y aprobación deja un evento de seguridad con una referencia del operador (`operator_ref`, 12 hex del sha256 del usuario) y sin el secreto ni correos | THR-01, THR-03 | CTL-06 | MEDIUM |

## 2. Design

### Components

- **Backend:** `app/modules/rating/partners.py` con `register`, `list_partners`, `revoke`, `rotate_secret`,
  `approve`, `ensure_partner_can_issue`, `slug_of`, `operator_ref`, `mask_email`, `environment_of` y
  `new_client_secret`; las mutaciones leen la fila con `SELECT … FOR UPDATE`. El router de operaciones agrega la
  dependencia `solventa_operator` (403 si el analista es de un socio) y los endpoints; los routers no tienen lógica.
  El id del socio se arma en ASCII (`unicodedata.normalize("NFKD")` y solo `[a-z0-9]`, `socio` si queda vacío) y las
  rutas aceptan `^[\w-]+$` de 2 a 64 para no dejar sin gestión a los socios ya creados con tildes.
  `ensure_partner_can_issue(session, partner_id)` es la única regla para emitir o cobrar en nombre de un socio: sin
  socio no hace nada; inexistente o revocado lanza `PartnerBlockedError` «Socio revocado o inexistente»; en sandbox
  «Un socio en sandbox no emite pólizas reales»; los routers la traducen a 403. La aplican
  `PolicyService.issue_policy` (socio de la cotización y del asesor) y `PolicyService.issue_partner_policy` solo
  cuando van a crear una póliza nueva: primero buscan la póliza de la cotización y, si existe, la devuelven (RC-02);
  `PaymentService.process_charge` antes de un cobro nuevo (socio del asesor y de la póliza; repetir una clave ya
  cobrada solo devuelve la respuesta guardada); y `PolicyOperations.renew` (socio de la póliza) después de bloquear la
  fila y antes de cambiarla, con rollback si la regla la bloquea. La app traduce el 403 de la renovación a su aviso
  genérico «No hay conexión» (`HttpPolicyActionsApi.renew` solo distingue 200, 409 y 401): el mensaje no es exacto,
  pero la póliza no cambia; corregirlo queda para un cambio de la app. El listado pide uno de más para saber si quedó truncado.
  El entorno se lee con `lower()`; la migración `0001` ya lo normaliza y deja el default `production`.
- **Portal:** `PartnerService`, `OperatorService` (una consulta a `/experience/operator` por sesión, identificada por
  el rol y el `sub` del token, o por el token entero si no se puede leer; renovar el token de la misma persona conserva
  la respuesta sin volver a consultar; otro usuario, otro rol o el cierre de sesión vuelven a `false`; descarta las
  respuestas de una sesión anterior; un error cuenta como «no es de Solventa»), `solventaGuard` (redirige a `/gestion`) y `PartnersComponent` con la tabla, el aviso de
  listado truncado, el formulario de alta, el panel de credenciales (solo en una señal del componente, se borra al
  cerrar o al salir), la confirmación de cada acción con el foco de vuelta al botón que la abrió (o al encabezado)
  y el enlace al contrato OpenAPI, que solo se muestra si `GET /openapi.json` responde en el mismo origen del
  portal: en local, por el proxy del servidor de desarrollo; en AWS y en producción CloudFront no publica esa ruta,
  así que el enlace no aparece. El `jwtInterceptor` no envía el token del analista a `/openapi.json`. El enlace abre
  en una pestaña nueva y lo dice al lector de pantalla. El aviso de listado truncado se asocia a la tabla con
  `aria-describedby` y, si aparece tras una acción, se agrega al mensaje de la región `aria-live`. `AppComponent` consulta el operador con cada
  sesión y muestra «Socios» solo si es de Solventa.

### Data flows

Analista → portal → `/experience/partners/*` → `partners` (hash bcrypt en `partners.client_secret_hash`) → respuesta
con el secreto nuevo una vez → señal del componente → portapapeles del analista → canal seguro con el socio.
Socio → `/quote-edge/auth/token` y `/quote-edge/*` → `get_current_partner` relee el estado en cada pedido.

### Data contract

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| GET /experience/operator | — | `{solventa}`; 401 sin sesión; 403 si no es analista |
| GET /experience/partners | — | `{items: [{partner_id, name, environment, status, quota_limit, quota_used, created_at, has_contact, contact_email_masked}], truncated}`, `no-store`; 403 analista de socio |
| POST /experience/partners/{id}/revoke | — | El resumen con `REVOKED`; idempotente; 404 inexistente |
| POST /experience/partners/{id}/credentials | — | `{partner_id, client_id, client_secret, message}`, `no-store`; 409 revocado; 404 |
| POST /experience/partners/{id}/approve (cambia) | `{quota_limit?}` | Igual que antes; 409 «Socio revocado.»; 403 analista de socio |
| POST /quote-edge/policies (cambia) | — | 403 «Un socio en sandbox no emite pólizas reales» o «Socio revocado o inexistente» |
| POST /experience/policies (cambia) | — | 403 con el mismo mensaje si el socio de la cotización o el del asesor está bloqueado |
| POST /experience/payments/charge (cambia) | — | 403 con el mismo mensaje si el socio del asesor o el de la póliza está bloqueado; una clave ya usada repite su respuesta |
| POST /experience/policies/{n}/renewal y /insured/policies/{n}/renewal (cambian) | — | 403 con el mismo mensaje si el socio de la póliza está bloqueado; la póliza no cambia |
| POST /quote-edge/partners/register (cambia) | — | Igual, con `Cache-Control: no-store` |

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| Dependencia `solventa_operator` sobre `operations_scope`, que lee el socio del usuario en la base | CTL-01 | Confiar en el guard del portal: solo oculta |
| Secreto nuevo con `secrets.token_urlsafe(24)` y hash bcrypt; se devuelve una vez y no se guarda en el navegador | CTL-02 | Mostrarlo de nuevo o guardarlo cifrado: habría que custodiar una llave y amplía la exposición |
| La revocación se apoya en la relectura del estado de `get_current_partner` | CTL-04 | Lista negra de tokens: más estado y el mismo efecto |
| La aprobación no toca el estado | CTL-04 | Que reactive: un socio comprometido volvería a entrar con su secreto filtrado |
| Correo enmascarado (`s***@dominio`) y `has_contact` | CTL-03 | Devolver el correo completo: no hace falta para operar |
| Una sola regla `ensure_partner_can_issue` en el módulo, llamada desde los servicios de emisión, cobro y renovación | CTL-05 | Comprobar en cada router: es fácil olvidar una ruta, como pasaba con el canal del asesor |
| La renovación aplica la regla al socio de la póliza y responde 403 sin cambiarla | CTL-05 | Renovar y bloquear solo el cobro: quedaría una póliza renovada pendiente que nadie puede pagar |
| La regla se aplica solo al crear una póliza o un cobro nuevos; repetir devuelve lo ya hecho | CTL-05 | Aplicarla antes de la idempotencia: un reintento tras revocar al socio daría 403 por una póliza que ya existe (RC-02) |
| `operator_ref` = 12 hex del sha256 del usuario | CTL-06 | El `id` de `UserDB`: el token no lo trae y pedirlo agrega una consulta; el correo: es dato personal |
| Rotar no invalida los tokens ya emitidos | CTL-04 | Lista negra o versión del secreto en el token: más estado; el riesgo queda en RISK-04 y la respuesta a un compromiso es revocar |

### Architecture decisions

N/A: reutiliza la arquitectura hexagonal (ADR 0003, 0004), el BFF por canal (ADR 0005) y los componentes con
señales del portal (ADR 0008, 0009, 0010).

### Configuration and deployment

- Sin migración: la `0001` ya deja el entorno en minúsculas y el default `production`. Sin variables nuevas.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Servicio de gestión de socios y endpoints de operaciones | AC-01, AC-03, AC-04, AC-05, SR-01, SR-02, SR-03, SR-05 | CTL-01, CTL-02, CTL-03, CTL-04, CTL-06 | DONE | backend/app/modules/rating/partners.py; backend/app/bff/experience/operations_router.py |
| T-02 | Regla única del socio en toda emisión y cobro | AC-06, SR-04 | CTL-05 | DONE | backend/app/modules/rating/partners.py; backend/app/modules/policy/service.py; backend/app/modules/payments/service.py |
| T-03 | `GET /experience/operator`, `OperatorService` y `solventaGuard` | AC-07, SR-01 | CTL-01, CTL-08 | DONE | web/src/app/core/guards/solventa.guard.ts |
| T-04 | Pantalla «Socios» con panel de credenciales y confirmaciones | AC-01, AC-02, AC-08, QR-01, SR-02 | CTL-03, CTL-08 | DONE | web/src/app/features/socios/partners.component.ts |
| T-06 | Alta con id ASCII, auditoría con `operator_ref` y listado truncado | AC-01, AC-09, SR-05 | CTL-06, CTL-07 | DONE | backend/app/modules/rating/partners.py |
| T-07 | Correcciones de la segunda revisión: renovación, idempotencia, eventos de bloqueo, accesibilidad del aviso, sesión del portal y OpenAPI | AC-06, AC-07, AC-09, QR-01, SR-04 | CTL-05, CTL-06, CTL-08 | DONE | backend/app/modules/policy/operations.py; backend/app/modules/policy/service.py; web/src/app/core/operator/operator.service.ts; web/src/app/core/interceptors/jwt.interceptor.ts |
| T-05 | Pruebas | AC-01 a AC-09 | CTL-01, CTL-02, CTL-03, CTL-04, CTL-05, CTL-08 | DONE | backend/tests/integration/test_partner_management_api.py; web/src/app/features/socios/partners.component.spec.ts |

# HOTFIX · Auditoría de suscripción

| Field | Value |
|---|---|
| ID | HOTFIX |
| Title | Auditoría de suscripción |
| User story | none: mejora del feature FE-WEB-08 sin historia en el tablero, acordada el 2026-11-14 |
| Owner | Juan Camilo Peña |
| Status | IN_PROGRESS |
| Created | 2026-11-15 |
| Security profile | HIGH |

## 1. Requirements

### User story

Mejora (no es una historia del tablero). FE-WEB-08 pide que el equipo de Solventa pueda auditar cada decisión de
suscripción: ver con qué entradas y con qué versión de las reglas se tomó y comprobar que, recalculada, da lo mismo.
Hoy la cotización guarda el puntaje y los tiers con su desglose, pero no la suma asegurada, el riesgo de ocupación ni
la versión de las reglas; el rechazo no guarda el puntaje ni la fecha de nacimiento y así sigue, porque HU-WEB-07
lo decidió (su CTL-03); la tabla `quotes` se puede
cambiar o borrar; y no hay forma de registrar que un titular revocó su consentimiento.

**Como** analista de operaciones de Solventa **quiero** consultar una cotización o un rechazo por su identificador,
ver sus entradas, su versión de reglas y su salida, y recalcular la cotización **para** demostrar que la decisión sale de las
reglas vigentes en ese momento y detectar cuándo una regla cambió.

Qué ya existe: `build_breakdown`, `age_on` y `evaluate` son funciones puras; los rechazos ya son inmutables en la
base; `solventa_operator` y `operator_ref` (HOTFIX-gestion-de-socios); `OperatorService` y `solventaGuard` en el
portal.

### Problem and outcome

Problema: una decisión de suscripción no se puede reconstruir ni probar, una cotización guardada se puede alterar y
la revocación del consentimiento no tiene efecto en el sistema. Resultado: la pestaña «Auditoría» del portal, solo
para el equipo de Solventa, con el detalle y el recálculo de cada decisión; `quotes` es de solo inserción; y la
revocación registrada impide cotizar con ese consentimiento y oculta las entradas personales en la auditoría.

### Goals and non-goals

Objetivos: versión de reglas `RULES_VERSION`; entradas para recalcular en `quotes` y solo la versión de reglas en
`underwriting_rejections`;
`quotes` inmutable en la base (modelo y migración `0005`); `GET /experience/decisions/{id}` con el documento
enmascarado; `POST /experience/decisions/{id}/replay` de una cotización con las mismas funciones puras de la
cotización y la edad a la fecha de la decisión (un rechazo responde 409); `POST /experience/consents/revoke`; pestaña «Auditoría» con búsqueda, tablas, «Recalcular» y
«Registrar revocación del consentimiento».

Fuera de alcance: guardar las versiones anteriores de las reglas para recalcular con ellas (el recálculo usa las
vigentes y muestra la diferencia); recalcular un rechazo, porque no guarda el puntaje del buró (se respeta CTL-03 de HU-WEB-07, decidido por el usuario el 2026-11-15);
reconstruir las entradas de las decisiones anteriores a la migración; borrar o
anonimizar los datos personales de las tablas inmutables al revocar (RISK-01); revocar desde la app o por el
titular; exigir consentimiento al canal de socios cuando no envía `consent_id` (RISK-06).

### Affected paths

| Path | Change |
|---|---|
| backend/app/modules/underwriting/rules.py | `RULES_VERSION = "2026.11"` |
| backend/app/modules/underwriting/models.py | `underwriting_rejections` agrega solo `rules_version`; sigue sin puntaje ni fecha de nacimiento (CTL-03 de HU-WEB-07) |
| backend/app/modules/underwriting/service.py | `record_rejection` guarda la versión de reglas |
| backend/app/modules/underwriting/audit.py | Nuevo: detalle, recálculo de cotizaciones (un rechazo da 409), revocación, enmascarado del documento y eventos `solventa.audit` |
| backend/app/modules/rating/models.py | `quotes` agrega `rules_version`, `insured_amount` y `occupation_risk` y queda inmutable con `make_immutable` |
| backend/app/modules/rating/service.py | `create_quote` guarda las entradas y la versión de la cotización; un `consent_id` revocado da 422 en cualquier canal |
| backend/app/modules/profile/service.py | `revoke_consents_async`, `consent_revoked_async`; la copia en memoria no vale si la base ya no tiene ese consentimiento GRANTED |
| backend/app/bff/experience/operations_router.py | `GET /experience/decisions/{id}`, `POST .../replay`, `POST /experience/consents/revoke`; el 403 de `solventa_operator` es genérico |
| backend/app/migrations/versions/0005_auditoria_de_suscripcion.py | Columnas nuevas y disparador `quotes_no_change`, idempotente y con `downgrade` |
| backend/tests/integration/test_underwriting_audit_api.py | Pruebas de los endpoints, el recálculo, el rechazo sin puntaje, la inmutabilidad, la revocación y los eventos |
| backend/tests/integration/test_migrations.py | La última versión es `0005`; subida desde `0004`, sin columnas de puntaje ni fecha en los rechazos, y reversión |
| web/src/app/core/models/audit.model.ts | Contrato de la auditoría |
| web/src/app/core/audit/audit.service.ts | Llamadas de la auditoría; el documento va en el cuerpo |
| web/src/app/features/auditoria/audit.component.ts | Pantalla «Auditoría»; un rechazo muestra solo lo guardado y, en lugar de «Recalcular», por qué no se recalcula |
| web/src/app/app.routes.ts | Ruta `/auditoria` con `authGuard`, `roleGuard(['analyst'])` y `solventaGuard`, cargada bajo demanda para no crecer el paquete inicial |
| web/src/app/app.component.html | Pestaña «Auditoría» junto a «Socios» |
| web/src/app/app.component.ts | Comentario: las pestañas de Solventa ya son dos |
| web/src/app/core/guards/solventa.guard.ts | Comentario: el guard protege las dos pestañas de Solventa |
| web/src/app/core/operator/operator.service.ts | Comentario: decide si se ven las dos pestañas de Solventa |
| web/src/app/**/*.spec.ts | Pruebas del portal |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Analista de Solventa | Consultar una decisión | 1. Abre «Auditoría». 2. Escribe `QUO-…` o `UWR-…`. 3. Ve entradas, versión de reglas, decisión y desglose por tier o regla y motivo | Formato inválido: aviso junto al campo y sin llamada; inexistente: 404 «No encontramos la decisión.» |
| UC-02 | Analista de Solventa | Recalcular una cotización | 1. Pulsa «Recalcular». 2. Lee «Coincide» o la tabla de diferencias | Un rechazo: no hay botón, la pantalla dice «Los rechazos no guardan el puntaje del buró (HU-WEB-07), así que no se pueden recalcular.» y el servidor responde 409 con ese motivo; cotización anterior a la auditoría: no hay botón y el servidor responde 409 |
| UC-03 | Analista de Solventa | Registrar la revocación del consentimiento | 1. Escribe el documento del titular. 2. Pulsa «Registrar revocación del consentimiento». 3. Confirma con el documento enmascarado. 4. Ve cuántos consentimientos quedaron revocados | Cancela: nada cambia y el foco vuelve al botón; sin consentimientos vigentes: «no tenía consentimientos vigentes» |
| UC-04 | Asesor | Cotizar tras la revocación | 1. Cotiza con el `consent_id` revocado o sin él | 422: el consentimiento fue revocado o no hay uno vigente |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | El analista de un socio o un asesor consulta decisiones de otros socios o revoca consentimientos | Analista de socio, asesor | THR-01 | 403 en cada endpoint; sin sesión 401; el consentimiento sigue vigente | TC-05 |
| AB-02 | Leer el documento completo, la fecha de nacimiento o el puntaje desde la auditoría, la caché o los registros | Analista o atacante con acceso a los registros | THR-02 | Documento enmascarado, `no-store`, eventos sin datos personales; con el consentimiento revocado se omiten | TC-01, TC-07, TC-08 |
| AB-03 | Alterar o borrar una cotización para que una decisión parezca otra | Operador con acceso a la base o código defectuoso | THR-03 | UPDATE y DELETE fallan en la base | TC-06 |
| AB-04 | Seguir cotizando con un consentimiento revocado, por su `consent_id` o por la copia en memoria de otra réplica | Asesor | THR-04 | 422 | TC-07 |
| AB-05 | Recorrer identificadores o inyectar rutas en el id o en el documento | Analista de Solventa | THR-05 | 422 por formato; 404 si no existe | TC-05 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN el analista de Solventa consulta una cotización, THE backend SHALL devolver sus entradas (documento enmascarado, fecha de nacimiento, suma asegurada, riesgo de ocupación y puntaje), la versión de reglas, el estado y los tiers con su desglose y la fecha | STATED |
| AC-02 | WHEN consulta un rechazo, THE backend SHALL devolver solo lo que el rechazo guarda: el documento enmascarado, el monto solicitado, la regla, el motivo, el monto máximo aprobable, la versión de reglas y la fecha, sin campos de puntaje, fecha de nacimiento ni riesgo de ocupación | DERIVED: se respeta CTL-03 de HU-WEB-07, decidido por el usuario el 2026-11-15 |
| AC-03 | WHEN recalcula una cotización activa o degradada sin que cambien las reglas, THE backend SHALL responder `matches: true` sin diferencias, con la edad a la fecha de la decisión | STATED |
| AC-04 | IF una regla o un multiplicador cambió, THEN el recálculo de una cotización SHALL responder `matches: false` con cada diferencia (campo, antes y ahora) y las dos versiones de reglas | STATED |
| AC-05 | IF la decisión es anterior a la auditoría (sin versión de reglas o sin entradas), THEN el detalle SHALL marcarla `replayable: false` y el recálculo de una cotización SHALL responder 409 «Decisión anterior a la auditoría: no se puede recalcular.» | DERIVED: los datos viejos no guardaron las entradas |
| AC-06 | THE base SHALL rechazar UPDATE y DELETE sobre `quotes` y sobre `underwriting_rejections`, también en una base migrada con Alembic | DERIVED: una auditoría sobre registros alterables no prueba nada |
| AC-07 | WHEN el analista registra la revocación del consentimiento de un documento, THE backend SHALL revocar todos sus consentimientos GRANTED, rechazar con 422 toda cotización del asesor con ese consentimiento y omitir en la auditoría el documento de sus decisiones y, en sus cotizaciones, la fecha de nacimiento y el puntaje, con `consent_revoked: true` | STATED |
| AC-08 | THE detalle, el recálculo y la revocación SHALL ser solo para el analista de Solventa: 403 al analista de un socio y al asesor, 401 sin sesión | STATED |
| AC-09 | THE pestaña «Auditoría» SHALL verse solo si `GET /experience/operator` dice `solventa: true`, y su ruta SHALL devolver a `/gestion` al analista de un socio | STATED |
| AC-10 | THE pantalla SHALL validar el identificador antes de llamar, mostrar las entradas y el desglose en tablas, anunciar el resultado del recálculo y pedir confirmación antes de registrar la revocación | STATED |
| AC-11 | THE rechazo SHALL guardar solo la versión de reglas además de lo que ya guardaba, sin puntaje del buró ni fecha de nacimiento; IF se pide recalcular un rechazo, THEN THE backend SHALL responder 409 «Los rechazos no guardan el puntaje del buró (HU-WEB-07), así que no se pueden recalcular.» y el portal SHALL mostrar ese texto en lugar del botón «Recalcular» | DERIVED: se respeta CTL-03 de HU-WEB-07, decidido por el usuario el 2026-11-15 |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | ACCESSIBILITY | Pantalla «Auditoría» | Tablas con `caption` y `th scope`; campos con etiqueta, `aria-invalid` y `aria-errormessage`; resultado del recálculo y de la revocación en `aria-live`; el foco va al panel de confirmación y vuelve al botón que lo abrió o al encabezado; contorno de foco `#0f6d94`; WCAG 2.2 AA |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | Documento y fecha de nacimiento del asegurado en las decisiones |
| Sensitive data (special categories, biometrics, health, financial, minors) | YES | Puntaje del buró de las cotizaciones; los rechazos siguen sin guardarlo |
| Authentication, sessions, credentials or one-time codes | NO | Reutiliza la sesión del analista sin cambios |
| Authorization or access to other users' resources | YES | Solo el analista de Solventa audita decisiones de todos los socios |
| Payments or money movement | NO | No toca cobros |
| Third-party services or integrations | NO | El recálculo no consulta el buró: usa el puntaje guardado |
| Infrastructure, network, IAM, CI/CD or secrets | NO | Sin infraestructura ni secretos nuevos |
| Multiple tenants, partners or organizations | YES | Las decisiones de los socios solo las ve Solventa |
| Storage or processing on a mobile device | NO | Es el portal |
| Web UI or data exposed in the browser | YES | Entradas personales enmascaradas en la pantalla |
| New or updated dependencies | NO | Sin dependencias nuevas |
| Critical paths (sdd/security/critical-paths.md) | YES | backend/app/migrations/** (HIGH) y web/src/app/core/guards/** (MEDIUM) |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | Solo el analista de Solventa (`partner_id` nulo leído de la base) consulta, recalcula y revoca; el portal solo oculta | THR-01 | CTL-01 | HIGH |
| SR-02 | El detalle enmascara el documento, responde `no-store` y no pone datos personales en la URL, el navegador ni los registros | DATA-01, DATA-02, DATA-03, THR-02 | CTL-02, CTL-05, CTL-07 | HIGH |
| SR-03 | `quotes` y `underwriting_rejections` son de solo inserción en la base, también tras la migración | THR-03 | CTL-03 | HIGH |
| SR-04 | Un consentimiento revocado no sirve para cotizar en el canal del asesor ni desde la copia en memoria, y sus decisiones omiten las entradas personales en la auditoría | DATA-01, DATA-02, DATA-04, THR-04 | CTL-04 | HIGH |
| SR-05 | Cada consulta, recálculo y revocación deja un evento en `solventa.audit` con `operator_ref` y sin documento, fecha, puntaje ni correo | THR-01, THR-02 | CTL-05 | MEDIUM |
| SR-06 | El identificador y el documento se validan por formato antes de tocar la base | THR-05 | CTL-06 | MEDIUM |
| SR-07 | `underwriting_rejections` solo agrega `rules_version`: el rechazo no guarda el puntaje del buró ni la fecha de nacimiento, y su detalle no devuelve entradas que no existen | DATA-01, DATA-02, THR-02 | CTL-08 | HIGH |

## 2. Design

### Components

- **Backend:** `rules.RULES_VERSION` identifica las reglas y la tarifa vigentes; quien cambie un cupo o un
  multiplicador la sube. `create_quote` guarda en `quotes` la versión, la suma y el riesgo de ocupación, y
  `record_rejection` guarda en `underwriting_rejections` solo la versión de reglas: el rechazo sigue sin puntaje ni
  fecha de nacimiento (se respeta CTL-03 de HU-WEB-07, decidido por el usuario el 2026-11-15).
  `make_immutable(QuoteDB.__table__)` y la migración `0005` crean `quotes_immutable()` y `quotes_no_change`.
  `app/modules/underwriting/audit.py` tiene la lógica: `detail` elige la tabla por el prefijo (`QUO-` cotización,
  `UWR-` rechazo), enmascara el documento (solo los cuatro últimos); en un rechazo devuelve `RejectionInputs`
  (documento y monto solicitado) con la regla, el motivo, el monto máximo, la versión y la fecha; omite fecha, documento y puntaje si
  `consent_revoked_async` dice que el consentimiento de la decisión está revocado (el de su `consent_id` o, sin él,
  el documento solo tiene revocados); `replay` recalcula con `age_on` a la fecha UTC de la decisión, `evaluate` y
  `build_breakdown` (con `partial_profile` si la cotización está degradada), compara la prima de cada tier y el
  multiplicador y el aporte de cada factor; con un rechazo levanta `RejectionNotReplayableError` (409 con
  `REJECTION_NOT_REPLAYABLE`) sin registrar `DECISION_REPLAYED`; `revoke_consent` llama a
  `profile_service.revoke_consents_async`, que pasa a REVOKED los GRANTED del documento con `FOR UPDATE` y saca la
  copia en memoria. `get_consent_async` ya no devuelve la copia en memoria de un consentimiento que en la base ya no
  está GRANTED (otra réplica). `create_quote` rechaza un `consent_id` revocado en cualquier canal. El router agrega
  tres rutas delgadas con `solventa_operator`.
- **Portal:** `AuditService`, `AuditComponent` en `/auditoria` y la pestaña «Auditoría» junto a «Socios», con
  `OperatorService` y `solventaGuard` sin cambios de lógica. La pantalla valida el id con la misma expresión del
  servidor, muestra las entradas y cada tier en tablas con `caption`, «Recalcular» solo en una cotización con
  `replayable` (en un rechazo, el texto «Los rechazos no guardan el puntaje del buró (HU-WEB-07), así que no se
  pueden recalcular.»), el
  resultado en `aria-live` y la tabla de diferencias; la revocación pide confirmación con el documento enmascarado,
  maneja el foco como «Socios», borra el documento del campo al terminar y vuelve a pedir la decisión abierta.

### Data flows

Asesor → `/experience/quotes` → `create_quote` → `quotes` (con entradas y versión) o `underwriting_rejections` (con la versión, sin puntaje ni fecha de nacimiento).
Analista de Solventa → portal → `/experience/decisions/{id}` → `audit.detail` → respuesta enmascarada `no-store`.
Analista → `/experience/decisions/{id}/replay` → funciones puras → `{matches, differences}` (solo cotizaciones; un rechazo da 409).
Analista → `/experience/consents/revoke` (documento en el cuerpo) → `consents.status = REVOKED` → evento `solventa.audit`.

### Data contract

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| GET /experience/decisions/{id} | — | `{decision_id, kind, created_at, rules_version, consent_revoked, replayable, inputs, outcome: {status, degradation_reason, tiers: [{tier, monthly_premium, breakdown}], rule_id, reason, max_approvable_amount}}`; `inputs` de una cotización `{document_masked, birth_date, insured_amount, occupation_risk, score}` y de un rechazo solo `{document_masked, insured_amount}` (monto solicitado), con `replayable: false`; `no-store`; id `QUO-` o `UWR-` más 12 hexadecimales en mayúsculas o 422; 404; 403 |
| POST /experience/decisions/{id}/replay | — | `{decision_id, matches, differences: [{field, then, now}], rules_version_then, rules_version_now}` para una cotización; 409 «Los rechazos no guardan el puntaje del buró (HU-WEB-07), así que no se pueden recalcular.» para cualquier rechazo; 409 «Decisión anterior a la auditoría: no se puede recalcular.» para una cotización sin entradas; 404; 403 |
| POST /experience/consents/revoke | `{document_number}` (`^[A-Za-z0-9]{4,20}$`) | `{document_masked, revoked}`; idempotente (`revoked: 0`); 422; 403 |
| POST /experience/quotes y /quote-edge/quotes (cambian) | — | 422 «El consentimiento informado del cliente fue revocado.» con un `consent_id` revocado |

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| Reutilizar `solventa_operator`, que lee el socio del usuario en la base en cada pedido | CTL-01 | Confiar en el guard del portal: solo oculta |
| Documento enmascarado a los cuatro últimos y `no-store`; fecha, documento y puntaje omitidos si el consentimiento está revocado | CTL-02, CTL-04 | Devolver el documento completo: no hace falta para auditar la regla |
| El documento de la revocación va en el cuerpo, no en la ruta | CTL-02 | `/consents/{document}/revoke`, como decía el diseño: el documento quedaría en los registros de acceso del ALB, CloudFront y uvicorn |
| `quotes` inmutable con el mismo `make_immutable` y en la migración `0005` | CTL-03 | Solo el disparador de `create_all`: una base migrada con Alembic quedaría sin él |
| Un solo cálculo: el recálculo llama a `build_breakdown`, `age_on` y `evaluate` | CTL-03 | Fórmulas duplicadas en la auditoría: podrían coincidir aunque la tarifa cambiara |
| La copia en memoria del consentimiento no vale si la base lo tiene y no está GRANTED | CTL-04 | Solo sacarla de la memoria al revocar: otras réplicas la seguirían usando |
| Eventos `solventa.audit` con `operator_ref`, el id de la decisión y conteos, sin datos personales | CTL-05 | Registrar el documento revocado: es dato personal |
| El rechazo no guarda el puntaje del buró ni la fecha de nacimiento, solo agrega la versión de reglas, y no se recalcula: se respeta CTL-03 de HU-WEB-07, decidido por el usuario el 2026-11-15 | CTL-08 | Guardar el puntaje y la fecha para recalcular el rechazo: contradice la minimización que decidió HU-WEB-07 y obligaba a reabrir ese paquete cerrado; sin el puntaje tampoco hace falta la fecha |

### Architecture decisions

N/A: reutiliza la arquitectura hexagonal (ADR 0003, 0004), el BFF por canal (ADR 0005), las migraciones
versionadas (ADR 0023) y los componentes con señales del portal (ADR 0008, 0009, 0010).

### Configuration and deployment

- Migración `0005_auditoria_de_suscripcion` (después de `0004`): columnas nulas, sin reescribir datos; disparador
  idempotente; `downgrade` quita el disparador, la función y las columnas. Sin variables nuevas.
- Las decisiones anteriores a la migración quedan con `rules_version` nulo y no se recalculan.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Versión de reglas, columnas nuevas (en los rechazos solo `rules_version`), `create_quote` y `record_rejection` las llenan, `quotes` inmutable y migración `0005` | AC-01, AC-02, AC-06, AC-11, SR-03, SR-07 | CTL-03, CTL-08 | DONE | backend/app/migrations/versions/0005_auditoria_de_suscripcion.py; backend/app/modules/rating/service.py |
| T-02 | Detalle y recálculo de cotizaciones con las funciones puras; el rechazo devuelve solo lo guardado y su recálculo da 409 | AC-01 a AC-05, AC-11, SR-02, SR-07 | CTL-02, CTL-03, CTL-08 | DONE | backend/app/modules/underwriting/audit.py |
| T-03 | Revocación del consentimiento y su efecto en la cotización y en el detalle | AC-07, SR-04 | CTL-04 | DONE | backend/app/modules/profile/service.py; backend/app/modules/underwriting/audit.py |
| T-04 | Endpoints de operaciones solo para Solventa, validación y eventos | AC-08, SR-01, SR-05, SR-06 | CTL-01, CTL-05, CTL-06 | DONE | backend/app/bff/experience/operations_router.py |
| T-05 | Pestaña, ruta y pantalla «Auditoría» | AC-09, AC-10, AC-11, QR-01 | CTL-01, CTL-02, CTL-07 | DONE | web/src/app/features/auditoria/audit.component.ts; web/src/app/core/audit/audit.service.ts |
| T-06 | Pruebas | AC-01 a AC-11, QR-01 | CTL-01 a CTL-08 | DONE | backend/tests/integration/test_underwriting_audit_api.py; web/src/app/features/auditoria/audit.component.spec.ts |
| T-07 | Respetar CTL-03 de HU-WEB-07 (decisión del usuario del 2026-11-15): el rechazo no guarda puntaje ni fecha de nacimiento, HU-WEB-07 queda sin cambios y su TC-03 sigue verificando que no se guarda el puntaje | AC-11, SR-07 | CTL-08 | DONE | backend/tests/integration/test_underwriting_api.py; backend/tests/integration/test_underwriting_audit_api.py |

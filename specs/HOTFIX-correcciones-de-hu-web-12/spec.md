# HOTFIX · Correcciones de HU-WEB-12

| Field | Value |
|---|---|
| ID | HOTFIX |
| Title | Correcciones de HU-WEB-12 |
| User story | none: hallazgos de la revisión del PR #265 (HU-WEB-12, brechas RC-02 a RC-25), fusionado el 2026-11-13 |
| Owner | Juan Camilo Peña |
| Status | IN_REVIEW |
| Created | 2026-11-13 |
| Security profile | HIGH |

## 1. Requirements

### User story

Reporte del incidente (2026-11-13). La revisión del PR #265, ya en `dev`, encontró lo siguiente:

1. **Código fijo que siempre sirve.** `verify_document_otp` acepta `839201` como válido para el consentimiento y para la firma de la póliza, también en producción.
2. **Emisión sin firma.** `issue_policy` emite sin código si el documento es `1020304050`.
3. **El portal no puede emitir.** El portal nunca pide el código de firma. Para cualquier otro cliente la emisión responde 422, y solo funciona el atajo del punto 2.
4. **Registro de socios abierto.** `POST /quote-edge/partners/register` no exige sesión: cualquiera crea socios activos con credenciales, sin validar el correo.
5. **Teléfono fijo y errores internos.** `/policies/otp-request` envía el código a un número fijo si no llega teléfono. Los dos endpoints de código responden `detail=str(exc)`, y un SMS que falla se ignora.
6. **Etiqueta borrada.** Se perdió la etiqueta «Vencida» (`EXPIRED`) de HU-MOV-11.
7. **Quality gate.** Hay código muerto después de un `return` en `RatingService.create_quote`, y SonarCloud falla con confiabilidad «C».
8. **Valores inconsistentes.** El entorno de los socios se guarda como `'PRODUCTION'` por defecto, pero el código usa `production` y `sandbox`.

### Problem and outcome

Problema: dos puertas traseras en la firma y el consentimiento, un registro de socios sin control y la emisión rota en el portal.

Resultado:
- **Códigos:** el consentimiento y la firma solo aceptan el código de un solo uso que se envió al cliente.
- **Portal:** el asesor envía el código de firma, el titular se lo dicta y la póliza se emite. En local el código de prueba se ve en pantalla, como en la app.
- **Socios:** solo el equipo de alianzas de Solventa los registra.
- **Etiqueta:** vuelve «Vencida».
- **SonarCloud:** el quality gate vuelve a pasar.

### Goals and non-goals

Objetivos: lo anterior, sin romper la emisión idempotente (diez emisiones simultáneas dejan una póliza).

Fuera de alcance:
- **El registro en memoria antiguo** (`ProfileService.register_consent`), que ningún endpoint usa;
- **La siembra `seed_default_consents`**, que solo usan las pruebas;
- **El vencimiento duplicado** `PolicyService.expire_outdated_policies`;
- **`create_user`**, que vuelve a escribir los usuarios sembrados;
- **El SMS real (RC-34).**

### Affected paths

| Path | Change |
|---|---|
| backend/app/modules/identity/service.py | Sin código fijo; el código se consume con la fila bloqueada y, en la emisión, dentro de su transacción; un SMS que falla se informa |
| backend/app/modules/policy/service.py | La emisión exige el código de firma; errores del dominio; la emisión simultánea devuelve la póliza ya creada |
| backend/app/bff/experience/router.py | Códigos con teléfono obligatorio, código de prueba solo en desarrollo, 503 si el SMS falla, 422 de firma |
| backend/app/bff/quote_edge/router.py | Registro de socios solo para el equipo de Solventa y correo validado |
| backend/app/modules/rating/service.py | Sin código muerto |
| backend/app/modules/rating/models.py | Entorno del socio en minúsculas por defecto |
| backend/app/main.py | Valor por defecto y datos existentes del entorno en minúsculas |
| backend/tests/** | Códigos reales en las pruebas y pruebas del hotfix |
| web/src/app/features/cotizador/** | Paso de firma antes de emitir y código de prueba en el consentimiento |
| web/src/app/core/** | Respuesta del envío de códigos |
| web/src/app/features/gestion/policy-labels.ts | Etiqueta «Vencida» |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Asesor | Emitir con firma | 1. Pulsa «Enviar código de firma». 2. El titular le dicta el código. 3. Lo escribe y pulsa «Emitir póliza» | Código errado o vencido: 422 con el motivo y puede pedir otro. Sin teléfono del titular: el botón queda deshabilitado |
| UC-02 | Asesor | Registrar el consentimiento | 1. Pide el código. 2. Escribe el que dicta el cliente. 3. Registra | En local, el código de prueba aparece en pantalla |
| UC-03 | Analista de Solventa | Registrar un socio | 1. Envía nombre y correo. 2. Recibe las credenciales de sandbox | Sin sesión: 401. Analista de un socio: 403. Correo inválido: 422 |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Usar el código fijo `839201` para firmar o consentir sin el cliente | Asesor o atacante con sesión | THR-01 | 422 | TC-02 |
| AB-02 | Emitir sin código para el documento de prueba | Asesor | THR-01 | 422 | TC-01 |
| AB-03 | Crear socios con credenciales sin sesión | Atacante en internet | THR-02 | 401 | TC-05 |
| AB-04 | Un analista de un socio registra otros socios | Analista de socio | THR-02 | 403 | TC-05 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | THE consentimiento y la firma SHALL aceptar solo el código de un solo uso enviado a ese documento, que se consume al usarse | STATED |
| AC-02 | IF la emisión no trae el código de firma, THE API SHALL responder 422 para cualquier documento | STATED |
| AC-03 | WHEN el asesor pide el código de firma, THE portal SHALL enviarlo al teléfono del titular y exigir los 6 dígitos para emitir | STATED |
| AC-04 | WHILE el entorno no es producción y el SMS es de consola, THE respuesta SHALL traer el código de prueba y el portal SHALL mostrarlo | DERIVED: sin SMS real el asesor no tendría el código en local; es el mismo criterio del ingreso de la app |
| AC-05 | THE registro de socios SHALL exigir una sesión del equipo de Solventa y un correo válido | STATED |
| AC-06 | IF el SMS no se puede enviar, THE API SHALL responder 503; IF falta el teléfono, 422 | STATED |
| AC-07 | WHEN diez emisiones simultáneas usan el mismo código, THE sistema SHALL dejar una póliza y devolverla a todas | DERIVED: la emisión era idempotente (TC-S1-07) y el código de un solo uso no debe romperlo |
| AC-08 | THE portal SHALL mostrar las pólizas vencidas como «Vencida» y permitir filtrarlas | STATED |
| AC-09 | THE quality gate de SonarCloud SHALL pasar | STATED |

### Quality requirements

N/A: cambia la validación de códigos y la autorización; no hay metas de rendimiento ni de interfaz nuevas.

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | Documento y teléfono del cliente para enviar el código |
| Sensitive data (special categories, biometrics, health, financial, minors) | NO | No cambia datos sensibles |
| Authentication, sessions, credentials or one-time codes | YES | Códigos de consentimiento y firma, y credenciales de socios |
| Authorization or access to other users' resources | YES | Quién registra socios |
| Payments or money movement | NO | No aplica |
| Third-party services or integrations | NO | El SMS ya existe |
| Infrastructure, network, IAM, CI/CD or secrets | NO | No aplica |
| Multiple tenants, partners or organizations | YES | Registro y alcance de socios |
| Storage or processing on a mobile device | NO | No aplica |
| Web UI or data exposed in the browser | YES | Paso de firma y código de prueba en el portal |
| New or updated dependencies | NO | Sin dependencias nuevas |
| Critical paths (sdd/security/critical-paths.md) | YES | backend/app/modules/identity/** |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | Ningún código fijo ni documento exento: solo el código enviado, una vez, con límite de intentos | DATA-01, THR-01 | CTL-01 | HIGH |
| SR-02 | El registro de socios exige el rol analyst sin socio, tomado de la base | DATA-02, THR-02 | CTL-02 | HIGH |
| SR-03 | El código de prueba solo vuelve con SMS de consola fuera de producción | DATA-01, THR-03 | CTL-03 | HIGH |
| SR-04 | Los errores de los códigos no exponen detalles internos | THR-04 | CTL-04 | MEDIUM |

## 2. Design

### Components

Backend:
- **`identity/service.py`:**
  - `verify_document_otp` sin atajos;
  - bloquea la fila del código (`FOR UPDATE`);
  - con `commit=False` deja el consumo en la transacción de la emisión;
  - `request_document_otp` deja propagar `SmsDeliveryError`.
- **`policy/service.py`:**
  - `SignatureRequiredError` e `InvalidSignatureError`;
  - si otra emisión con el mismo código ganó la carrera, devuelve la póliza de la cotización.
- **`bff/experience/router.py`:**
  - `otp_sent` agrega `debug_code` solo con SMS de consola y `debug_enabled`;
  - `send_document_otp` traduce el SMS fallido a 503;
  - `PolicySignatureOtpRequest` exige teléfono;
  - la emisión traduce los errores de firma a 422.
- **`bff/quote_edge/router.py`:** el registro usa `operations_scope` (rol analyst y socio leído de la base) y valida el correo con un patrón.

Portal:
- **Bloque de firma en `cotizador.component.ts`:** enviar o reenviar el código, campo de 6 dígitos y código de prueba en local.
- **Consentimiento:** muestra el código de prueba.
- **`policy-labels.ts`:** con `EXPIRED`.

### Data flows

Asesor → `POST /experience/policies/otp-request` (documento, teléfono) → código cifrado con HMAC en `document_otps` → SMS al titular → el titular dicta el código → `POST /experience/policies` con `otp_code` → se consume en la misma transacción que crea la póliza y su firma.

### Data contract

| Método y ruta | Cambio |
|---|---|
| POST /experience/consent/otp-request | Respuesta con `debug_code` (solo en desarrollo); 503 si el SMS falla |
| POST /experience/policies/otp-request | `phone` obligatorio (422 sin él); `debug_code` en desarrollo; 503 si el SMS falla |
| POST /experience/policies | 422 sin código o con código inválido |
| POST /quote-edge/partners/register | Exige sesión de analista de Solventa (401 o 403) y correo válido (422) |

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| Código de prueba en la respuesta solo con SMS de consola fuera de producción | CTL-03 | Un código fijo: sirve también en producción |
| Consumir el código en la transacción de la emisión con la fila bloqueada | CTL-01 | Confirmar al validar: otra emisión simultánea quedaría sin código y sin póliza |
| Registro de socios para el equipo de Solventa | CTL-02 | Autoservicio abierto con límite de solicitudes: sigue entregando credenciales a cualquiera |
| Patrón simple para el correo | CTL-02 | `EmailStr`: agrega la dependencia `email-validator` |

### Architecture decisions

N/A: corrección dentro de la arquitectura hexagonal existente (ADR 0004). Los servicios lanzan errores del dominio y el router los traduce a HTTP.

### Configuration and deployment

- **Datos:** el arranque pone en minúsculas el entorno de los socios existentes.
- **Variables:** ninguna nueva.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Quitar el código fijo y el documento exento; consumir el código con bloqueo y en la transacción de la emisión | AC-01, AC-02, AC-07, SR-01 | CTL-01 | DONE | backend/app/modules/identity/service.py; backend/app/modules/policy/service.py |
| T-02 | Endpoints de código con teléfono, código de prueba en desarrollo, 503 y 422 sin detalles internos | AC-04, AC-06, SR-03, SR-04 | CTL-03, CTL-04 | DONE | backend/app/bff/experience/router.py |
| T-03 | Registro de socios para el equipo de Solventa con correo válido | AC-05, SR-02 | CTL-02 | DONE | backend/app/bff/quote_edge/router.py |
| T-04 | Paso de firma en el portal, código de prueba y etiqueta «Vencida» | AC-03, AC-04, AC-08 | CTL-03 | DONE | web/src/app/features/cotizador/cotizador.component.ts; web/src/app/features/gestion/policy-labels.ts |
| T-05 | Código muerto y entorno de los socios | AC-09 | CTL-04 | DONE | backend/app/modules/rating/service.py; backend/app/main.py |
| T-06 | Pruebas con códigos reales y del hotfix | AC-01 a AC-09 | CTL-01, CTL-02, CTL-03, CTL-04 | DONE | backend/tests/integration/test_policy_signature.py; backend/tests/integration/test_partner_registration.py |

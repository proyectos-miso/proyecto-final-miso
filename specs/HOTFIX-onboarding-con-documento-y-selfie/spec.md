# HOTFIX · Onboarding con documento y selfie

| Field | Value |
|---|---|
| ID | HOTFIX |
| Title | Onboarding con documento y selfie |
| User story | none: mejora del feature FE-MOV-01 sin historia en el tablero, acordada el 2026-11-14 |
| Owner | Juan Camilo Peña |
| Status | IN_PROGRESS |
| Created | 2026-11-15 |
| Security profile | HIGH |

## 1. Requirements

### User story

Mejora (no es una historia del tablero). FE-MOV-01 pide que una persona pueda crear su cuenta desde la app
verificando su identidad con la foto de su documento y una selfie; hasta ahora las cuentas del asegurado solo
existían por semillas o por `InsuredAuthService.register`, sin endpoint.

**Como** persona que compró un seguro con Solventa **quiero** crear mi cuenta desde la app con mi documento y una
selfie **para** ver mis pólizas y reportar siniestros sin ir a una oficina.

### Problem and outcome

Problema: no hay registro desde la app. Resultado: un flujo de seis pasos (datos, código SMS, foto del documento,
selfie, autorización biométrica y resultado) que crea la cuenta aprobada o en verificación, o la rechaza sin
crearla, guardando las imágenes solo mientras la verificación está pendiente.

### Goals and non-goals

Objetivos: `POST /auth/register/otp` y `POST /auth/register` (multipart), detrás de `REGISTRATION_ENABLED`;
puerto `IdentityVerifier` con un adaptador simulado que solo decide en development, local y test; verificación
diferida que decide en lotes, vence a los 30 días y borra todas las versiones de las imágenes; autorización
biométrica en `consents` y su revocación (`POST /insured/consents/biometric/revoke`, también desde «Avisos» en la
app); migración `0007`; pantallas de la app con la cámara trasera y la frontal; la cuenta en verificación no ve
pólizas ni siniestros.

Fuera de alcance: contratar un proveedor real de verificación; aviso push cuando la verificación diferida decide
(la app no lo promete); prueba de vida (*liveness*); reverso del documento desde la app (el backend lo acepta
opcional); corregir en todo el backend la IP tomada de `X-Forwarded-For` (RISK-06) y el tope del cuerpo de
`POST /insured/claims` (RISK-09).

### Affected paths

| Path | Change |
|---|---|
| backend/app/migrations/versions/0007_verificacion_de_identidad.py | Columnas `document_type`, `verification_status` y `verified_at` en `insured_accounts`; `binding_hash` en `document_otps`; tabla `identity_verifications`; el downgrade borra primero las cuentas no aprobadas y las autorizaciones biométricas |
| backend/app/core/config.py | `REGISTRATION_ENABLED` y la lista de ambientes del simulado |
| backend/app/core/middleware.py | `BodySizeLimitMiddleware`: 413 antes de leer el multipart |
| backend/app/modules/identity/ports.py | Puerto `IdentityVerifier` y `VerificationResult` |
| backend/app/modules/identity/adapters.py | `SimulatedIdentityVerifier` y `get_identity_verifier` con la lista de ambientes |
| backend/app/modules/identity/service_registration.py | Código de registro, registro, verificación diferida en lotes, vencimiento y revocación |
| backend/app/modules/identity/service.py | Propósito `registration`, estados de verificación, código atado al teléfono sin borrar el de otro, cuentas REJECTED y EXPIRED como inexistentes, `duplicate` y `rejected` en el límite por IP |
| backend/app/modules/identity/db_models.py | Columnas nuevas, `binding_hash` e `IdentityVerificationDB` |
| backend/app/modules/identity/models.py | `RegistrationOtpRequest`, `RegistrationData`, `RegistrationResult` |
| backend/app/modules/identity/policy.py | Cinco registros por hora por documento y tres códigos por hora por teléfono |
| backend/app/modules/profile/service.py | Autorización biométrica en `consents`, excluida de Open Finance y de la consulta por `consent_id` |
| backend/app/core/storage.py | `purge`: borra todas las versiones y marcadores de un prefijo |
| backend/app/bff/auth/router.py | `POST /auth/register/otp` y `POST /auth/register` |
| backend/app/bff/insured/router.py | Las rutas de datos exigen identidad aprobada; `POST /insured/consents/biometric/revoke` |
| backend/app/main.py | La tarea diaria vence y decide las verificaciones pendientes; tope del cuerpo del registro |
| backend/tests/** | Pruebas de integración, unitarias y de la migración |
| terraform/modules/ecs/main.tf | El rol de la tarea escribe, lee y borra versiones solo bajo `identidad/` y lista versiones solo con `s3:prefix` en `identidad/` |
| terraform/modules/storage_events/main.tf | Ciclo de vida de `identidad/`: versiones no actuales a 1 día, actuales a 30 días y marcadores vencidos |
| terraform/tests/imagenes_de_identidad.tftest.hcl | Pruebas del permiso y del ciclo de vida |
| movil/app/src/main/java/com/solventa/app/domain/auth/Registration.kt | Contrato del registro y reglas de formato |
| movil/app/src/main/java/com/solventa/app/domain/auth/AuthApi.kt | `requestRegistrationOtp` y `register` |
| movil/app/src/main/java/com/solventa/app/data/auth/HttpAuthApi.kt | Código y multipart en memoria |
| movil/app/src/main/java/com/solventa/app/ui/auth/RegistrationViewModel.kt | Pasos, errores y fotos en memoria |
| movil/app/src/main/java/com/solventa/app/ui/auth/RegistrationScreens.kt | Pantallas del registro |
| movil/app/src/main/java/com/solventa/app/ui/auth/AuthViewModel.kt | Abrir el registro y abrir la sesión de la cuenta nueva |
| movil/app/src/main/java/com/solventa/app/ui/auth/BiometricLoginScreen.kt | Enlace «Crear cuenta» |
| movil/app/src/main/java/com/solventa/app/ui/auth/AlternativeLoginScreen.kt | Enlace «Crear cuenta» |
| movil/app/src/main/java/com/solventa/app/ui/claims/EvidenceCamera.kt | Selector de cámara trasera o frontal; vista previa por cámara; aviso sin la cámara pedida |
| movil/app/src/main/java/com/solventa/app/ui/theme/Theme.kt | Colores del visor de la cámara |
| movil/app/src/main/java/com/solventa/app/ui/settings/NotificationPreferencesScreen.kt | Revocar la autorización biométrica desde «Avisos» |
| movil/app/src/main/java/com/solventa/app/ui/settings/NotificationPreferencesViewModel.kt | Confirmación y llamada de la revocación |
| movil/app/src/main/java/com/solventa/app/domain/policies/PolicyActions.kt | `revokeBiometricConsent` |
| movil/app/src/main/java/com/solventa/app/data/policies/HttpPolicyActionsApi.kt | `POST /insured/consents/biometric/revoke` |
| movil/app/src/main/java/com/solventa/app/data/policies/SessionPolicyActions.kt | La revocación con el token de la sesión |
| movil/app/src/main/java/com/solventa/app/MainActivity.kt | Navegación, cámara frontal, ventana segura del registro y revocación en «Avisos» |
| movil/app/src/main/res/values/strings.xml | Textos |
| movil/app/src/test/** | Pruebas de la app |
| docs/adr/0031-backend-identity-verifier-port.md | ADR Proposed del puerto |
| CHANGELOG.md | Entrada de la mejora |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Persona sin cuenta | Crear la cuenta | 1. Toca «Crear cuenta». 2. Llena sus datos y la clave. 3. Escribe el código del SMS (o lo autocompleta). 4. Toma la foto del documento. 5. Se toma la selfie. 6. Autoriza el tratamiento biométrico. 7. Ve «Su cuenta está lista» y entra; se le ofrece la huella | Verificación pendiente: ve «Estamos verificando su identidad» y entra sin ver pólizas. Rechazada: ve «No pudimos verificar su identidad» sin cuenta. Código inválido: vuelve al paso del código. Sin red: reintenta con las fotos en memoria. Sin cámara frontal: aviso y salida. «Atrás» en el resultado: entra con la cuenta creada. Registro apagado en el ambiente: 404 |
| UC-02 | Sistema | Decidir las verificaciones pendientes | 1. La tarea diaria vence las de más de 30 días. 2. Pide la decisión de las demás al proveedor, en lotes de 20. 3. Guarda el resultado. 4. Borra todas las versiones de las imágenes | Rechazada o vencida: cierra las sesiones, las huellas y la autorización biométrica de la cuenta y libera el documento. Sin decisión: queda pendiente hasta los 30 días |
| UC-03 | Asegurado | Revocar la autorización biométrica | 1. En «Avisos» toca «Revocar autorización» y confirma (o llama al endpoint con su sesión). 2. Se borran las imágenes pendientes y la autorización queda REVOKED | S3 caído: 503 sin cambios y la app avisa |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Registrarse con el documento de otra persona para ver sus pólizas | Persona maliciosa | THR-01 | Sin verificación aprobada no se ve nada del documento (403); con el documento ya registrado, 409 genérico | TC-03, TC-05 |
| AB-02 | Descubrir qué correos o documentos tienen cuenta | Persona maliciosa | THR-02 | El 409 no dice cuál dato está repetido; límites por documento e IP | TC-05, TC-07 |
| AB-03 | Adivinar el código o pedir códigos sin fin | Persona maliciosa | THR-03 | Código atado al documento y al teléfono, tres intentos, cinco registros por hora por documento y un código por minuto | TC-01, TC-06, TC-07 |
| AB-04 | Subir un archivo que no es foto o muy grande | Persona maliciosa | THR-04 | 422 sin firma JPEG, 413 sobre 2 MB, sin guardar nada | TC-06 |
| AB-05 | Obtener las imágenes de identidad de otra persona | Persona maliciosa o interna | THR-05 | Sin endpoint que las devuelva; solo existen en S3 mientras están pendientes | TC-03, TC-09 |
| AB-06 | Usar una cuenta rechazada después del registro | Persona maliciosa | THR-01 | Sesiones y huellas revocadas; el ingreso, el código, la recuperación y la huella responden como si no existiera | TC-10, TC-22 |
| AB-07 | Ocupar el documento de otra persona con un registro que nunca se aprueba | Persona maliciosa | THR-08 | Una cuenta rechazada, vencida o revocada no ocupa el documento; una pendiente lo ocupa a lo sumo 30 días | TC-20 |
| AB-08 | Pedir códigos de registro sin fin a un teléfono, o con el documento de otra persona para borrar su código | Persona maliciosa | THR-09 | Tres códigos por hora por teléfono; el código de un teléfono no borra el de otro | TC-23 |
| AB-09 | Usar la autorización biométrica como consentimiento para cotizar | Asesor | THR-07 | El `consent_id` biométrico responde 422 | TC-26 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN la persona toca «Crear cuenta», THE app SHALL pedir datos, código SMS, foto del documento con la cámara trasera, selfie con la frontal y la autorización biométrica, en ese orden | STATED |
| AC-02 | WHEN el proveedor aprueba, THE backend SHALL crear la cuenta APPROVED, responder 201 con la sesión y no guardar las imágenes | STATED |
| AC-03 | WHEN el proveedor deja la verificación pendiente o tarda más de 400 ms, THE backend SHALL crear la cuenta PENDING, responder 202 con la sesión y guardar las imágenes en S3 bajo `identidad/` | STATED |
| AC-04 | WHEN el proveedor rechaza, THE backend SHALL responder 422 «No pudimos verificar su identidad.» sin crear la cuenta ni guardar imágenes | STATED |
| AC-05 | IF el correo o el documento ya tienen cuenta, THE backend SHALL responder 409 «No pudimos crear la cuenta con esos datos.» sin decir cuál | STATED |
| AC-06 | IF la clave es débil, el código no es válido para ese documento y teléfono o una foto no es JPEG, THE backend SHALL responder 422 sin crear nada | STATED |
| AC-07 | THE backend SHALL limitar los códigos y los intentos de registro por documento y por IP | STATED |
| AC-08 | THE backend SHALL guardar la autorización biométrica en `consents` con su hash, y la revocación SHALL borrar las imágenes pendientes y marcarla REVOKED | STATED |
| AC-09 | THE verificación diferida SHALL decidir las pendientes y borrar sus imágenes | STATED |
| AC-10 | WHILE la identidad no está aprobada, THE backend SHALL negar con 403 todas las rutas de datos del asegurado: pólizas, siniestros, carátula, renovación, preferencias, token de push y el reporte con el token de acceso o el de reporte | DERIVED: sin verificación, cualquiera podría registrarse con un documento ajeno y ver sus datos |
| AC-11 | WHEN el registro termina aprobado o pendiente, THE app SHALL abrir la sesión como el ingreso con contraseña y ofrecer la huella | STATED |
| AC-12 | THE app SHALL mantener las fotos solo en memoria, con la ventana segura | STATED |
| AC-13 | THE autorización biométrica SHALL no contar como consentimiento Open Finance ni revocarse con él | DERIVED: comparten la tabla `consents` y el cotizador toma cualquier consentimiento vigente del documento |
| AC-14 | WHEN la verificación diferida rechaza una cuenta, THE backend SHALL cerrar sus sesiones, huellas y autorización biométrica, y el ingreso con clave o huella, el código y la recuperación SHALL tratarla como inexistente | DERIVED: una cuenta rechazada no puede seguir usando la sesión del registro |
| AC-15 | WHILE `REGISTRATION_ENABLED` está apagado, THE backend SHALL responder 404 en `POST /auth/register/otp` y `POST /auth/register`; sin valor, SHALL estar encendido solo en development, local y test | DERIVED: sin proveedor real, el registro fuera de desarrollo guardaría datos biométricos sin propósito |
| AC-16 | WHEN una verificación lleva 30 días PENDING, THE backend SHALL marcarla EXPIRED, borrar sus imágenes, cerrar la cuenta y dejar libre el documento | DERIVED: una pendiente no puede guardar datos biométricos ni ocupar un documento indefinidamente |
| AC-17 | THE backend SHALL borrar todas las versiones y marcadores de las imágenes, y el bucket SHALL vencer las versiones no actuales bajo `identidad/` | DERIVED: el bucket tiene versionado; borrar la clave no borra la imagen |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | ACCESSIBILITY | Pantallas del registro | TalkBack lee cada campo, opción, casilla y el obturador con su nombre y estado; objetivos de 48 dp; títulos de pantalla |
| QR-02 | PERFORMANCE | Espera máxima del proveedor | La respuesta del registro no espera al proveedor más de 400 ms; después queda PENDING |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | Nombre, correo, documento y celular |
| Sensitive data (special categories, biometrics, health, financial, minors) | YES | Selfie y foto del documento: datos biométricos |
| Authentication, sessions, credentials or one-time codes | YES | Clave, código SMS y sesión del registro |
| Authorization or access to other users' resources | YES | La cuenta en verificación no ve datos del documento |
| Payments or money movement | NO | No aplica |
| Third-party services or integrations | NO | El proveedor de identidad es simulado; Twilio ya existe |
| Infrastructure, network, IAM, CI/CD or secrets | YES | Permiso del rol de la tarea sobre `identidad/` |
| Multiple tenants, partners or organizations | NO | No aplica |
| Storage or processing on a mobile device | YES | Fotos y datos en memoria en la app |
| Web UI or data exposed in the browser | NO | Es la app |
| New or updated dependencies | NO | Sin dependencias nuevas |
| Critical paths (sdd/security/critical-paths.md) | YES | `**/identity/**`, `**/auth/**`, `**/migrations/**`, `**/*.tf`, `backend/app/main.py` y `MainActivity.kt` |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | Una cuenta sin identidad aprobada no ve pólizas, siniestros ni reporta; una rechazada pierde la sesión | THR-01 | CTL-01, CTL-09 | HIGH |
| SR-02 | El duplicado responde igual para correo y documento | THR-02 | CTL-02 | MEDIUM |
| SR-03 | Código de un solo uso atado al documento y al teléfono, con límites por documento e IP; la clave fuerte y guardada con bcrypt | DATA-03, THR-03 | CTL-03 | HIGH |
| SR-04 | Entrada validada: datos con Pydantic estricto y fotos JPEG de hasta 2 MB | THR-04 | CTL-04 | MEDIUM |
| SR-05 | Las imágenes solo existen en S3 mientras la verificación está pendiente (máximo 30 días), cifradas con la llave del bucket, sin endpoint que las devuelva, y se borran con todas sus versiones al decidir, vencer o revocar | DATA-02, THR-05 | CTL-05, CTL-06, CTL-11 | HIGH |
| SR-06 | La autorización biométrica queda registrada con su hash, es revocable y no vale como Open Finance | DATA-02, DATA-04 | CTL-06, CTL-07 | HIGH |
| SR-07 | Ni los registros ni la auditoría guardan datos personales del registro | DATA-01 | CTL-08 | MEDIUM |
| SR-08 | La app no escribe las fotos ni los datos en disco, registros ni estado guardado, y usa la ventana segura | DATA-02 | CTL-10 | HIGH |
| SR-09 | Fuera de development, local y test el registro está apagado salvo decisión explícita, y el simulado nunca decide | DATA-02, THR-01 | CTL-01, CTL-11 | HIGH |
| SR-10 | Un registro abandonado o rechazado no ocupa el documento; los códigos tienen límite por teléfono y no se borran entre teléfonos; un registro fallido no deja el código reutilizable | THR-08, THR-09 | CTL-03, CTL-13, CTL-14 | MEDIUM |
| SR-11 | El cuerpo del registro tiene un tope antes de leer el multipart | THR-04 | CTL-12 | MEDIUM |

## 2. Design

### Components

- **Backend:** `identity/ports.py` (`IdentityVerifier`, `VerificationResult`); `identity/adapters.py`
  (`SimulatedIdentityVerifier`: termina en 9 → REJECTED, en 8 → PENDING, si no APPROVED; solo decide con
  `ENVIRONMENT` en development, local o test, y en cualquier otro todo queda PENDING);
  `identity/service_registration.py` (`InsuredRegistrationService`: `request_otp`, `register`, `decide_pending`,
  `expire_pending`, `revoke_biometric_consent`). El tiempo de espera de 400 ms (`asyncio.wait_for`) y cualquier
  error del proveedor dejan la verificación PENDING. Unicidad con `pg_advisory_xact_lock` por documento y el índice
  único del correo; el documento solo lo ocupa una cuenta APPROVED o una PENDING de menos de 30 días. La
  autorización biométrica (`ProfileService.add_biometric_consent`) entra en la misma transacción que la cuenta.
  `S3DocumentStorage.purge(prefijo)` lista y borra cada versión y marcador. `insured_account` exige APPROVED;
  `session_account` no, y lo usa la revocación. `registration_open` responde 404 con `REGISTRATION_ENABLED`
  apagado. `BodySizeLimitMiddleware` corta `POST /auth/register` en 7 MB.
- **App:** `RegistrationViewModel` (pasos, errores, fotos y clave en memoria, `takeSession`, `cameraUnavailable`);
  `RegistrationFlow` y sus pantallas, con `key(state.step)` en la cámara, `CameraUnavailableScreen` y
  autocompletar del código (`AutofillType.SmsOtpCode`); `CameraXEvidenceCamera(lens = FRONT)` con
  `remember(this)` y `DisposableEffect(lifecycleOwner, this)`; `AuthViewModel.openRegistration`,
  `closeRegistration` y `completeRegistration`; «Crear cuenta» en las dos pantallas de ingreso; «Revocar
  autorización» en «Avisos».

### Data flows

App → `POST /auth/register/otp` → `document_otps` (HMAC del código, el documento, el tipo y el teléfono, y
`binding_hash` del teléfono) → SMS.
App → `POST /auth/register` (multipart, HTTPS, máximo 7 MB) → verificación del código → proveedor (≤ 400 ms) →
`insured_accounts`, `identity_verifications`, `consents` y, solo si queda PENDING, S3 `identidad/{user}/{verificación}/`.
Tarea diaria → vencimiento (30 días) y proveedor → `identity_verifications` → borrado de todas las versiones en S3.
App («Avisos») → `POST /insured/consents/biometric/revoke` → borrado en S3 y `consents` REVOKED.

### Data contract

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| POST /auth/register/otp | `{document_type: CC/CE/PA, document_number, phone}`; campos extra 422 | 200 `OtpResponse` (`debug_code` solo en desarrollo con SMS de consola); 404 con el registro apagado; 429 (un código por minuto y cinco por hora por documento, tres por hora por teléfono); 503 |
| POST /auth/register | multipart: `data` (JSON con `email`, `full_name`, `document_type`, `document_number`, `phone`, `password`, `otp_code`, `terms_accepted: true`, `biometric_consent_accepted: true`), `document_front`, `selfie`, `document_back` opcional | 201 `RegistrationResult` (TokenPair + `verification_status`); 202 igual con PENDING; 422 `{detail, code}` con `code` en `INVALID_DATA`, `INVALID_PHOTO`, `WEAK_PASSWORD`, `INVALID_CODE` o `IDENTITY_REJECTED`; 413 `INVALID_PHOTO` (una foto de más de 2 MB o un cuerpo de más de 7 MB); 409 `DUPLICATE`; 404 con el registro apagado; 429; 503 |
| POST /insured/consents/biometric/revoke | — (token del asegurado, aprobado o no) | 200 `MessageResponse`; 401; 503 |
| GET /insured/policies, /claims y demás rutas de datos | — | 403 «Estamos verificando su identidad.» si la cuenta no está APPROVED |

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| La cuenta PENDING recibe sesión pero no ve datos del documento | CTL-01 | Sin sesión hasta aprobar: el diseño pide abrir la sesión; ver datos sin verificar: suplantación |
| Código atado al teléfono con un `binding` en el HMAC del código y un `binding_hash` (HMAC del teléfono) para elegir la fila: cada teléfono tiene su código | CTL-03, CTL-13 | Una fila por documento: pedir un código con el documento ajeno borraba el de la víctima; guardar el teléfono en claro: dato personal |
| Límites y auditoría con seudónimos HMAC del documento y del teléfono | CTL-03, CTL-08, CTL-13 | Guardar el documento o el teléfono en `login_attempts`: dato personal en una tabla de seguridad |
| El simulado solo decide con una lista cerrada de ambientes (development, local, test) | CTL-01, CTL-11 | «Todo menos production»: staging, prod o un nombre mal escrito aprobarían a cualquiera |
| `REGISTRATION_ENABLED` encendido por defecto solo en esa lista; apagado responde 404 | CTL-11 | Registro abierto en producción con el simulado: guardaría datos biométricos que nadie decide |
| Vencimiento de las pendientes a los 30 días, en la tarea diaria y en la consulta de duplicados | CTL-11, CTL-14 | Sin vencimiento: imágenes y documentos ocupados para siempre |
| Borrado por versión (`list_object_versions` + `delete_object` con `VersionId`) y ciclo de vida de respaldo bajo `identidad/` | CTL-05, CTL-06 | Suspender el versionado del bucket: lo exige HA-08 para las pólizas; solo ciclo de vida: las imágenes vivirían hasta un día más |
| Con APPROVED la autorización biométrica queda GRANTED; con REJECTED o EXPIRED se revoca | CTL-06, CTL-07 | Revocarla también al aprobar: borraría la constancia de que se autorizó el tratamiento ya hecho; las imágenes ya no existen |
| Tras `IntegrityError` o una falla de S3, el código se consume y el intento se audita en una transacción aparte | CTL-03 | Solo rollback: devolvía el código a la tabla y se podía reintentar sin límite de códigos |
| Tope de 7 MB por `Content-Length` o contando los bytes, antes del parser | CTL-12 | Medir cada foto en la ruta: el multipart ya se leyó completo, con partes en archivos temporales |
| Errores del registro con un `code` estable y un mensaje genérico | CTL-02 | Comparar textos en la app: frágil |

### Architecture decisions

| ADR | Title | Status |
|---|---|---|
| docs/adr/0031-backend-identity-verifier-port.md | Identity verification behind a port, with a simulated adapter | Proposed |

### Configuration and deployment

- Migración `0007`. Su downgrade borra primero las cuentas que no estén APPROVED (con sus verificaciones, huellas
  y códigos por la llave foránea), todas las autorizaciones biométricas y los códigos de registro, y después quita
  las columnas: el código anterior no conoce el estado y mostraría las pólizas del documento a una cuenta sin
  verificar, y tomaría la autorización biométrica como consentimiento Open Finance.
- Variable nueva `REGISTRATION_ENABLED` (opcional): sin valor, encendida solo con `ENVIRONMENT` en development,
  local o test. El adaptador simulado decide solo en esa misma lista.
- Terraform: `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` y `s3:DeleteObjectVersion` sobre `identidad/*` y
  `s3:ListBucketVersions` sobre el bucket con `s3:prefix` en `identidad/` o `identidad/*` para el rol de la tarea;
  `aws_s3_bucket_lifecycle_configuration` del bucket de documentos con dos reglas bajo `identidad/`: versiones no
  actuales a 1 día y actuales a 30 días, y limpieza de marcadores de borrado vencidos.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Migración, puerto, adaptador simulado y servicio de registro | AC-02 a AC-09, AC-13, AC-14, SR-01 a SR-07 | CTL-01 a CTL-09 | DONE | backend/app/modules/identity/service_registration.py; backend/tests/integration/test_insured_registration_api.py |
| T-02 | Endpoints, exigencia de identidad aprobada y revocación | AC-02 a AC-08, AC-10, SR-01, SR-04 | CTL-01, CTL-04, CTL-06 | DONE | backend/app/bff/auth/router.py; backend/app/bff/insured/router.py |
| T-03 | Permiso de S3 en Terraform | SR-05 | CTL-05 | DONE | terraform/tests/imagenes_de_identidad.tftest.hcl |
| T-04 | Pantallas, ViewModel, cámara frontal y cliente HTTP en la app | AC-01, AC-11, AC-12, QR-01, SR-08 | CTL-10 | DONE | movil/app/src/main/java/com/solventa/app/ui/auth/RegistrationScreens.kt |
| T-05 | Prueba manual de punta a punta en DEV | AC-01 a AC-04, AC-11 | — | TODO | — |
| T-06 | Borrado por versión, permisos de versiones y ciclo de vida de `identidad/` | AC-17, SR-05 | CTL-05, CTL-06 | DONE | backend/tests/integration/test_insured_registration_api.py::test_borrar_las_imagenes_quita_todas_las_versiones_y_marcadores_del_prefijo; terraform/tests/imagenes_de_identidad.tftest.hcl::el_bucket_vence_las_imagenes_de_identidad_y_sus_versiones |
| T-07 | `REGISTRATION_ENABLED`, lista de ambientes del simulado y vencimiento a 30 días | AC-15, AC-16, SR-09 | CTL-01, CTL-11 | DONE | backend/tests/integration/test_insured_registration_api.py::test_con_el_registro_apagado_las_dos_rutas_responden_404; backend/tests/unit/test_identity_verifier.py::test_get_identity_verifier_nunca_aprueba_fuera_de_la_lista_de_ambientes |
| T-08 | Pruebas de todas las rutas de datos con una cuenta PENDING y del ingreso de una REJECTED | AC-10, AC-14 | CTL-01, CTL-09 | DONE | backend/tests/integration/test_insured_registration_api.py::test_una_cuenta_pendiente_no_alcanza_ninguna_ruta_de_datos |
| T-09 | Documento ocupado, límite por teléfono, código por teléfono, código consumido tras un rollback, tope del cuerpo, consentimiento biométrico fuera de la cotización, lotes y downgrade | SR-10, SR-11, AC-13 | CTL-03, CTL-07, CTL-12, CTL-13, CTL-14 | DONE | backend/tests/integration/test_insured_registration_api.py; backend/tests/integration/test_migrations.py |
| T-10 | App: vista previa por paso, aviso sin cámara frontal, «Atrás» en el resultado, autocompletar del código, colores del tema, textos y revocación en «Avisos» | AC-01, AC-08, AC-11, QR-01 | CTL-06, CTL-10 | DONE | movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationCameraTest.kt; movil/app/src/test/java/com/solventa/app/ui/settings/NotificationPreferencesScreenTest.kt |

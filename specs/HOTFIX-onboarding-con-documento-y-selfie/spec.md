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

Objetivos: `POST /auth/register/otp` y `POST /auth/register` (multipart); puerto `IdentityVerifier` con un
adaptador simulado; verificación diferida que decide y borra; autorización biométrica en `consents` y su
revocación (`POST /insured/consents/biometric/revoke`); migración `0007`; pantallas de la app con la cámara
trasera y la frontal; la cuenta en verificación no ve pólizas ni siniestros.

Fuera de alcance: contratar un proveedor real de verificación; pantalla de la app para revocar la autorización
(el endpoint existe); aviso push cuando la verificación diferida decide; prueba de vida (*liveness*); reverso del
documento desde la app (el backend lo acepta opcional).

### Affected paths

| Path | Change |
|---|---|
| backend/app/migrations/versions/0007_verificacion_de_identidad.py | Columnas `document_type`, `verification_status` y `verified_at` en `insured_accounts`; tabla `identity_verifications` |
| backend/app/modules/identity/ports.py | Puerto `IdentityVerifier` y `VerificationResult` |
| backend/app/modules/identity/adapters.py | `SimulatedIdentityVerifier` y `get_identity_verifier` |
| backend/app/modules/identity/service_registration.py | Código de registro, registro, verificación diferida y revocación |
| backend/app/modules/identity/service.py | Propósito `registration`, estados de verificación, código atado al teléfono, el ingreso rechaza cuentas REJECTED |
| backend/app/modules/identity/db_models.py | Columnas nuevas e `IdentityVerificationDB` |
| backend/app/modules/identity/models.py | `RegistrationOtpRequest`, `RegistrationData`, `RegistrationResult` |
| backend/app/modules/identity/policy.py | Límite de cinco registros por hora por documento |
| backend/app/modules/profile/service.py | Autorización biométrica en `consents`, excluida de Open Finance |
| backend/app/core/storage.py | `delete` en el almacenamiento de documentos |
| backend/app/bff/auth/router.py | `POST /auth/register/otp` y `POST /auth/register` |
| backend/app/bff/insured/router.py | Las rutas de datos exigen identidad aprobada; `POST /insured/consents/biometric/revoke` |
| backend/app/main.py | La tarea diaria decide las verificaciones pendientes |
| backend/tests/** | Pruebas de integración, unitarias y de la migración |
| terraform/modules/ecs/main.tf | El rol de la tarea escribe, lee y borra solo bajo `identidad/` |
| terraform/tests/imagenes_de_identidad.tftest.hcl | Prueba del permiso |
| movil/app/src/main/java/com/solventa/app/domain/auth/Registration.kt | Contrato del registro y reglas de formato |
| movil/app/src/main/java/com/solventa/app/domain/auth/AuthApi.kt | `requestRegistrationOtp` y `register` |
| movil/app/src/main/java/com/solventa/app/data/auth/HttpAuthApi.kt | Código y multipart en memoria |
| movil/app/src/main/java/com/solventa/app/ui/auth/RegistrationViewModel.kt | Pasos, errores y fotos en memoria |
| movil/app/src/main/java/com/solventa/app/ui/auth/RegistrationScreens.kt | Pantallas del registro |
| movil/app/src/main/java/com/solventa/app/ui/auth/AuthViewModel.kt | Abrir el registro y abrir la sesión de la cuenta nueva |
| movil/app/src/main/java/com/solventa/app/ui/auth/BiometricLoginScreen.kt | Enlace «Crear cuenta» |
| movil/app/src/main/java/com/solventa/app/ui/auth/AlternativeLoginScreen.kt | Enlace «Crear cuenta» |
| movil/app/src/main/java/com/solventa/app/ui/claims/EvidenceCamera.kt | Selector de cámara trasera o frontal |
| movil/app/src/main/java/com/solventa/app/MainActivity.kt | Navegación, cámara frontal y ventana segura del registro |
| movil/app/src/main/res/values/strings.xml | Textos |
| movil/app/src/test/** | Pruebas de la app |
| docs/adr/0031-backend-identity-verifier-port.md | ADR Proposed del puerto |
| CHANGELOG.md | Entrada de la mejora |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Persona sin cuenta | Crear la cuenta | 1. Toca «Crear cuenta». 2. Llena sus datos y la clave. 3. Escribe el código del SMS. 4. Toma la foto del documento. 5. Se toma la selfie. 6. Autoriza el tratamiento biométrico. 7. Ve «Su cuenta está lista» y entra; se le ofrece la huella | Verificación pendiente: ve «Estamos verificando su identidad» y entra sin ver pólizas. Rechazada: ve «No pudimos verificar su identidad» sin cuenta. Código inválido: vuelve al paso del código. Sin red: reintenta con las fotos en memoria |
| UC-02 | Sistema | Decidir las verificaciones pendientes | 1. La tarea diaria pide la decisión al proveedor. 2. Guarda el resultado. 3. Borra las imágenes | Rechazada: cierra las sesiones y las huellas de la cuenta. Sin decisión: queda pendiente |
| UC-03 | Asegurado | Revocar la autorización biométrica | 1. Llama al endpoint con su sesión. 2. Se borran las imágenes pendientes y la autorización queda REVOKED | S3 caído: 503 sin cambios |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Registrarse con el documento de otra persona para ver sus pólizas | Persona maliciosa | THR-01 | Sin verificación aprobada no se ve nada del documento (403); con el documento ya registrado, 409 genérico | TC-03, TC-05 |
| AB-02 | Descubrir qué correos o documentos tienen cuenta | Persona maliciosa | THR-02 | El 409 no dice cuál dato está repetido; límites por documento e IP | TC-05, TC-07 |
| AB-03 | Adivinar el código o pedir códigos sin fin | Persona maliciosa | THR-03 | Código atado al documento y al teléfono, tres intentos, cinco registros por hora por documento y un código por minuto | TC-01, TC-06, TC-07 |
| AB-04 | Subir un archivo que no es foto o muy grande | Persona maliciosa | THR-04 | 422 sin firma JPEG, 413 sobre 2 MB, sin guardar nada | TC-06 |
| AB-05 | Obtener las imágenes de identidad de otra persona | Persona maliciosa o interna | THR-05 | Sin endpoint que las devuelva; solo existen en S3 mientras están pendientes | TC-03, TC-09 |
| AB-06 | Usar una cuenta rechazada después del registro | Persona maliciosa | THR-01 | Sesiones y huellas revocadas; el ingreso responde como si no existiera | TC-10 |

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
| AC-10 | WHILE la identidad no está aprobada, THE backend SHALL negar las pólizas, los siniestros y el reporte con 403 | DERIVED: sin verificación, cualquiera podría registrarse con un documento ajeno y ver sus datos |
| AC-11 | WHEN el registro termina aprobado o pendiente, THE app SHALL abrir la sesión como el ingreso con contraseña y ofrecer la huella | STATED |
| AC-12 | THE app SHALL mantener las fotos solo en memoria, con la ventana segura | STATED |
| AC-13 | THE autorización biométrica SHALL no contar como consentimiento Open Finance ni revocarse con él | DERIVED: comparten la tabla `consents` y el cotizador toma cualquier consentimiento vigente del documento |
| AC-14 | WHEN la verificación diferida rechaza una cuenta, THE backend SHALL cerrar sus sesiones y huellas y el ingreso SHALL tratarla como inexistente | DERIVED: una cuenta rechazada no puede seguir usando la sesión del registro |

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
| SR-05 | Las imágenes solo existen en S3 mientras la verificación está pendiente, cifradas con la llave del bucket, sin endpoint que las devuelva, y se borran al decidir o revocar | DATA-02, THR-05 | CTL-05, CTL-06 | HIGH |
| SR-06 | La autorización biométrica queda registrada con su hash, es revocable y no vale como Open Finance | DATA-02, DATA-04 | CTL-06, CTL-07 | HIGH |
| SR-07 | Ni los registros ni la auditoría guardan datos personales del registro | DATA-01 | CTL-08 | MEDIUM |
| SR-08 | La app no escribe las fotos ni los datos en disco, registros ni estado guardado, y usa la ventana segura | DATA-02 | CTL-10 | HIGH |

## 2. Design

### Components

- **Backend:** `identity/ports.py` (`IdentityVerifier`, `VerificationResult`); `identity/adapters.py`
  (`SimulatedIdentityVerifier`: termina en 9 → REJECTED, en 8 → PENDING, si no APPROVED; fuera de desarrollo
  todo queda PENDING); `identity/service_registration.py` (`InsuredRegistrationService`: `request_otp`,
  `register`, `decide_pending`, `revoke_biometric_consent`). El tiempo de espera de 400 ms (`asyncio.wait_for`) y
  cualquier error del proveedor dejan la verificación PENDING. Unicidad con `pg_advisory_xact_lock` por documento
  y el índice único del correo. La autorización biométrica (`ProfileService.add_biometric_consent`) entra en la
  misma transacción que la cuenta. `S3DocumentStorage.delete`. `insured_account` exige APPROVED;
  `session_account` no, y lo usa la revocación.
- **App:** `RegistrationViewModel` (pasos, errores, fotos y clave en memoria, `takeSession`); `RegistrationFlow`
  y sus pantallas; `CameraXEvidenceCamera(lens = FRONT)`; `AuthViewModel.openRegistration`, `closeRegistration`
  y `completeRegistration`; «Crear cuenta» en las dos pantallas de ingreso.

### Data flows

App → `POST /auth/register/otp` → `document_otps` (HMAC del código, el documento, el tipo y el teléfono) → SMS.
App → `POST /auth/register` (multipart, HTTPS) → verificación del código → proveedor (≤ 400 ms) →
`insured_accounts`, `identity_verifications`, `consents` y, solo si queda PENDING, S3 `identidad/{user}/{verificación}/`.
Tarea diaria → proveedor → `identity_verifications` → borrado en S3.

### Data contract

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| POST /auth/register/otp | `{document_type: CC/CE/PA, document_number, phone}`; campos extra 422 | 200 `OtpResponse` (`debug_code` solo en desarrollo con SMS de consola); 429; 503 |
| POST /auth/register | multipart: `data` (JSON con `email`, `full_name`, `document_type`, `document_number`, `phone`, `password`, `otp_code`, `terms_accepted: true`, `biometric_consent_accepted: true`), `document_front`, `selfie`, `document_back` opcional | 201 `RegistrationResult` (TokenPair + `verification_status`); 202 igual con PENDING; 422 `{detail, code}` con `code` en `INVALID_DATA`, `INVALID_PHOTO`, `WEAK_PASSWORD`, `INVALID_CODE` o `IDENTITY_REJECTED`; 413 `INVALID_PHOTO`; 409 `DUPLICATE`; 429; 503 |
| POST /insured/consents/biometric/revoke | — (token del asegurado, aprobado o no) | 200 `MessageResponse`; 401; 503 |
| GET /insured/policies, /claims y demás rutas de datos | — | 403 «Estamos verificando su identidad.» si la cuenta no está APPROVED |

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| La cuenta PENDING recibe sesión pero no ve datos del documento | CTL-01 | Sin sesión hasta aprobar: el diseño pide abrir la sesión; ver datos sin verificar: suplantación |
| Código atado al teléfono con un `binding` en el HMAC del código | CTL-03 | Guardar el teléfono en `document_otps`: otra columna para lo mismo |
| Límites y auditoría con un seudónimo HMAC del documento | CTL-03, CTL-08 | Guardar el documento en `login_attempts`: dato personal en una tabla de seguridad |
| El simulado nunca aprueba fuera de desarrollo | CTL-01 | Aprobar todo en producción: cualquiera se haría pasar por otro |
| Errores del registro con un `code` estable y un mensaje genérico | CTL-02 | Comparar textos en la app: frágil |

### Architecture decisions

| ADR | Title | Status |
|---|---|---|
| docs/adr/0031-backend-identity-verifier-port.md | Identity verification behind a port, with a simulated adapter | Proposed |

### Configuration and deployment

- Migración `0007`; sin variables nuevas. El adaptador simulado decide solo con `ENVIRONMENT` de desarrollo.
- Terraform: permiso `s3:PutObject`, `s3:GetObject` y `s3:DeleteObject` sobre `identidad/*` para el rol de la tarea.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Migración, puerto, adaptador simulado y servicio de registro | AC-02 a AC-09, AC-13, AC-14, SR-01 a SR-07 | CTL-01 a CTL-09 | DONE | backend/app/modules/identity/service_registration.py; backend/tests/integration/test_insured_registration_api.py |
| T-02 | Endpoints, exigencia de identidad aprobada y revocación | AC-02 a AC-08, AC-10, SR-01, SR-04 | CTL-01, CTL-04, CTL-06 | DONE | backend/app/bff/auth/router.py; backend/app/bff/insured/router.py |
| T-03 | Permiso de S3 en Terraform | SR-05 | CTL-05 | DONE | terraform/tests/imagenes_de_identidad.tftest.hcl |
| T-04 | Pantallas, ViewModel, cámara frontal y cliente HTTP en la app | AC-01, AC-11, AC-12, QR-01, SR-08 | CTL-10 | DONE | movil/app/src/main/java/com/solventa/app/ui/auth/RegistrationScreens.kt |
| T-05 | Prueba manual de punta a punta en DEV | AC-01 a AC-04, AC-11 | — | TODO | — |

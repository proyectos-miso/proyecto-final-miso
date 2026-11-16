# Security and privacy assessment · HOTFIX

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Datos del registro: nombre, correo, tipo y número de documento, celular | CONFIDENTIAL | YES | NO | Crear la cuenta y ligarla a las pólizas del documento |
| DATA-02 | Foto del documento y selfie | RESTRICTED | YES | YES | Verificar que quien se registra es el titular del documento |
| DATA-03 | Clave y código SMS del registro | RESTRICTED | YES | NO | Autenticar a la persona y comprobar el celular |
| DATA-04 | Autorización biométrica (`consents`, `purpose=biometric_verification`) | CONFIDENTIAL | YES | NO | Constancia de que la persona autorizó el tratamiento de DATA-02 |

## 2. Data lifecycle

### DATA-01 · Datos del registro

| Question | Answer |
|---|---|
| What it contains | Nombre, correo, tipo y número de documento y celular con indicativo |
| Why it is needed | AC-02, AC-03; el documento liga la cuenta a sus pólizas |
| Who can access it | El backend; el asegurado ve su nombre en la app |
| Where it is captured | Pantalla de datos del registro en la app |
| How it is transmitted | HTTPS (CRY-01) |
| Where it is stored | `insured_accounts` en PostgreSQL; en la app solo en la memoria del ViewModel |
| Copies, caches and backups | Respaldos de RDS; nada en el `Bundle` ni en preferencias de la app |
| How long it is kept | Mientras exista la cuenta; la cuenta rechazada en el momento no se crea |
| How access is audited | `login_attempts` registra cada intento con un seudónimo HMAC del documento (CTL-08), sin el número |
| How it is deleted or anonymized | Igual que las demás cuentas del asegurado; no hay borrado de cuentas en este cambio |
| What happens if it leaks | Identifica a la persona: impacto medio |
| Transfers to third parties | El celular y el código van a Twilio en el SMS, como en el ingreso; el nombre y el documento irían al proveedor de identidad, hoy simulado |

### DATA-02 · Foto del documento y selfie

| Question | Answer |
|---|---|
| What it contains | Dos JPEG de hasta 2 MB sin EXIF, tomados con la cámara de la app |
| Why it is needed | AC-02 a AC-04: comparar la cara con el documento |
| Who can access it | El backend y el proveedor de identidad; ningún endpoint las devuelve |
| Where it is captured | `CameraXEvidenceCamera` en memoria (`OnImageCapturedCallback`), cámara trasera y frontal |
| How it is transmitted | Multipart por HTTPS (CRY-01) |
| Where it is stored | Solo con PENDING: S3 `identidad/{user_id}/{verification_id}/` cifrado con la llave KMS del bucket (CRY-02) |
| Copies, caches and backups | En la app solo en memoria y se ponen en cero al terminar; en el backend, Starlette puede escribir una parte de más de 1 MB a un archivo temporal que borra al terminar la petición; las versiones del bucket, si están activas, conservan el objeto borrado (RISK-02) |
| How long it is kept | APPROVED o REJECTED: no se guardan. PENDING: hasta la decisión diferida o la revocación |
| How access is audited | CloudTrail de S3 según la configuración del bucket; el registro de la aplicación dice solo el resultado |
| How it is deleted or anonymized | `decide_pending` y `revoke_biometric_consent` borran las claves; la fila queda con `s3_keys` nulo |
| What happens if it leaks | Datos biométricos: suplantación de identidad, impacto crítico |
| Transfers to third parties | Al proveedor de identidad, hoy simulado (RISK-01) |

### DATA-03 · Clave y código SMS

| Question | Answer |
|---|---|
| What it contains | Clave elegida y código de 6 dígitos |
| Why it is needed | Autenticar la cuenta y comprobar el celular |
| Who can access it | Nadie en claro: la clave se guarda con bcrypt y el código como HMAC |
| Where it is captured | Pantallas de datos y de código de la app |
| How it is transmitted | HTTPS (CRY-01) |
| Where it is stored | `insured_accounts.password_hash`; `document_otps.code_hash` hasta su uso o vencimiento (5 min) |
| Copies, caches and backups | En la app solo en memoria; se borran del estado al terminar |
| How long it is kept | El código, 5 minutos o hasta usarse; la clave, mientras exista la cuenta |
| How access is audited | `login_attempts` con el resultado de cada intento |
| How it is deleted or anonymized | El código se borra al usarse, al vencer o al tercer error |
| What happens if it leaks | Toma de la cuenta: impacto alto |
| Transfers to third parties | El código va a Twilio en el SMS |

### DATA-04 · Autorización biométrica

| Question | Answer |
|---|---|
| What it contains | Documento, canal `app`, propósito, alcance, vigencia de 365 días, estado y hash SHA-256 |
| Why it is needed | AC-08: constancia de la autorización y de su revocación |
| Who can access it | El backend |
| Where it is captured | Paso de autorización de la app |
| How it is transmitted | HTTPS dentro del registro |
| Where it is stored | Tabla `consents` |
| Copies, caches and backups | Respaldos de RDS |
| How long it is kept | Sin purga definida, como los demás consentimientos (RISK-03) |
| How access is audited | Sin consulta por endpoint; la revocación cambia el estado |
| How it is deleted or anonymized | No se borra: queda REVOKED |
| What happens if it leaks | Revela que una persona se registró en Solventa: impacto bajo |
| Transfers to third parties | Ninguna |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Persona sin cuenta | anónimo | No confiable | Pedir el código y registrarse |
| Asegurado en verificación | insured, PENDING | Autenticado | Ver su sesión, activar la huella y revocar la autorización; no ve datos del documento |
| Asegurado aprobado | insured, APPROVED | Autenticado | Todo lo de la app |
| Backend | Sistema | Interno | Verificar, guardar, decidir y borrar |
| Proveedor de identidad | Tercero (simulado) | Externo | Comparar las imágenes |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| App → backend | Teléfono e internet | Esquema estricto, firma JPEG y tamaño, código atado al documento y teléfono, límites |
| Backend → proveedor | Backend y tercero | Tiempo máximo de 400 ms; cualquier error deja PENDING |
| Backend → S3 | Backend y AWS | Rol de la tarea limitado a `identidad/*`; cifrado del bucket |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| App | Backend | DATA-01, DATA-02, DATA-03 | HTTPS | CRY-01, CTL-03, CTL-04 |
| Backend | S3 | DATA-02 (solo PENDING) | HTTPS de AWS | CRY-02, CTL-05 |
| Backend | Proveedor (simulado) | DATA-01, DATA-02 | En proceso | CTL-09 |
| Backend | Twilio | Celular y código | HTTPS | CTL-03 |

## 6. Third-party services

| Service | Use | Data shared | Location | Controls |
|---|---|---|---|---|
| Proveedor de verificación de identidad (simulado, sin contrato) | Comparar la selfie con el documento | DATA-01, DATA-02 | UNKNOWN: no hay proveedor elegido | CTL-09 |
| Twilio (sin cambio) | SMS del código | Celular y código | Estados Unidos | CTL-03 |

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| POST /auth/register/otp | Pública | Tipo y número de documento, celular |
| POST /auth/register | Pública | JSON de datos y tres archivos |
| POST /insured/consents/biometric/revoke | Autenticada (insured) | Ninguno además del token |
| Rutas de datos de /insured | Autenticada (insured) | El token de una cuenta PENDING |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | S | Pólizas y siniestros de un documento | Registrarse con un documento ajeno y ver sus datos, o seguir usando una cuenta rechazada | Persona maliciosa | MEDIUM | HIGH | HIGH | CTL-01, CTL-09 |
| THR-02 | I | Existencia de cuentas | Enumerar correos o documentos registrados | Persona maliciosa | MEDIUM | LOW | LOW | CTL-02, CTL-03 |
| THR-03 | S | Celular y código | Adivinar el código o usar el de otro teléfono | Persona maliciosa | MEDIUM | HIGH | HIGH | CTL-03 |
| THR-04 | D | Backend | Archivos enormes o que no son fotos, cuerpos malformados | Persona maliciosa | MEDIUM | MEDIUM | MEDIUM | CTL-04 |
| THR-05 | I | DATA-02 | Leer o conservar las imágenes de identidad más de lo necesario | Persona maliciosa o interna | LOW | CRITICAL | HIGH | CTL-05, CTL-06 |
| THR-06 | I | DATA-01, DATA-02 | Datos personales en registros, auditoría o almacenamiento del teléfono | Interno o malware | LOW | HIGH | MEDIUM | CTL-08, CTL-10 |
| THR-07 | E | Consentimiento Open Finance | La autorización biométrica habilita cotizar con el buró | Asesor | LOW | MEDIUM | LOW | CTL-07 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | `insured_account` y `reporting_account` exigen `verification_status=APPROVED` (403); el simulado fuera de desarrollo nunca aprueba | API | EXISTING | THR-01 | TC-03, TC-11 | T-02 | API1:2023, CWE-639 | — |
| CTL-02 | Duplicado de correo o documento con el mismo 409 y mensaje; bloqueo por documento y unicidad del correo | API | EXISTING | THR-02 | TC-05 | T-01 | CWE-204 | — |
| CTL-03 | Código HMAC atado a documento, tipo y celular, tres intentos, un código por minuto y cinco por hora por documento; cinco registros por hora por documento; límites por IP | BACKEND | EXISTING | THR-02, THR-03 | TC-01, TC-06, TC-07 | T-01 | CWE-307, ASVS 2.7 | — |
| CTL-04 | Pydantic con `extra="forbid"`, patrones y longitudes; JPEG por firma y hasta 2 MB leídos con tope | BACKEND | EXISTING | THR-04 | TC-01, TC-06 | T-02 | CWE-20, CWE-770 | — |
| CTL-05 | Imágenes solo con PENDING, bajo `identidad/`, cifradas por el bucket, sin endpoint de lectura; el rol solo escribe, lee y borra en ese prefijo | INFRA | EXISTING | THR-05 | TC-02, TC-03, TC-04, TC-12 | T-01, T-03 | MASVS-STORAGE, CWE-359 | — |
| CTL-06 | La decisión diferida y la revocación borran las imágenes; la revocación marca REVOKED la autorización y detiene la verificación | DATA | EXISTING | THR-05 | TC-08, TC-09 | T-01 | CWE-359 | — |
| CTL-07 | Autorización biométrica con hash en `consents`, excluida de la consulta y de la revocación Open Finance | DATA | EXISTING | THR-07 | TC-08 | T-01 | — | — |
| CTL-08 | Registros con el resultado solamente; auditoría con un seudónimo HMAC del documento | BACKEND | EXISTING | THR-06 | TC-13 | T-01 | CWE-532 | — |
| CTL-09 | Tiempo máximo de 400 ms y errores del proveedor como PENDING; rechazo diferido revoca sesiones y huellas; el ingreso trata REJECTED como inexistente | BACKEND | EXISTING | THR-01 | TC-04, TC-10 | T-01 | — | — |
| CTL-10 | Fotos y datos solo en memoria en la app (sin disco, registros ni estado guardado), puestas en cero al terminar; ventana segura en release | MOBILE | EXISTING | THR-06 | TC-14, TC-15 | T-04 | MASVS-STORAGE-2, MASVS-PLATFORM-3 | — |

## 10. Cryptography

### CRY-01 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02, DATA-03 |
| Type | TLS |
| Algorithm or service | HTTPS de CloudFront y del ALB (TLS 1.2 o superior); la app exige HTTPS fuera de desarrollo |
| Rationale | Viajan por internet |
| Key management | Certificados de ACM |
| Permissions | Administración de AWS |
| Rotation | La de ACM |
| Recovery | ACM renueva el certificado |
| Failure behavior | Sin TLS no hay conexión |
| Tests | TC-02 |

### CRY-02 · Imágenes en reposo

| Field | Value |
|---|---|
| Protects | DATA-02 |
| Type | AT_REST |
| Algorithm or service | SSE-KMS del bucket de documentos con la llave de almacenamiento |
| Rationale | Datos biométricos mientras la verificación está pendiente |
| Key management | KMS gestionado por Terraform (`storage_kms_key_arn`) |
| Permissions | El rol de la tarea con `kms:GenerateDataKey` y `kms:Decrypt` sobre esa llave |
| Rotation | Rotación automática de KMS |
| Recovery | Sin la llave no se leen; la verificación queda pendiente |
| Failure behavior | Si S3 o KMS fallan, el registro responde 503 y no crea la cuenta |
| Tests | TC-12 |

### CRY-03 · Código y seudónimo

| Field | Value |
|---|---|
| Protects | DATA-03, DATA-01 en la auditoría |
| Type | MAC |
| Algorithm or service | HMAC-SHA256 con `OTP_SECRET_KEY` |
| Rationale | El código y el documento son de baja entropía: un hash simple se revierte |
| Key management | Secreto inyectado en la tarea (sin cambio) |
| Permissions | Backend |
| Rotation | Según la llave de códigos existente |
| Recovery | Cambiar la llave invalida los códigos vigentes y cambia los seudónimos de los límites |
| Failure behavior | Sin llave el backend no arranca |
| Tests | TC-01, TC-13 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| `OTP_SECRET_KEY` (sin cambio; nuevo uso para el seudónimo) | Secretos de la tarea de ECS | Backend | DEV, STAGING, PROD | Manual | Rotar el secreto | El gate de secretos |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-01, CTL-09 | — |
| Secure authentication | YES | CTL-03 | Clave fuerte y código SMS |
| Session management | YES | CTL-09 | La sesión del registro es la misma del ingreso |
| Token expiration | NO | — | Sin cambio |
| Revocation | YES | CTL-09 | El rechazo diferido sube `token_version` y revoca huellas |
| Server-side permission checks | YES | CTL-01 | — |
| Per-resource access (ownership) | YES | CTL-01 | Datos por el documento de la cuenta aprobada |
| Roles | YES | CTL-01 | Solo el rol insured |
| Privilege separation | YES | CTL-01 | PENDING frente a APPROVED |
| Brute-force protection | YES | CTL-03 | — |
| Rate limiting | YES | CTL-03 | — |
| One-time code (OTP) handling | YES | CTL-03 | — |
| User enumeration prevention | YES | CTL-02 | El 409 es igual para correo y documento; el código se envía exista o no la cuenta |
| CSRF protection | NO | — | Endpoints sin cookies |

## 13. Data isolation and multi-tenancy

N/A: el asegurado no es un socio; el aislamiento lo cubre CTL-01.

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | CameraX (`androidx.camera`) | Sin cambio de versión | app | Ahora también la cámara frontal para la selfie | MEDIUM | Revisión manual 2026-11-15 | Sin cambio; evaluada en HU-MOV-08 | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | No hay proveedor real: en desarrollo el simulado aprueba casi todo y fuera de desarrollo deja todo PENDING, así que en producción nadie llega a ver sus pólizas por esta vía; elegir un proveedor exige evaluar contrato, ubicación de los datos y transferencia internacional | THR-01, THR-05 | MEDIUM | HIGH | HIGH | MITIGATE | Alto hasta contratar un proveedor y evaluarlo; propuesta: dejar el registro deshabilitado en producción hasta entonces | OPEN |
| RISK-02 | Si el bucket tiene versiones, borrar la clave deja una versión anterior de las imágenes; y Starlette puede escribir una parte grande del multipart a un archivo temporal del contenedor durante la petición | THR-05 | LOW | HIGH | MEDIUM | MITIGATE | Medio; propuesta: regla de ciclo de vida que expire versiones no actuales bajo `identidad/` y límite de memoria del parser | OPEN |
| RISK-03 | La autorización biométrica y `login_attempts` no tienen retención ni purga definidas | THR-06 | LOW | MEDIUM | LOW | MITIGATE | Bajo; propuesta: definir la retención con el responsable de datos | OPEN |
| RISK-04 | Una verificación pendiente sin respuesta del proveedor nunca vence; y la cuenta PENDING ve la billetera vacía con un aviso genérico de «sin conexión», no uno de verificación | THR-01 | MEDIUM | LOW | LOW | MITIGATE | Bajo; propuesta: vencimiento de las pendientes y un estado propio en la billetera | OPEN |
| RISK-05 | La app no ofrece todavía la pantalla para revocar la autorización: solo existe el endpoint | THR-05 | MEDIUM | MEDIUM | MEDIUM | MITIGATE | Medio; propuesta: botón en ajustes de la app | OPEN |

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Proveedor real de verificación de identidad y su evaluación de privacidad (RISK-01) | Contratar uno; dejar el registro solo en desarrollo | Dejar el registro fuera de producción hasta contratar y evaluar un proveedor | Juan Camilo Peña | NO | PENDING |

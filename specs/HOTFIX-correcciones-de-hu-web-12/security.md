# Security and privacy assessment · HOTFIX

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Código de un solo uso de consentimiento y de firma, ligado al documento y enviado al teléfono del cliente | RESTRICTED | YES | NO | Probar que el cliente autoriza el tratamiento de datos y firma su póliza |
| DATA-02 | Credenciales de un socio nuevo (client_id y secreto) | RESTRICTED | NO | NO | Que el socio cotice en sandbox |

## 2. Data lifecycle

### DATA-01 · Código de un solo uso

| Question | Answer |
|---|---|
| What it contains | Seis dígitos; en la base solo su HMAC-SHA256 con la llave OTP, el documento, el propósito, el vencimiento y los intentos |
| Why it is needed | Consentimiento informado (RC-06) y firma electrónica de la póliza (RC-02) |
| Who can access it | El cliente que lo recibe por SMS; el asesor solo lo escribe cuando el cliente se lo dicta |
| Where it is captured | El backend lo genera al pedirlo el asesor |
| How it is transmitted | SMS al teléfono del cliente; en desarrollo, en la respuesta como código de prueba; el portal lo envía por HTTPS (CRY-01) |
| Where it is stored | Tabla `document_otps`, solo el HMAC |
| Copies, caches and backups | Respaldos de la base, solo con el HMAC |
| How long it is kept | Cinco minutos, o hasta usarlo o agotar los intentos |
| How access is audited | La firma queda en `policy_signatures`, inmutable, con el hash del código |
| How it is deleted or anonymized | Se borra al usarlo, al vencer o al agotar los intentos |
| What happens if it leaks | Permite consentir o firmar en nombre del cliente mientras siga vigente |
| Transfers to third parties | El proveedor de SMS, como en el ingreso de la app |

### DATA-02 · Credenciales del socio

| Question | Answer |
|---|---|
| What it contains | `client_id` y secreto; en la base solo el hash bcrypt del secreto |
| Why it is needed | Autenticar al socio en el canal B2B |
| Who can access it | El analista de Solventa que hace el alta, que se lo entrega al socio |
| Where it is captured | El backend lo genera en el registro |
| How it is transmitted | Una sola vez, en la respuesta del registro por HTTPS (CRY-01) |
| Where it is stored | Tabla `partners`, solo el hash |
| Copies, caches and backups | Respaldos de la base, solo con el hash |
| How long it is kept | Mientras el socio esté activo |
| How access is audited | Eventos de seguridad del canal de socios |
| How it is deleted or anonymized | Se revoca el socio |
| What happens if it leaks | Permite cotizar en sandbox con la cuota del socio |
| Transfers to third parties | Ninguna |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Asesor comercial | advisor | Interno autenticado | Pedir códigos y emitir con el código que dicta el cliente |
| Analista de Solventa | analyst sin socio | Interno autenticado | Registrar socios |
| Analista de un socio | analyst con socio | Externo autenticado | No puede registrar socios |
| Cliente | Sin sesión | Externo | Recibe y dicta el código |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| Portal → backend | Navegador y API | Token de acceso con rol, teléfono y formato del código, alcance del analista desde la base |
| Backend → proveedor de SMS | API y proveedor | Solo el texto con el código |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Portal | Backend | DATA-01 | HTTPS | CRY-01, CTL-01 |
| Backend | Teléfono del cliente | DATA-01 | SMS | CTL-01, CTL-03 |
| Backend | Portal del analista | DATA-02 | HTTPS | CRY-01, CTL-02 |

## 6. Third-party services

N/A: el proveedor de SMS ya existe (HU-MOV-02) y este hotfix no cambia lo que se le envía.

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| POST /experience/consent y /experience/policies | Autenticado (advisor) | El código y el documento |
| POST /experience/consent/otp-request y /experience/policies/otp-request | Autenticado (advisor) | Documento y teléfono |
| POST /quote-edge/partners/register | Autenticado (analyst de Solventa) | Nombre y correo del socio |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | S | DATA-01 | Consentir o firmar sin el cliente con un código fijo o sin código | Asesor o atacante con sesión | HIGH | HIGH | CRITICAL | CTL-01 |
| THR-02 | E | DATA-02 | Crear socios con credenciales sin autorización | Atacante en internet o analista de un socio | HIGH | HIGH | CRITICAL | CTL-02 |
| THR-03 | I | DATA-01 | El código de prueba aparece en producción | Cualquiera con sesión | LOW | HIGH | MEDIUM | CTL-03 |
| THR-04 | I | Backend | Los mensajes de error revelan detalles internos | Atacante con sesión | MEDIUM | LOW | LOW | CTL-04 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | Solo el código enviado, una vez, con límite de intentos, consumido en la transacción de la emisión; sin documentos exentos | BACKEND | EXISTING | THR-01, DATA-01 | TC-01, TC-02, TC-03, TC-04 | T-01 | ASVS V2.7, CWE-798 | — |
| CTL-02 | Registro de socios con rol analyst y socio leído de la base; correo validado | API | EXISTING | THR-02, DATA-02 | TC-05, TC-06 | T-03 | API5:2023, CWE-862 | — |
| CTL-03 | Código de prueba solo con SMS de consola fuera de producción | BACKEND | EXISTING | THR-03, DATA-01 | TC-07, TC-08 | T-02, T-04 | ASVS V14.3 | — |
| CTL-04 | Errores de los códigos con mensajes fijos para el usuario | API | EXISTING | THR-04 | TC-07, TC-09 | T-02, T-05 | CWE-209 | — |
| CTL-05 | El portal solo muestra el código de prueba cuando la respuesta lo trae y exige los 6 dígitos antes de emitir | WEB | EXISTING | THR-03, THR-01 | TC-08, TC-09 | T-04 | ASVS V14.3 | — |

## 10. Cryptography

### CRY-01 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02 |
| Type | TLS |
| Algorithm or service | HTTPS de CloudFront y del ALB (TLS 1.2 o superior, RC-31), sin cambios |
| Rationale | El hotfix no agrega canales |
| Key management | Certificados administrados por AWS (ACM) |
| Permissions | Solo la administración de AWS |
| Rotation | La de ACM |
| Recovery | ACM renueva el certificado |
| Failure behavior | Sin TLS no hay conexión |
| Tests | TC-01 |

### CRY-02 · HMAC del código

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | MAC |
| Algorithm or service | HMAC-SHA256 con `OTP_SECRET_KEY`, comparado en tiempo constante |
| Rationale | Un volcado de la base no revela los códigos vigentes |
| Key management | `OTP_SECRET_KEY`, secreto de CI en la tarea de ECS |
| Permissions | Solo el backend |
| Rotation | Rotar la llave invalida los códigos vigentes, que duran 5 minutos |
| Recovery | El cliente pide otro código |
| Failure behavior | Si no coincide, cuenta un intento y al agotarlos se borra |
| Tests | TC-02, TC-04 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| `OTP_SECRET_KEY` | Secretos de GitHub hacia la tarea de ECS; variable de entorno en local | Backend | Todos | Sin cambio en este hotfix | Rotarla invalida los códigos vigentes | El gate de secretos revisa cada commit |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-02 | — |
| Secure authentication | YES | CTL-01 | — |
| Session management | NO | — | No cambia |
| Token expiration | NO | — | No cambia |
| Revocation | NO | — | No cambia |
| Server-side permission checks | YES | CTL-02 | — |
| Per-resource access (ownership) | NO | — | No cambia |
| Roles | YES | CTL-02 | — |
| Privilege separation | YES | CTL-02 | — |
| Brute-force protection | YES | CTL-01 | — |
| Rate limiting | NO | — | El límite de intentos por código ya existe |
| One-time code (OTP) handling | YES | CTL-01, CTL-03, CTL-05 | — |
| User enumeration prevention | NO | — | Los endpoints son de asesores y analistas autenticados |
| CSRF protection | NO | — | Token en cabecera, sin cookies |

## 13. Data isolation and multi-tenancy

| Check | Applies | Controls | Tests | Rationale |
|---|---|---|---|---|
| Tenant isolation | YES | CTL-02 | TC-05 | — |
| Server-side authorization | YES | CTL-02 | TC-05 | — |
| Horizontal access | NO | — | — | No cambia |
| Vertical access | YES | CTL-02 | TC-05 | — |
| Resource identifiers | NO | — | — | No cambia |
| Queries and filters | NO | — | — | No cambia |
| Shared caches | NO | — | — | Sin caché |
| Storage and logs | NO | — | — | No cambia |
| Negative cross-tenant tests | YES | CTL-02 | TC-05 | — |

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | FastAPI y Pydantic | Sin cambio de versión | backend | Validar el teléfono, el código y el correo | HIGH | Revisión manual 2026-11-13 | Sin cambio de versión; se descartó agregar `email-validator` | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | Un asesor con el código dictado por el cliente lo usa para otra cotización del mismo cliente | THR-01 | LOW | MEDIUM | LOW | ACCEPT | Bajo: el código vale 5 minutos, una vez, y la firma queda registrada con el asesor | ACCEPTED |

## 16. Pending decisions

N/A: sin decisiones abiertas.

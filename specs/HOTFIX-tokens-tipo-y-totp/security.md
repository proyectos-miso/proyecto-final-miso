# Security and privacy assessment · HOTFIX

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Tokens JWT de acceso y de refresco | CONFIDENTIAL | NO | NO | Autorizar las llamadas a la API |
| DATA-02 | Último paso TOTP aceptado por usuario (`users.last_totp_step`) | INTERNAL | NO | NO | No aceptar dos veces el mismo código |

## 2. Data lifecycle

### DATA-01 · Tokens JWT

| Question | Answer |
|---|---|
| What it contains | Usuario, rol, tipo, vencimiento y, en el canal de socios, el socio; firmados con la llave JWT |
| Why it is needed | Autorizar cada llamada sin volver a pedir credenciales |
| Who can access it | El cliente que lo recibió y el backend que lo valida |
| Where it is captured | Al entrar al portal, a la app o al canal de socios |
| How it is transmitted | Cabecera `Authorization` por HTTPS |
| Where it is stored | En la sesión del navegador, el Keystore de la app o el sistema del socio; el backend no lo guarda |
| Copies, caches and backups | Ninguna en el backend |
| How long it is kept | Hasta su vencimiento: 15 minutos el de acceso y 7 días el de refresco |
| How access is audited | Registros de acceso de la API y eventos de seguridad del canal de socios |
| How it is deleted or anonymized | Vence solo; la app y el portal lo borran al cerrar sesión |
| What happens if it leaks | Acceso con el rol del dueño hasta que venza; por eso solo el de acceso abre la API |
| Transfers to third parties | Ninguna |

### DATA-02 · Último paso TOTP

| Question | Answer |
|---|---|
| What it contains | Un entero: el número de ventana de 30 s del último código aceptado. No guarda el código ni el secreto |
| Why it is needed | Rechazar un código que ya sirvió o uno anterior |
| Who can access it | El backend |
| Where it is captured | En el ingreso del portal |
| How it is transmitted | No sale del backend |
| Where it is stored | Tabla `users` en PostgreSQL |
| Copies, caches and backups | Respaldos de RDS |
| How long it is kept | Lo que dure el usuario; se reemplaza en cada ingreso |
| How access is audited | No aplica: no es un dato personal ni un secreto |
| How it is deleted or anonymized | Con el usuario |
| What happens if it leaks | Nada útil para un atacante: no permite calcular códigos |
| Transfers to third parties | Ninguna |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Asesor o analista | advisor, analyst | Autenticado | Entrar al portal con contraseña y TOTP |
| Socio B2B | partner | Autenticado | Usar el canal de socios con su token |
| Atacante | Ninguno | No confiable | Presentar un token sin tipo o de refresco, o repetir un código TOTP |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| API | Cliente y backend | Firma, vencimiento, `type="access"` y rol del token |
| Ingreso | Portal y backend | Contraseña, código TOTP de un solo uso y bloqueo |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Portal o socio | Backend | DATA-01 | HTTPS por CloudFront | CRY-01, CTL-01 |
| Portal | Backend | Código TOTP | HTTPS por CloudFront | CRY-01, CTL-02 |

## 6. Third-party services

N/A: sin servicios externos.

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| Endpoints con `require_roles` | Pública con token | El token |
| Canal de socios (`/quote-edge`) | Pública con token de socio | El token |
| POST /experience/auth/login | Pública | Usuario, contraseña y código TOTP |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | S, E | DATA-01 | Un token sin tipo o de refresco se usa como token de acceso | Atacante | LOW | HIGH | MEDIUM | CTL-01 |
| THR-02 | S | Cuenta del asesor | Un código TOTP visto u obtenido se repite mientras sigue vigente | Atacante con la contraseña | MEDIUM | HIGH | HIGH | CTL-02 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | `require_roles` y `get_current_partner` exigen `type="access"` | API | EXISTING | THR-01 | TC-01, TC-02 | T-01 | API2:2023, CWE-287 | — |
| CTL-02 | El ingreso acepta un paso TOTP solo si es posterior al último aceptado; si no, responde como código errado | BACKEND | EXISTING | THR-02 | TC-03, TC-04 | T-02 | CWE-294, ASVS V2.8 | — |

## 10. Cryptography

### CRY-01 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01 y el código TOTP |
| Type | TLS |
| Algorithm or service | HTTPS de CloudFront (TLS 1.2 o 1.3), sin cambios |
| Rationale | El hotfix no agrega canales |
| Key management | Certificados administrados por AWS |
| Permissions | Solo el backend y la administración de AWS |
| Rotation | La de AWS |
| Recovery | No aplica |
| Failure behavior | Sin TLS no hay conexión |
| Tests | TC-01 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| Llave JWT (`JWT_SECRET_KEY`) | Secretos de GitHub hacia la tarea de ECS; variable de entorno en local | Backend | Todos | Sin cambio en este hotfix | Rotarla invalida todos los tokens | El gate de secretos revisa cada commit |
| Secreto TOTP de cada usuario | Tabla `users` | Backend | Todos | Sin cambio en este hotfix | Regenerarlo por usuario | El gate de secretos revisa cada commit |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-02 | — |
| Secure authentication | YES | CTL-02 | — |
| Session management | YES | CTL-01 | — |
| Token expiration | NO | — | El vencimiento no cambia |
| Revocation | NO | — | El cierre de sesión con revocación es RC-20 |
| Server-side permission checks | YES | CTL-01 | — |
| Per-resource access (ownership) | NO | — | No cambia |
| Roles | YES | CTL-01 | — |
| Privilege separation | YES | CTL-01 | — |
| Brute-force protection | YES | CTL-02 | — |
| Rate limiting | NO | — | El bloqueo del ingreso ya existe |
| One-time code (OTP) handling | YES | CTL-02 | — |
| User enumeration prevention | YES | CTL-02 | — |
| CSRF protection | NO | — | Token en cabecera, sin cookies |

## 13. Data isolation and multi-tenancy

| Check | Applies | Controls | Tests | Rationale |
|---|---|---|---|---|
| Tenant isolation | YES | CTL-01 | TC-02 | — |
| Server-side authorization | YES | CTL-01 | TC-01, TC-02 | — |
| Horizontal access | NO | — | — | No cambia |
| Vertical access | YES | CTL-01 | TC-01 | — |
| Resource identifiers | NO | — | — | No cambia |
| Queries and filters | NO | — | — | No cambia |
| Shared caches | NO | — | — | Sin caché |
| Storage and logs | NO | — | — | No cambia |
| Negative cross-tenant tests | YES | CTL-01 | TC-02 | — |

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | PyJWT | Sin cambio de versión | backend | Decodificar y validar los tokens | HIGH | Revisión manual 2026-11-04 | Sin cambio de versión | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | Dos ingresos simultáneos con el mismo código podrían leer el mismo último paso antes de guardarlo | THR-02 | LOW | MEDIUM | LOW | ACCEPT | Bajo: el atacante necesita la contraseña y llegar en el mismo instante que el dueño | ACCEPTED |

## 16. Pending decisions

N/A: sin decisiones abiertas.

# Security and privacy assessment · HOTFIX

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Preferencias de avisos (tres booleanos) ligadas al documento del asegurado | CONFIDENTIAL | YES | NO | Enviar solo los avisos que el asegurado quiere |

## 2. Data lifecycle

### DATA-01 · Preferencias de avisos

| Question | Answer |
|---|---|
| What it contains | `claims`, `renewals`, `payments` y la fecha del último cambio, por documento |
| Why it is needed | AC-01 y AC-02 |
| Who can access it | El asegurado, con su sesión |
| Where it is captured | Pantalla «Avisos» de la app |
| How it is transmitted | HTTPS con el token del asegurado (CRY-01) |
| Where it is stored | Tabla `push_preferences` en PostgreSQL; copia en SharedPreferences por cuenta en el teléfono |
| Copies, caches and backups | Respaldos de RDS; `allowBackup` sigue desactivado en la app |
| How long it is kept | Mientras exista la cuenta; la copia del teléfono hasta cerrar sesión |
| How access is audited | Registros de acceso del backend sin datos personales |
| How it is deleted or anonymized | La copia del teléfono se borra al cerrar sesión |
| What happens if it leaks | Revela qué avisos quiere una persona: impacto bajo |
| Transfers to third parties | La categoría viaja en el `data` del push a Firebase; no viaja la preferencia |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Asegurado | insured | Autenticado | Leer y cambiar sus preferencias |
| Backend | Sistema | Interno | Consultar la preferencia antes de cada envío |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| App → backend | Teléfono e internet | Token del asegurado y cuerpo con tres booleanos |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| App | Backend | DATA-01 | HTTPS | CRY-01, CTL-01, CTL-02 |

## 6. Third-party services

N/A: Firebase ya está evaluado en HU-MOV-11 y solo recibe la categoría del aviso.

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| GET y PUT /insured/devices/push-preferences | Autenticado (insured) | Tres booleanos |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | E | DATA-01 | Cambiar o leer las preferencias de otra persona | Asegurado | LOW | LOW | LOW | CTL-01 |
| THR-02 | T | Backend | Cuerpo malformado o tipos inesperados | Asegurado | LOW | LOW | LOW | CTL-02 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | El documento sale de la cuenta del token; los endpoints no reciben identificadores del cliente | API | EXISTING | THR-01, DATA-01 | TC-01 | T-01 | API1:2023 | — |
| CTL-02 | Cuerpo validado por Pydantic; el filtro corre en el servidor antes de llamar a Firebase, y la app repite el filtro para sus avisos locales | BACKEND | EXISTING | THR-02 | TC-01, TC-02 | T-01, T-02 | CWE-20 | — |
| CTL-03 | Copia local por identificador de cuenta (sin correo ni documento), borrada al cerrar sesión, revocar la huella o cambiar la contraseña | MOBILE | EXISTING | DATA-01 | TC-05 | T-02 | MASVS-STORAGE-2 | — |

## 10. Cryptography

### CRY-01 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | TLS |
| Algorithm or service | HTTPS de CloudFront y del ALB (TLS 1.2 o superior) |
| Rationale | Viaja por internet |
| Key management | Certificados de ACM |
| Permissions | Administración de AWS |
| Rotation | La de ACM |
| Recovery | ACM renueva el certificado |
| Failure behavior | Sin TLS no hay conexión |
| Tests | TC-01 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| `FIREBASE_CREDENTIALS_JSON` (sin cambio) | Secretos de GitHub hacia la tarea de ECS | Backend | DEV | Según HU-MOV-11 | Consola de Google Cloud | El gate de secretos |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-01 | — |
| Secure authentication | NO | — | No cambia |
| Session management | NO | — | No cambia |
| Token expiration | NO | — | No cambia |
| Revocation | NO | — | No cambia |
| Server-side permission checks | YES | CTL-01 | — |
| Per-resource access (ownership) | YES | CTL-01 | — |
| Roles | YES | CTL-01 | Solo el rol insured |
| Privilege separation | NO | — | No cambia |
| Brute-force protection | NO | — | Sin credenciales |
| Rate limiting | NO | — | Tres booleanos por cuenta |
| One-time code (OTP) handling | NO | — | No aplica |
| User enumeration prevention | NO | — | Endpoint autenticado |
| CSRF protection | NO | — | Token Bearer, sin cookies |

## 13. Data isolation and multi-tenancy

N/A: el asegurado no es un socio; el aislamiento lo cubre CTL-01.

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | firebase-admin y firebase-messaging | Sin cambio de versión | backend y app | Envío y recepción del push | MEDIUM | Revisión manual 2026-11-14 | Sin cambio; evaluadas en HU-MOV-11 | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | La copia del teléfono queda desactualizada si el cambio se hizo desde otro teléfono | THR-02 | LOW | LOW | LOW | ACCEPT | Bajo: el servidor filtra de todos modos y la app vuelve a leer en cada sincronización | ACCEPTED |
| RISK-02 | Un cambio hecho sin red se pierde si el asegurado cierra sesión antes de recuperar la señal | THR-02 | LOW | LOW | LOW | ACCEPT | Bajo: el cierre de sesión borra la cuenta del teléfono; al volver a entrar se ven las del servidor | ACCEPTED |

## 16. Pending decisions

N/A: sin decisiones abiertas.

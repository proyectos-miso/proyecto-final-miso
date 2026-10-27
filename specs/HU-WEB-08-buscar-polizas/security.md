# Security and privacy assessment · HU-WEB-08

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Datos del titular en la póliza: nombre, tipo y número de documento, correo y teléfono | CONFIDENTIAL | YES | NO | Identificar al titular al atender una consulta (y en HU-WEB-09) |
| DATA-02 | Socio al que pertenece un usuario del portal | INTERNAL | NO | NO | Limitar la consulta a las pólizas del socio |

## 2. Data lifecycle

### DATA-01 · Titular de la póliza

| Question | Answer |
|---|---|
| What it contains | Nombre, tipo y número de documento, correo y teléfono |
| Why it is needed | Atender consultas y mostrar el titular; contacto enmascarado en HU-WEB-09 |
| Who can access it | El backend; en la búsqueda, el analista ve nombre y documento enmascarado; correo y teléfono no salen en la lista |
| Where it is captured | Paso 1 del cotizador, al emitir |
| How it is transmitted | HTTPS por CloudFront y red privada hasta el backend |
| Where it is stored | Tabla `policies` en PostgreSQL |
| Copies, caches and backups | Respaldos de RDS |
| How long it is kept | Lo que dure la póliza más la retención legal que defina la aseguradora (DEC-01) |
| How access is audited | Registros de acceso de la API; la búsqueda no registra el texto buscado |
| How it is deleted or anonymized | Con la póliza, según la retención (DEC-01) |
| What happens if it leaks | Exposición de contacto e identidad de clientes; la lista minimiza lo que muestra |
| Transfers to third parties | Ninguna |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Analista de operaciones | analyst, sin socio | Autenticado | Buscar todas las pólizas |
| Usuario de un socio | analyst, con socio | Autenticado | Buscar solo las pólizas de su socio |
| Asesor | advisor | Autenticado | Cotizar y emitir; no busca (403) |
| Atacante externo | Ninguno | No confiable | Llamar sin token (401) |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| API | Portal y backend | Token, rol analyst, usuario activo y su socio en la base; parámetros con Pydantic |
| Base de datos | Backend y PostgreSQL | Consulta parametrizada con el filtro de socio |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Portal | Backend | Texto de búsqueda | HTTPS por CloudFront | CRY-01, CTL-04 |
| Backend | Portal | DATA-01 (nombre y documento enmascarado) | HTTPS por CloudFront | CRY-01, CTL-01, CTL-03 |
| Portal | Backend | DATA-01 al emitir | HTTPS por CloudFront | CRY-01 |

## 6. Third-party services

N/A: la historia no usa servicios externos.

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| GET /api/v1/experience/policies/search | Autenticada (analyst) | q y status |
| POST /api/v1/experience/policies | Autenticada (advisor) | holder |
| Ruta /gestion del portal | Autenticada | La URL |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | I, E | DATA-01 | Un usuario de un socio ve pólizas de otro socio | Usuario de un socio | MEDIUM | HIGH | HIGH | CTL-01 |
| THR-02 | E | DATA-01 | Un asesor u otro rol usa la búsqueda | Asesor | MEDIUM | MEDIUM | MEDIUM | CTL-02 |
| THR-03 | I | DATA-01 | Listar documentos completos y datos de contacto con búsquedas amplias | Analista | MEDIUM | MEDIUM | MEDIUM | CTL-03 |
| THR-04 | T, I | DATA-01 | Inyección o comodines en el texto de búsqueda | Analista | LOW | HIGH | MEDIUM | CTL-04 |
| THR-05 | S | Cuentas de prueba | Usuarios de prueba con acceso en producción | Atacante externo | LOW | HIGH | MEDIUM | CTL-05 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | El socio del usuario se lee en la base en cada búsqueda y filtra la consulta; un socio revocado no consulta | API, DATA | EXISTING | THR-01 | TC-03 | T-02, T-03 | API1:2023, CWE-639 | — |
| CTL-02 | Rol analyst en el endpoint y guardia por rol en el portal | API, WEB | EXISTING | THR-02 | TC-04 | T-02, T-04 | API5:2023, CWE-285 | — |
| CTL-03 | Documento enmascarado, sin correo ni teléfono, tope de 50 resultados | API | EXISTING | THR-03 | TC-05 | T-03 | API3:2023, CWE-359 | — |
| CTL-04 | Consulta parametrizada, comodines escapados y texto de 80 caracteres como máximo | BACKEND | EXISTING | THR-04 | TC-05 | T-03 | CWE-89, API8:2023 | — |
| CTL-05 | Usuarios de prueba sembrados solo con ENVIRONMENT distinto de production, con la credencial del asesor semilla | BACKEND | EXISTING | THR-05 | TC-07 | T-02 | CWE-798 | — |

## 10. Cryptography

### CRY-01 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | TLS |
| Algorithm or service | HTTPS de CloudFront (TLS 1.2 o 1.3) y red privada hasta el balanceador y RDS, sin cambios |
| Rationale | La historia no agrega canales |
| Key management | Certificados administrados por AWS |
| Permissions | Solo el backend y la administración de AWS |
| Rotation | La de AWS |
| Recovery | No aplica |
| Failure behavior | Sin TLS no hay conexión |
| Tests | TC-01 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| Credencial de los usuarios de prueba | La del asesor semilla, ya en el código | Equipo | Solo desarrollo | No aplica | No se siembran en production | El gate de secretos revisa cada commit |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-01 | — |
| Secure authentication | NO | — | El ingreso con contraseña y TOTP no cambia (HU-WEB-13) |
| Session management | NO | — | Sin cambios en la sesión |
| Token expiration | NO | — | Sin cambios en el token |
| Revocation | YES | CTL-01 | — |
| Server-side permission checks | YES | CTL-02 | — |
| Per-resource access (ownership) | YES | CTL-01 | — |
| Roles | YES | CTL-02 | — |
| Privilege separation | YES | CTL-02 | — |
| Brute-force protection | NO | — | El bloqueo del ingreso ya existe (HU-WEB-13) |
| Rate limiting | NO | — | Búsqueda interna con tope de resultados |
| One-time code (OTP) handling | NO | — | Sin cambios en el TOTP |
| User enumeration prevention | NO | — | La búsqueda es para personal autenticado de operaciones |
| CSRF protection | NO | — | Token en cabecera, sin cookies |

## 13. Data isolation and multi-tenancy

| Check | Applies | Controls | Tests | Rationale |
|---|---|---|---|---|
| Tenant isolation | YES | CTL-01 | TC-03 | — |
| Server-side authorization | YES | CTL-01, CTL-02 | TC-03, TC-04 | — |
| Horizontal access | YES | CTL-01 | TC-03 | — |
| Vertical access | YES | CTL-02 | TC-04 | — |
| Resource identifiers | YES | CTL-01 | TC-03 | — |
| Queries and filters | YES | CTL-01, CTL-04 | TC-03, TC-05 | — |
| Shared caches | NO | — | — | La búsqueda no usa caché |
| Storage and logs | YES | CTL-03 | TC-05 | — |
| Negative cross-tenant tests | YES | CTL-01 | TC-03 | — |

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | sqlalchemy | 2.0.x (sin cambio) | backend | Consulta parametrizada de la búsqueda | HIGH | Revisión manual 2026-10-27 | Sin cambio de versión | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | Las bases existentes necesitan las columnas nuevas antes de desplegar, porque no hay migraciones | THR-01 | MEDIUM | MEDIUM | MEDIUM | MITIGATE | Bajo: el arranque las agrega con ADD COLUMN IF NOT EXISTS | MITIGATED |
| RISK-02 | La búsqueda por contenido del nombre recorre la tabla; con millones de pólizas haría falta un índice de trigramas | THR-03 | LOW | LOW | LOW | MITIGATE | Bajo: índices GIN de trigramas sobre número, documento y titular; la prueba verifica que el plan los usa | MITIGATED |

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Retención de los datos del titular | Plazo legal de la aseguradora | Definirlo con el área legal | Responsable de datos | NO | PENDING |

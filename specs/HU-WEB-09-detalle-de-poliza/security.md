# Security and privacy assessment · HU-WEB-09

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Datos del titular en la póliza: nombre, tipo y número de documento, correo y teléfono | CONFIDENTIAL | YES | NO | Identificar al titular al atender una consulta |
| DATA-02 | Historial de cobros de la póliza: fecha, valor, medio, estado, recibo y motivo de rechazo | CONFIDENTIAL | YES | NO | Responder si la póliza está al día |
| DATA-03 | Siniestros de la póliza: radicado, estado y fecha de reporte | CONFIDENTIAL | YES | NO | Responder por los siniestros sin exponer su descripción |

## 2. Data lifecycle

### DATA-01 · Titular de la póliza

| Question | Answer |
|---|---|
| What it contains | Nombre, tipo y número de documento, correo y teléfono |
| Why it is needed | Reconocer al cliente al atender una consulta |
| Who can access it | El backend completo; el analista ve nombre, tipo de documento y documento, correo y teléfono enmascarados |
| Where it is captured | Paso 1 del cotizador, al emitir (HU-WEB-08) |
| How it is transmitted | HTTPS por CloudFront y red privada hasta el backend |
| Where it is stored | Tabla `policies` en PostgreSQL |
| Copies, caches and backups | Respaldos de RDS; el portal no lo guarda fuera de la memoria de la página |
| How long it is kept | Lo que dure la póliza más la retención legal que defina la aseguradora (DEC-01) |
| How access is audited | Registros de acceso de la API con el número de póliza consultado, sin datos del titular |
| How it is deleted or anonymized | Con la póliza, según la retención (DEC-01) |
| What happens if it leaks | Exposición de contacto e identidad de clientes; el detalle solo deja ver lo enmascarado |
| Transfers to third parties | Ninguna |

### DATA-02 · Historial de cobros

| Question | Answer |
|---|---|
| What it contains | Fecha, valor, moneda, medio (PSE o tarjeta), estado, número de recibo y motivo de rechazo; nunca el token ni la cuenta |
| Why it is needed | Saber si la póliza está al día |
| Who can access it | El backend; el analista lo ve en el detalle |
| Where it is captured | Al cobrar la póliza (canal de socios) |
| How it is transmitted | HTTPS por CloudFront y red privada hasta el backend |
| Where it is stored | Tabla `payment_transactions` en PostgreSQL |
| Copies, caches and backups | Respaldos de RDS |
| How long it is kept | La retención contable que defina la aseguradora (DEC-01) |
| How access is audited | Registros de acceso de la API |
| How it is deleted or anonymized | Según la retención contable (DEC-01) |
| What happens if it leaks | Se sabe qué pagó el cliente y cuándo; no hay datos para cobrarle |
| Transfers to third parties | Ninguna desde esta historia |

### DATA-03 · Siniestros

| Question | Answer |
|---|---|
| What it contains | Radicado, póliza, estado y fecha de reporte |
| Why it is needed | Responder por el estado de un siniestro |
| Who can access it | El backend; el analista lo ve en el detalle |
| Where it is captured | Al reportar el siniestro (HU-MOV-10, todavía no existe) |
| How it is transmitted | HTTPS por CloudFront y red privada hasta el backend |
| Where it is stored | Tabla `claims` en PostgreSQL |
| Copies, caches and backups | Respaldos de RDS |
| How long it is kept | La retención que defina la aseguradora (DEC-01) |
| How access is audited | Registros de acceso de la API |
| How it is deleted or anonymized | Según la retención (DEC-01) |
| What happens if it leaks | Se sabe que un cliente tiene un siniestro y en qué estado; sin descripción ni ubicación |
| Transfers to third parties | Ninguna |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Analista de operaciones | analyst, sin socio | Autenticado | Ver el detalle de cualquier póliza |
| Usuario de un socio | analyst, con socio | Autenticado | Ver el detalle de las pólizas de su socio |
| Asesor | advisor | Autenticado | Cotizar y emitir; no ve el detalle (403) |
| Atacante externo | Ninguno | No confiable | Llamar sin token (401) |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| API | Portal y backend | Token, rol analyst, usuario activo y su socio en la base; número de póliza con patrón |
| Base de datos | Backend y PostgreSQL | Consulta parametrizada con el filtro de socio |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Portal | Backend | Número de póliza | HTTPS por CloudFront | CRY-01, CTL-04 |
| Backend | Portal | DATA-01 enmascarado, DATA-02 y DATA-03 | HTTPS por CloudFront | CRY-01, CTL-01, CTL-03 |

## 6. Third-party services

N/A: la historia no usa servicios externos.

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| GET /api/v1/experience/policies/{policy_number} | Autenticada (analyst) | policy_number |
| Ruta /gestion?poliza= del portal | Autenticada | El parámetro poliza |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | I, E | DATA-01, DATA-02, DATA-03 | Un usuario de un socio ve el detalle de una póliza de otro socio o confirma que existe | Usuario de un socio | MEDIUM | HIGH | HIGH | CTL-01 |
| THR-02 | E | DATA-01, DATA-02, DATA-03 | Un asesor o alguien sin sesión consulta el detalle | Asesor, atacante externo | MEDIUM | MEDIUM | MEDIUM | CTL-02 |
| THR-03 | I | DATA-01, DATA-02, DATA-03 | El detalle expone documento y contacto completos, datos del medio de pago o la descripción del siniestro | Analista | MEDIUM | MEDIUM | MEDIUM | CTL-03 |
| THR-04 | T, I | DATA-01 | Recorrer números consecutivos o inyectar texto en la ruta | Analista | LOW | MEDIUM | LOW | CTL-04, CTL-01 |
| THR-05 | T | Portal | HTML guardado en el nombre del titular se ejecuta en el panel | Asesor al emitir | LOW | HIGH | MEDIUM | CTL-05 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | El socio del usuario se lee en la base y filtra la consulta; fuera del alcance responde 404 como una póliza inexistente; un socio revocado no consulta | API, DATA | EXISTING | THR-01, THR-04 | TC-05 | T-02 | API1:2023, CWE-639 | — |
| CTL-02 | Rol analyst y la dependencia de alcance compartida con la búsqueda | API | EXISTING | THR-02 | TC-06 | T-02 | API5:2023, CWE-285 | — |
| CTL-03 | Documento, correo y teléfono enmascarados en el backend; sin beneficiarios, token ni cuenta; siniestro sin descripción ni ubicación | API | EXISTING | THR-03 | TC-02, TC-07 | T-01, T-02 | API3:2023, CWE-359 | — |
| CTL-04 | Patrón del número de póliza en la ruta y consulta parametrizada | BACKEND | EXISTING | THR-04 | TC-03 | T-02 | CWE-20, CWE-89 | — |
| CTL-05 | El panel usa interpolación de Angular, sin innerHTML | WEB | EXISTING | THR-05 | TC-11 | T-03 | CWE-79 | — |

## 10. Cryptography

### CRY-01 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02, DATA-03 |
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

N/A: la historia no agrega secretos ni llaves.

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
| Rate limiting | NO | — | Consulta interna de personal autenticado |
| One-time code (OTP) handling | NO | — | Sin cambios en el TOTP |
| User enumeration prevention | YES | CTL-01 | — |
| CSRF protection | NO | — | Token en cabecera, sin cookies |

## 13. Data isolation and multi-tenancy

| Check | Applies | Controls | Tests | Rationale |
|---|---|---|---|---|
| Tenant isolation | YES | CTL-01 | TC-05 | — |
| Server-side authorization | YES | CTL-01, CTL-02 | TC-05, TC-06 | — |
| Horizontal access | YES | CTL-01 | TC-05 | — |
| Vertical access | YES | CTL-02 | TC-06 | — |
| Resource identifiers | YES | CTL-01, CTL-04 | TC-03, TC-05 | — |
| Queries and filters | YES | CTL-01, CTL-04 | TC-03, TC-05 | — |
| Shared caches | NO | — | — | El detalle no usa caché |
| Storage and logs | YES | CTL-03 | TC-07 | — |
| Negative cross-tenant tests | YES | CTL-01 | TC-05 | — |

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | sqlalchemy | 2.0.x (sin cambio) | backend | Consulta parametrizada del detalle | HIGH | Revisión manual 2026-10-27 | Sin cambio de versión | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | HU-MOV-10 puede necesitar otra forma para la tabla de siniestros | THR-03 | MEDIUM | LOW | LOW | MITIGATE | Bajo: la tabla es mínima y solo se lee; HU-MOV-10 la amplía con columnas nuevas y el PR deja la nota para David | MITIGATED |

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Retención de los datos del titular, de los cobros y de los siniestros | Plazo legal y contable de la aseguradora | Definirlo con el área legal | Responsable de datos | NO | PENDING |

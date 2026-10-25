# Security and privacy assessment · HOTFIX

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Rutas declaradas en los specs y patrones de rutas críticas | INTERNAL | NO | NO | Decidir qué perfil mínimo exige cada spec |

## 2. Data lifecycle

N/A: DATA-01 es INTERNAL y vive en el repositorio.

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Autor de un spec | Persona o agente del equipo | Confiable con revisión | Declarar rutas en Affected paths |
| Revisor | Persona distinta al autor | Confiable | Aprobar el PR |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| Gate de SDD | Rama y `dev` | El perfil mínimo de las rutas críticas, en los hooks y en el job `sdd-gate` |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| spec.md | Validador | DATA-01 | Lectura local | CTL-01, CTL-02 |

## 6. Third-party services

N/A: sin servicios externos.

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| Tabla Affected paths | Quien escribe un spec | Las rutas declaradas |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | T, E | DATA-01 | Un archivo crítico concreto deja de exigir el perfil de su ruta | Autor de un spec | LOW | HIGH | MEDIUM | CTL-01 |
| THR-02 | T, E | DATA-01 | Una carpeta concreta con rutas críticas deja de exigir su perfil | Autor de un spec | LOW | HIGH | MEDIUM | CTL-02 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | Un archivo concreto se cruza con un patrón crítico por coincidencia exacta del patrón | PROCESS | EXISTING | THR-01 | TC-01 | T-01 | CWE-693 | — |
| CTL-02 | Una carpeta concreta se trata como `carpeta/**` al cruzarla | PROCESS | EXISTING | THR-02 | TC-02 | T-01 | CWE-693 | — |

## 10. Cryptography

### CRY-01 · Transporte del repositorio

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | TLS |
| Algorithm or service | SSH o HTTPS (TLS 1.2 o 1.3) de GitHub, sin cambios |
| Rationale | El validador no cifra ni descifra nada; las rutas viajan con el repositorio por el canal de GitHub. En reposo viven sin cifrar en el repositorio porque no son secretas |
| Key management | Llaves SSH o credenciales de GitHub de cada persona, fuera de este cambio |
| Permissions | Colaboradores del repositorio |
| Rotation | La de cada cuenta de GitHub |
| Recovery | No aplica |
| Failure behavior | Sin canal seguro no hay push ni pull |
| Tests | TC-03 |

## 11. Secrets and keys

N/A: sin secretos.

## 12. Authentication, authorization and sessions

N/A: sin autenticación.

## 13. Data isolation and multi-tenancy

N/A: sin inquilinos.

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | Python (biblioteca estándar: re) | 3.12 | sdd/tools | Expresiones de los patrones de rutas | LOW | Revisión manual 2026-10-24 | Biblioteca estándar sin cambio de versión | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | Una carpeta concreta cuyo último tramo lleva punto (por ejemplo `.github`) se trata como archivo y deja de cruzarse con las rutas que contiene, salvo que se escriba con `/` final | THR-02 | LOW | MEDIUM | LOW | ACCEPT | Bajo: el escaneo de archivos cambiados (`scan paths`) sigue exigiendo el paquete para cada archivo crítico | ACCEPTED |

## 16. Pending decisions

N/A: sin decisiones abiertas.

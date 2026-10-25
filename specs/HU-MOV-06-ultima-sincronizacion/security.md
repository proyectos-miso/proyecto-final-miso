# Security and privacy assessment · HU-MOV-06

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Hora de la última sincronización de la billetera de cada cuenta | INTERNAL | NO | NO | Decir de cuándo es la copia y si puede estar desactualizada |

## 2. Data lifecycle

N/A: DATA-01 es INTERNAL; vive y se borra con la copia cifrada de HU-MOV-04.

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Asegurado | insured | Autenticado | Ver la fecha de su copia y el aviso |
| Quien tenga el teléfono | Ninguno | No confiable | Cambiar el reloj del sistema o tocar los archivos de la app |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| Reloj del sistema | Teléfono y app | La hora guardada no puede quedar en el futuro respecto al reloj |
| Base local cifrada | App y almacenamiento | SQLCipher detecta páginas alteradas (HU-MOV-04) |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Billetera | Base local de la cuenta | DATA-01 | Archivo en el teléfono | CTL-01 |
| Base local de la cuenta | Billetera | DATA-01 | Archivo en el teléfono | CTL-01, CTL-02 |

## 6. Third-party services

N/A: la historia no usa servicios externos.

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| Reloj del sistema | Quien tenga el teléfono desbloqueado | La fecha y hora del teléfono |
| Base local de la billetera | Almacenamiento del teléfono | El archivo, si tiene acceso al almacenamiento |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | T | DATA-01 | Atrasar el reloj para esconder el aviso de una copia vieja | Quien muestra la billetera | MEDIUM | LOW | LOW | CTL-02 |
| THR-02 | T | DATA-01 | Alterar la hora guardada para que la copia parezca reciente | Quien tenga el teléfono | LOW | LOW | LOW | CTL-01 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | La hora vive en la base SQLCipher de la cuenta y se borra con la copia | MOBILE, DATA | EXISTING | THR-02, DATA-01 | TC-04, TC-05 | T-01 | MASVS-STORAGE-1 | — |
| CTL-02 | Una hora guardada en el futuro cuenta como copia desactualizada, también al volver a la app | MOBILE | EXISTING | THR-01 | TC-03, TC-07 | T-02, T-04 | MASVS-RESILIENCE-2 | — |

## 10. Cryptography

### CRY-01 · Base local cifrada (de HU-MOV-04)

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | AT_REST |
| Algorithm or service | SQLCipher 4 con la frase por cuenta envuelta por el Keystore (ADR 0021) |
| Rationale | La hora queda dentro de la misma base cifrada y autenticada que la copia |
| Key management | Sin cambios respecto a HU-MOV-04 |
| Permissions | Solo la app |
| Rotation | Sin cambios respecto a HU-MOV-04 |
| Recovery | Si la base no abre, se borra y se descarga de nuevo con red |
| Failure behavior | La billetera pide conexión |
| Tests | TC-05 |

## 11. Secrets and keys

N/A: no hay secretos ni llaves nuevas.

## 12. Authentication, authorization and sessions

N/A: no cambia el ingreso, la sesión ni los permisos.

## 13. Data isolation and multi-tenancy

N/A: canal del asegurado, sin socios ni inquilinos.

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | net.zetetic:sqlcipher-android | 4.9.0 | movil | Base cifrada donde vive la hora (sin cambio de versión) | CRITICAL | OSV.dev 2026-10-24 | No known vulnerabilities | EVALUATED |
| DEP-02 | androidx.room:room-runtime | 2.6.1 | movil | Lectura y escritura de wallet_meta (sin cambio de versión) | HIGH | OSV.dev 2026-10-24 | No known vulnerabilities | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | Si el reloj se adelanta o atrasa sin llegar a dejar la hora guardada en el futuro, el cálculo de las 24 horas se corre esa misma diferencia | THR-01 | LOW | LOW | LOW | ACCEPT | Bajo: la fecha mostrada sigue siendo la real de la sincronización | ACCEPTED |

## 16. Pending decisions

N/A: el umbral de 24 horas lo fija la historia.

# Security and privacy assessment · HU-WEB-15

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Asegurado y beneficiarios: nombre, tipo y número de documento, parentesco y porcentaje | CONFIDENTIAL | YES | NO | Identificar a quién se le paga y mostrarlo enmascarado |
| DATA-02 | Reporte del siniestro: descripción, fotos, ubicación, hora y huella | RESTRICTED | YES | YES | Dictaminar con soporte probatorio; puede mostrar lesiones o la causa de una muerte |
| DATA-03 | Liquidación y pagos: valor, amparo, beneficiario, recibo y devolución de prima | RESTRICTED | YES | YES | Pagar la indemnización y la devolución |
| DATA-04 | Registro de decisiones: dictamen, endosos, cancelación, responsable y justificación | CONFIDENTIAL | YES | NO | Reconstruir cada decisión (HA-18) |

## 2. Data lifecycle

### DATA-01 · Asegurado y beneficiarios

| Question | Answer |
|---|---|
| What it contains | Nombre, tipo y número de documento, parentesco y porcentaje |
| Why it is needed | Pagar la indemnización a quien corresponde |
| Who can access it | El backend; el analista ve el documento enmascarado |
| Where it is captured | Al emitir en el cotizador y en el endoso de beneficiarios |
| How it is transmitted | HTTPS por CloudFront hasta el backend |
| Where it is stored | `policies.beneficiaries` en PostgreSQL; la carátula no los incluye (HU-WEB-16, AC-07) |
| Copies, caches and backups | Respaldos de RDS |
| How long it is kept | Lo que dure la póliza más la retención legal (DEC-01) |
| How access is audited | Registros de acceso de la API; cada endoso en `policy_events` |
| How it is deleted or anonymized | Con la póliza, según la retención (DEC-01) |
| What happens if it leaks | Se exponen datos de familiares del titular; la pantalla solo muestra el documento enmascarado |
| Transfers to third parties | La pasarela simulada recibe el documento para pagar |

### DATA-02 · Reporte del siniestro

| Question | Answer |
|---|---|
| What it contains | Descripción, de 1 a 5 fotos, ubicación con precisión, si fue simulada, hora y huella SHA-256 |
| Why it is needed | Evaluar el siniestro y detectar fotos cambiadas o ubicaciones falsas |
| Who can access it | El backend y el analista con el alcance de su socio |
| Where it is captured | En la app del asegurado (HU-MOV-08) y se recibe con HU-MOV-09 |
| How it is transmitted | HTTPS; la foto llega al navegador por un enlace prefirmado de 15 minutos |
| Where it is stored | PostgreSQL (`claims`, `claim_photos`) y S3 cifrado con KMS (`siniestros/*`) |
| Copies, caches and backups | Respaldos de RDS y de S3; el expediente responde con `Cache-Control: no-store` |
| How long it is kept | La retención de siniestros de la aseguradora (DEC-01) |
| How access is audited | Registros de acceso de la API y de S3 |
| How it is deleted or anonymized | Según la retención (DEC-01) |
| What happens if it leaks | Exposición de datos de salud y de ubicación; por eso los enlaces vencen y el backend solo puede leer |
| Transfers to third parties | Ninguna |

### DATA-03 · Liquidación y pagos

| Question | Answer |
|---|---|
| What it contains | Valor liquidado, amparo, beneficiario, porcentaje, estado y recibo |
| Why it is needed | Pagar una sola vez y dejar constancia |
| Who can access it | El backend y el analista |
| Where it is captured | Al dictaminar o por la regla paramétrica |
| How it is transmitted | HTTPS; a la pasarela simulada solo van el valor y el documento |
| Where it is stored | `payouts` y `claim_events` en PostgreSQL |
| Copies, caches and backups | Respaldos de RDS |
| How long it is kept | La retención contable (DEC-01) |
| How access is audited | Registro inmutable de la decisión y del pago |
| How it is deleted or anonymized | Según la retención contable (DEC-01) |
| What happens if it leaks | Se sabe cuánto se pagó y a quién; no hay datos de cuentas bancarias |
| Transfers to third parties | La pasarela de pagos (simulada) |

### DATA-04 · Registro de decisiones

| Question | Answer |
|---|---|
| What it contains | Tipo de decisión, responsable, fecha, justificación, valor y antes y después del endoso |
| Why it is needed | Que un dictamen o un endoso no se puedan cambiar y se puedan reconstruir |
| Who can access it | El backend y el analista con su alcance |
| Where it is captured | En cada asignación, dictamen, endoso, cancelación y terminación |
| How it is transmitted | HTTPS |
| Where it is stored | `claim_events` y `policy_events`, de solo inserción |
| Copies, caches and backups | Respaldos de RDS |
| How long it is kept | La retención regulatoria (DEC-01) |
| How access is audited | Es el propio registro de auditoría |
| How it is deleted or anonymized | No se borra: la base rechaza DELETE |
| What happens if it leaks | Se conocen las decisiones internas y quién las tomó |
| Transfers to third parties | Solo cifras agregadas en los reportes al regulador y al reasegurador |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Analista de operaciones | analyst, sin socio | Autenticado | Atender siniestros, operar pólizas y descargar reportes |
| Usuario de un socio | analyst, con socio | Autenticado | Lo mismo, solo con las pólizas de su socio y sin reportes |
| Asesor | advisor | Autenticado | Emitir con beneficiarios; no usa el back office (403) |
| Regla paramétrica | Sistema | Interno | Liquidar un siniestro paramétrico que cumple su condición |
| Atacante | Ninguno | No confiable | Llamar sin token, reutilizar un refresh o cambiar una foto |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| API | Portal y backend | Token de acceso, rol analyst, usuario activo y socio; cuerpos con Pydantic |
| Almacenamiento | Backend y S3 | Rol IAM de solo lectura en `siniestros/*`; huella de cada foto |
| Base de datos | Backend y PostgreSQL | Consultas con el filtro de socio; disparadores de solo inserción |
| Pasarela | Backend y pasarela simulada | Clave por intento y pago único por beneficiario |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Portal | Backend | Dictamen, endosos y cancelación | HTTPS por CloudFront | CRY-01, CTL-02, CTL-03 |
| Backend | Portal | DATA-01 enmascarado y DATA-02 con enlaces prefirmados | HTTPS por CloudFront | CRY-01, CTL-01, CTL-06 |
| S3 | Navegador | Foto del siniestro | HTTPS prefirmado de 15 minutos | CRY-02, CTL-06 |
| Backend | Pasarela simulada | Valor y documento del beneficiario | Llamada interna | CTL-03 |

## 6. Third-party services

N/A: la pasarela de pagos y el oráculo de eventos son simulados dentro del backend; S3 ya existía (HU-WEB-16).

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| /experience/claims/** | Autenticada (analyst) | Radicado, estado, perito, decisión, amparo, valor y justificación |
| /experience/policies/{n}/endorsements, /cancellation, /audit | Autenticada (analyst) | Beneficiarios, suma, justificación y motivo |
| /experience/reports/{periodo} | Autenticada (analyst sin socio) | Periodo y formato |
| /experience/auth/logout | Pública | El refresh |
| POST /experience/policies | Autenticada (advisor) | Beneficiarios |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | I, E | DATA-01, DATA-02 | Un usuario de un socio ve o decide siniestros y pólizas de otro socio | Usuario de un socio | MEDIUM | HIGH | HIGH | CTL-01 |
| THR-02 | T | DATA-03 | Liquidar por encima del amparo, decidir dos veces o pagar dos veces | Analista o reintento | MEDIUM | HIGH | HIGH | CTL-03 |
| THR-03 | T, R | DATA-04 | Cambiar o borrar un dictamen o un endoso para ocultar una decisión | Analista o acceso directo a la base | LOW | HIGH | MEDIUM | CTL-04 |
| THR-04 | T | DATA-02 | Sustituir una foto o fingir la ubicación para cobrar | Asegurado o atacante | MEDIUM | HIGH | HIGH | CTL-05, CTL-09 |
| THR-05 | E | Back office | Un asesor o alguien sin sesión usa los endpoints del analista | Asesor, atacante | MEDIUM | MEDIUM | MEDIUM | CTL-02 |
| THR-06 | T, E | DATA-03 | Provocar un pago paramétrico sin que se cumpla la condición | Atacante | LOW | HIGH | MEDIUM | CTL-07 |
| THR-07 | S | Sesión | Reutilizar un refresh robado después de cerrar sesión | Atacante | LOW | MEDIUM | LOW | CTL-08 |
| THR-08 | I | DATA-01, DATA-02 | Exponer documentos completos, fotos permanentes o datos personales en los reportes | Analista o atacante | MEDIUM | HIGH | HIGH | CTL-06 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | Filtro de socio en cada consulta; fuera del alcance responde 404; reportes solo sin socio | API, DATA | EXISTING | THR-01 | TC-07 | T-01, T-07 | API1:2023, CWE-639 | — |
| CTL-02 | Rol analyst y dependencia de alcance común; patrones en las rutas | API | EXISTING | THR-05 | TC-07 | T-07 | API5:2023, CWE-285 | — |
| CTL-03 | Bloqueo de fila, estados cerrados, valor dentro del amparo, póliza al día y pago único por beneficiario | BACKEND | EXISTING | THR-02 | TC-03, TC-04 | T-02 | CWE-840, HA-11 | — |
| CTL-04 | Disparadores de solo inserción en `claim_events` y `policy_events` | DATA | EXISTING | THR-03 | TC-09 | T-02, T-03 | CWE-284, HA-17 | — |
| CTL-05 | Huella SHA-256 recalculada sobre lo guardado y advertencias de ubicación simulada y hora | BACKEND | EXISTING | THR-04 | TC-02 | T-01 | CWE-345 | — |
| CTL-06 | Documentos enmascarados, enlaces de 15 minutos, `no-store` y reportes agregados | API, WEB | EXISTING | THR-08 | TC-02, TC-06 | T-01, T-04, T-06 | API3:2023, CWE-359 | — |
| CTL-07 | La regla paramétrica verifica la medida, el umbral y la vigencia antes de liquidar | BACKEND | EXISTING | THR-06 | TC-05 | T-02 | HA-20 | — |
| CTL-08 | Refresh con `jti`, rotación al renovar y revocación al cerrar sesión | BACKEND | EXISTING | THR-07 | TC-08 | T-05 | CWE-613, ASVS V3.3 | — |
| CTL-09 | IAM del backend con solo `s3:GetObject` sobre `siniestros/*` | INFRA | EXISTING | THR-04 | TC-02 | T-07 | CWE-732 | — |

## 10. Cryptography

### CRY-01 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02, DATA-03, DATA-04 |
| Type | TLS |
| Algorithm or service | HTTPS de CloudFront (TLS 1.2 o 1.3), sin cambios |
| Rationale | La historia no agrega canales |
| Key management | Certificados administrados por AWS |
| Permissions | Solo el backend y la administración de AWS |
| Rotation | La de AWS |
| Recovery | No aplica |
| Failure behavior | Sin TLS no hay conexión |
| Tests | TC-01 |

### CRY-02 · Fotos en S3

| Field | Value |
|---|---|
| Protects | DATA-02 |
| Type | AT_REST |
| Algorithm or service | SSE-KMS del bucket y URL prefirmada SigV4 de 15 minutos |
| Rationale | Las fotos pueden mostrar datos de salud |
| Key management | Llave KMS del bucket, administrada por Terraform |
| Permissions | El backend solo lee `siniestros/*` |
| Rotation | La de la llave KMS |
| Recovery | No aplica |
| Failure behavior | Sin S3 la foto queda «sin verificar» y sin enlace |
| Tests | TC-02 |

### CRY-03 · Huella de la foto

| Field | Value |
|---|---|
| Protects | DATA-02 |
| Type | Hash |
| Algorithm or service | SHA-256 del JPEG, calculado en el teléfono y recalculado en el backend |
| Rationale | Detectar una foto cambiada después de tomarla |
| Key management | No aplica |
| Permissions | No aplica |
| Rotation | No aplica |
| Recovery | No aplica |
| Failure behavior | Si no coincide, la foto se marca alterada |
| Tests | TC-02 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| Llave JWT | Secretos de GitHub hacia la tarea de ECS | Backend | Todos | Sin cambio | Rotarla invalida todas las sesiones | Gate de secretos en cada commit |
| Llave KMS del bucket | AWS KMS | Rol del backend | Producción | La de AWS | Revocar el permiso del rol | No sale de AWS |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-01 | — |
| Secure authentication | NO | — | El ingreso no cambia |
| Session management | YES | CTL-08 | — |
| Token expiration | NO | — | Los vencimientos no cambian |
| Revocation | YES | CTL-08 | — |
| Server-side permission checks | YES | CTL-02 | — |
| Per-resource access (ownership) | YES | CTL-01 | — |
| Roles | YES | CTL-02 | — |
| Privilege separation | YES | CTL-02 | — |
| Brute-force protection | NO | — | El bloqueo del ingreso ya existe |
| Rate limiting | NO | — | Back office interno de personal autenticado |
| One-time code (OTP) handling | NO | — | Sin cambios en el TOTP |
| User enumeration prevention | YES | CTL-01 | — |
| CSRF protection | NO | — | Token en cabecera, sin cookies |

## 13. Data isolation and multi-tenancy

| Check | Applies | Controls | Tests | Rationale |
|---|---|---|---|---|
| Tenant isolation | YES | CTL-01 | TC-07 | — |
| Server-side authorization | YES | CTL-01, CTL-02 | TC-07 | — |
| Horizontal access | YES | CTL-01 | TC-07 | — |
| Vertical access | YES | CTL-02 | TC-07 | — |
| Resource identifiers | YES | CTL-01, CTL-02 | TC-07 | — |
| Queries and filters | YES | CTL-01 | TC-07 | — |
| Shared caches | YES | CTL-06 | TC-02 | — |
| Storage and logs | YES | CTL-06, CTL-09 | TC-02, TC-06 | — |
| Negative cross-tenant tests | YES | CTL-01 | TC-07 | — |

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | boto3 | Sin cambio (HU-WEB-16) | backend | Leer y firmar las fotos en S3 | HIGH | Revisión manual 2026-11-06 | Sin cambio de versión | EVALUATED |
| DEP-02 | fpdf2 | Sin cambio (HU-WEB-16) | backend | Reporte mensual en PDF | MEDIUM | Revisión manual 2026-11-06 | Sin cambio de versión | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | La ubicación y la hora se pueden falsear en un teléfono con root; el expediente lo advierte pero no lo impide | THR-04 | MEDIUM | MEDIUM | MEDIUM | MITIGATE | Medio: el analista decide con la advertencia y puede asignar un perito | MITIGATED |
| RISK-02 | La firma del evento paramétrico la valida HU-MOV-10, que aún no existe | THR-06 | LOW | HIGH | MEDIUM | MITIGATE | Bajo: la regla vuelve a verificar la condición; HU-MOV-10 debe validar la firma del oráculo | MITIGATED |
| RISK-03 | Cambia el contrato de emisión del portal: los beneficiarios son obligatorios | THR-02 | MEDIUM | LOW | LOW | ACCEPT | Bajo: el portal ya los envía; el canal B2B no cambia | ACCEPTED |

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Retención de siniestros, fotos, pagos y registros de decisiones | Plazos legal, contable y regulatorio | Definirlos con el área legal | Responsable de datos | NO | PENDING |

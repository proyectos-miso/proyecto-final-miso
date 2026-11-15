# Security and privacy assessment · HU-MOV-09

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Reporte del siniestro: póliza, descripción, fecha y radicado | RESTRICTED | YES | YES | Registrar la reclamación; la descripción puede contar lesiones o una muerte |
| DATA-02 | Fotos del siniestro con ubicación, precisión, hora y huella SHA-256 | RESTRICTED | YES | YES | Evidencia de la reclamación (HU-MOV-08, HU-WEB-15) |
| DATA-03 | Token de reporte del asegurado | RESTRICTED | NO | NO | Enviar el reporte con la app cerrada |
| DATA-04 | Llave de firma del APK de release | RESTRICTED | NO | NO | Firmar la app que se distribuye |

## 2. Data lifecycle

### DATA-01, DATA-02 · Reporte y fotos

| Question | Answer |
|---|---|
| What it contains | Póliza, descripción (máximo 500 caracteres), hasta 5 fotos JPEG de máximo 2 MB con ubicación, precisión, hora, origen de la hora, ubicación simulada y huella |
| Why it is needed | AC-01 a AC-08 |
| Who can access it | El asegurado en su teléfono; en el servidor, los analistas con el alcance de HU-WEB-08 |
| Where it is captured | En el teléfono, con la cámara y el GPS (HU-MOV-08) |
| How it is transmitted | HTTPS con el token del asegurado (CRY-01), en un solo envío multiparte |
| Where it is stored | Teléfono: base SQLCipher y archivos AES-GCM por cuenta hasta que el servidor lo recibe (CRY-02). Servidor: PostgreSQL y S3 en `siniestros/<radicado>/`, cifrado con la llave KMS del bucket (CRY-03) |
| Copies, caches and backups | `allowBackup` desactivado en la app; respaldos de RDS y versiones de S3 según la infraestructura |
| How long it is kept | En el teléfono, hasta que el servidor lo recibe o el asegurado lo descarta; en el servidor, según la retención de siniestros |
| How access is audited | Historial inmutable del siniestro (`claim_events`) y el expediente de HU-WEB-15 |
| How it is deleted or anonymized | El teléfono borra la base y las fotos al recibirse el reporte, al descartarlo o al cerrar sesión |
| What happens if it leaks | Expone la salud o la muerte de una persona y su ubicación: impacto alto |
| Transfers to third parties | Ninguna |

### DATA-03 · Token de reporte

| Question | Answer |
|---|---|
| What it contains | JWT con el asegurado, el alcance `claims:submit`, la versión de la cuenta y la familia de la sesión (`sid`) |
| Why it is needed | AC-02: enviar con la app cerrada, cuando no hay sesión en memoria |
| Who can access it | La app, en el teléfono del asegurado |
| Where it is captured | Lo entrega el backend en el ingreso y en cada renovación |
| How it is transmitted | HTTPS (CRY-01) |
| Where it is stored | Teléfono, cifrado con una llave del Keystore sin autenticación del usuario, por cuenta (CRY-04) |
| Copies, caches and backups | Ninguna |
| How long it is kept | 7 días, o hasta cerrar sesión o cambiar la contraseña |
| How access is audited | El siniestro registra que lo envió la app del asegurado |
| How it is deleted or anonymized | El teléfono lo borra al cerrar sesión; el backend lo invalida al revocar la familia (cierre de sesión explícito) o subir la versión de la cuenta (cambio de contraseña); cerrar la app no lo invalida |
| What happens if it leaks | Permite reportar siniestros sobre las pólizas del asegurado durante 7 días; no permite leer nada |
| Transfers to third parties | Ninguna |

### DATA-04 · Llave de firma del APK

| Question | Answer |
|---|---|
| What it contains | Keystore de release con su contraseña y alias |
| Why it is needed | AC-10 |
| Who can access it | El equipo, y el CI al construir |
| Where it is captured | La crea el equipo una vez |
| How it is transmitted | Solo como secreto de GitHub hacia el job |
| Where it is stored | Secretos de GitHub y un archivo temporal del job |
| Copies, caches and backups | La copia que guarde el equipo fuera del repositorio |
| How long it is kept | Mientras se publique la app |
| How access is audited | Registro de ejecuciones de GitHub Actions |
| How it is deleted or anonymized | El archivo temporal se borra al terminar el job |
| What happens if it leaks | Cualquiera podría firmar una app que se haga pasar por Solventa |
| Transfers to third parties | GitHub |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Asegurado | insured | Autenticado | Reportar siniestros sobre sus pólizas vigentes |
| Trabajo de envío de la app | insured (token de reporte) | Autenticado y acotado | Solo enviar el reporte en cola |
| Analista | analyst | Interno | Ver el expediente (HU-WEB-15) |
| CI | Sistema | Interno | Firmar el APK de release |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| App → backend | Teléfono e internet | Token de acceso o de reporte (versión vigente y familia no revocada), póliza propia y vigente, formato, límites, JPEG y huella de cada foto |
| Backend → S3 | API y almacenamiento | Solo escritura y lectura en `siniestros/*` con la llave KMS del bucket |
| GitHub → job de release | Repositorio y runner | Los secretos solo se leen en el job de release |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| App | Backend | DATA-01, DATA-02, DATA-03 | HTTPS multiparte | CRY-01, CTL-01, CTL-02, CTL-03 |
| Backend | S3 | DATA-02 | API de S3 | CRY-03, CTL-06 |
| Backend | App | DATA-03 | HTTPS | CRY-01, CTL-02 |

## 6. Third-party services

N/A: solo usa S3 de AWS, que ya está en la infraestructura (HU-WEB-15, HU-WEB-16).

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| POST /insured/claims | Autenticado (insured, acceso o reporte) | JSON del reporte y hasta 5 archivos |
| Trabajo de envío en el teléfono | Interno | Ninguno |
| Job de release del CI | Interno | Ninguno |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | E | DATA-01 | Reportar sobre la póliza de otra persona o reusar el identificador de un reporte ajeno | Asegurado | MEDIUM | HIGH | HIGH | CTL-01 |
| THR-02 | T | DATA-02 | Cambiar una foto después de capturarla o en el camino | Asegurado o intermediario | MEDIUM | HIGH | HIGH | CTL-03 |
| THR-03 | T | DATA-01 | Duplicar siniestros reenviando el mismo reporte | Red inestable o asegurado | HIGH | MEDIUM | HIGH | CTL-04 |
| THR-04 | D | S3 | Llenar el almacenamiento con fotos enormes o demasiadas | Asegurado | MEDIUM | MEDIUM | MEDIUM | CTL-03 |
| THR-05 | I | DATA-01, DATA-02 | Leer la cola en un teléfono robado | Atacante con el teléfono | MEDIUM | HIGH | HIGH | CTL-05 |
| THR-06 | S | DATA-03 | Usar un token de reporte robado para otra cosa o después de cerrar sesión | Atacante con el token | LOW | MEDIUM | LOW | CTL-02 |
| THR-07 | S | DATA-04 | Firmar una app falsa con la llave de release | Atacante con acceso al repositorio | LOW | HIGH | MEDIUM | CTL-06 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | El documento sale de la cuenta del token; póliza propia, ACTIVE y vigente; el identificador de un reporte ajeno responde 404 | API | EXISTING | THR-01, DATA-01 | TC-07, TC-08 | T-01 | API1:2023, CWE-639 | — |
| CTL-02 | Token de reporte con tipo `report`, alcance `claims:submit`, 7 días, versión de la cuenta y familia de sesión no revocada; el cierre de sesión explícito revoca la familia; solo lo acepta `POST /insured/claims` | API | EXISTING | THR-06, DATA-03 | TC-09, TC-10 | T-02 | API2:2023, ASVS V3 | — |
| CTL-03 | SHA-256 recalculado, JPEG, 2 MB por foto, 5 fotos y 500 caracteres antes de guardar nada | BACKEND | EXISTING | THR-02, THR-04, DATA-02 | TC-03, TC-04, TC-05 | T-01 | CWE-345, CWE-400 | — |
| CTL-04 | `client_report_id` único: el reintento o el envío simultáneo devuelven el mismo siniestro | DATA | EXISTING | THR-03 | TC-02, TC-06 | T-01 | ASVS V11 | — |
| CTL-05 | Cola, fotos y token de reporte cifrados por cuenta en el teléfono y borrados al cerrar sesión o al recibirse | MOBILE | EXISTING | THR-05, DATA-01, DATA-02, DATA-03 | TC-12 | T-03, T-04 | MASVS-STORAGE-1 | — |
| CTL-06 | IAM solo `PutObject` y `GetObject` sobre `siniestros/*`; la llave de firma solo en secretos de GitHub y en un archivo temporal del job | INFRA | EXISTING | THR-04, THR-07, DATA-04 | TC-13 | T-05 | ASVS V14 | — |

## 10. Cryptography

### CRY-01 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02, DATA-03 |
| Type | TLS |
| Algorithm or service | HTTPS de CloudFront y del ALB (TLS 1.2 o superior, RC-31) |
| Rationale | El reporte viaja por redes móviles |
| Key management | Certificados de ACM |
| Permissions | Administración de AWS |
| Rotation | La de ACM |
| Recovery | ACM renueva el certificado |
| Failure behavior | Sin TLS no hay envío y el reporte sigue en cola |
| Tests | TC-01 |

### CRY-02 · Cola local

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02 |
| Type | AT_REST |
| Algorithm or service | SQLCipher (AES-256) para la base y AES-256-GCM por foto, con la frase protegida por el Keystore (HU-MOV-08, ADR 0021) |
| Rationale | La cola puede quedar días en un teléfono sin señal |
| Key management | Una frase por cuenta en el Keystore; una llave por foto guardada en la base cifrada |
| Permissions | Solo la app |
| Rotation | Base nueva por reporte |
| Recovery | Si la llave se invalida el reporte no se puede abrir y se pide reportar de nuevo |
| Failure behavior | Una foto dañada marca el reporte como rechazado con el motivo |
| Tests | TC-12 |

### CRY-03 · Fotos en S3

| Field | Value |
|---|---|
| Protects | DATA-02 |
| Type | AT_REST |
| Algorithm or service | SSE-KMS con la llave del bucket (HU-WEB-16) |
| Rationale | Evidencia con datos sensibles |
| Key management | Llave de KMS administrada en Terraform |
| Permissions | Solo la tarea del backend |
| Rotation | Rotación anual de KMS |
| Recovery | Las versiones de S3 |
| Failure behavior | Si S3 no responde el envío da 503 y se reintenta |
| Tests | TC-01 |

### CRY-04 · Token de reporte en el teléfono

| Field | Value |
|---|---|
| Protects | DATA-03 |
| Type | AT_REST |
| Algorithm or service | AES-GCM con una llave del Android Keystore sin autenticación del usuario, por cuenta |
| Rationale | El trabajo en segundo plano debe leerlo sin huella |
| Key management | Keystore del teléfono; la llave no sale de él |
| Permissions | Solo la app |
| Rotation | El token cambia en cada renovación de la sesión |
| Recovery | Sin token, el reporte sale cuando el asegurado vuelve a entrar |
| Failure behavior | Si no se puede descifrar, el envío espera a la siguiente sesión |
| Tests | TC-12 |

### CRY-05 · Llave de firma en GitHub

| Field | Value |
|---|---|
| Protects | DATA-04 |
| Type | AT_REST |
| Algorithm or service | Secretos cifrados de GitHub Actions (caja sellada de libsodium) |
| Rationale | La llave no puede vivir en el repositorio ni en el runner |
| Key management | GitHub; el equipo guarda una copia fuera del repositorio |
| Permissions | Administradores del repositorio y el job de release |
| Rotation | Al rotar la llave de la app |
| Recovery | La copia del equipo |
| Failure behavior | Sin los secretos el job de release se omite |
| Tests | TC-13 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| Llave de firma del APK (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) | Secretos de GitHub | Job de release | CI | Al rotar la llave de la app | Quitar los secretos y emitir una llave nueva | El gate de secretos revisa cada commit |
| `JWT_SECRET_KEY` | Secretos de GitHub hacia ECS | Backend | Todos | Sin cambio en esta historia | Rotarla invalida todos los tokens, incluidos los de reporte | El gate de secretos |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-01 | — |
| Secure authentication | YES | CTL-02 | — |
| Session management | YES | CTL-02 | — |
| Token expiration | YES | CTL-02 | — |
| Revocation | YES | CTL-02 | — |
| Server-side permission checks | YES | CTL-01, CTL-02 | — |
| Per-resource access (ownership) | YES | CTL-01 | — |
| Roles | YES | CTL-02 | — |
| Privilege separation | YES | CTL-02 | — |
| Brute-force protection | NO | — | No hay credenciales nuevas que adivinar |
| Rate limiting | NO | — | Un reporte por cuenta a la vez y límites de tamaño |
| One-time code (OTP) handling | NO | — | No hay códigos en esta historia |
| User enumeration prevention | YES | CTL-01 | — |
| CSRF protection | NO | — | La app usa token Bearer, sin cookies |

## 13. Data isolation and multi-tenancy

N/A: el asegurado no es un socio; el aislamiento entre asegurados lo cubre CTL-01 con TC-07 y TC-08.

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | python-multipart | Sin cambio de versión | backend | Leer el envío multiparte | HIGH | Revisión manual 2026-11-13 | Ya era dependencia; sin cambio | EVALUATED |
| DEP-02 | WorkManager, Room y SQLCipher | Sin cambio de versión | app | Cola y envío en segundo plano | HIGH | Revisión manual 2026-11-13 | Ya eran dependencias; sin cambio | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | Un token de reporte robado del teléfono permite reportar siniestros falsos durante 7 días | THR-06 | LOW | MEDIUM | LOW | ACCEPT | Bajo: está cifrado con el Keystore, no permite leer nada, el analista revisa cada siniestro y cerrar sesión lo revoca | ACCEPTED |
| RISK-02 | Si el registro falla después de subir las fotos, quedan objetos en S3 sin siniestro | THR-04 | LOW | LOW | LOW | ACCEPT | Bajo: solo pasa en una carrera o una caída de la base, y el reintento usa otro radicado | ACCEPTED |

## 16. Pending decisions

N/A: las decisiones se tomaron el 2026-11-13 y están en spec.md.

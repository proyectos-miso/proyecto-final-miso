# Security and privacy assessment · HU-MOV-11

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Siniestros del asegurado: radicado, póliza, estado, fechas, línea de tiempo y motivo del rechazo | RESTRICTED | YES | YES | Que el asegurado sepa en qué va su reclamación; el estado de un siniestro de vida revela salud o muerte |
| DATA-02 | Token push del teléfono ligado al documento | CONFIDENTIAL | YES | NO | Enviar los avisos al teléfono correcto |
| DATA-03 | Llave de la cuenta de servicio de Firebase | RESTRICTED | NO | NO | Que el backend envíe los push |

## 2. Data lifecycle

### DATA-01 · Siniestros del asegurado

| Question | Answer |
|---|---|
| What it contains | Radicado, póliza, fechas, estado, explicación de cada paso y motivo del rechazo; sin fotos, perito, responsable ni montos internos |
| Why it is needed | AC-01, AC-02 y AC-07 |
| Who can access it | El asegurado titular de la póliza, con su sesión; el personal de operaciones por HU-WEB-15 |
| Where it is captured | En el backend, al reportar y dictaminar (HU-MOV-08, HU-WEB-15) |
| How it is transmitted | HTTPS con token del asegurado y `Cache-Control: no-store` (CRY-02) |
| Where it is stored | PostgreSQL y la base SQLCipher de la billetera en el teléfono (CRY-01) |
| Copies, caches and backups | La copia cifrada de la app (`allowBackup` sigue desactivado) y, si el asegurado la pide, la carátula en PDF en Descargas, que queda bajo su control como cualquier documento que descarga |
| How long it is kept | En el teléfono, hasta cerrar sesión o la siguiente sincronización; en el backend, según la retención de siniestros |
| How access is audited | Registros de acceso del backend sin datos personales |
| How it is deleted or anonymized | Al cerrar sesión se borra la base y la llave de la cuenta (ADR 0021) |
| What happens if it leaks | Revela el estado de salud o la muerte de una persona: impacto alto |
| Transfers to third parties | Ninguna; el push solo lleva el radicado y un texto general |

### DATA-02 · Token push

| Question | Answer |
|---|---|
| What it contains | El token que Firebase entrega al teléfono y el documento de la cuenta |
| Why it is needed | AC-08 y AC-09 |
| Who can access it | Solo el backend |
| Where it is captured | La app lo envía después de iniciar sesión y cuando cambia |
| How it is transmitted | HTTPS con token del asegurado (CRY-02) |
| Where it is stored | Tabla `push_tokens` en PostgreSQL |
| Copies, caches and backups | Respaldos de la base |
| How long it is kept | Hasta que el teléfono registre otro o lo reasigne otra cuenta |
| How access is audited | No se registra el token en los logs |
| How it is deleted or anonymized | Se reemplaza al registrar uno nuevo; Firebase lo invalida al desinstalar |
| What happens if it leaks | Permite mandar push a ese teléfono solo con la llave de Firebase |
| Transfers to third parties | Firebase Cloud Messaging (Google) recibe el token y el texto general |

### DATA-03 · Llave de Firebase

| Question | Answer |
|---|---|
| What it contains | Credencial de la cuenta de servicio del proyecto Firebase |
| Why it is needed | Autenticar el envío de push |
| Who can access it | El proceso del backend y el responsable del proyecto Firebase |
| Where it is captured | Consola de Firebase |
| How it is transmitted | Nunca por la red de la aplicación; se copia al servidor como archivo |
| Where it is stored | En local, archivo fuera del repositorio (`FIREBASE_CREDENTIALS_FILE`); en AWS, secreto de GitHub que Terraform pone en la tarea de ECS (`FIREBASE_CREDENTIALS_JSON`) |
| Copies, caches and backups | Ninguna en el repositorio |
| How long it is kept | Hasta su rotación |
| How access is audited | Consola de Google Cloud |
| How it is deleted or anonymized | Se revoca en la consola de Google Cloud |
| What happens if it leaks | Permite enviar push falsos a los teléfonos de Solventa |
| Transfers to third parties | Ninguna |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Asegurado | insured | Autenticado | Ver sus siniestros, descargar su carátula, renovar su póliza y registrar su teléfono |
| Analista de operaciones | analyst | Interno | Renovar pólizas desde el portal |
| Tarea diaria | Sistema | Interno | Vencer pólizas y enviar avisos de vencimiento |
| Firebase Cloud Messaging | Tercero | Externo | Entregar el push |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| App → backend | Teléfono e internet | Token del asegurado, sesión no revocada, propiedad de la póliza, formato del token push |
| Backend → Firebase | Backend y Google | Credencial de la cuenta de servicio; el mensaje no lleva datos sensibles |
| Firebase → app | Google y el teléfono | Solo se aceptan los tipos `CLAIM_STATUS` y `RENEWAL_REMINDER`; el detalle se pide con sesión |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| App | Backend | DATA-01, DATA-02 | HTTPS | CRY-02, CTL-01, CTL-02, CTL-05 |
| Backend | App (base local) | DATA-01 | HTTPS y SQLCipher | CRY-01, CRY-02, CTL-03 |
| Backend | Firebase | DATA-02 | HTTPS | CRY-02, CTL-04, CTL-06 |

## 6. Third-party services

| Service | Use | Data shared | Location | Controls |
|---|---|---|---|---|
| Firebase Cloud Messaging | Entrega de push | DATA-02 | Estados Unidos (Google) | CTL-04, CTL-06 |

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| GET /insured/claims | Autenticado | Token |
| GET /insured/policies/{n}/caratula y POST /insured/policies/{n}/renewal | Autenticado | Número de póliza |
| POST /insured/devices/push-token | Autenticado | Token push |
| POST /experience/policies/{n}/renewal | Autenticado (analista) | Número de póliza |
| Servicio de mensajería de la app | Interno (`exported=false`) | Contenido del push si se roba la llave de Firebase |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | E | DATA-01 | Pedir siniestros, carátula o renovación de otra persona cambiando el número | Asegurado | MEDIUM | HIGH | HIGH | CTL-01, CTL-08 |
| THR-02 | I | DATA-01 | Leer la copia local en un teléfono robado | Atacante con el teléfono | MEDIUM | HIGH | HIGH | CTL-03 |
| THR-03 | S | DATA-01 | Usar un token del portal, vencido o revocado | Atacante | MEDIUM | HIGH | HIGH | CTL-02 |
| THR-04 | T | DATA-02 | Registrar un token con contenido inyectado o ligarlo a otro documento | Asegurado | LOW | MEDIUM | MEDIUM | CTL-05 |
| THR-05 | I | DATA-01 | El texto del push expone el motivo o el monto en la pantalla bloqueada o a Google | Tercero | MEDIUM | HIGH | HIGH | CTL-04, CTL-07 |
| THR-06 | D | Dictamen | Una falla de Firebase revierte o bloquea el dictamen | Sistema | MEDIUM | MEDIUM | MEDIUM | CTL-05 |
| THR-07 | S | DATA-03 | La llave de Firebase termina en el repositorio y se usa para enviar push falsos | Atacante | LOW | HIGH | MEDIUM | CTL-06 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | El documento sale de la cuenta del token y una póliza ajena responde 404 | API | EXISTING | THR-01, DATA-01 | TC-01, TC-06, TC-08 | T-01, T-02 | API1:2023, CWE-639 | — |
| CTL-02 | Rol insured y sesión vigente en `/insured/*` | API | EXISTING | THR-03 | TC-07 | T-02 | API2:2023, ASVS V3 | — |
| CTL-03 | Copia local en la base SQLCipher por cuenta, borrada al cerrar sesión | MOBILE | EXISTING | THR-02, DATA-01 | TC-12 | T-05 | MASVS-STORAGE-1 | — |
| CTL-04 | Push con identificadores y texto general; notificación privada en la pantalla bloqueada | MOBILE | EXISTING | THR-05, DATA-02 | TC-09, TC-13 | T-03, T-05 | MASVS-PRIVACY | — |
| CTL-05 | Token validado y ligado a la cuenta; envío después del commit y de mejor esfuerzo | BACKEND | EXISTING | THR-04, THR-06 | TC-09, TC-10 | T-03 | CWE-20 | — |
| CTL-06 | Llave de Firebase fuera del repositorio: archivo en local y secreto de CI con variable sensible de Terraform en AWS | INFRA | EXISTING | THR-07, DATA-03 | TC-14 | T-03 | ASVS V14 | — |
| CTL-07 | La vista del asegurado solo expone estado, fechas, explicación y motivo | API | EXISTING | THR-05 | TC-01 | T-01 | API3:2023 | — |
| CTL-08 | Renovación solo en la ventana de 30 días o vencida, con evento en la auditoría | BACKEND | EXISTING | THR-01 | TC-08, TC-11 | T-04 | CWE-840 | — |

## 10. Cryptography

### CRY-01 · Base local cifrada

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | AT_REST |
| Algorithm or service | SQLCipher (AES-256) con frase protegida por una llave del Android Keystore (ADR 0021) |
| Rationale | Reutiliza la base de la billetera |
| Key management | Una llave por cuenta en el Keystore |
| Permissions | Solo la app |
| Rotation | Nueva llave en cada inicio de sesión |
| Recovery | Si la llave se invalida, se borra y se vuelve a sincronizar |
| Failure behavior | Sin llave no se muestra la copia y se pide conexión |
| Tests | TC-12 |

### CRY-02 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02, DATA-03 |
| Type | TLS |
| Algorithm or service | HTTPS (TLS 1.2 o superior) |
| Rationale | Los datos y la autenticación de la cuenta de servicio ante Google viajan por internet |
| Key management | Certificados del balanceador y de Google |
| Permissions | Certificados administrados por el balanceador de AWS y por Google; la app no los toca |
| Rotation | Automática del proveedor |
| Recovery | Si un certificado vence, el proveedor lo renueva; la app reintenta en la siguiente sincronización |
| Failure behavior | La app no acepta enlaces de carátula que no sean https |
| Tests | TC-06 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| Llave de la cuenta de servicio de Firebase | Local: archivo fuera del repositorio. AWS: secreto `FIREBASE_CREDENTIALS_JSON` de GitHub, variable sensible de Terraform | Proceso del backend | LOCAL, DEV | Anual o ante sospecha | Consola de Google Cloud | Escaneo de secretos del pipeline |
| `google-services.json` | `movil/app/`, en `.gitignore` | Compilación de la app | LOCAL, DEV | Con el proyecto Firebase | Consola de Firebase | `.gitignore` y escaneo de secretos |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-02 | — |
| Secure authentication | YES | CTL-02 | — |
| Session management | YES | CTL-02 | — |
| Token expiration | YES | CTL-02 | — |
| Revocation | YES | CTL-02 | — |
| Server-side permission checks | YES | CTL-01, CTL-02 | — |
| Per-resource access (ownership) | YES | CTL-01 | — |
| Roles | YES | CTL-02 | — |
| Privilege separation | YES | CTL-02 | — |
| Brute-force protection | NO | — | No hay credenciales en esta historia; el inicio de sesión es de HU-MOV-03 |
| Rate limiting | NO | — | Lo cubre el límite general del backend |
| One-time code (OTP) handling | NO | — | No hay códigos en esta historia |
| User enumeration prevention | YES | CTL-01 | — |
| CSRF protection | NO | — | La app usa token Bearer, no cookies |

## 13. Data isolation and multi-tenancy

N/A: el asegurado no es un socio; el aislamiento entre asegurados lo cubren CTL-01 y TC-01, TC-06 y TC-08.

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | firebase-admin | 7.7.0 | Backend | Envío de push | MEDIUM | OSV.dev 2026-11-09 | Sin vulnerabilidades conocidas | EVALUATED |
| DEP-02 | com.google.firebase:firebase-bom (firebase-messaging) | 33.16.0 | App | Recepción de push | MEDIUM | OSV.dev 2026-11-09 | Sin vulnerabilidades conocidas | EVALUATED |
| DEP-03 | com.google.gms.google-services | 4.4.3 | App (compilación) | Configuración de Firebase | LOW | OSV.dev 2026-11-09 | Sin vulnerabilidades conocidas | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | Acceso a siniestros de otra persona | THR-01, THR-03 | LOW | HIGH | MEDIUM | MITIGATE | Bajo: alcance por cuenta y 404 | MITIGATED |
| RISK-02 | Push perdido si Firebase falla o no está configurado | THR-06 | MEDIUM | LOW | LOW | MITIGATE | La sincronización muestra la notificación local | MITIGATED |
| RISK-03 | Push falsos si se filtra la llave de Firebase | THR-07 | LOW | MEDIUM | LOW | MITIGATE | El push solo trae identificadores; el detalle se pide con sesión | MITIGATED |

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Servicio de push | Firebase Cloud Messaging o Amazon SNS | Firebase, gratuito | Product owner | NO | APPROVED |
| DEC-02 | Quién implementa la renovación (RC-24) | HU-WEB-12 o esta historia | Esta historia | Product owner | NO | APPROVED |

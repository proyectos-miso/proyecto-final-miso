# Security and privacy assessment · HU-MOV-10

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Radicado, póliza y fecha del siniestro (constancia y eventos) | CONFIDENTIAL | YES | NO | Constancia del reporte y seguimiento |
| DATA-02 | Evento del oráculo y los siniestros paramétricos que genera, con su pago | RESTRICTED | YES | YES | Liquidar sin analista los eventos que cumplen la regla |
| DATA-03 | Llave compartida del oráculo | RESTRICTED | NO | NO | Firmar los eventos |
| DATA-04 | Ciudad del asegurado en la cotización | CONFIDENTIAL | YES | NO | Cruzar el evento con las pólizas |

## 2. Data lifecycle

### DATA-01, DATA-04 · Radicado, constancia y ciudad

| Question | Answer |
|---|---|
| What it contains | Radicado oficial o provisional, póliza, fecha del reporte y ciudad declarada al cotizar |
| Why it is needed | AC-01 a AC-05 |
| Who can access it | El asegurado (sus siniestros, HU-MOV-11) y los analistas con su alcance (HU-WEB-08) |
| Where it is captured | El radicado al registrar; el provisional en el teléfono; la ciudad en el cotizador |
| How it is transmitted | HTTPS (CRY-01) |
| Where it is stored | PostgreSQL; la constancia en la base cifrada por cuenta del teléfono (CRY-02) |
| Copies, caches and backups | Respaldos de RDS; en SQS solo el identificador |
| How long it is kept | Según la retención de siniestros y pólizas; la constancia del teléfono hasta que se cierra o se cierra sesión |
| How access is audited | Historial inmutable del siniestro |
| How it is deleted or anonymized | El teléfono la borra al cerrar sesión |
| What happens if it leaks | El radicado solo no da acceso: las consultas filtran por la cuenta |
| Transfers to third parties | Ninguna |

### DATA-02 · Evento del oráculo y siniestro paramétrico

| Question | Answer |
|---|---|
| What it contains | Identificador, tipo, magnitud, ciudad y hora del evento; los siniestros y pagos que generó |
| Why it is needed | AC-05 y AC-06 |
| Who can access it | El oráculo envía; los analistas lo ven en el expediente |
| Where it is captured | Endpoint del oráculo |
| How it is transmitted | HTTPS con firma HMAC (CRY-01, CRY-03) |
| Where it is stored | `oracle_events` y el siniestro en PostgreSQL |
| Copies, caches and backups | Respaldos de RDS |
| How long it is kept | Según la retención de siniestros |
| How access is audited | `oracle_events` guarda cada evento con sus radicados; el historial del siniestro es inmutable |
| How it is deleted or anonymized | No se borra: es evidencia de un pago |
| What happens if it leaks | Muestra qué pólizas de una ciudad recibieron un auxilio |
| Transfers to third parties | Ninguna |

### DATA-03 · Llave del oráculo

| Question | Answer |
|---|---|
| What it contains | Secreto compartido de al menos 32 caracteres |
| Why it is needed | SR-01 |
| Who can access it | El backend y el oráculo |
| Where it is captured | La crea el equipo |
| How it is transmitted | Secreto de GitHub hacia la tarea de ECS |
| Where it is stored | Secretos de GitHub y variable de entorno de la tarea |
| Copies, caches and backups | La copia del oráculo |
| How long it is kept | Hasta su rotación |
| How access is audited | Registro de ejecuciones de GitHub Actions |
| How it is deleted or anonymized | Se reemplaza el secreto y se despliega |
| What happens if it leaks | Cualquiera podría crear siniestros paramétricos y disparar pagos: impacto crítico |
| Transfers to third parties | El oráculo |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Asegurado | insured | Autenticado | Ver su constancia y sus radicados |
| Oráculo de eventos | Sistema externo con la llave | Externo autenticado por firma | Reportar eventos |
| Publicador del outbox | Sistema | Interno | Enviar identificadores a SQS |
| Equipo | Desarrollo | Interno | Escribir migraciones |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| Oráculo → backend | Internet y API | Firma HMAC, marca de tiempo, tamaño, formato y `event_id` único |
| Backend → SQS | API y cola | Solo identificadores; IAM limitado a esa cola |
| App → backend | Teléfono e internet | Igual que HU-MOV-09 |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Oráculo | Backend | DATA-02 | HTTPS firmado | CRY-01, CRY-03, CTL-01, CTL-02 |
| Backend | SQS | DATA-01 | API de SQS | CRY-04, CTL-04 |
| Backend | App | DATA-01 | HTTPS | CRY-01, CTL-03 |

## 6. Third-party services

| Service | Use | Data shared | Location | Controls |
|---|---|---|---|---|
| Oráculo de eventos (simulado) | Reportar eventos externos | DATA-02 | Simulado por el equipo | CTL-01, CTL-02 |
| Amazon SQS | Cola de eventos | DATA-01 | us-east-1 | CTL-04 |

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| POST /oracle/events | Pública con firma | Cabeceras de firma y cuerpo |
| Cola SQS | Interna | Ninguno |
| Arranque con migraciones | Interno | Ninguno |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | S | DATA-02 | Inventar o alterar un evento para cobrar auxilios | Atacante en internet | MEDIUM | CRITICAL | CRITICAL | CTL-01 |
| THR-02 | T | DATA-02 | Repetir un evento firmado para duplicar pagos | Atacante que capturó un pedido | MEDIUM | HIGH | HIGH | CTL-02 |
| THR-03 | E | DATA-01 | Usar radicados adivinados para ver siniestros ajenos | Asegurado | LOW | MEDIUM | LOW | CTL-03 |
| THR-04 | I | DATA-01 | Datos personales en la cola o envío a otras colas | Sistema o atacante con acceso a SQS | LOW | MEDIUM | LOW | CTL-04 |
| THR-05 | S | DATA-03 | La llave del oráculo en el repositorio o en los registros | Atacante | LOW | CRITICAL | HIGH | CTL-05 |
| THR-06 | I | DATA-01 | Leer la constancia en un teléfono robado | Atacante con el teléfono | LOW | LOW | LOW | CTL-06 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | HMAC-SHA256 de marca y cuerpo, comparación en tiempo constante, ventana de 5 minutos, cuerpo de máximo 4 KB y formato estricto | API | EXISTING | THR-01, DATA-02 | TC-05, TC-06 | T-05 | API2:2023, CWE-345 | — |
| CTL-02 | `event_id` como llave primaria y una sola transacción por evento | DATA | EXISTING | THR-02 | TC-07 | T-05 | CWE-294 | — |
| CTL-03 | El radicado no se usa para autorizar: consultas filtradas por la cuenta (HU-MOV-11) | API | EXISTING | THR-03, DATA-01 | TC-01 | T-01 | API1:2023 | — |
| CTL-04 | Mensajes con solo identificadores; IAM `sqs:SendMessage` y `GetQueueUrl` sobre la cola de eventos | INFRA | EXISTING | THR-04, DATA-01 | TC-09 | T-04, T-06 | ASVS V14 | — |
| CTL-05 | Llave del oráculo solo en secretos de CI; sin ella el canal responde 503 | SECRETS | EXISTING | THR-05, DATA-03 | TC-08 | T-06 | ASVS V14.3 | — |
| CTL-06 | Constancia en la base cifrada por cuenta y borrada al cerrar sesión | MOBILE | EXISTING | THR-06, DATA-01 | TC-12 | T-02 | MASVS-STORAGE-1 | — |

## 10. Cryptography

### CRY-01 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02 |
| Type | TLS |
| Algorithm or service | HTTPS de CloudFront y del ALB (TLS 1.2 o superior) |
| Rationale | El evento y la constancia viajan por internet |
| Key management | Certificados de ACM |
| Permissions | Administración de AWS |
| Rotation | La de ACM |
| Recovery | ACM renueva el certificado |
| Failure behavior | Sin TLS no hay conexión |
| Tests | TC-05 |

### CRY-02 · Constancia en el teléfono

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | AT_REST |
| Algorithm or service | SQLCipher (AES-256) de la base de la cuenta, con la frase protegida por el Keystore (ADR 0021) |
| Rationale | La constancia queda en el teléfono hasta que se cierra |
| Key management | Frase por cuenta en el Keystore |
| Permissions | Solo la app |
| Rotation | Con la base de la cuenta |
| Recovery | El radicado sigue en «Mis siniestros» |
| Failure behavior | Sin la llave no se muestra la constancia |
| Tests | TC-12 |

### CRY-03 · Firma del oráculo

| Field | Value |
|---|---|
| Protects | DATA-02 |
| Type | MAC |
| Algorithm or service | HMAC-SHA256 de `marca.cuerpo` con la llave compartida |
| Rationale | Prueba que el evento lo envió el oráculo y que no cambió |
| Key management | `ORACLE_SECRET` en los secretos de GitHub y el oráculo |
| Permissions | Backend y oráculo |
| Rotation | Coordinada con el oráculo |
| Recovery | Nueva llave en los dos extremos |
| Failure behavior | Firma inválida o vieja: 401 sin crear nada |
| Tests | TC-05, TC-06 |

### CRY-04 · Cola SQS

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | AT_REST |
| Algorithm or service | SSE-KMS de la cola con la llave de almacenamiento (HA-15) |
| Rationale | Los mensajes esperan en la cola |
| Key management | Llave de KMS en Terraform |
| Permissions | La tarea del backend envía; los consumidores leen |
| Rotation | Anual de KMS |
| Recovery | La DLQ conserva los mensajes fallidos |
| Failure behavior | Si SQS no responde, el mensaje queda pendiente en el outbox |
| Tests | TC-09 |

### CRY-05 · Llave del oráculo en GitHub

| Field | Value |
|---|---|
| Protects | DATA-03 |
| Type | AT_REST |
| Algorithm or service | Secretos cifrados de GitHub Actions (caja sellada de libsodium) hacia la variable de la tarea de ECS |
| Rationale | La llave no puede vivir en el repositorio |
| Key management | GitHub; el oráculo guarda su copia |
| Permissions | Administradores del repositorio y el job de despliegue |
| Rotation | Coordinada con el oráculo |
| Recovery | Nueva llave en los dos extremos |
| Failure behavior | Sin la llave el canal responde 503 |
| Tests | TC-08 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| `ORACLE_SECRET` | Secretos de GitHub hacia la tarea de ECS | Backend y oráculo | DEV | Coordinada con el oráculo | Reemplazar el secreto y desplegar | El gate de secretos revisa cada commit |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-01 | El oráculo se identifica por su firma |
| Secure authentication | YES | CTL-01 | — |
| Session management | NO | — | El oráculo no tiene sesión |
| Token expiration | YES | CTL-01 | La marca vale 5 minutos |
| Revocation | YES | CTL-05 | Cambiar la llave revoca al oráculo |
| Server-side permission checks | YES | CTL-03 | — |
| Per-resource access (ownership) | YES | CTL-03 | — |
| Roles | NO | — | El oráculo tiene un solo endpoint |
| Privilege separation | YES | CTL-04 | El backend solo envía a la cola |
| Brute-force protection | YES | CTL-01 | Adivinar un HMAC-SHA256 de 256 bits no es factible |
| Rate limiting | NO | — | Canal de un solo emisor con firma |
| One-time code (OTP) handling | NO | — | No aplica |
| User enumeration prevention | NO | — | No hay usuarios en el canal del oráculo |
| CSRF protection | NO | — | Sin cookies |

## 13. Data isolation and multi-tenancy

N/A: no hay socios en esta historia; el aislamiento entre asegurados lo cubre CTL-03.

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | alembic | 1.20.1 | backend | Migraciones del esquema | HIGH | OSV.dev 2026-11-14 | Sin vulnerabilidades conocidas | EVALUATED |
| DEP-02 | mako | 1.4.3 | backend (de alembic) | Plantillas de migraciones | LOW | OSV.dev 2026-11-14 | Sin vulnerabilidades conocidas en esta versión | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | La llave compartida se filtra y permite crear eventos falsos | THR-01, THR-05 | LOW | CRITICAL | HIGH | MITIGATE | Medio: la llave solo vive en secretos; un pago paramétrico tiene tope por regla y queda auditado | MITIGATED |
| RISK-02 | La migración 0001 usa los modelos actuales: en una base vacía crea el esquema de hoy, no el de su fecha | THR-04 | LOW | LOW | LOW | ACCEPT | Bajo: las migraciones siguientes usan `IF NOT EXISTS` y las pruebas cubren la base vacía y la existente | ACCEPTED |
| RISK-03 | Si el registro falla después de enviar a SQS, el mensaje sale dos veces | THR-04 | LOW | LOW | LOW | ACCEPT | Bajo: la entrega es al menos una vez y el `message_id` permite descartar duplicados | ACCEPTED |

## 16. Pending decisions

N/A: las decisiones se tomaron el 2026-11-14 y están en spec.md.

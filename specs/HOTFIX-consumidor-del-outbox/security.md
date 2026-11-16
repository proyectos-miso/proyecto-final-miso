# Security and privacy assessment · HOTFIX

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Documento del titular y tokens de Firebase, leídos por el consumidor para enviar el push | CONFIDENTIAL | YES | NO | Saber a qué teléfonos enviar el aviso |
| DATA-02 | Eventos del outbox y de la cola: radicado, número de póliza, tipo de cambio, versión | INTERNAL | NO | NO | Decidir qué aviso enviar |
| DATA-03 | `processed_messages`: id aleatorio del mensaje y hora | INTERNAL | NO | NO | No repetir un aviso |

## 2. Data lifecycle

### DATA-01 · Documento y tokens del titular

| Question | Answer |
|---|---|
| What it contains | El documento de `policies.insured_document` y los tokens de `push_tokens` de ese documento |
| Why it is needed | AC-03: enviar el push al titular de la póliza del evento |
| Who can access it | El proceso del backend, con las credenciales de la base de la tarea |
| Where it is captured | No se captura nada nuevo; se lee de tablas existentes |
| How it is transmitted | Dentro de la VPC entre la tarea y RDS; los tokens van a Firebase por HTTPS (CRY-01) |
| Where it is stored | Sin cambio: `policies` y `push_tokens`; no se copia a la cola ni a `processed_messages` |
| Copies, caches and backups | Ninguna copia nueva; respaldos de RDS como hoy |
| How long it is kept | Sin cambio |
| How access is audited | Registros del consumidor con el tipo de evento y la clase del error, sin documento ni token |
| How it is deleted or anonymized | Sin cambio |
| What happens if it leaks | No se agrega exposición: la cola no lo lleva |
| Transfers to third parties | Los tokens a Firebase, como en HU-MOV-11 |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Backend (petición) | Productor | Interno | Escribe eventos en el outbox en la transacción del cambio |
| Publicador | Productor | Interno | Envía a la cola `events` |
| Consumidor | Lector | Interno | Recibe, aplaza y borra mensajes de `events`; lee el outbox, las pólizas y el estado del siniestro; borra tokens que Firebase ya no reconoce |
| Quien tenga acceso a la cola | Externo al backend | No confiable | Podría poner mensajes en la cola |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| Cola SQS → consumidor | SQS y el backend | JSON, `message_id` UUID, `event_type` alfabético de hasta 64, existencia en el outbox con el mismo tipo |
| Consumidor → Firebase | Backend e internet | Igual que HU-MOV-11: solo radicado o póliza y un texto general |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Backend | Outbox (PostgreSQL) | DATA-02 | Transacción local | CTL-04 |
| Publicador | SQS `events` | DATA-02 | HTTPS hacia SQS, cola cifrada con KMS | CRY-02, CTL-04, CTL-05 |
| SQS `events` | Consumidor | DATA-02 | HTTPS | CRY-02, CTL-01, CTL-03, CTL-05 |
| Consumidor | Firebase | DATA-01 | HTTPS | CRY-01, CTL-02, CTL-06 |

## 6. Third-party services

| Service | Use | Data shared | Location | Controls |
|---|---|---|---|---|
| Firebase Cloud Messaging | Envío del push (sin cambio de contenido) | DATA-01 | Google, global | CTL-02, CTL-06 |
| Amazon SQS | Cola de eventos con DLQ | DATA-02 | AWS us-east-1 | CTL-04, CTL-05 |

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| Cola SQS `events` | Interna (IAM de la cuenta) | Cuerpo y atributos de un mensaje |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | S | Push del asegurado | Un mensaje forjado en la cola elige destinatario o texto | Quien tenga acceso a la cola | LOW | MEDIUM | LOW | CTL-01, CTL-05 |
| THR-02 | T | Push del asegurado | Entrega repetida de SQS o dos réplicas envían el mismo aviso dos veces | SQS | MEDIUM | LOW | LOW | CTL-02 |
| THR-03 | D | Cola de eventos | Un mensaje mal formado vuelve sin fin y llena la DLQ, o uno que falla con la base frena al resto del lote | Productor con error | MEDIUM | LOW | LOW | CTL-03 |
| THR-04 | I | DATA-01 | Datos personales en la cola, en la tabla nueva o en los registros | Quien lee logs o la cola | LOW | MEDIUM | LOW | CTL-04, CTL-03 |
| THR-05 | E | Cola de eventos | El rol de la tarea recibe permisos de más (purgar, otras colas) | Error de configuración | LOW | MEDIUM | LOW | CTL-05 |
| THR-06 | D | Aviso al asegurado | Una caída de Firebase pierde el aviso, también cuando el SDK devuelve los fallos por token dentro de la respuesta en lugar de lanzarlos | Firebase | MEDIUM | MEDIUM | MEDIUM | CTL-06 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | El consumidor lee el evento del outbox por `message_id`, exige el mismo tipo y resuelve el destinatario desde la póliza | BACKEND | EXISTING | THR-01 | TC-05, TC-02 | T-02, T-03 | CWE-345 | — |
| CTL-02 | `processed_messages` con `message_id` como llave primaria, insertado antes del manejador en la misma transacción | DATA | EXISTING | THR-02 | TC-03, TC-08, TC-13 | T-02 | CWE-362 | — |
| CTL-03 | Mal formados, ajenos al outbox y de la lista de ignorados se borran; un tipo sin manejador se aplaza 15 min hasta la DLQ; una falla de la base, del commit o del borrado con un mensaje no detiene el lote; los registros solo llevan el tipo de evento validado y la clase del error | BACKEND | EXISTING | THR-03, THR-04 | TC-05, TC-07, TC-14 | T-02, T-08 | CWE-20, CWE-532 | — |
| CTL-04 | Eventos con solo identificadores; `processed_messages` sin datos personales | DATA | EXISTING | THR-04, DATA-01 | TC-01, TC-08 | T-01, T-02 | ASVS V8 | — |
| CTL-05 | Rol de la tarea con `ReceiveMessage`, `DeleteMessage` y `ChangeMessageVisibility` solo sobre el ARN de `events` (sin `GetQueueAttributes`: el número de recepciones llega con el mensaje) | INFRA | EXISTING | THR-01, THR-05 | TC-09 | T-04, T-08 | CWE-250 | — |
| CTL-06 | `notify(strict=True)` en el consumidor y `FirebaseSender.send` que revisa el `BatchResponse`: sin ningún token entregado y con un error pasajero lanza `PushDeliveryError`; el mensaje no se borra y se aplaza 30 s × 2^(n-1), hasta 15 min; tras ocho recepciones va a la DLQ | BACKEND | EXISTING | THR-06 | TC-04, TC-10, TC-14 | T-02, T-03, T-08 | — | — |

## 10. Cryptography

### CRY-01 · Transporte hacia Firebase

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | TLS |
| Algorithm or service | HTTPS del SDK firebase-admin, TLS 1.2 o superior |
| Rationale | Sale a internet |
| Key management | Cuenta de servicio de Firebase (sin cambio, HU-MOV-11) |
| Permissions | Solo la tarea del backend |
| Rotation | Según HU-MOV-11 |
| Recovery | Nueva cuenta de servicio |
| Failure behavior | Sin TLS no hay envío; el mensaje se reintenta |
| Tests | TC-04 |

### CRY-02 · Cola cifrada en reposo

| Field | Value |
|---|---|
| Protects | DATA-02 |
| Type | AT_REST |
| Algorithm or service | SSE de SQS con la llave KMS de almacenamiento (`aws_kms_key.storage`) |
| Rationale | La cola guarda eventos hasta cuatro días y la DLQ catorce |
| Key management | KMS administrado por Terraform, rotación anual activa |
| Permissions | El rol de la tarea tiene `kms:Decrypt` y `kms:GenerateDataKey` sobre esa llave (sin cambio) |
| Rotation | Automática de KMS |
| Recovery | Ventana de borrado de 7 días |
| Failure behavior | Sin permiso de la llave la recepción falla y el bucle lo registra y reintenta |
| Tests | TC-09 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| `FIREBASE_CREDENTIALS_JSON` (sin cambio) | Hoy llega a la tarea de ECS como variable de entorno en texto, desde los secretos del despliegue; el PR #279 la pasa a Secrets Manager | Backend | DEV | Según HU-MOV-11 | Consola de Google Cloud | El gate de secretos |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | NO | — | Proceso interno, sin usuario |
| Secure authentication | NO | — | La cola se alcanza con el rol IAM de la tarea |
| Session management | NO | — | No cambia |
| Token expiration | NO | — | No cambia |
| Revocation | NO | — | No cambia |
| Server-side permission checks | YES | CTL-05 | IAM limita quién lee y borra |
| Per-resource access (ownership) | YES | CTL-01 | El push va al titular de la póliza del evento guardado |
| Roles | NO | — | Sin roles de usuario nuevos |
| Privilege separation | YES | CTL-05 | El rol no puede purgar ni leer la DLQ |
| Brute-force protection | NO | — | Sin credenciales |
| Rate limiting | NO | — | El ritmo lo marca la cola: 10 mensajes por vuelta |
| One-time code (OTP) handling | NO | — | No aplica |
| User enumeration prevention | NO | — | Sin interfaz |
| CSRF protection | NO | — | Sin interfaz web |

## 13. Data isolation and multi-tenancy

N/A: los avisos son del asegurado, que no es un socio; el destinatario lo fija CTL-01.

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | boto3 y firebase-admin | Sin cambio de versión | backend | SQS y push | MEDIUM | Revisión manual 2026-11-15 | Sin cambio; evaluadas en HU-MOV-10 y HU-MOV-11 | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | Si Firebase envía y el commit de `processed_messages` falla, o la tarea se apaga entre el envío y el commit, el reintento envía el aviso otra vez (en efecto una vez, no exactamente una) | THR-02 | LOW | LOW | LOW | ACCEPT | Bajo: un aviso duplicado sin datos sensibles | OPEN |
| RISK-02 | Una caída de Firebase de más de unos 60 min (esperas de 30 s a 15 min en ocho recepciones, 3630 s en total) manda los avisos a la DLQ, que no se reprocesa sola ni tiene alarma todavía | THR-06 | MEDIUM | MEDIUM | MEDIUM | MITIGATE | Medio hasta tener una alarma sobre la DLQ y un procedimiento para devolverlos; para tolerar más, subir el máximo de espera o `maxReceiveCount` | OPEN |
| RISK-03 | `processed_messages` crece sin límite | THR-03 | MEDIUM | LOW | LOW | MITIGATE | Bajo: una fila pequeña por evento; falta una limpieza de lo más viejo que la retención de la cola | OPEN |
| RISK-04 | Los avisos de una misma transacción pueden llegar en otro orden (por ejemplo «pagado» antes de «aprobado») | THR-02 | MEDIUM | LOW | LOW | ACCEPT | Bajo: la app muestra el estado actual al sincronizar | OPEN |
| RISK-05 | La cola `events` de DEV acumula hasta cuatro días de eventos que nadie leía; al primer despliegue el consumidor los enviaría como avisos tardíos | THR-03 | MEDIUM | LOW | LOW | MITIGATE | Bajo: `ClaimFiled` de más de 24 h o con el siniestro ya decidido y `RenewalDue` de otra versión se borran sin push; purgar la cola de DEV antes del primer despliegue | OPEN |
| RISK-06 | En un despliegue escalonado, una réplica sin el manejador de un tipo nuevo lo aplaza en vez de borrarlo; si el despliegue tarda más de unas 2 h (8 × 15 min), el evento termina en la DLQ | THR-03 | LOW | LOW | LOW | ACCEPT | Bajo: el evento queda en la DLQ 14 días para devolverlo; los tipos sin consumidor previsto van en la lista de ignorados | OPEN |

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Aceptar el ADR 0030 | Aceptar; pedir cambios | Aceptar | Equipo | NO | PENDING |

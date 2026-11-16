# HOTFIX · Consumidor del outbox

| Field | Value |
|---|---|
| ID | HOTFIX |
| Title | Consumidor del outbox |
| User story | none: mejora para completar el patrón outbox (ASR-05), sin historia en el tablero, decidida por el usuario el 2026-11-15 |
| Owner | Juan Camilo Peña |
| Status | IN_PROGRESS |
| Created | 2026-11-15 |
| Security profile | HIGH |

## 1. Requirements

### User story

Mejora (no es una historia del tablero). El backend guarda `PolicyIssued`, `PaymentApproved` y `ClaimFiled` en la
tabla `outbox` y el publicador los lleva a SQS cada 5 s (ADR 0026), pero nadie lee la cola. Los push se envían en
línea dentro de la petición: la decisión del analista, la liquidación paramétrica y el reintento del pago llaman
`claim_changed`, y la tarea diaria llama `renewal_due`. El 2026-11-15 el usuario decidió agregar un consumidor real:
las notificaciones salen del camino de la petición y se reintentan si Firebase falla.

### Problem and outcome

Problema: la cola de eventos no tiene lector, una caída de Firebase pierde el aviso y un Firebase lento frena la
petición del analista. Resultado: cada cambio de estado de un siniestro, cada siniestro recibido y cada
recordatorio de renovación queda como evento en el outbox, y un consumidor en el backend lo convierte en push en
efecto una vez por mensaje, con reintentos de espera creciente y cola de mensajes fallidos.

### Goals and non-goals

Objetivos: eventos `ClaimStatusChanged` y `RenewalDue` en la misma transacción que su cambio; push de `ClaimFiled`;
consumidor con long polling, despacho por tipo, idempotencia por `message_id` y borrado solo de lo procesado;
permisos de lectura de la cola para el rol de la tarea; ADR 0030.

Fuera de alcance: consumidores para `PolicyIssued` y `PaymentApproved` (están en la lista explícita de tipos
ignorados y se borran); reproceso de la DLQ; alarma sobre la DLQ; limpieza de `processed_messages`; orden entre eventos de una misma transacción; cambiar el texto o el
contenido de los push.

### Affected paths

| Path | Change |
|---|---|
| backend/app/core/outbox.py | `ProcessedMessageDB`, `OutboxEvent`, `OutboxConsumer`, `events_queue_configured`, `IGNORED_EVENT_TYPES`; `SqsQueue` recibe con `ApproximateReceiveCount`, borra y aplaza con espera creciente |
| backend/app/main.py | Registra los manejadores y lanza `consume_outbox()` solo con cola o emulador; al apagar espera a que las tareas terminen; la tarea diaria ya no envía push |
| backend/app/migrations/versions/0006_mensajes_procesados.py | Tabla `processed_messages` |
| backend/app/modules/claims/service.py | `ClaimService.event` escribe `ClaimStatusChanged`; `commit` ya no avisa; `status_of` para el manejador de `ClaimFiled` |
| backend/app/modules/policy/service.py | `PolicyService.find`: notificaciones lee la póliza por el servicio y no por el modelo |
| backend/app/modules/claims/oracle.py | Sin la lista de avisos en la sesión |
| backend/app/modules/policy/operations.py | `remind_renewals` escribe `RenewalDue` y ya no recibe `notify` |
| backend/app/modules/notifications/service.py | `notify(strict=...)`, `PushDeliveryError`, revisión del `BatchResponse` de Firebase y borrado de tokens no registrados, manejadores `on_claim_filed`, `on_claim_status_changed`, `on_renewal_due` y `event_handlers()` |
| backend/tests/integration/test_outbox_consumer.py | Pruebas del consumidor con PostgreSQL y el emulador SQS |
| backend/tests/integration/test_migrations.py | Última versión `0006`; subida desde `0005` y reversión |
| backend/tests/integration/test_insured_claims_api.py | El recordatorio deja `RenewalDue` en lugar de llamar al emisor |
| backend/tests/integration/test_oracle_events_api.py | La liquidación paramétrica deja sus `ClaimStatusChanged` una vez |
| backend/tests/unit/test_notifications.py | Tarea diaria sin emisor; arranque del consumidor y su bucle; respuesta de Firebase; espera creciente y tipos ignorados |
| terraform/modules/ecs/main.tf | Permisos de consumo de la cola `events` para el rol de la tarea |
| terraform/modules/storage_events/main.tf | Cola `events` con visibilidad de 120 s y `maxReceiveCount` 8 |
| terraform/tests/consumidor_del_outbox.tftest.hcl | Prueba de los permisos y de la cola con el proveedor simulado |
| docs/adr/0030-backend-outbox-consumer.md | Decisión del consumidor |
| docs/adr/0026-backend-outbox-publisher.md | Ya existe un consumidor; el rol ya no solo envía |
| docs/adr/README.md | Índice |
| AGENTS.md | Rango de ADR del backend |
| CHANGELOG.md | Entrada de la mejora |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Analista | Decidir un siniestro sin esperar a Firebase | 1. Rechaza, liquida o toma el caso. 2. El cambio y su `ClaimStatusChanged` se confirman juntos. 3. Responde 204 sin llamar a Firebase | Si la transacción falla, no queda evento ni push |
| UC-02 | Sistema | Entregar un evento como push | 1. El publicador lleva el evento a SQS. 2. El consumidor lo recibe, lee el outbox y el titular de la póliza. 3. Envía el push con su categoría. 4. Borra el mensaje | Categoría apagada o teléfono sin token: se borra sin push. Firebase falla: no se borra y se reintenta con espera creciente; tras ocho recepciones va a la DLQ. Siniestro ya decidido o evento de más de 24 h: se borra sin push |
| UC-03 | Sistema | Recordar la renovación | 1. La tarea diaria marca la póliza y escribe `RenewalDue`. 2. El consumidor envía el push de vencimiento | Póliza ya no vigente o de otra versión al consumir: se borra sin push |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Poner en la cola un mensaje con un tipo o un id que no están en el outbox, o con cuerpo mal formado, para enviar un push o envenenar la cola | Atacante con acceso a la cola o un error de otro productor | THR-01, THR-03 | Se registra sin datos personales y se borra; no hay push | TC-05 |
| AB-02 | Repetir el mismo mensaje para enviar pushes duplicados | SQS (al menos una vez) o un atacante | THR-02 | Un solo push; las copias se borran | TC-03 |
| AB-03 | Leer datos personales desde la cola o los registros | Quien lee la cola o los logs | THR-04 | La cola y `processed_messages` solo tienen identificadores; los logs solo el tipo y la clase del error | TC-01, TC-05 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN un siniestro pasa a ASSIGNED, REJECTED, SETTLED o PAID, THE backend SHALL escribir en la misma transacción un `ClaimStatusChanged` con `aggregate_id` el radicado y un payload de solo identificadores y `kind`, y SHALL no llamar a Firebase en la petición | STATED |
| AC-02 | WHEN la tarea diaria marca un recordatorio de renovación, THE backend SHALL escribir `RenewalDue` con `aggregate_id` el número de póliza en lugar de enviar el push | STATED |
| AC-03 | WHEN el consumidor recibe `ClaimFiled`, `ClaimStatusChanged` o `RenewalDue`, THE backend SHALL enviar el push con su texto y categoría (PAID a payments, vencimientos a renewals, el resto a claims) y borrar el mensaje | STATED |
| AC-04 | IF un mensaje llega repetido, THE consumidor SHALL no enviar un segundo push y SHALL borrarlo | STATED |
| AC-05 | IF Firebase falla, THE consumidor SHALL no borrar el mensaje y SHALL aplazarlo 30 s × 2^(n-1), hasta 15 min, según su número de recepciones n, para que SQS lo reintente y, tras ocho recepciones, lo mueva a la DLQ | STATED |
| AC-06 | IF el mensaje está mal formado o su tipo está en la lista de ignorados (`PolicyIssued`, `PaymentApproved`), THE consumidor SHALL registrarlo sin datos personales y borrarlo; IF el tipo no tiene manejador ni está en esa lista, THE consumidor SHALL no borrarlo y SHALL aplazarlo 15 min hasta que llegue a la DLQ | STATED |
| AC-07 | WHILE el asegurado tiene la categoría apagada o no tiene token, THE consumidor SHALL no enviar el push y SHALL contar el mensaje como procesado | STATED |
| AC-08 | WHILE no hay `SQS_EVENTS_URL` ni emulador, THE backend SHALL no arrancar el consumidor; WHILE los hay, SHALL arrancarlo y, al apagarse, esperar a que termine | STATED |
| AC-09 | IF el id del mensaje no está en el outbox o su tipo no coincide con el guardado, THE consumidor SHALL descartarlo sin push | DERIVED: el consumidor confía solo en la base, no en el cuerpo de la cola |
| AC-10 | THE rol de la tarea SHALL poder recibir, borrar y aplazar solo en la cola `events`, además de publicar, y nada más de SQS | STATED |
| AC-11 | IF al consumir `ClaimFiled` el siniestro ya no está en REPORTED o el evento tiene más de 24 h, THE consumidor SHALL borrarlo sin push | DERIVED: una cola acumulada no debe avisar «Recibimos» de algo que ya cambió |
| AC-12 | IF al consumir `RenewalDue` la póliza no está activa o su versión no es la del evento, THE consumidor SHALL borrarlo sin push | DERIVED: un endoso o una renovación posteriores dejan el aviso sin objeto |
| AC-13 | IF Firebase responde sin ningún token entregado, THE consumidor SHALL registrar las clases de error sin tokens ni datos personales; IF además hubo al menos un error pasajero (no disponible, interno, 429 `RESOURCE_EXHAUSTED`, desconocido, tiempo agotado o red) o todos los tokens fallaron por la configuración del servidor (`UNAUTHENTICATED`, `PERMISSION_DENIED`, `THIRD_PARTY_AUTH_ERROR`, `SENDER_ID_MISMATCH`), SHALL tratarlo como falla y reintentarlo hasta la DLQ sin borrar tokens; IF los errores son de token (`UNREGISTERED`, `INVALID_ARGUMENT`, o `SENDER_ID_MISMATCH` sin afectar a todos), o hubo un éxito parcial, SHALL no reintentar y SHALL borrar esos tokens de `push_tokens` en la misma transacción que marca el mensaje, sin que una falla del borrado impida confirmarlo | DERIVED: firebase-admin devuelve los fallos por token dentro de la respuesta y no los lanza |
| AC-14 | IF procesar, confirmar o borrar un mensaje falla por la base o por SQS, THE consumidor SHALL seguir con el resto del lote y SHALL aplazar el que falló | DERIVED: un mensaje no debe frenar a los demás |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | AVAILABILITY | Una caída de Firebase no pierde avisos | Esperas de 30, 60, 120, 240, 480, 900, 900 y 900 s entre las ocho recepciones: el mensaje se entrega si Firebase vuelve antes de unos 60 min (3630 s) desde el primer intento; después queda en la DLQ 14 días. Un tipo sin manejador llega a la DLQ en unas 2 h (8 × 900 s). Si el consumidor cae sin aplazar, el mensaje vuelve a los 120 s de visibilidad de la cola. Firebase se inicia con `httpTimeout` de 10 s, así que cada intento HTTP termina en 10 s y los tokens de un mensaje se envían en paralelo: un lote de 10 mensajes × 10 s = 100 s cabe en los 120 s de visibilidad. Si un reintento interno del SDK (una relectura o un 500/503) alarga un lote más allá, el mensaje vuelve a otra recepción, pero `processed_messages` bloquea la copia hasta el commit y no sale un push duplicado |
| QR-02 | MAINTAINABILITY | `core` no importa módulos | `app/core/outbox.py` no importa de `app.modules`; los manejadores se registran en `app/main.py` |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | El consumidor resuelve el documento del titular para buscar sus tokens |
| Sensitive data (special categories, biometrics, health, financial, minors) | NO | El push no lleva montos ni nombres; sin datos nuevos |
| Authentication, sessions, credentials or one-time codes | NO | No cambia |
| Authorization or access to other users' resources | YES | El push debe llegar solo al titular de la póliza del evento |
| Payments or money movement | NO | El aviso de pago cambia de camino, no el pago |
| Third-party services or integrations | YES | Firebase Cloud Messaging y SQS |
| Infrastructure, network, IAM, CI/CD or secrets | YES | Permisos IAM nuevos sobre la cola |
| Multiple tenants, partners or organizations | NO | El asegurado no es un socio |
| Storage or processing on a mobile device | NO | La app no cambia |
| Web UI or data exposed in the browser | NO | Sin cambios de UI |
| New or updated dependencies | NO | boto3 y firebase-admin ya estaban |
| Critical paths (sdd/security/critical-paths.md) | YES | backend/app/main.py, backend/app/migrations/** y terraform/**/*.tf |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | El consumidor solo actúa sobre eventos que existen en el outbox con el mismo tipo; el destinatario sale de la póliza en la base | THR-01 | CTL-01 | HIGH |
| SR-02 | Un mensaje produce a lo sumo un push aunque llegue varias veces o a dos réplicas | THR-02 | CTL-02 | MEDIUM |
| SR-03 | Un mensaje mal formado o desconocido no bloquea la cola y se descarta sin datos personales en los registros | THR-03, THR-04 | CTL-03 | MEDIUM |
| SR-04 | La cola y la tabla nueva no guardan datos personales | DATA-01, THR-04 | CTL-04 | HIGH |
| SR-05 | El rol de la tarea tiene en SQS solo enviar, obtener la URL, recibir, borrar y aplazar en la cola `events` | THR-05 | CTL-05 | HIGH |
| SR-06 | Una falla de Firebase no se traga en el camino del consumidor: el mensaje se reintenta | THR-06 | CTL-06 | MEDIUM |

## 2. Design

### Components

- **`app/core/outbox.py`:** `ProcessedMessageDB` (migración `0006`); `OutboxEvent` (id, tipo, agregado y payload
  leídos del outbox); `OutboxConsumer` con `register`, `consume` (long polling de 10 s y hasta 10 mensajes) y
  `process`; `SqsQueue.receive` (pide `ApproximateReceiveCount` con `MessageSystemAttributeNames`, sin
  `GetQueueAttributes`), `delete` y `retry_later` (`ChangeMessageVisibility` a 30 s × 2^(n-1), hasta 15 min);
  `events_queue_configured()`. `process` devuelve DONE, RETRY o UNHANDLED; `consume` captura por mensaje
  cualquier excepción (incluido el commit) y la falla de `delete`, y sigue con el lote. `IGNORED_EVENT_TYPES`
  lista los tipos que se borran sin manejador; cualquier otro se aplaza al máximo hasta la DLQ.
- **`app/modules/notifications/service.py`:** `notify(..., strict=False)`; con `strict=True` un fallo de Firebase
  o de la lectura sale como `PushDeliveryError`. `FirebaseSender.send` revisa el `BatchResponse`: sin ningún
  éxito registra las clases de error y, con un error pasajero (incluido el 429 `ResourceExhaustedError`) o con un
  error de configuración en todos los tokens, lanza `PushDeliveryError`; devuelve los tokens `UNREGISTERED`,
  `INVALID_ARGUMENT` y `SENDER_ID_MISMATCH` y `notify` los borra de `push_tokens` en un savepoint. Envía un
  `Message` por token con `send_each` (sin `MulticastMessage.tokens`) y Firebase se inicia con `httpTimeout` de
  10 s. `on_claim_filed` (solo con el siniestro en REPORTED y el evento de menos de 24 h),
  `on_claim_status_changed` y `on_renewal_due` (solo con la póliza activa y la misma versión) buscan el titular con
  `policy_service.find` y llaman `notify` estricto; `event_handlers()` los entrega al composition root.
- **`app/modules/claims/service.py`:** `ClaimService.event` agrega `ClaimStatusChanged` para ASSIGNED, REJECTED,
  SETTLED y PAID; cubre la decisión del analista, la liquidación paramétrica del oráculo y el reintento del pago.
  `commit` solo confirma. Se elimina `session.info["claim_notices"]`.
- **`app/modules/policy/operations.py`:** `remind_renewals(session)` escribe `RenewalDue` con el evento
  `RENEWAL_REMINDER`.
- **`app/main.py`:** registra los manejadores y lanza `consume_outbox()` si hay cola o emulador; al apagar cancela
  las tareas y las espera con `asyncio.gather(..., return_exceptions=True)`.
- **Terraform:** sentencia `ConsumirEventos` en `aws_iam_role_policy.task_documents` (recibir, borrar y aplazar);
  la cola `events` con `visibility_timeout_seconds = 120` y `maxReceiveCount = 8`.

Deuda conocida de arquitectura: notificaciones ya no importa modelos de otros módulos, pero depende de
`policy_service` y `claim_service` para leer la póliza y el estado del siniestro; si esos servicios llegan a
importar notificaciones habrá un ciclo, y la salida sería una consulta publicada por cada módulo.

### Data flows

```mermaid
sequenceDiagram
    participant A as Analista / tarea diaria
    participant B as Backend (petición)
    participant DB as PostgreSQL
    participant P as Publicador
    participant Q as SQS events
    participant C as Consumidor
    participant F as Firebase
    A->>B: decisión / recordatorio
    B->>DB: cambio + outbox (misma transacción)
    P->>DB: PENDING
    P->>Q: message_id, event_type, aggregate_id, occurred_at
    C->>Q: ReceiveMessage (10 s, 10 mensajes)
    C->>DB: outbox por message_id; INSERT processed_messages
    C->>DB: póliza → documento → tokens y preferencia
    C->>F: push
    C->>DB: COMMIT
    C->>Q: DeleteMessage (o ChangeMessageVisibility si falló)
```

La frontera de confianza nueva es la cola: el consumidor no usa del cuerpo más que el id y el tipo, y los valida.

### Data contract

| Evento | aggregate_id | payload |
|---|---|---|
| `ClaimStatusChanged` (nuevo) | radicado | `{claim_number, policy_number, kind}`; `kind` ∈ ASSIGNED, REJECTED, SETTLED, PAID |
| `RenewalDue` (nuevo) | número de póliza | `{policy_number, version}` |
| `ClaimFiled` (sin cambio) | radicado | `{claim_number, policy_number, origin}` |

Cuerpo en SQS sin cambio (ADR 0026). Push: `ClaimFiled` lleva «Recibimos su siniestro {radicado}. Le avisaremos
cuando haya novedades.» con `type: CLAIM_STATUS`, categoría claims; los demás conservan texto y `data`.

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| El consumidor lee el evento del outbox por `message_id` y exige el mismo tipo | CTL-01 | Usar el cuerpo de la cola: un mensaje forjado elegiría destinatario |
| `processed_messages` insertado antes del manejador con `ON CONFLICT DO NOTHING`, en la misma transacción | CTL-02 | Marcar después de enviar: dos réplicas enviarían dos veces |
| Mal formados, ajenos al outbox e ignorados se borran; un tipo sin manejador ni lista se aplaza hasta la DLQ; los fallos de envío se reintentan con espera creciente | CTL-03, CTL-06 | Borrar todo tipo desconocido: en un despliegue escalonado, una réplica vieja borraría eventos que la nueva sí sabe manejar |
| Revisar el `BatchResponse` de Firebase y reintentar solo si ningún token recibió y hubo un error pasajero | CTL-06 | Ignorar la respuesta: los fallos por token no se lanzan y el aviso se perdía; reintentar ante un éxito parcial: duplicaría el push |
| `notify(strict=True)` solo en el consumidor | CTL-06 | Cambiar `notify`: otros llamadores perderían el mejor esfuerzo |
| IAM con acciones de consumo solo sobre el ARN de `events` | CTL-05 | `sqs:*` o `Resource = "*"` |

### Architecture decisions

| ADR | Title | Status |
|---|---|---|
| docs/adr/0030-backend-outbox-consumer.md | An in-process consumer delivers outbox events effectively once | Proposed |
| docs/adr/0026-backend-outbox-publisher.md | Outbox events published to SQS at least once (actualizado) | Proposed |

### Configuration and deployment

- Migración `0006`; sin variables nuevas. El consumidor usa `SQS_EVENTS_URL` (AWS) o `AWS_ENDPOINT_URL` (Floci).
- La cola pasa a `maxReceiveCount = 8` (antes 3) y `visibility_timeout_seconds = 120`; sigue cifrada con la llave
  de almacenamiento; el rol ya tenía `kms:Decrypt` y `kms:GenerateDataKey` sobre esa llave.
- Sin credenciales de Firebase los mensajes se borran sin push, igual que hoy el envío en línea no hacía nada.
- Sin cola ni emulador (`SQS_EVENTS_URL` y `AWS_ENDPOINT_URL` vacíos) no se envían push: el consumidor no arranca
  y los eventos se quedan publicados o pendientes en el outbox.
- Antes del primer despliegue, purgar la cola `events` de DEV: tiene hasta cuatro días de eventos que nadie leía
  (RISK-05).

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Eventos `ClaimStatusChanged` y `RenewalDue`; sin push en línea | AC-01, AC-02, SR-04 | CTL-04 | DONE | backend/app/modules/claims/service.py; backend/app/modules/policy/operations.py |
| T-02 | Consumidor, idempotencia y migración `0006` | AC-03, AC-04, AC-05, AC-06, AC-08, AC-09, SR-01, SR-02, SR-03, SR-06 | CTL-01, CTL-02, CTL-03, CTL-06 | DONE | backend/app/core/outbox.py; backend/app/migrations/versions/0006_mensajes_procesados.py |
| T-03 | Manejadores de notificaciones y `notify` estricto | AC-03, AC-07, SR-06 | CTL-06 | DONE | backend/app/modules/notifications/service.py |
| T-04 | Permisos IAM de consumo y su prueba | AC-10, SR-05 | CTL-05 | DONE | terraform/modules/ecs/main.tf; terraform/tests/consumidor_del_outbox.tftest.hcl |
| T-05 | ADR 0030, ADR 0026, índice y AGENTS.md; `core` sin importar módulos | QR-02 | — | DONE | docs/adr/0030-backend-outbox-consumer.md |
| T-06 | Pruebas de integración y ajuste de las existentes | AC-01 a AC-10 | CTL-01 a CTL-06 | DONE | backend/tests/integration/test_outbox_consumer.py |
| T-07 | Prueba manual: push real en el teléfono a través del consumidor | AC-03, QR-01 | — | TODO | — |
| T-08 | Correcciones de la revisión independiente: respuesta de Firebase, tolerancia por mensaje, espera creciente, cola de 8 recepciones, tipos ignorados, frescura de `ClaimFiled`, versión de `RenewalDue`, IAM sin `GetQueueAttributes` y apagado ordenado | AC-05, AC-06, AC-08, AC-10, AC-11, AC-12, AC-13, AC-14, QR-01 | CTL-03, CTL-05, CTL-06 | DONE | backend/app/core/outbox.py; backend/app/modules/notifications/service.py; terraform/modules/storage_events/main.tf |
| T-09 | Menores de la segunda revisión: errores de configuración y 429 reintentables con registro de las clases de error, `httpTimeout` de 10 s, borrado de tokens mal formados o de otro proyecto, `send_each` por token y DLQ en la prueba de Terraform | AC-13, QR-01 | CTL-06 | DONE | backend/app/modules/notifications/service.py; backend/tests/unit/test_notifications.py; backend/tests/integration/test_outbox_consumer.py; terraform/tests/consumidor_del_outbox.tftest.hcl |

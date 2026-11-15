# HU-MOV-10 · Obtener un número de radicado

| Field | Value |
|---|---|
| ID | HU-MOV-10 |
| Title | Obtener un número de radicado |
| User story | https://github.com/proyectos-miso/proyecto-final-miso/issues/76 |
| Owner | Juan Camilo Peña |
| Status | IN_PROGRESS |
| Created | 2026-11-14 |
| Security profile | HIGH |

## 1. Requirements

### User story

**HU-MOV-10 · Obtener un número de radicado por cada siniestro** (Sprint 3, Must have, 1 punto, FE-MOV-06, ESC-DIS-04; depende de HU-MOV-09).

**Como** cliente asegurado **quiero** recibir un número de radicado al reportar **para** tener constancia de mi reclamación y poder hacerle seguimiento.

Criterios de aceptación de la historia:

1. **Radicado al recibir.** *Dado* un reporte que llega al servidor, *cuando* se registra, *entonces* la app muestra la constancia con el número de radicado, la fecha y la póliza.
2. **Radicado provisional.** *Dado* un reporte en cola sin conexión, *cuando* se guarda, *entonces* la app muestra un radicado provisional, y lo reemplaza por el oficial al enviarse.
3. **Número único.** *Dados* dos reportes, aun enviados al mismo tiempo, *cuando* se registran, *entonces* cada uno recibe un número distinto, y un reintento del mismo reporte conserva su número.
4. **Constancia disponible.** *Dado* un siniestro radicado, *cuando* el asegurado vuelve a la app, *entonces* encuentra su número de radicado en la lista de siniestros.

Requisitos no funcionales: HA-11 (integridad ante fallos y reintentos), HA-17 (registros cerrados inmutables). Mockup en el issue: constancia oficial (`SIN-2026-00871`), constancia provisional en cola (`OFF-2026-9901`), constancia actualizada y radicado en la lista. Tareas: #239 radicado único y registro inmutable, #240 constancia provisional y oficial, #241 pruebas de unicidad, concurrencia y reintentos.

**Brechas de rutas críticas que absorbe** (`specs/pendientes/HU-MOV-10.md`): RC-13 migraciones versionadas; RC-14 publicador del outbox hacia SQS; RC-26 siniestros paramétricos por eventos externos.

**Base que ya existe:** HU-MOV-09 asigna el radicado `SIN-AAAA-NNNNN` con la secuencia `claim_number_seq` al registrar, y un reintento del mismo reporte devuelve el mismo siniestro; HU-MOV-11 muestra el radicado en «Mis siniestros»; HU-WEB-15 tiene la regla paramétrica `SISMO-6.5` que liquida sola; la tabla `outbox` guarda `PolicyIssued` sin publicarlo; la cola SQS existe en Terraform.

### Problem and outcome

El asegurado envía su reporte, pero la app no le entrega una constancia: no sabe con qué número reclamar mientras el reporte está en cola, ni ve el número oficial cuando llega. Además, los siniestros paramétricos no tienen por dónde entrar, los eventos del outbox nunca salen hacia SQS y el esquema de la base cambia con sentencias sueltas al arrancar.

Resultado:
- **Constancia en la app:** con radicado provisional mientras el reporte está en cola, reemplazado por el oficial al recibirse, con fecha y póliza.
- **Siniestros paramétricos:** un evento externo firmado por el oráculo crea el siniestro con su radicado y origen paramétrico.
- **Outbox:** los eventos se publican en SQS al menos una vez.
- **Esquema:** cambia solo por migraciones versionadas.

### Goals and non-goals

Objetivos: constancia provisional y oficial en la app; pruebas de unicidad y concurrencia del radicado; endpoint de eventos del oráculo con firma; publicador del outbox; Alembic con la migración inicial desde el esquema actual.

Fuera de alcance:
- **Proveedores reales de eventos** (sismos, vuelos): el oráculo es simulado y firma con una llave compartida.
- **Consumidores de SQS:** esta historia publica; quién los consume queda para después.
- **Notificar al asegurado de la constancia por SMS o correo:** el push de HU-MOV-11 ya avisa los cambios de estado.

Decisiones de Juan Camilo Peña (2026-11-14): se incluyen RC-13, RC-14 y RC-26; el radicado provisional lo genera el teléfono (`OFF-AAAA-XXXX`); la cotización guarda la ciudad y el evento afecta las pólizas vigentes de su ciudad con cobertura de incapacidad; el oráculo firma con HMAC-SHA256 y una llave compartida, con marca de máximo 5 minutos y `event_id` único; el publicador envía solo identificadores; las migraciones viven en el paquete y el arranque las aplica.

### Affected paths

| Path | Change |
|---|---|
| backend/alembic.ini | Configuración de Alembic para crear migraciones desde la línea de comandos |
| backend/app/main.py | El arranque aplica las migraciones en vez de los `ALTER ... IF NOT EXISTS`; tarea del publicador |
| backend/app/core/outbox.py | Publicador hacia SQS con reintentos y estado del mensaje |
| backend/app/modules/claims/** | Siniestros paramétricos desde eventos del oráculo y evento `ClaimFiled` en el outbox |
| backend/app/bff/oracle/** | Endpoint de eventos del oráculo con firma |
| backend/app/modules/rating/** | Ciudad del asegurado en la cotización |
| backend/app/migrations/** | Migraciones versionadas dentro del paquete |
| backend/app/core/migrations.py | Aplicación de las migraciones al arrancar |
| backend/app/core/config.py | Llave del oráculo y configuración del publicador |
| backend/pyproject.toml | Dependencia `alembic` |
| backend/requirements.txt | Dependencia `alembic` para la imagen |
| backend/tests/** | Pruebas de unicidad, concurrencia, oráculo, publicador y migraciones |
| terraform/modules/ecs/** | `sqs:SendMessage` y la llave del oráculo en la tarea |
| terraform/main.tf | Cola y llave del oráculo hacia el módulo de ECS |
| terraform/variables.tf | Variable sensible de la llave del oráculo |
| .github/workflows/** | Secreto de la llave del oráculo |
| movil/app/src/main/java/com/solventa/app/domain/claims/** | Radicado provisional y constancia |
| movil/app/src/main/java/com/solventa/app/data/claims/** | Radicado provisional guardado con el reporte y el oficial al recibirse |
| movil/app/src/main/java/com/solventa/app/ui/claims/** | Pantalla de constancia y radicado en los pendientes |
| movil/app/src/main/java/com/solventa/app/ui/wallet/** | Tarjeta de la constancia oficial en la billetera |
| movil/app/src/main/java/com/solventa/app/MainActivity.kt | Navegación a la constancia y al siniestro |
| movil/app/src/main/java/com/solventa/app/SolventaApplication.kt | Guardado de la constancia al recibirse |
| movil/app/schemas/** | Esquema de la base de reportes |
| movil/app/src/test/** | Pruebas de la constancia y del radicado provisional |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Asegurado | Ver la constancia | 1. Pulsa «Enviar». 2. Ve la constancia con el radicado provisional, la fecha y la póliza. 3. Al recibirse, la constancia muestra el radicado oficial | Con señal, el provisional cambia en segundos |
| UC-02 | Oráculo de eventos | Reportar un evento | 1. Envía el evento firmado. 2. El sistema busca las pólizas que cumplen la condición. 3. Crea un siniestro paramétrico con radicado por cada una y los liquida | Firma inválida o evento repetido: no crea nada nuevo |
| UC-03 | Sistema | Publicar eventos | 1. Una tarea toma los mensajes pendientes del outbox. 2. Los envía a SQS. 3. Los marca como publicados | SQS caído: quedan pendientes y se reintentan |
| UC-04 | Equipo | Cambiar el esquema | 1. Escribe una migración. 2. Al desplegar, el arranque la aplica | — |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Inventar un evento de sismo para cobrar auxilios | Atacante en internet | THR-01 | 401 sin firma válida o con marca de tiempo vieja | TC-06 |
| AB-02 | Repetir un evento firmado para duplicar pagos | Atacante que capturó el pedido | THR-02 | El mismo evento no crea siniestros nuevos | TC-07 |
| AB-03 | Adivinar radicados para ver siniestros ajenos | Asegurado | THR-03 | El radicado no da acceso: la consulta sigue filtrada por la cuenta (HU-MOV-11) | TC-01 |
| AB-04 | Publicar datos personales en SQS | Sistema | THR-04 | Los eventos llevan identificadores, no datos personales | TC-09 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN el servidor registra el reporte, THE app SHALL mostrar la constancia con el radicado, la fecha y la póliza | STATED |
| AC-02 | WHEN el reporte queda en cola sin conexión, THE app SHALL mostrar un radicado provisional y reemplazarlo por el oficial al recibirse | STATED |
| AC-03 | WHEN dos reportes se registran, aun al mismo tiempo, THE servidor SHALL darles números distintos, y un reintento del mismo reporte SHALL conservar su número | STATED |
| AC-04 | WHEN el asegurado vuelve a la app, THE lista de siniestros SHALL mostrar el radicado | STATED |
| AC-05 | WHEN llega un evento externo con firma válida que cumple la condición de una póliza vigente, THE sistema SHALL crear el siniestro con radicado y origen paramétrico | DERIVED: RC-26, no hay siniestros paramétricos |
| AC-06 | IF el evento no trae firma válida, es viejo o ya se procesó, THE sistema SHALL rechazarlo o devolver lo ya creado sin crear siniestros nuevos | DERIVED: RC-26, un evento falso o repetido pagaría dinero |
| AC-07 | WHEN se guarda un evento en el outbox, THE publicador SHALL enviarlo a SQS al menos una vez y reintentar si SQS falla | DERIVED: RC-14, los eventos se quedan en la tabla |
| AC-08 | WHEN se registra un siniestro, THE sistema SHALL guardar el evento `ClaimFiled` en el outbox en la misma transacción | DERIVED: RC-14, el siniestro no avisa a nadie fuera del backend |
| AC-09 | THE esquema de la base SHALL cambiar solo por migraciones versionadas que el arranque aplica | DERIVED: RC-13, hoy cambia con `ALTER ... IF NOT EXISTS` |
| AC-10 | WHEN la base ya existe con el esquema actual, THE primera migración SHALL adoptarla sin perder datos | DERIVED: RC-13, DEV y las bases locales ya tienen datos |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | PERFORMANCE | Publicación del outbox | Un evento sale a SQS en menos de 10 segundos con SQS disponible |
| QR-02 | ACCESSIBILITY | Constancia | Lineamientos de Android: TalkBack lee el radicado, la fecha y la póliza; objetivos de 48 dp |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | Radicado, póliza y asegurado del siniestro |
| Sensitive data (special categories, biometrics, health, financial, minors) | YES | Un siniestro paramétrico paga dinero |
| Authentication, sessions, credentials or one-time codes | YES | Firma del oráculo |
| Authorization or access to other users' resources | YES | El radicado no debe dar acceso a siniestros ajenos |
| Payments or money movement | YES | El siniestro paramétrico se liquida y paga solo |
| Third-party services or integrations | YES | Oráculo de eventos (simulado) |
| Infrastructure, network, IAM, CI/CD or secrets | YES | `sqs:SendMessage` y la llave del oráculo |
| Multiple tenants, partners or organizations | NO | No aplica |
| Storage or processing on a mobile device | YES | Radicado provisional en la cola cifrada |
| Web UI or data exposed in the browser | NO | Es la app y el backend |
| New or updated dependencies | YES | `alembic` |
| Critical paths (sdd/security/critical-paths.md) | YES | backend/app/modules/claims/** y movil/app/src/main/java/com/solventa/app/data/claims/** |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | Un evento del oráculo solo se procesa con firma HMAC-SHA256 válida sobre la marca y el cuerpo, con marca de máximo 5 minutos | DATA-02, THR-01 | CTL-01 | HIGH |
| SR-02 | El mismo evento crea sus siniestros una sola vez, aunque llegue repetido o al mismo tiempo | THR-02 | CTL-02 | HIGH |
| SR-03 | El radicado y la ciudad no dan acceso: toda consulta sigue filtrada por la cuenta del token | DATA-01, DATA-04, THR-03 | CTL-03 | HIGH |
| SR-04 | Los mensajes publicados en SQS solo llevan identificadores, y el backend solo puede enviar a esa cola | DATA-01, THR-04 | CTL-04 | MEDIUM |
| SR-05 | La llave del oráculo vive en los secretos de CI; sin ella el canal queda cerrado | DATA-03, THR-05 | CTL-05 | HIGH |
| SR-06 | La constancia guardada en el teléfono queda cifrada por cuenta y se borra al cerrar sesión | DATA-01, THR-06 | CTL-06 | MEDIUM |

## 2. Design

### Components

Backend (ADR 0004 y 0005):
- **Migraciones (RC-13):** `app/migrations/` con `env.py` y las versiones; `core/migrations.upgrade_database()` las aplica al arrancar.
  - `0001` adopta el esquema actual: es idempotente y reemplaza los `ALTER ... IF NOT EXISTS` del arranque.
  - `0002` agrega la ciudad a la cotización.
  - `0003` agrega el estado de publicación del outbox y la tabla de eventos del oráculo.
- **Outbox (RC-14):**
  - `core/outbox.py` con `OutboxPublisher`: toma los pendientes con `FOR UPDATE SKIP LOCKED`, los envía a SQS solo con identificadores y los marca publicados. Si uno falla, queda pendiente y suma un intento.
  - `main.py` corre el publicador cada 5 segundos.
  - `register_claim` guarda `ClaimFiled` en la misma transacción.
- **Oráculo (RC-26):**
  - `modules/claims/oracle.py` verifica la firma; busca las pólizas ACTIVE y vigentes cuya cotización es de la ciudad del evento (sin tildes ni mayúsculas) y que incluyen la cobertura de la regla.
  - Crea un siniestro paramétrico por cada una con `next_claim_number`, y la regla `SISMO-6.5` lo liquida.
  - Los siniestros y el registro del evento van en una sola transacción.
  - `bff/oracle/router.py` expone `POST /oracle/events`.
- **Radicado:** `claims/intake.next_claim_number` es el único generador, usado por la app y por el oráculo.

App (ADR 0015 y 0021): radicado provisional derivado del identificador del reporte; constancia provisional en la cola y oficial al recibirse, guardada cifrada por cuenta; radicado provisional en los pendientes de «Mis siniestros».

### Data flows

- **Constancia:** «Enviar» → constancia con `OFF-AAAA-XXXX` → envío (HU-MOV-09) → `201 {claim_number, reported_at}` → constancia oficial guardada → la app la muestra hasta que el asegurado la cierra.
- **Evento paramétrico:** oráculo → `POST /oracle/events` firmado → verificación → pólizas de la ciudad → siniestros con radicado, liquidación y pago → `ClaimFiled` en el outbox → push al asegurado (HU-MOV-11).
- **Outbox:** tabla `outbox` → publicador → SQS (`message_id`, `event_type`, `aggregate_id`, `occurred_at`).

### Data contract

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| POST /oracle/events | Cabeceras `X-Oracle-Timestamp` (segundos) y `X-Oracle-Signature` (HMAC-SHA256 hex de `marca.cuerpo`); cuerpo `{event_id, type: "SISMO", magnitude, city, occurred_at}` | 201 `{event_id, claim_numbers[], duplicate: false}`; 200 repetido; 401 firma inválida o vieja; 413; 422; 503 sin llave |
| Mensaje SQS | `{message_id, event_type, aggregate_id, occurred_at}` y atributos `event_type` y `message_id` | — |
| POST /insured/claims (sin cambios) | — | La app usa `claim_number` y `reported_at` para la constancia |

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| HMAC-SHA256 de marca y cuerpo con llave compartida y ventana de 5 minutos | CTL-01 | Token Bearer fijo: no protege el cuerpo ni impide repetir el pedido |
| `event_id` como llave primaria y una sola transacción por evento | CTL-02 | Revisar antes de crear: dos entregas simultáneas pasarían las dos |
| Mensajes con solo identificadores | CTL-04 | Enviar el registro completo: datos personales en la cola |
| Migraciones dentro del paquete, aplicadas al arrancar | CTL-05 | Un paso aparte en el despliegue: más piezas que pueden fallar sin aviso |

### Architecture decisions

N/A: reutiliza la arquitectura hexagonal (ADR 0004) y el outbox transaccional existente (ASR-05). Alembic se adopta sin cambiar la arquitectura; las migraciones viven en el paquete para que la imagen de Docker las lleve.

### Configuration and deployment

- **Migraciones:** el arranque aplica `alembic upgrade head`; en DEV la `0001` adopta la base sin tocar los datos.
- **Variables y secretos:** `ORACLE_SECRET` (secreto de GitHub hacia la tarea de ECS; sin él el canal queda cerrado).
- **IAM:** `sqs:SendMessage` y `sqs:GetQueueUrl` sobre la cola de eventos.
- **Dependencia nueva:** `alembic`.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Radicado único con pruebas de unicidad y concurrencia (#239) | AC-03, SR-03 | CTL-03 | DONE | backend/app/modules/claims/intake.py; backend/tests/integration/test_claim_report_api.py |
| T-02 | Constancia provisional y oficial en la app (#240) | AC-01, AC-02, AC-04, QR-02, SR-06 | CTL-06 | DONE | movil/app/src/main/java/com/solventa/app/ui/claims/ClaimScreens.kt |
| T-03 | Migraciones con Alembic | AC-09, AC-10 | CTL-05 | DONE | backend/app/migrations/versions/0001_esquema_inicial.py; backend/tests/integration/test_migrations.py |
| T-04 | Publicador del outbox y `ClaimFiled` | AC-07, AC-08, QR-01, SR-04 | CTL-04 | DONE | backend/app/core/outbox.py; backend/tests/integration/test_outbox_publisher.py |
| T-05 | Eventos del oráculo con firma y siniestros paramétricos | AC-05, AC-06, SR-01, SR-02 | CTL-01, CTL-02 | DONE | backend/app/modules/claims/oracle.py; backend/tests/integration/test_oracle_events_api.py |
| T-06 | IAM de SQS y llave del oráculo en Terraform y CI | SR-04, SR-05 | CTL-04, CTL-05 | DONE | terraform/modules/ecs/main.tf; .github/workflows/deploy.yml |
| T-07 | Pruebas de unicidad, concurrencia y reintentos (#241) | AC-01 a AC-10, QR-01, QR-02 | CTL-01 a CTL-06 | DONE | backend/tests/integration/test_oracle_events_api.py |

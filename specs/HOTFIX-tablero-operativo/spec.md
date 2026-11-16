# HOTFIX · Tablero operativo

| Field | Value |
|---|---|
| ID | HOTFIX |
| Title | Tablero operativo |
| User story | none: mejora del feature FE-WEB-07 sin historia en el tablero, acordada el 2026-11-14 |
| Owner | Juan Camilo Peña |
| Status | IN_PROGRESS |
| Created | 2026-11-15 |
| Security profile | HIGH |

## 1. Requirements

### User story

Mejora (no es una historia del tablero). FE-WEB-07 pide que el equipo de operaciones de Solventa vea en el portal
cómo va el canal: cuántas cotizaciones entran, cuántas terminan en póliza, cuántos siniestros siguen abiertos, si
las rutas críticas cumplen su meta de latencia y si la base, S3, SQS y Firebase responden. Hoy `/health` es
estático y nada de esto se ve sin entrar a la base o a AWS.

**Como** analista de operaciones de Solventa **quiero** un tablero con los indicadores del canal, la latencia de las
rutas críticas y el estado de las dependencias **para** detectar a tiempo una caída o una degradación y actuar antes
de que la note el cliente.

Qué ya existe: `TenantContextMiddleware` en `app/core/middleware.py`; `/health` estático; S3 en
`app/core/storage.py`; SQS y el outbox (`PENDING`/`PUBLISHED`) en `app/core/outbox.py`; Firebase en
`get_sender()`; el reporte mensual con `month_bounds` en hora de Colombia; `OperatorService` y `solventaGuard` en el
portal.

### Problem and outcome

Problema: el equipo de Solventa no ve la salud del canal ni sabe si una ruta crítica dejó de cumplir su meta. Resultado:
la pestaña «Operación» del portal, solo para el equipo de Solventa, con indicadores por periodo, latencia p50, p95 y p99
contra la meta y un semáforo de dependencias que se actualiza solo mientras la pestaña está visible.

### Goals and non-goals

Objetivos: medir la latencia de las rutas críticas con muy bajo costo y sin guardar datos del pedido;
`GET /experience/operations/indicators?period=today|7d|30d`; comprobación en paralelo de base, S3, SQS y Firebase
con un segundo de límite; respuesta guardada 15 s por réplica y periodo; pestaña «Operación» con refresco cada 30 s,
accesible y que marca como desactualizado lo que no pudo refrescar.

Fuera de alcance: métricas agregadas entre réplicas (CloudWatch, Prometheus); alertas o notificaciones; histórico de
latencias; réplica de lectura; indicadores por socio; cambiar `/health` del ALB.

### Affected paths

| Path | Change |
|---|---|
| backend/app/core/metrics.py | Middleware ASGI de latencia que solo registra respuestas por debajo de 400, ventana de 500 muestras por grupo, clasificación por plantilla de ruta y percentiles |
| backend/app/main.py | Registra `LatencyMiddleware` como el middleware más externo |
| backend/app/modules/reports/dashboard.py | Indicadores por periodo en una sola sentencia con `statement_timeout`, latencia contra la meta, comprobación de dependencias con clientes y hilos propios y caché de 15 s |
| backend/app/bff/experience/operations_router.py | `GET /experience/operations/indicators` con `dashboard_operator` (2 s de límite a la lectura del analista) y `no-store` |
| backend/app/modules/notifications/service.py | `get_sender()` inicia Firebase una sola vez aunque lo llamen dos hilos a la vez |
| backend/tests/integration/test_operations_dashboard_api.py | Pruebas del endpoint, de las dependencias, de la base caída, del caché y del middleware |
| backend/tests/unit/test_operations_metrics.py | Percentiles, ventana, clasificación de rutas, filtro por estado, hilos y clientes de las sondas, periodos y costo del registro |
| backend/tests/unit/test_notifications.py | Inicio de Firebase con dos hilos |
| backend/tests/conftest.py | Base de pruebas configurable con `TEST_DATABASE_NAME` |
| backend/tests/db_name.py | Nombre de la base de pruebas, validado con `[a-z_][a-z0-9_]*` |
| backend/tests/integration/test_lifespan.py | La prueba del arranque usa la base de pruebas configurable |
| web/src/app/core/models/operations.model.ts | Contrato del tablero |
| web/src/app/core/operations/operations.service.ts | Llamada al endpoint |
| web/src/app/features/operacion/operations.component.ts | Pantalla «Operación» |
| web/src/app/app.routes.ts | Ruta `/operacion` diferida con `solventaGuard` |
| web/src/app/app.component.html | Pestaña «Operación» |
| web/src/app/**/*.spec.ts | Pruebas del portal, incluida la de la ruta `/operacion` (`app.routes.spec.ts`) |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Analista de Solventa | Revisar la operación | 1. Abre «Operación». 2. Ve los indicadores de hoy, la latencia y el semáforo. 3. Cambia el periodo a 7 o 30 días | Sin cotizaciones: «Sin cotizaciones» en la conversión; (a) la base falla después de validar al analista: conteos «Sin datos» y la base en «Caída»; (b) la base está caída del todo: no se puede validar al analista y el endpoint responde un 503 genérico en unos 2 s |
| UC-02 | Analista de Solventa | Vigilar sin recargar | 1. Deja la pestaña abierta. 2. Cada 30 s se actualiza. 3. Si una dependencia cambia de estado, el lector de pantalla lo anuncia | Pestaña oculta: no consulta; al volver se actualiza; carga pedida por el analista en curso: el refresco automático se omite; error del servidor: aviso genérico y las tarjetas, la latencia y el semáforo quedan marcados «Sin datos desde HH:mm:ss» |
| UC-03 | Analista de un socio | Intentar ver el tablero | 1. No ve la pestaña. 2. Abre `/operacion` a mano | El guard lo devuelve a `/gestion`; la API responde 403 |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | El analista de un socio o un asesor consulta los indicadores globales de Solventa | Analista de socio, asesor | THR-01 | 403; sin sesión 401 | TC-02 |
| AB-02 | Leer del tablero detalles de una falla de AWS o de la base (mensajes, recursos, credenciales) | Analista o atacante con la respuesta | THR-02 | Solo `name`, `status` y `latency_ms`; el motivo va al registro con el tipo de excepción | TC-03 |
| AB-03 | Que la ventana de latencia guarde rutas con radicados, pólizas o documentos | Quien lea la memoria o un volcado del proceso | THR-03 | Solo duraciones por grupo fijo | TC-05 |
| AB-04 | Colgar el tablero o el backend con una dependencia lenta, una base caída o muchas pestañas abiertas | Dependencia caída, analistas | THR-04 | Un segundo por comprobación, en paralelo, en hilos daemon propios con clientes de 1 s y un intento; 2 s a la lectura del analista y a los conteos; respuesta guardada 15 s; refresco solo con la pestaña visible | TC-03, TC-06, TC-10 |
| AB-05 | Que una falla de la medición rompa una cotización, una emisión, un cobro o un reporte | Falla interna | THR-05 | El pedido responde igual | TC-05 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN el analista de Solventa pide los indicadores de `today`, `7d` o `30d`, THE backend SHALL devolver las cotizaciones del periodo, las cotizaciones por minuto, la conversión (pólizas de cotizaciones del periodo sobre cotizaciones del periodo), las pólizas emitidas, los siniestros en curso (ni SETTLED, ni REJECTED, ni PAID) y los pendientes del outbox, con los periodos desde las 00:00 de Colombia | STATED |
| AC-02 | IF no hay cotizaciones en el periodo, THEN THE backend SHALL devolver la conversión en null sin fallar y el portal SHALL mostrar «Sin cotizaciones» | DERIVED: división segura |
| AC-03 | THE backend SHALL medir p50, p95 y p99 de cotización, emisión, cobro y reporte de siniestro sobre las últimas 500 respuestas con estado por debajo de 400 de cada grupo en la réplica y compararlos con su meta por p95 (cotización 400 ms, HA-01; las demás 1000 ms); las respuestas 4xx y 5xx SHALL no contar; sin muestras, los percentiles y el cumplimiento SHALL ser null | STATED |
| AC-04 | THE backend SHALL informar base, S3, SQS y Firebase con `status` (`ok`, `degraded`, `down`, `not_configured`) y `latency_ms`, cada comprobación con 1 s de límite y en paralelo; IF una falla o pasa el límite, THEN SHALL marcarla `down`, y si tarda 500 ms o más, `degraded`, sin fallar el endpoint. (a) IF la base falla o se cuelga después de validar al analista, THEN THE backend SHALL responder 200 con la base `down` y los conteos en null, y un conteo que pase de 2 s SHALL quedar en null; (b) IF la base está caída del todo y no se puede leer al analista, THEN THE backend SHALL responder 503 con un mensaje genérico en a lo sumo unos 2 s | STATED |
| AC-05 | THE pestaña «Operación» SHALL verse solo si `GET /experience/operator` dice `solventa: true`; su ruta SHALL devolver a `/gestion` al analista de un socio; el endpoint SHALL responder 403 al analista de un socio y al asesor y 401 sin sesión | STATED |
| AC-06 | WHILE la pestaña del navegador está visible, THE portal SHALL actualizar cada 30 s; WHILE está oculta SHALL no consultar; WHEN vuelve a estar visible SHALL actualizar; WHEN se sale de la pantalla SHALL detener el temporizador | STATED |
| AC-07 | THE semáforo de dependencias y el estado de la latencia SHALL usar texto e icono, no solo color | STATED |
| AC-08 | IF un periodo no es `today`, `7d` ni `30d`, THEN THE backend SHALL responder 422 | DERIVED: validación de entrada |
| AC-09 | IF un refresco falla, THEN THE portal SHALL marcar las tarjetas, la latencia y el semáforo con «Sin datos desde HH:mm:ss» (la hora de la última respuesta), con texto visible y anunciado en `role="alert"`, y no SHALL mostrar los estados anteriores como actuales; WHEN un refresco vuelve a responder SHALL quitar la marca y anunciarlo | DERIVED: un semáforo viejo que parece actual engaña al analista |
| AC-10 | THE backend SHALL guardar la respuesta completa de cada periodo 15 s en la réplica, después de autorizar el pedido | DERIVED: varias pestañas abiertas no multiplican las comprobaciones ni los conteos (THR-04) |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | ACCESSIBILITY | Pantalla «Operación» | Selector con etiqueta; tablas con `caption`, `th scope="col"` y `th scope="row"`; estados con texto e icono `aria-hidden`; región `aria-live="polite"` que solo anuncia el cambio de periodo, los cambios de estado y la recuperación tras un error, no cada refresco, y que se vacía antes de escribir para que un aviso igual al anterior también se lea; errores en `role="alert"`; contorno de foco `#0f6d94`; WCAG 2.2 AA |
| QR-02 | PERFORMANCE | Costo del middleware en las rutas críticas | Clasificar y registrar una muestra cuesta menos de 50 µs en promedio sobre 10.000 registros, en local |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | NO | Solo conteos agregados, duraciones y estados; la ventana no guarda rutas ni cuerpos |
| Sensitive data (special categories, biometrics, health, financial, minors) | NO | No aplica |
| Authentication, sessions, credentials or one-time codes | NO | Reutiliza la sesión del analista sin cambios |
| Authorization or access to other users' resources | YES | Solo el analista de Solventa ve los indicadores globales |
| Payments or money movement | NO | Solo se mide la duración de la ruta de cobro; no cambia ningún cobro |
| Third-party services or integrations | YES | Comprueba S3, SQS y Firebase |
| Infrastructure, network, IAM, CI/CD or secrets | NO | Sin cambios de Terraform ni de IAM: usa `s3:HeadBucket` tolerando el 403 (RISK-02) y `sqs:GetQueueUrl`, ya permitido |
| Multiple tenants, partners or organizations | YES | Los indicadores son de todos los socios: el analista de un socio no los ve |
| Storage or processing on a mobile device | NO | Es el portal |
| Web UI or data exposed in the browser | YES | Pestaña «Operación» |
| New or updated dependencies | NO | Sin dependencias nuevas |
| Critical paths (sdd/security/critical-paths.md) | YES | backend/app/main.py |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | Solo el analista de Solventa (`partner_id` nulo, leído de la base) consulta los indicadores; el portal solo oculta | THR-01 | CTL-01, CTL-05 | HIGH |
| SR-02 | El middleware solo guarda duraciones por grupo fijo, nunca la URL, identificadores ni datos personales, y una falla suya no rompe el pedido | THR-03, THR-05 | CTL-02 | HIGH |
| SR-03 | La respuesta no lleva mensajes de error, nombres de recursos ni secretos y va con `no-store` | THR-02 | CTL-03 | HIGH |
| SR-04 | Las comprobaciones no bloquean el backend: 1 s de límite, en paralelo, en hilos daemon propios con clientes de 1 s y un intento; la lectura del analista y los conteos con 2 s de límite; respuesta guardada 15 s; el portal refresca solo con la pestaña visible | THR-04 | CTL-04, CTL-05 | MEDIUM |

## 2. Design

### Components

- **Backend:**
  - `app/core/metrics.py`:
    - `LatencyMiddleware` es ASGI puro, no `BaseHTTPMiddleware`, para no copiar el cuerpo ni sumar una tarea por pedido.
    - Mide con `perf_counter` de punta a punta y, al terminar, clasifica el pedido con `route_group(scope)`.
    - Envuelve `send` para leer el `status` de `http.response.start` y solo registra las respuestas por debajo de 400: un 401 o un 422 rápidos no bajan el percentil. Si la aplicación lanza una excepción sin responder, no hay muestra.
    - `route_group` usa el método y la plantilla de la ruta que eligió el router (`scope["route"].path`, sin el prefijo `/api/v1`). Nunca usa la URL cruda.
    - Los grupos son fijos: `quote` (`POST /quote-edge/quotes` y `/experience/quotes`), `issue` (`POST /quote-edge/policies` y `/experience/policies`), `payment` (`POST /experience/payments/charge`) y `claim` (`POST /insured/claims`).
    - `LatencyWindow` guarda un `deque(maxlen=500)` por grupo con un `threading.Lock`.
    - `percentile` usa el rango más cercano.
    - Una excepción al clasificar o registrar va al registro con su tipo y el pedido sigue.
    - Se registra con `app.add_middleware` después de `TenantContextMiddleware`, así que es el más externo de los nuestros.
  - `app/modules/reports/dashboard.py`:
    - `build_indicators` primero comprueba las dependencias en paralelo con `asyncio.gather`. Cada comprobación usa `asyncio.wait_for` con 1 s.
    - S3, SQS y Firebase corren en hilos daemon, a lo sumo 4 a la vez (`BoundedSemaphore`): sin hilo libre, la comprobación da `down` en seguida. Al ser daemon, un hilo colgado no retrasa el apagado del proceso.
    - S3 y SQS usan clientes propios de las sondas (`s3_probe_client`, `sqs_probe_client`), cacheados por proceso, con `Config(connect_timeout=1, read_timeout=1, retries={"total_max_attempts": 1})`, la misma región y el mismo endpoint que `storage.py` y `outbox.py`, y las credenciales de prueba con el emulador. Así un hilo colgado se libera en unos 2 s.
    - La base se comprueba con su propia conexión del pool (`session.bind.connect()`), no con la sesión del pedido: si el límite corta la consulta, la sesión queda usable y cerrable.
    - Si la base no está `down`, calcula los cinco conteos en una sola sentencia de subconsultas escalares, con `set_config('statement_timeout', '2000', true)` local a la transacción; si pasa el límite o falla, los deja en null y deshace la transacción.
    - `cached_indicators` guarda la respuesta completa por periodo 15 s en la memoria de la réplica. La dependencia de autorización corre antes, así que el caché nunca responde a quien no pasó `dashboard_operator`.
    - Los periodos empiezan a las 00:00 de Colombia (`COLOMBIA` de `reports/service.py`): hoy, hace 6 días o hace 29 días, hasta ahora.
    - La conversión usa `safe_ratio`, que devuelve null con 0 cotizaciones.
    - Las cotizaciones por minuto son las cotizaciones sobre los minutos transcurridos del periodo, con mínimo 1.
    - Comprobaciones:
      - base: `SELECT 1`;
      - S3: `head_bucket`, con el 403 tratado como «responde»;
      - SQS: `get_queue_url` con el nombre de la cola, también cuando la configuración trae la URL;
      - Firebase: `get_sender() is not None`, o `not_configured` si no hay credenciales.
    - `get_dependency_checks` es una dependencia de FastAPI para poder simularla en las pruebas.
  - `operations_router.py`: `GET /experience/operations/indicators` con `dashboard_operator`, `period: Literal` y `Cache-Control: no-store`. `dashboard_operator` hace lo mismo que `solventa_operator`, pero envuelve la lectura del analista (`operations_scope`) en un `asyncio.wait_for` de 2 s: con la base caída del todo, el pedido no espera la conexión y responde 503 con «El tablero no está disponible en este momento. Intente de nuevo en unos segundos.». El 401 y el 403 siguen igual. Los demás endpoints no cambian.
  - `notifications/service.py`: `get_sender()` toma un `threading.Lock` antes del `lru_cache` que inicia Firebase. Sin él, dos hilos podían iniciarlo a la vez; el segundo fallaba con «ya existe» y el caché podía quedar sin emisor.
- **Portal:**
  - `OperationsService` y `OperationsComponent`, en la ruta `/operacion`. La ruta es diferida para no sumar al bundle inicial y usa `solventaGuard`. La pestaña «Operación» aparece en `AppComponent` junto a «Socios».
  - El componente muestra:
    - un selector de periodo con etiqueta;
    - el botón «Actualizar ahora»;
    - la hora de la última actualización, fuera de la región viva;
    - seis tarjetas en una `dl`;
    - la tabla de latencia contra la meta;
    - la tabla del semáforo (✓ Operativa, ! Degradada, ✕ Caída, – Sin configurar), con el icono `aria-hidden` y el texto visible.
  - El refresco usa un `setInterval` de 30 s:
    - arranca solo con `visibilityState` distinto de `hidden`;
    - se detiene con `visibilitychange` a oculto;
    - al volver a visible, consulta y lo reinicia;
    - en `ngOnDestroy` se limpia el intervalo, se quita el listener y se cancela la petición en curso.
  - Una carga del analista (botón, periodo) cancela la anterior. El refresco automático (tick o vuelta a visible) se omite si hay una carga del analista en curso, para no cancelarla.
  - La región `aria-live="polite"` anuncia solo la carga que pide el analista, las dependencias que cambiaron de estado y la recuperación tras un error. El refresco silencioso no la toca. Para que un aviso igual al anterior se lea, la región se vacía y se escribe 100 ms después (`ANNOUNCE_DELAY_MS`).
  - Un error sin datos previos muestra un aviso genérico en `role="alert"`. Con datos previos, el aviso dice «Sin datos desde HH:mm:ss» y las tarjetas (borde punteado y una nota), el título de la latencia y el semáforo quedan marcados con ese texto; el semáforo deja de mostrar los estados anteriores. El siguiente refresco que responde quita la marca.

### Data flows

Rutas críticas → `LatencyMiddleware` → `LatencyWindow` (memoria de la réplica, solo números). Analista → portal →
`GET /experience/operations/indicators` → `dashboard_operator` (lee el usuario en la base, 2 s de límite) → caché de
15 s por periodo → comprobaciones en paralelo (PostgreSQL con su propia conexión, S3, SQS, Firebase) y conteos
agregados en una sentencia (`quotes`, `policies`, `claims`, `outbox`) → respuesta `no-store` con números y estados →
señal del componente.

### Data contract

| Método y ruta | Entrada | Respuesta |
|---|---|---|
| GET /experience/operations/indicators | `period`: `today` (por omisión), `7d` o `30d`; otro valor 422 | `{period, since, generated_at, quotes, quotes_per_minute, conversion, policies_issued, open_claims, outbox_pending, latency: [{group, samples, p50_ms, p95_ms, p99_ms, target_ms, within_target}], dependencies: [{name, status, latency_ms}]}`, `no-store`; los conteos son null si la base falló después de validar al analista o el conteo pasó de 2 s; 401 sin sesión; 403 asesor o analista de socio; 503 genérico si la base está caída del todo |

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| Clasificar por la plantilla de ruta del router en un conjunto cerrado de grupos | CTL-02 | Guardar `scope["path"]`: llevaría radicados, números de póliza o documentos |
| Middleware ASGI puro con el registro aislado en `try/except` | CTL-02 | `BaseHTTPMiddleware`: más costo por pedido; sin aislar, una falla de la medición rompería una emisión |
| Respuesta solo con `name`, `status` y `latency_ms`; en el registro, solo el tipo de excepción | CTL-03 | Devolver el mensaje: los de boto3 traen ARN, bucket, cola o cuenta |
| Hilos daemon propios, a lo sumo 4, con clientes de sonda de 1 s y un intento | CTL-04 | `asyncio.to_thread`: un S3 o SQS colgado ocuparía los hilos que usan las fotos, los PDF y el outbox; `ThreadPoolExecutor`: sus hilos se esperan al salir del intérprete; los clientes de `storage.py` y `outbox.py`: reintentos y timeouts pensados para guardar documentos, no para una sonda |
| Comprobar la base con su propia conexión y limitar los conteos con `statement_timeout` | CTL-04 | Cortar con `wait_for` una consulta de la sesión del pedido: la deja con la transacción inválida hasta un `rollback` |
| `wait_for` de 2 s solo sobre la lectura del analista de este endpoint | CTL-04 | `statement_timeout` o `connect_timeout` globales: cambiarían el comportamiento de todos los endpoints |
| Caché de 15 s por periodo después de autorizar | CTL-04 | Sin caché: cada pestaña visible repite cuatro comprobaciones y los conteos cada 30 s |
| Reutilizar `solventa_operator` | CTL-01 | Un rol nuevo: el analista de Solventa ya existe y se valida en la base |
| Tratar el 403 de `head_bucket` como «S3 responde» | CTL-04 | Dar `s3:ListBucket` al rol: es un cambio de IAM que pide ADR y amplía permisos solo para el tablero. Deja abierto RISK-02 |

### Architecture decisions

N/A: reutiliza la arquitectura hexagonal (ADR 0003, 0004), el BFF por canal (ADR 0005) y los componentes con señales
del portal (ADR 0008, 0009, 0010). No cambia infraestructura ni IAM.

### Configuration and deployment

- Sin migración: el tablero no necesita esquema. La `0007` del diseño queda vacía y no se crea.
- Sin variables nuevas. Los límites son constantes del código: `CHECK_TIMEOUT_SECONDS = 1`, `DEGRADED_AFTER_MS = 500`, `COUNTS_TIMEOUT_MS = 2000`, `CACHE_SECONDS = 15`, `MAX_CHECK_THREADS = 4`, `OPERATOR_TIMEOUT_SECONDS = 2`, `WINDOW = 500`, `REFRESH_MS = 30000` y `ANNOUNCE_DELAY_MS = 100`.
- La latencia y el caché son por réplica: en ECS, cada consulta ve la ventana y el caché de la tarea que la atiende (RISK-01).

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Middleware de latencia, ventana y percentiles | AC-03, QR-02, SR-02 | CTL-02 | DONE | backend/app/core/metrics.py; backend/app/main.py |
| T-02 | Indicadores por periodo y comprobación de dependencias | AC-01, AC-02, AC-04, AC-08, AC-10, SR-03, SR-04 | CTL-03, CTL-04 | DONE | backend/app/modules/reports/dashboard.py |
| T-03 | Endpoint `GET /experience/operations/indicators` solo para Solventa | AC-05, SR-01 | CTL-01 | DONE | backend/app/bff/experience/operations_router.py |
| T-04 | Pantalla y pestaña «Operación» con refresco visible y accesible | AC-02, AC-05, AC-06, AC-07, AC-09, QR-01, SR-04 | CTL-05 | DONE | web/src/app/features/operacion/operations.component.ts; web/src/app/app.routes.ts |
| T-05 | Pruebas | AC-01 a AC-10, QR-01, QR-02 | CTL-01, CTL-02, CTL-03, CTL-04, CTL-05 | DONE | backend/tests/integration/test_operations_dashboard_api.py; backend/tests/unit/test_operations_metrics.py; web/src/app/features/operacion/operations.component.spec.ts |
| T-06 | Prueba manual en el portal local | AC-05, AC-06, AC-07, QR-01 | CTL-05 | TODO | — |
| T-07 | Correcciones de la revisión independiente: base caída en dos casos, filtro por estado de la latencia, clientes e hilos de las sondas, caché y límite de los conteos, inicio de Firebase, refresco y anuncios del portal, ruta `/operacion` y nombre de la base de pruebas | AC-03, AC-04, AC-06, AC-09, AC-10, QR-01, SR-02, SR-04 | CTL-02, CTL-04, CTL-05 | DONE | backend/app/modules/reports/dashboard.py; backend/app/bff/experience/operations_router.py; backend/app/core/metrics.py; backend/app/modules/notifications/service.py; backend/tests/db_name.py; web/src/app/features/operacion/operations.component.ts; web/src/app/app.routes.spec.ts |

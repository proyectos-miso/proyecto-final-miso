# HOTFIX · Prestadores y asistencia

| Field | Value |
|---|---|
| ID | HOTFIX |
| Title | Prestadores y asistencia |
| User story | none: mejora del feature FE-MOV-07 sin historia en el tablero, acordada el 2026-11-14 |
| Owner | Juan Camilo Peña |
| Status | IN_PROGRESS |
| Created | 2026-11-15 |
| Security profile | HIGH |

## 1. Requirements

### User story

Mejora (no es una historia del tablero). FE-MOV-07 pide que el asegurado encuentre prestadores cercanos (talleres,
clínicas, grúas, cerrajería, plomería y asistencia vial) y pida asistencia desde la app; hasta ahora la app no
tenía directorio de prestadores ni forma de pedir ayuda fuera del reporte de siniestro.

**Como** asegurado con una póliza vigente **quiero** ver los prestadores cercanos y pedir asistencia desde la app
**para** recibir ayuda rápido sin buscar teléfonos por otro lado.

### Problem and outcome

Problema: ante una emergencia el asegurado no sabe a quién llamar ni cuánto tardará la ayuda. Resultado: desde la
billetera, «Asistencia» muestra hasta 20 prestadores a 50 km o menos ordenados por distancia (o de la ciudad elegida
si no da permiso de ubicación), con «Llamar» y «Ver en el mapa»; «Pedir asistencia» registra la solicitud y sugiere
el prestador más cercano que ofrece el servicio, con un tiempo estimado si el asegurado lo contacta; nadie despacha
todavía al prestador, así que la app pide llamarlo para coordinar (RISK-05); sin red se ve la última lista guardada
y, si hay línea de Solventa configurada, se ofrece llamarla.

### Goals and non-goals

Objetivos: tablas `providers` y `assistance_requests` (migración `0009`); catálogo ficticio de 18 prestadores en
cinco ciudades, sembrado fuera de producción; `POST /insured/providers/search` (la ubicación va en el cuerpo, no en la URL), `GET /insured/catalogs/cities`,
`POST /insured/assistance` (con `Idempotency-Key` obligatorio) y `GET /insured/assistance`; evento
`AssistanceRequested` en el outbox, ignorado por el consumidor; en la app, botón «Asistencia» en la billetera,
lista con filtro por servicio, permiso de ubicación aproximada opcional, intents `tel:` y `geo:`, copia sin
conexión en la base cifrada de la billetera (versión 5) y formulario «Pedir asistencia».

Fuera de alcance: mapa embebido o SDK de mapas con llave de API (se abre la app de mapas del teléfono con `geo:`);
despacho real al prestador (integración, aviso o seguimiento del estado); carga del catálogo real de prestadores
para producción; pantalla de historial de solicitudes en la app (el backend ya expone `GET /insured/assistance`);
cancelar una solicitud; purga de solicitudes antiguas; administración de prestadores desde el portal.

### Affected paths

| Path | Change |
|---|---|
| backend/app/migrations/versions/0009_prestadores_y_asistencia.py | Tablas `providers` y `assistance_requests` con sus índices |
| backend/app/modules/assistance/__init__.py | Módulo nuevo |
| backend/app/modules/assistance/models.py | `ProviderDB`, `AssistanceRequestDB` y contratos |
| backend/app/modules/assistance/service.py | Catálogo ficticio, búsqueda por distancia o ciudad, prestador sugerido, tiempo estimado, límite por hora y outbox |
| backend/app/bff/insured/router.py | Rutas de prestadores, ciudades y asistencia con idempotencia |
| backend/app/core/outbox.py | `AssistanceRequested` en `IGNORED_EVENT_TYPES` |
| backend/app/core/idempotency.py | La huella acepta campos excluidos (sin `lat` ni `lng` en el espacio `insured:`) y `purge_expired` borra los registros de más de 24 h |
| backend/app/main.py | Registro de tablas, siembra del catálogo fuera de producción en su propio `try` y purga diaria de la idempotencia |
| backend/tests/integration/test_lifespan.py | La siembra que falla no impide la cuenta y la tarea diaria purga |
| backend/tests/conftest.py | Registro de las tablas nuevas |
| backend/tests/integration/test_providers_and_assistance_api.py | Pruebas de integración |
| backend/tests/integration/test_migrations.py | Migración `0009` desde `0008` y su reversión |
| backend/tests/unit/test_notifications.py | El evento nuevo se ignora |
| movil/app/src/main/java/com/solventa/app/domain/assistance/Assistance.kt | Modelo y contratos |
| movil/app/src/main/java/com/solventa/app/data/assistance/HttpAssistanceApi.kt | Cliente HTTP |
| movil/app/src/main/java/com/solventa/app/data/assistance/ProvidersJson.kt | JSON del prestador y de la copia |
| movil/app/src/main/java/com/solventa/app/data/assistance/SessionAssistance.kt | Llamadas con la sesión abierta |
| movil/app/src/main/java/com/solventa/app/data/assistance/AndroidApproximateLocator.kt | Una ubicación aproximada con `LocationManager` |
| movil/app/src/main/java/com/solventa/app/data/assistance/AndroidProviderContact.kt | Intents `tel:` y `geo:` |
| movil/app/src/main/java/com/solventa/app/data/policies/WalletDatabase.kt | Versión 5 con `providers_copy` y `MIGRATION_4_5` |
| movil/app/src/main/java/com/solventa/app/data/policies/EncryptedPolicyStore.kt | Copia de prestadores por cuenta |
| movil/app/src/main/java/com/solventa/app/ui/assistance/AssistanceViewModel.kt | Búsqueda, copia sin conexión, formulario y llave de idempotencia |
| movil/app/src/main/java/com/solventa/app/ui/assistance/AssistanceScreens.kt | Pantallas de prestadores y de «Pedir asistencia» |
| movil/app/src/main/java/com/solventa/app/ui/wallet/WalletScreen.kt | Botón «Asistencia» |
| movil/app/src/main/java/com/solventa/app/MainActivity.kt | Navegación, permiso de ubicación aproximada, ventana segura y «Asistencia» oculto sin línea de Solventa |
| movil/app/src/main/java/com/solventa/app/SolventaApplication.kt | Cliente de asistencia y copia |
| movil/app/src/main/res/values/strings.xml | Textos; la línea de Solventa vacía en release |
| movil/app/src/debug/res/values/assistance.xml | Número ficticio de la línea de Solventa solo en depuración |
| movil/app/src/test/** | Pruebas de la app |
| CHANGELOG.md | Entrada de la mejora |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Asegurado | Ver prestadores cercanos | 1. Toca «Asistencia» en la billetera. 2. Toca «Usar mi ubicación» y da el permiso de ubicación aproximada. 3. Ve hasta 20 prestadores por distancia. 4. Filtra por servicio. 5. Toca «Llamar» o «Ver en el mapa» | Sin permiso: elige su ciudad y ve los de la ciudad. Sin ubicación a tiempo: aviso y elige ciudad. Sin red: la última lista guardada con su fecha, o el aviso y la línea de Solventa. Sin app de mapas o marcador: aviso |
| UC-02 | Asegurado | Pedir asistencia | 1. Toca «Pedir asistencia». 2. Elige póliza y servicio y describe qué pasó. 3. Envía. 4. Ve «Solicitud recibida», el prestador sugerido más cercano y el tiempo estimado si lo contacta. 5. Lo llama para coordinar | Sin red: aviso y reintento con la misma llave. Póliza vencida o ajena, sin prestador del servicio a 50 km, llave usada con otra solicitud o demasiadas solicitudes: un aviso propio para cada caso. Con línea de Solventa configurada, los avisos ofrecen llamarla; sin ella, no la mencionan |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Pedir asistencia con la póliza de otra persona o una vencida | Asegurado malicioso | THR-01 | 404 sin registrar nada | TC-04 |
| AB-02 | Reenviar la misma solicitud (doble toque, red inestable) para generar varias o cambiar el cuerpo con la misma llave | Asegurado o error de red | THR-02 | Misma respuesta sin duplicar; otro cuerpo 409 con `IDEMPOTENCY_KEY_REUSED` | TC-05 |
| AB-03 | Inundar a los prestadores con solicitudes, también en paralelo | Asegurado malicioso | THR-03 | 429 después de 5 en una hora, contadas en serie | TC-06 |
| AB-04 | Ver prestadores o pedir asistencia sin sesión, con otro rol o con la identidad en verificación | Persona maliciosa | THR-01 | 401, 403 y 403 | TC-03 |
| AB-05 | Reconstruir dónde estuvo el asegurado desde la base, los registros de acceso, la huella de idempotencia, el outbox o el teléfono | Interno o ladrón | THR-04 | El servidor no guarda coordenadas ni las pone en la huella; la búsqueda las recibe en el cuerpo, no en la URL; la app las redondea, no las registra y la copia está cifrada | TC-02, TC-09, TC-10 |
| AB-06 | Coordenadas, ciudad, servicio, llave o descripción malformadas o enormes | Persona maliciosa | THR-05 | 422 | TC-07 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN el asegurado abre «Asistencia» con ubicación, THE app SHALL mostrar hasta 20 prestadores activos a 50 km o menos, ordenados por distancia, con su distancia, servicios y dirección; sin ninguno, el aviso de lista vacía | DERIVED: el pedido dice ordenar por distancia; el radio de 50 km sale de la revisión del 2026-11-16 (AC-10) |
| AC-02 | WHERE el asegurado no da permiso de ubicación o no se obtiene, THE app SHALL dejarle elegir la ciudad y mostrar los prestadores de esa ciudad | STATED |
| AC-03 | WHEN el asegurado elige un servicio, THE app SHALL mostrar solo los prestadores que lo ofrecen | STATED |
| AC-04 | WHEN el asegurado toca «Llamar» o «Ver en el mapa», THE app SHALL abrir el marcador con `tel:` o la app de mapas con `geo:`, sin mapa embebido ni llave de API | STATED |
| AC-05 | IF no hay red, THE app SHALL mostrar la última lista guardada con su fecha o, sin copia, avisar y ofrecer la línea de Solventa | STATED |
| AC-06 | WHEN el asegurado envía «Pedir asistencia» con una póliza vigente propia, THE backend SHALL registrar la solicitud en estado RECEIVED y responder el prestador activo más cercano que ofrece el servicio como sugerencia y un tiempo estimado si lo contacta (distancia a 30 km/h más 15 minutos; sin coordenadas, 45 minutos), y THE app SHALL decir que la solicitud se recibió y pedir que lo llame para coordinar, sin decir que fue asignado ni que va en camino | DERIVED: el pedido dice asignar; sin despacho real, la auditoría del 2026-11-16 pide sugerir y no asignar (RISK-05) |
| AC-07 | THE backend SHALL exigir `Idempotency-Key`: la misma llave con el mismo cuerpo devuelve la misma respuesta sin crear otra solicitud; con otro cuerpo, 409 con `code` `IDEMPOTENCY_KEY_REUSED`, distinto del 409 sin prestador | STATED |
| AC-08 | WHEN se registra una solicitud, THE backend SHALL escribir `AssistanceRequested` en el outbox en la misma transacción, solo con identificadores, y el consumidor SHALL descartarlo | STATED |
| AC-09 | THE backend SHALL no guardar la ubicación del asegurado al buscar prestadores ni al pedir asistencia, ni en la huella de idempotencia, ni recibirla en la URL | STATED |
| AC-10 | IF la póliza no es del titular, no existe o está vencida, THE backend SHALL responder 404; IF ningún prestador ofrece el servicio a 50 km o menos (con coordenadas) o en la ciudad (sin ellas), 409 con `code` `NO_PROVIDER` | STATED |
| AC-11 | IF no hay red al pedir asistencia, THE app SHALL avisar, reintentar con la misma llave y ofrecer llamar a la línea de Solventa | STATED |
| AC-12 | IF la identidad del asegurado no está aprobada, THE backend SHALL negar prestadores y asistencia con 403 | DERIVED: la regla de FE-MOV-01 para las rutas del asegurado |
| AC-13 | THE backend SHALL limitar a 5 las solicitudes de asistencia por asegurado en una hora, contadas en serie aunque lleguen a la vez, con `Retry-After` igual al tiempo que falta para liberar un cupo | DERIVED: sin tope una cuenta podría saturar a los prestadores |
| AC-15 | WHERE la app no tiene configurado el número de la línea de Solventa (release), THE app SHALL ocultar «Asistencia» en la billetera y ningún aviso SHALL mandar a llamar a esa línea | DERIVED: en producción no hay catálogo real ni línea (RISK-02) |
| AC-14 | WHEN el asegurado consulta `GET /insured/assistance`, THE backend SHALL devolver solo sus solicitudes | STATED |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | ACCESSIBILITY | Pantallas de prestadores y de «Pedir asistencia» | TalkBack lee el título, cada prestador, «Llamar a …» y «Ver … en el mapa», los filtros, las opciones del formulario y los avisos como región en vivo; objetivos de 48 dp |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | La ubicación aproximada del asegurado y sus solicitudes ligadas a la cuenta y a la póliza |
| Sensitive data (special categories, biometrics, health, financial, minors) | YES | Pedir atención médica y la descripción libre pueden revelar datos de salud |
| Authentication, sessions, credentials or one-time codes | NO | Usa la sesión existente sin cambiarla |
| Authorization or access to other users' resources | YES | Solo el titular usa su póliza y ve sus solicitudes |
| Payments or money movement | NO | No aplica |
| Third-party services or integrations | NO | Sin servicios externos: el marcador y la app de mapas del teléfono reciben solo datos públicos del prestador |
| Infrastructure, network, IAM, CI/CD or secrets | NO | Sin cambios en Terraform ni secretos |
| Multiple tenants, partners or organizations | NO | Las rutas son del asegurado, sin socio |
| Storage or processing on a mobile device | YES | Ubicación en memoria y copia de la lista en la base cifrada de la billetera |
| Web UI or data exposed in the browser | NO | Sin cambios en el portal |
| New or updated dependencies | NO | Sin dependencias nuevas |
| Critical paths (sdd/security/critical-paths.md) | YES | `**/migrations/**`, `backend/app/main.py`, `MainActivity.kt` y `SolventaApplication.kt` |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | Solo el titular con identidad aprobada busca prestadores y pide asistencia, y solo con una póliza propia vigente; lo ajeno responde 404 y solo él ve sus solicitudes | THR-01, DATA-02 | CTL-01 | HIGH |
| SR-02 | La solicitud es idempotente por asegurado y llave, sin duplicar con envíos repetidos o simultáneos | THR-02 | CTL-02 | HIGH |
| SR-03 | Tope de 5 solicitudes por asegurado en una hora | THR-03 | CTL-03 | MEDIUM |
| SR-04 | La ubicación del asegurado no se guarda en el servidor ni sale en registros o eventos; la app la pide aproximada, una vez, la redondea y no la guarda en estado ni registros | THR-04, DATA-01 | CTL-04, CTL-05 | HIGH |
| SR-05 | Entrada acotada: coordenadas en rango, ciudad y llave con patrón, servicio de una lista, descripción de 300 caracteres y cuerpo sin campos extra | THR-05 | CTL-06 | MEDIUM |
| SR-06 | La copia de la lista vive cifrada en la base de la billetera de la cuenta y se borra con ella | DATA-03, THR-04 | CTL-05 | MEDIUM |

## 2. Design

### Components

- **Backend:** módulo `assistance` (ADR 0004): `ProviderDB` y `AssistanceRequestDB`; `AssistanceService.nearby`
  (activos, filtro por ciudad y por servicio en SQL, con coordenadas solo los de 50 km o menos por haversine; sin
  ellas, por nombre; tope de 20), `request` (bloqueo `FOR UPDATE` de la cuenta para contar el tope en serie, póliza
  propia `ACTIVE` y vigente, tope por hora con el `Retry-After` que falta, prestador sugerido más cercano, tiempo
  estimado, fila RECEIVED y outbox sin commit) y `requests_of`. El router usa `insured_account` (identidad aprobada;
  en verificación, 403 con `code` `IDENTITY_IN_REVIEW`); `POST /insured/providers/search` recibe `ProviderSearch`
  en el cuerpo; en `POST /insured/assistance`, `find_response` y `save_response` de `core/idempotency.py` con el
  espacio `insured:{user_id}` en `partner_id` y la huella sin `lat` ni `lng`; los errores llevan `{detail, code}`
  (`NO_PROVIDER`, `IDEMPOTENCY_KEY_REUSED`, `TOO_MANY_REQUESTS`) y sus textos no mandan a llamar a ninguna línea; la
  carrera de dos llaves iguales la resuelve la llave primaria y devuelve la respuesta del ganador. La tarea diaria
  borra los registros de idempotencia de más de 24 h.
- **App:** `AssistanceViewModel` (búsqueda por ubicación o ciudad, filtro, copia sin conexión, intents, formulario
  y llave por formulario; un 422 en la búsqueda es «no pudimos hacer esta búsqueda» y no «sin conexión»; la llave ya
  usada da su propio aviso y renueva la llave; la identidad en verificación tiene su aviso); las pantallas reciben la
  línea de Solventa y, vacía, usan textos sin ella; la solicitud recibida muestra el prestador sugerido, el tiempo
  estimado si lo contacta y «Llamar a …» como acción principal; `SolventaMobileRoot` oculta «Asistencia» sin línea y
  la pantalla tiene ventana segura; `SessionAssistance` + `HttpAssistanceApi`; `AndroidApproximateLocator` (una sola
  `getCurrentLocation` con permiso aproximado); `AndroidProviderContact` (`ACTION_DIAL` con `tel:` y `ACTION_VIEW`
  con `geo:`); `EncryptedPolicyStore` guarda la copia en `providers_copy` de `WalletDatabase` versión 5.

### Data flows

App (ubicación aproximada redondeada a 3 decimales, en memoria) → `POST /insured/providers/search` (HTTPS, la
ubicación en el cuerpo) → filtro en SQL, radio de 50 km y orden en memoria → respuesta → copia cifrada en la
billetera de la cuenta.
App → `POST /insured/assistance` (HTTPS, `Idempotency-Key`) → `assistance_requests` (sin coordenadas),
`idempotency_records` (respuesta sin coordenadas y huella del cuerpo sin `lat` ni `lng`, purgada a las 24 h) y `outbox` → publicador → SQS `events` →
el consumidor lo ignora y lo borra; nadie despacha al prestador.

### Data contract

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| POST /insured/providers/search | `{lat?, lng?, city?, service?}` sin campos extra | 200 `{providers: [{provider_id, name, kind, services, city_code, address, phone, lat, lng, distance_km}]}` con `Cache-Control: no-store`, a 50 km o menos con coordenadas; 401; 403 (`IDENTITY_IN_REVIEW` si la identidad está en verificación); 422 sin ubicación ni ciudad válidas |
| GET /insured/catalogs/cities | — | 200 `[{id, code, name, is_active}]`; 401; 403 |
| POST /insured/assistance | Encabezado `Idempotency-Key` (8 a 128 letras, números o guiones); `{policy_number, service, lat?, lng?, city?, description}` sin campos extra | 201 `{request_id, policy_number, service, status: "RECEIVED", provider (sugerido), eta_minutes (estimado si lo contacta), created_at}` (la misma con la misma llave); 401; 403 (`IDENTITY_IN_REVIEW`); 404 póliza ajena, inexistente o vencida; 409 `{detail, code}` con `IDEMPOTENCY_KEY_REUSED` o `NO_PROVIDER`; 422; 429 `TOO_MANY_REQUESTS` con `Retry-After` en segundos hasta liberar un cupo |
| GET /insured/assistance | — | 200 `{requests: [...]}` del asegurado, hasta 50, con `Cache-Control: no-store` |
| Evento `AssistanceRequested` | — | `{request_id, provider_id, service}` |

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| Idempotencia con la tabla existente usando `insured:{user_id}` como `partner_id`; la columna es `VARCHAR(64)` sin llave foránea a `partners` | CTL-02 | Tabla nueva o columna `client_request_id`: duplica lo que ya resuelve `core/idempotency.py` |
| Coordenadas solo para ordenar y sugerir; se guarda la distancia al prestador y la ciudad del prestador | CTL-04 | Guardar latitud y longitud de la solicitud: dato de ubicación sin uso, porque no hay despacho al prestador |
| Búsqueda por `POST /insured/providers/search` con la ubicación en el cuerpo | CTL-04 | `GET` con `lat` y `lng` en la URL: quedan en los registros de acceso del ALB o de CloudFront |
| Huella de idempotencia sin `lat` ni `lng` (`exclude` en `core/idempotency.py`, solo en el espacio `insured:`) y purga diaria de más de 24 h | CTL-02, CTL-04 | Huella del cuerpo completo: con el resto conocido, probar coordenadas redondeadas las recupera. Cambiar la huella del canal B2B: cambiaría su comportamiento |
| Bloqueo `FOR UPDATE` de la cuenta antes de contar el tope | CTL-03 | Contar sin bloqueo: varias solicitudes simultáneas cuentan lo mismo y pasan del tope |
| Estado RECEIVED y textos de prestador sugerido | CTL-08 | ASSIGNED y «Prestador asignado»: sin despacho, el asegurado creería que la ayuda va en camino |
| Sin línea de Solventa, «Asistencia» oculto y textos sin la línea; no se oculta por catálogo vacío | CTL-07 | Ocultarlo también si el catálogo responde vacío: la búsqueda exige ubicación o ciudad, así que la billetera tendría que preguntar por una ciudad o la ubicación solo para decidir si muestra un botón; en release la falta de línea ya lo oculta |
| Permiso de ubicación aproximada y una sola lectura, redondeada a 3 decimales (unos 110 m) | CTL-05 | Ubicación precisa o actualizaciones continuas: más dato del necesario para ordenar prestadores |
| Intents `tel:` con `ACTION_DIAL` y `geo:` a la app de mapas del teléfono | CTL-05 | Mapa embebido con SDK y llave de API: agrega una dependencia, un secreto en la app y un tercero que recibe la ubicación |
| Copia de la lista en `WalletDatabase` versión 5 con `MIGRATION_4_5` | CTL-05 | Base aparte: otra frase, otra llave y otro ciclo de borrado para un caché pequeño; la de la billetera ya está cifrada por cuenta y se borra al cerrar sesión |
| Catálogo ficticio sembrado solo fuera de producción y línea ficticia solo en depuración | CTL-07 | Sembrarlo en la migración o dejar el número en `main`: en producción un asegurado llamaría a números ficticios en una emergencia |

### Architecture decisions

N/A: reutiliza la arquitectura hexagonal (ADR 0004), la idempotencia existente y la base cifrada de la billetera
(ADR 0014, 0021); no hay dependencia nueva ni mapa embebido que decidir.

### Configuration and deployment

- Migración `0009` (crea las tablas; no siembra). Sin variables nuevas ni cambios en Terraform.
- Fuera de producción (`ENVIRONMENT` distinto de `production`) el arranque siembra 18 prestadores ficticios con
  teléfonos del rango 555 en Bogotá, Medellín, Cali, Barranquilla y Bucaramanga. En producción el catálogo queda
  vacío hasta cargar el real (RISK-02).
- La línea de Solventa (`assistance_line_phone`) es ficticia y solo existe en depuración; en release está vacía, el
  botón «Asistencia» de la billetera no aparece y ningún texto manda a llamar a la línea hasta configurar el número
  real (RISK-02).
- La app no pide permisos nuevos: usa `ACCESS_COARSE_LOCATION`, ya declarado.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Migración, modelos, catálogo ficticio y búsqueda de prestadores | AC-01, AC-02, AC-03, AC-09, AC-12, SR-01, SR-04, SR-05 | CTL-01, CTL-04, CTL-06, CTL-07 | DONE | backend/app/modules/assistance/service.py; backend/tests/integration/test_providers_and_assistance_api.py |
| T-02 | Solicitud de asistencia con idempotencia, tope, outbox y listado | AC-06, AC-07, AC-08, AC-10, AC-13, AC-14, SR-02, SR-03 | CTL-01, CTL-02, CTL-03, CTL-04 | DONE | backend/app/bff/insured/router.py; backend/tests/integration/test_providers_and_assistance_api.py |
| T-03 | App: ViewModel, pantallas, cliente HTTP, ubicación, intents y copia cifrada | AC-01 a AC-05, AC-11, QR-01, SR-04, SR-06 | CTL-05 | DONE | movil/app/src/main/java/com/solventa/app/ui/assistance/AssistanceViewModel.kt; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt |
| T-04 | Prueba manual de punta a punta en DEV con un teléfono, con y sin permiso de ubicación y en modo avión | AC-01 a AC-06, AC-11, QR-01 | — | TODO | — |

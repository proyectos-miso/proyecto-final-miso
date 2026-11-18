# HU-MOV-09 · Enviar el reporte sin señal

| Field | Value |
|---|---|
| ID | HU-MOV-09 |
| Title | Enviar el reporte sin señal |
| User story | https://github.com/proyectos-miso/proyecto-final-miso/issues/75 |
| Owner | Juan Camilo Peña |
| Status | IN_PROGRESS |
| Created | 2026-11-13 |
| Security profile | HIGH |

## 1. Requirements

### User story

**HU-MOV-09 · Enviar el reporte aunque no tenga señal** (Sprint 3, Must have, 2 puntos, FE-MOV-06, ESC-DIS-01; depende de HU-MOV-08).

**Como** cliente asegurado **quiero** que mi reporte se guarde y se envíe solo cuando recupere la señal **para** no perder la evidencia por estar en un sitio sin cobertura.

Criterios de aceptación de la historia:

1. **Reporte en cola.** *Dado* un asegurado sin conexión, *cuando* envía su reporte, *entonces* la app lo guarda cifrado en el teléfono y le muestra "Reporte en cola de envío".
2. **Envío automático.** *Dado* un reporte en cola, *cuando* el teléfono recupera la conexión, *entonces* la app lo envía sola, aunque esté cerrada, sin que el asegurado repita el trámite.
3. **Sin duplicados.** *Dado* un envío que se corta y se reintenta, *cuando* llega al servidor más de una vez, *entonces* se registra un solo siniestro.
4. **Reintentos ante fallos.** *Dado* un error del servidor, *cuando* falla el envío, *entonces* la app reintenta con espera creciente y conserva el reporte hasta que se reciba.
5. **Pendientes visibles.** *Dado* uno o más reportes en cola, *cuando* el asegurado entra a Siniestros, *entonces* los ve como pendientes de envío.

Requisitos no funcionales: HA-08 (contrapresión), HA-11 (integridad ante fallos y reintentos), HA-16 (datos almacenados). Mockup en el issue: aviso sin conexión, reporte en cola, envío en segundo plano y pendientes en la lista. Tareas: #235 cola cifrada, #236 envío con WorkManager, #237 recepción idempotente con fotos a S3 y su huella, #238 pruebas.

**Brechas de rutas críticas que absorbe** (`specs/pendientes/HU-MOV-09.md`): RC-11 verificar la huella de cada foto al recibirla; RC-12 APK de release firmado en el CI.

### Problem and outcome

HU-MOV-08 deja el reporte con sus fotos cifrado en el teléfono y marcado como «listo», pero nada lo envía: el siniestro nunca llega a operaciones (HU-WEB-15) ni a «Mis siniestros» (HU-MOV-11). Resultado: al pulsar «Enviar» el reporte queda en una cola cifrada; el teléfono lo envía solo cuando hay red, aunque la app esté cerrada; el servidor lo registra una sola vez, guarda las fotos en S3 verificando la huella que calculó el teléfono, y el siniestro aparece en la bandeja del analista y en la lista del asegurado.

### Goals and non-goals

Objetivos: cola local cifrada (la misma base de HU-MOV-08) con estado del envío; trabajo de WorkManager con red y espera creciente; endpoint del asegurado que recibe el reporte y sus fotos de forma idempotente, verifica cada huella y registra el siniestro; pendientes visibles en «Mis siniestros»; permiso de escritura en `siniestros/*`; APK de release firmado en el CI.

Fuera de alcance:
- **El número de radicado como constancia** (oficial y provisional) y su unicidad bajo concurrencia: HU-MOV-10. Esta historia asigna el número al registrar con el mismo formato `SIN-AAAA-NNNNN`, porque el siniestro lo necesita, y HU-MOV-10 lo muestra y lo prueba.
- **Editar o cancelar un reporte ya en cola:** un reporte enviado no se edita (regla de HU-MOV-08); se puede descartar mientras no se haya recibido.
- **Más de un reporte a la vez por cuenta:** la base de HU-MOV-08 guarda uno; tras recibirse, el asegurado puede reportar otro.

Decisiones de Juan Camilo Peña (2026-11-13): esta historia asigna el radicado y HU-MOV-10 lo muestra y prueba su unicidad; las fotos llegan al backend en el mismo envío y él verifica la huella y las sube a S3; el APK firmado se deja listo y la llave la crea el equipo en los secretos de GitHub; tras recibirse se borra el reporte local; con la app cerrada el envío usa un token que solo sirve para reportar (ver SR-02).

### Affected paths

| Path | Change |
|---|---|
| backend/app/bff/insured/router.py | `POST /insured/claims` con el reporte y sus fotos |
| backend/app/modules/identity/** | Token de reporte en el ingreso y la renovación; familia de la sesión y `keep_reporting` |
| backend/app/bff/auth/router.py | `keep_reporting` en el cierre de sesión |
| movil/app/src/main/java/com/solventa/app/domain/auth/** | Token de reporte en los tokens de la sesión y `keepReporting` en el cierre |
| movil/app/src/main/java/com/solventa/app/data/auth/** | Lectura del token de reporte y envío de `keep_reporting` |
| movil/app/src/main/java/com/solventa/app/ui/auth/AuthViewModel.kt | Cerrar la app envía `keep_reporting`; el cierre de sesión borra el token de reporte |
| movil/app/src/main/java/com/solventa/app/MainActivity.kt | Envío al iniciar sesión y botones de la billetera |
| movil/app/src/main/java/com/solventa/app/data/policies/** | Llave sin desbloqueo para el token de reporte y revocación con `keep_reporting` |
| movil/app/src/main/java/com/solventa/app/ui/wallet/** | Acceso al reporte en cola o rechazado |
| movil/app/src/main/res/values/strings.xml | Textos de la cola, el rechazo y los pendientes |
| backend/app/core/security.py | Validación del token de reporte |
| movil/app/src/main/java/com/solventa/app/data/session/** | Token de reporte cifrado con el Keystore |
| backend/app/modules/claims/** | Registro desde la app con número, fotos verificadas e idempotencia por reporte |
| backend/app/core/storage.py | `put_bytes` en el puerto de almacenamiento |
| backend/app/main.py | Columna del identificador del reporte con `ADD COLUMN IF NOT EXISTS` |
| backend/tests/** | Pruebas de la recepción, la huella y los reintentos |
| terraform/modules/ecs/** | `s3:PutObject` sobre `siniestros/*` |
| movil/app/src/main/java/com/solventa/app/domain/claims/** | Estados del envío y contrato del API de envío |
| movil/app/src/main/java/com/solventa/app/data/claims/** | Cola en la base cifrada, cliente HTTP y trabajo de envío |
| movil/app/src/main/java/com/solventa/app/ui/claims/** | «Reporte en cola de envío», pendientes en «Mis siniestros» y el aviso de lo que falta solo al tocar «Enviar» (hallazgo de la prueba manual sobre HU-MOV-08) |
| movil/app/src/main/java/com/solventa/app/SolventaApplication.kt | Dependencias del trabajo de envío |
| movil/app/schemas/** | Esquema de la base de reportes, versión 2 |
| movil/app/build.gradle.kts | Firma de release desde secretos |
| .github/workflows/** | Job de APK de release firmado |
| movil/app/src/test/** | Pruebas de la cola, el trabajo y los reintentos |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Asegurado | Enviar el reporte sin señal | 1. Completa el reporte y pulsa «Enviar». 2. Ve «Reporte en cola de envío». 3. Cuando hay red, el teléfono lo envía solo. 4. El siniestro aparece en «Mis siniestros» | Con señal se envía en segundos. Error del servidor: reintenta con espera creciente |
| UC-02 | Asegurado | Ver sus pendientes | 1. Entra a «Mis siniestros». 2. Ve el reporte como pendiente de envío arriba de los recibidos | Sin pendientes: solo los recibidos |
| UC-03 | Sistema | Recibir el reporte | 1. Recibe reporte y fotos. 2. Verifica dueño de la póliza, límites y huellas. 3. Guarda las fotos en S3 y registra el siniestro una vez | Repetido: devuelve el mismo siniestro. Huella distinta: 422 y el teléfono lo marca dañado |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Reportar sobre la póliza de otra persona | Asegurado | THR-01 | 404, como si no existiera | TC-07, TC-08 |
| AB-02 | Mandar una foto alterada o cambiada después de capturarla | Asegurado o intermediario | THR-02 | 422 por huella distinta; nada se guarda | TC-03 |
| AB-03 | Reenviar muchas veces el mismo reporte para duplicar siniestros | Asegurado o red inestable | THR-03 | Un solo siniestro por reporte | TC-02 |
| AB-04 | Mandar fotos enormes o demasiadas para llenar el almacenamiento | Asegurado | THR-04 | 413 o 422 por los límites de HU-MOV-08 | TC-04 |
| AB-05 | Leer la cola en un teléfono robado | Atacante con el teléfono | THR-05 | Base SQLCipher y fotos AES-GCM por cuenta (HU-MOV-08) | TC-12 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN el asegurado envía sin conexión, THE app SHALL guardar el reporte cifrado y mostrar "Reporte en cola de envío" | STATED |
| AC-02 | WHEN el teléfono recupera la conexión, THE app SHALL enviar el reporte en cola aunque esté cerrada, sin acción del asegurado | STATED |
| AC-03 | WHEN el mismo reporte llega más de una vez, THE servidor SHALL registrar un solo siniestro y devolver el mismo en cada reintento | STATED |
| AC-04 | IF el envío falla por red o error del servidor, THE app SHALL reintentar con espera creciente y conservar el reporte hasta que se reciba | STATED |
| AC-05 | WHILE hay reportes en cola, THE lista de «Mis siniestros» SHALL mostrarlos como pendientes de envío | STATED |
| AC-06 | WHEN el backend recibe una foto, THE sistema SHALL verificar su SHA-256 contra la huella del teléfono y rechazar el reporte si no coincide | DERIVED: RC-11, la evidencia debe llegar íntegra |
| AC-07 | THE servidor SHALL aceptar el reporte solo sobre una póliza vigente del titular del token y con los límites de HU-MOV-08 (5 fotos, 2 MB cada una, 500 caracteres) | DERIVED: hoy los límites solo existen en el teléfono |
| AC-08 | WHEN el servidor recibe el reporte, THE app SHALL borrar la copia local y el siniestro SHALL aparecer en «Mis siniestros» y en la bandeja del analista | DERIVED: el reporte local deja de ser necesario y la evidencia vive en el servidor |
| AC-09 | IF el servidor rechaza el reporte por un motivo que reintentar no arregla (póliza no vigente o foto alterada), THE app SHALL dejar de reintentar y explicarle al asegurado qué hacer | DERIVED: reintentar para siempre un rechazo definitivo gasta batería y datos |
| AC-10 | WHEN se integra a `dev`, THE CI SHALL producir un APK de release firmado | DERIVED: RC-12, el envío sin señal solo sirve en una app instalable |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | AVAILABILITY | Envío de la cola | El reporte llega en menos de 1 minuto tras recuperar la red (WorkManager con red disponible) y nunca se pierde por un fallo de red o del servidor |
| QR-02 | ACCESSIBILITY | Aviso de cola y pendientes | Lineamientos de Android: TalkBack anuncia el estado del envío; objetivos de 48 dp |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | Póliza, ubicación y fotos del siniestro |
| Sensitive data (special categories, biometrics, health, financial, minors) | YES | Las fotos y la descripción pueden mostrar lesiones o la muerte |
| Authentication, sessions, credentials or one-time codes | YES | Token de reporte guardado en el teléfono para enviar con la app cerrada |
| Authorization or access to other users' resources | YES | Solo sobre pólizas propias |
| Payments or money movement | NO | El pago es de HU-WEB-15 |
| Third-party services or integrations | NO | S3 ya existe |
| Infrastructure, network, IAM, CI/CD or secrets | YES | `s3:PutObject` y la llave de firma del APK en los secretos de GitHub |
| Multiple tenants, partners or organizations | NO | El asegurado no es un socio |
| Storage or processing on a mobile device | YES | Cola cifrada con las fotos |
| Web UI or data exposed in the browser | NO | Es la app |
| New or updated dependencies | NO | Room, SQLCipher, WorkManager y boto3 ya están |
| Critical paths (sdd/security/critical-paths.md) | YES | movil/app/src/main/java/com/solventa/app/data/claims/** |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | El reporte solo se acepta sobre una póliza vigente del titular de la cuenta del token; otra responde 404 | DATA-01, THR-01 | CTL-01 | HIGH |
| SR-02 | El token de reporte solo sirve para `POST /insured/claims`, vale 7 días y pertenece a la familia de la sesión: cerrar la app no lo revoca, el cierre de sesión explícito y el cambio de contraseña sí | DATA-03, THR-06 | CTL-02 | HIGH |
| SR-03 | Cada foto se acepta solo si su SHA-256 coincide con la huella del teléfono y es un JPEG dentro de los límites | DATA-02, THR-02, THR-04 | CTL-03 | HIGH |
| SR-04 | Un reporte se registra una sola vez aunque llegue varias veces o al mismo tiempo | THR-03 | CTL-04 | HIGH |
| SR-05 | La cola, las fotos y el token de reporte quedan cifrados por cuenta en el teléfono y se borran al cerrar sesión | DATA-01, DATA-02, DATA-03, THR-05 | CTL-05 | HIGH |
| SR-06 | El backend solo escribe en `siniestros/*`; la llave de firma del APK solo vive en los secretos de GitHub | DATA-04, THR-07 | CTL-06 | MEDIUM |

## 2. Design

### Components

Backend (ADR 0004 y 0005):
- **`modules/claims/intake.py`** (nuevo): `receive_report` valida póliza propia y vigente, límites, JPEG y huella; sube cada foto con `put_bytes` a `siniestros/<radicado>/<posición>.jpg` y llama a `claim_service.register_claim`. Si el `report_id` ya existe para esa cuenta, devuelve el mismo siniestro.
- **`modules/claims/models.py`:** `ClaimDB.client_report_id` (UUID del teléfono, único) y la secuencia `claim_number_seq` para `SIN-AAAA-NNNNN`.
- **`core/storage.py`:** `put_bytes` entra al puerto `DocumentStorage`.
- **`modules/identity/service.py`:** cada ingreso crea una familia de sesión (`sid`) que comparten el refresh y el `report_token` (tipo `report`, alcance `claims:submit`, `ver`, 7 días) y que se conserva al renovar; `account_for_report` exige que la familia no esté revocada; `logout` revoca el refresh y, salvo `keep_reporting`, también la familia.
- **`bff/insured/router.py`:** `POST /insured/claims` (multipart) acepta el token de acceso o el de reporte; 201 nuevo, 200 repetido, 404, 413, 422 y 503.
- **Terraform:** `s3:PutObject` sobre `siniestros/*` en la tarea de ECS.

App (ADR 0014, 0015 y 0021):
- **Dominio:** `ReportState` suma `QUEUED` y `REJECTED`; `ClaimSubmitApi` con `Received`, `Retry`, `Rejected(motivo)` y `Unauthorized`.
- **`data/claims`:** base de reportes versión 2 con migración que conserva los datos (estado, intentos, motivo del rechazo); `HttpClaimSubmitApi` (multipart); `ClaimSender` que lee el reporte, descifra cada foto con `photoImage` y lo envía; `ClaimSendWorker` (red disponible, espera exponencial desde 10 s, trabajo único por cuenta).
- **`data/session`:** `ReportTokenStore` guarda el último token de reporte cifrado con una llave del Keystore sin huella, por cuenta; se borra al cerrar sesión.
- **UI:** «Enviar» deja el reporte en cola y muestra «Reporte en cola de envío»; «Mis siniestros» muestra arriba los pendientes; un rechazo definitivo explica qué hacer.
- **CI:** `signingConfig` de release desde variables de entorno y job `assembleRelease` que publica el APK si los secretos existen.

### Data flows

- **Encolar:** pulsa «Enviar» → la base cifrada pasa el reporte a `QUEUED` → se agenda el trabajo único.
- **Enviar:** WorkManager con red → `ClaimSender` → token de acceso de la sesión abierta o, si no hay, el token de reporte → `POST /insured/claims` con el reporte y las fotos → backend verifica, sube a S3, registra y notifica → la app borra el reporte y sincroniza «Mis siniestros».
- **Fallo:** red o 5xx → `Result.retry` con espera creciente; 404 o 422 definitivo → `REJECTED` con el motivo y sin más reintentos; 401 → espera a la próxima sesión.

### Data contract

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| POST /insured/claims | multipart: `report` (JSON: `report_id`, `policy_number`, `description`, `photos[]` con `position`, `sha256`, `captured_at`, `trusted_time_source`, `latitude`, `longitude`, `accuracy_m`, `mock_location`) y `photo_1` … `photo_5` (JPEG) | 201 `{claim_number, status, reported_at}`; 200 el mismo si se repite; 404 póliza ajena o no vigente; 413 foto de más de 2 MB; 422 huella distinta, no JPEG o límites; 503 S3 no disponible |
| POST /auth/login, /auth/refresh (cambia) | — | `TokenPair` suma `report_token` |
| POST /auth/logout (cambia) | `refresh_token`, `keep_reporting` (falso por defecto) | 204; con `keep_reporting` el token de reporte sigue sirviendo |

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| Token de reporte de un solo alcance, en la familia de la sesión; cerrar la app envía `keep_reporting` y el cierre de sesión revoca la familia | CTL-02 | Atarlo al refresh: cerrar la app lo revocaba y AC-02 no se cumplía; no revocar al cerrar la app: debilita el cierre de la sesión de HU-MOV-07; guardar el refresh en disco: rompe HU-MOV-07 y da acceso a todo; esperar a que el asegurado abra la app: AC-02 quedaría parcial |
| Huella recalculada en el backend antes de guardar | CTL-03 | Confiar en la huella enviada sin recalcular |
| `client_report_id` único y relectura ante la carrera | CTL-04 | Registro de idempotencia por cabecera: no protege dos envíos simultáneos del mismo reporte |
| Fotos por el backend en un solo envío | CTL-03 | URL prefirmada de subida: la huella se verificaría después y quedaría evidencia sin siniestro |

### Architecture decisions

N/A: reutiliza la arquitectura hexagonal (ADR 0004), WorkManager (ADR 0014), la base cifrada por cuenta (ADR 0021) y el modelo de evidencias del teléfono (ADR 0024). El token de reporte se documenta en la evaluación de seguridad.

### Configuration and deployment

- **Columna nueva:** `claims.client_report_id` con `ADD COLUMN IF NOT EXISTS` e índice único; secuencia `claim_number_seq`.
- **IAM:** `s3:PutObject` sobre `siniestros/*`.
- **Secretos de GitHub (los crea el equipo):** `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Sin ellos el job de release se omite.
- **App:** base de reportes versión 2 con migración.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Recepción idempotente con huella, límites, fotos a S3 y radicado (#237) | AC-03, AC-06, AC-07, SR-01, SR-03, SR-04 | CTL-01, CTL-03, CTL-04 | DONE | backend/app/modules/claims/intake.py; backend/app/bff/insured/router.py; backend/tests/integration/test_claim_report_api.py |
| T-02 | Token de reporte en el backend | AC-02, SR-02 | CTL-02 | DONE | backend/app/modules/identity/service.py; backend/app/bff/auth/router.py |
| T-03 | Cola cifrada con migración, token de reporte guardado y aviso de cola (#235) | AC-01, AC-05, SR-05 | CTL-05 | DONE | movil/app/src/main/java/com/solventa/app/data/claims/EncryptedClaimStore.kt; movil/app/src/main/java/com/solventa/app/data/session/ReportTokenStore.kt |
| T-04 | Envío con WorkManager, reintentos y rechazo definitivo (#236) | AC-02, AC-04, AC-08, AC-09, QR-01 | CTL-02, CTL-05 | DONE | movil/app/src/main/java/com/solventa/app/data/claims/ClaimSender.kt; movil/app/src/main/java/com/solventa/app/data/claims/ClaimSendWorker.kt |
| T-05 | `s3:PutObject` en Terraform y APK de release firmado en el CI | AC-10, SR-06 | CTL-06 | DONE | terraform/modules/ecs/main.tf; .github/workflows/ci.yml; movil/app/build.gradle.kts |
| T-06 | Pruebas de la cola, los reintentos, la idempotencia y la huella (#238) | AC-01 a AC-10, QR-01, QR-02 | CTL-01 a CTL-06 | DONE | backend/tests/integration/test_claim_report_api.py; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt |
| T-07 | Aviso del job de release en la raíz del repositorio: sin los secretos de la llave, el paso corría en `movil/` antes del checkout y el job fallaba en vez de omitirse | AC-10 | — | DONE | .github/workflows/ci.yml:198; corrida fallida 43678228736 |

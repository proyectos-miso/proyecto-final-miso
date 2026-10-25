# HU-MOV-07 · Sincronizacion automatica

| Field | Value |
|---|---|
| ID | HU-MOV-07 |
| Title | Sincronizacion automatica |
| User story | https://github.com/proyectos-miso/proyecto-final-miso/issues/73 |
| Owner | David Gutiérrez |
| Status | READY |
| Created | 2026-10-25 |
| Security profile | HIGH |

## 1. Requirements

### User story

**HU-MOV-07 · Actualizar la copia local al recuperar la conexión** (Sprint 2, Must have, 2 puntos, FE-MOV-03, ESC-DIS-01; depende de HU-MOV-04).

**Como** cliente asegurado **quiero** que la aplicación actualice sola mi información cuando vuelvo a tener señal **para** no tener que acordarme de sincronizar.

Criterios de aceptación de la historia:

1. **Sincronización automática.** *Dada* la app sin conexión, *cuando* el teléfono recupera la red, *entonces* las pólizas se actualizan en segundo plano sin acción del usuario.
2. **Sin duplicados.** *Dada* una sincronización, *cuando* trae pólizas ya guardadas, *entonces* las actualiza sin duplicarlas.
3. **Reintento ante falla.** *Dada* una sincronización que falla, *cuando* ocurre el error, *entonces* se reintenta con espera creciente sin bloquear la app.
4. **Fecha de sincronización.** *Dada* una sincronización exitosa, *cuando* termina, *entonces* actualiza la fecha que muestra HU-MOV-06.

Requisitos no funcionales: HA-08 (contrapresión, #38), HA-10 (recuperarse de un fallo de persistencia, #40), HA-11 (integridad ante fallos y reintentos, #41). Mockup: no aplica. Tareas: #198 sincronización con WorkManager, #199 reintentos y sin duplicados, #200 pruebas del sincronizador.

### Problem and outcome

La billetera solo pide las pólizas al abrirse: si se abrió sin red o con el servidor caído, muestra la copia con el aviso de sin conexión y no vuelve a intentar. Además el access token dura 15 minutos y la app no lo renueva, así que un intento tardío recibiría un 401 y borraría la copia. Resultado: al volver la red la app trae las pólizas sola, reintenta con espera creciente, reemplaza la copia sin duplicar y la billetera muestra la nueva fecha sin el aviso.

### Goals and non-goals

Objetivos:
- Sincronizar al volver la red, en primer o segundo plano, y al volver a la app con la billetera sin conexión.
- Una sincronización a la vez, con reintentos crecientes y sin ocultar la copia.
- Renovar el access token sin revivir una sesión cerrada.
- Tras un ingreso sin red con la huella, sincronizar durante 15 minutos.
- Que un bloqueo de la cuenta no borre la copia (backend) y que la release solo use HTTPS.

Fuera de alcance:
- Sincronizar después de que Android cierre la app: los tokens solo viven en memoria (ADR 0017).
- Sincronizar periódicamente, un botón para sincronizar y notificaciones.
- Vida máxima de la sesión y revocación por familia en el backend: otra historia (RISK-04, DEC-07).
- Renovar el token en `registerDevice` y `revokeDevice`, y cambiar de cliente HTTP (ADR 0016).

### Affected paths

| Path | Change |
|---|---|
| backend/app/modules/identity/service.py | Renovación con el ingreso suspendido: bloqueo, sin rotar |
| backend/app/bff/auth/router.py | `auth/refresh` responde 423 con `Retry-After` |
| backend/tests/** | Pruebas de la renovación con el ingreso suspendido |
| movil/gradle/libs.versions.toml | WorkManager 2.10.5 y su librería de pruebas |
| movil/app/build.gradle.kts | Dependencias y URL `https` obligatoria en release |
| movil/app/src/main/AndroidManifest.xml | Quita permisos de WorkManager |
| movil/app/src/main/res/xml/network_security_config.xml | Sin HTTP sin TLS en release |
| movil/app/src/debug/res/xml/network_security_config.xml | HTTP sin TLS al backend local, solo en debug |
| movil/app/src/main/res/values/strings.xml | Aviso para volver a ingresar |
| movil/app/src/main/java/com/solventa/app/domain/** | Puerto `PolicySync`, renovación y `save` con resultado |
| movil/app/src/main/java/com/solventa/app/data/** | Sincronizador, trabajo, sesión atómica, `refresh` y guardado sin repetidos |
| movil/app/src/main/java/com/solventa/app/ui/** | Billetera y sesión conectadas a la sincronización |
| movil/app/src/main/java/com/solventa/app/MainActivity.kt | Conexión de las pantallas |
| movil/app/src/main/java/com/solventa/app/SolventaApplication.kt | Composición |
| movil/app/src/test/** | Pruebas unitarias y de integración en Robolectric |
| docs/adr/** | ADR 0022 nueva; ADR 0016 y 0017 actualizadas |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Asegurado | La billetera se actualiza sola al volver la red | 1. La billetera muestra la copia sin conexión. 2. Vuelve la red. 3. La app trae las pólizas y la billetera muestra la nueva fecha sin el aviso | El servidor falla: reintenta con espera creciente. La app está en segundo plano: al volver ya está actualizada. Sin copia: pasa de pedir conexión a mostrar las pólizas |
| UC-02 | Asegurado | Sincronizar con el access token vencido | 1. La billetera muestra la copia sin conexión. 2. La red vuelve más de 15 minutos después. 3. La app renueva el token y sincroniza | La renovación es rechazada: se borra la copia y se pide el ingreso. La cuenta está bloqueada: 423 y se reintenta. La renovación anterior quedó sin respuesta: se pide el ingreso sin borrar la copia |
| UC-03 | Asegurado | Sincronizar tras entrar sin red con la huella | 1. Entra con la huella sin red. 2. La red vuelve antes de 15 minutos. 3. La app entra con la credencial del teléfono y sincroniza | La credencial es rechazada: se borra la copia y se desvincula la huella. Pasan 15 minutos o cambia la huella: se suelta la credencial; al volver la red se pide el ingreso sin borrar la copia |
| UC-04 | Asegurado | Cerrar sesión con una sincronización en curso o pendiente | 1. Cierra sesión. 2. La app termina la sesión, cancela la sincronización y borra la copia enseguida. 3. Revoca el refresh token | Una renovación estaba en curso: la sesión no revive y el par nuevo se revoca. La actividad termina sin cerrar sesión: termina la sesión y se revoca, sin borrar la copia |
| UC-05 | Asegurado | Volver a la app con la billetera sin conexión | 1. La red volvió con el teléfono bloqueado y no se pudo guardar. 2. La persona vuelve a la app. 3. La app sincroniza enseguida | Sigue sin red: se conserva el trabajo pendiente |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Prender y apagar la red muchas veces para generar ráfagas contra el backend | Quien tenga el teléfono | THR-01 | Un trabajo pendiente, una sincronización a la vez y espera creciente | TC-03 |
| AB-02 | Buscar el correo, los tokens o la credencial en la base de WorkManager | Quien extraiga los archivos | THR-02 | El trabajo no guarda datos de la cuenta | TC-10, TC-14 |
| AB-03 | Cerrar sesión durante la descarga para que la copia reaparezca | Quien comparta el teléfono | THR-03 | La copia se borra enseguida y la sincronización no guarda | TC-09 |
| AB-04 | Volcar la memoria de la app abierta sin red para sacar la credencial | Quien tenga root o depuración | THR-04 | La credencial vive en memoria a lo sumo 15 minutos | TC-06 |
| AB-05 | Bloquear la cuenta de otra persona con contraseñas malas para que su app borre la copia | Quien conozca el correo | THR-10 | La renovación responde 423 y la app reintenta sin borrar | TC-05, TC-17 |
| AB-06 | Compilar una release sin URL `https` | Quien prepare una build | THR-11 | La build falla | TC-18 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN el teléfono recupera la red mientras la billetera muestra la copia sin conexión o pide conexión, THE app SHALL traer las pólizas en segundo plano sin acción del usuario, con la excepción de AC-07 | STATED |
| AC-02 | WHEN una sincronización trae pólizas que ya están en la copia, THE app SHALL reemplazar la copia sin duplicarlas | STATED |
| AC-03 | IF una sincronización falla por la red o el servidor, THE app SHALL reintentarla con espera creciente sin bloquear la billetera | STATED |
| AC-04 | WHEN una sincronización guarda la copia, THE billetera SHALL mostrar las pólizas y la nueva fecha de HU-MOV-06 sin el aviso de sin conexión | STATED |
| AC-05 | WHEN la consulta de pólizas recibe un 401, THE app SHALL renovar el token una vez, guardar el par nuevo solo en memoria y repetir la consulta | DERIVED: el token dura 15 minutos; las demás llamadas siguen sin renovar (ADR 0016) |
| AC-06 | IF el servidor rechaza la renovación (401), rechaza otra vez la consulta tras renovar o rechaza la credencial, THE app SHALL borrar la copia, terminar la sesión y pedir el ingreso, y con la credencial SHALL desvincular la huella; si la renovación anterior se envió y no tuvo respuesta válida, SHALL terminar la sesión sin borrar la copia | DERIVED: mismo trato que HU-MOV-03 y HU-MOV-04; el backend pudo rotar el token sin que llegara la respuesta (RISK-02, DEC-08) |
| AC-07 | WHEN la persona entra sin red con la huella, THE app SHALL conservar la credencial solo en memoria y a lo sumo 15 minutos, y soltarla al obtener los tokens, al cerrar sesión, al desactivar la huella o si cambia la huella; IF la red vuelve después, THE app SHALL terminar la sesión sin borrar la copia y pedir el ingreso para actualizar | DERIVED: sin tokens no hay con qué sincronizar y pedir la huella sería una acción; el tope acota el uso sin huella nueva (DEC-03) |
| AC-08 | WHILE no hay una sesión abierta, THE sincronización SHALL terminar sin llamar al servidor ni escribir la copia | DERIVED: los tokens solo viven en memoria (ADR 0017) |
| AC-09 | WHEN la persona cierra sesión, THE app SHALL terminar la sesión, cancelar la sincronización, borrar la copia enseguida y después revocar el refresh token, aunque la pantalla se cierre | DERIVED: la carrera que HU-MOV-05 corrigió, ahora en segundo plano |
| AC-10 | IF la copia no se puede guardar, por ejemplo con el teléfono bloqueado, THE app SHALL reintentar y conservar la fecha anterior | DERIVED: la llave exige el teléfono desbloqueado (HU-MOV-05); HA-10 |
| AC-11 | THE app SHALL tener a lo sumo una sincronización en curso, contando la de la billetera y la del trabajo, y un trabajo pendiente | DERIVED: HA-08; dos a la vez renovarían con el mismo refresh token |
| AC-12 | IF la respuesta trae la misma póliza dos veces, THE app SHALL guardarla una vez | DERIVED: el número de póliza es la clave; un repetido haría fallar cada reintento (HA-11) |
| AC-13 | WHEN la app vuelve al primer plano con la billetera sin conexión, THE app SHALL sincronizar enseguida, a lo sumo una vez cada 30 s | DERIVED: con el teléfono bloqueado la espera del trabajo crece |
| AC-14 | WHILE la cuenta tiene el ingreso suspendido, THE backend SHALL responder 423 a la renovación sin rotar el refresh token, y THE app SHALL reintentar sin borrar la copia | DERIVED: con el 401 actual, bloquear la cuenta de otra persona borraría su copia (AB-05, DEC-06) |
| AC-15 | WHEN la actividad termina sin cerrar sesión, THE app SHALL terminar la sesión que abrió, detener la sincronización y revocar su refresh token, sin borrar la copia | DERIVED: al volver se pide el ingreso; en Android 12 o superior "atrás" no termina la actividad raíz (ADR 0015) |
| AC-16 | THE build release SHALL fallar si la URL del backend no es `https://` y SHALL no permitir HTTP sin TLS | DERIVED: la sincronización envía secretos sin acción de la persona (DEC-09; regla 5 de ADR 0016) |
| AC-17 | WHILE una sincronización está en curso, THE billetera SHALL seguir mostrando la copia o las pólizas que ya mostraba | DERIVED: AC-03; con una sincronización a la vez, reiniciar la pantalla ocultaría la copia |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | AVAILABILITY | La sincronización arranca al recuperar la red | Con la app en primer plano, la billetera se actualiza dentro de 60 s tras quitar el modo avión, en 5 de 5 intentos en el teléfono |
| QR-02 | AVAILABILITY | Los reintentos esperan cada vez más | El primer reintento espera 10 s y cada uno duplica la espera hasta 5 horas |
| QR-03 | ACCESSIBILITY | La actualización se puede seguir con TalkBack | Guías de Android: al actualizarse la billetera el foco no salta y la fecha nueva se lee completa con la fuente al máximo |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | Pólizas con el titular; el correo identifica la cuenta |
| Sensitive data (special categories, biometrics, health, financial, minors) | NO | Coberturas y sumas aseguradas (HU-MOV-05) |
| Authentication, sessions, credentials or one-time codes | YES | Renovación, credencial en memoria y 423 de la renovación |
| Authorization or access to other users' resources | NO | Mismo endpoint con el token de la cuenta |
| Payments or money movement | NO | No aplica |
| Third-party services or integrations | NO | WorkManager es una librería local |
| Infrastructure, network, IAM, CI/CD or secrets | YES | Configuración de red y URL `https` en release |
| Multiple tenants, partners or organizations | NO | Canal del asegurado |
| Storage or processing on a mobile device | YES | Trabajo en segundo plano y su base |
| Web UI or data exposed in the browser | NO | Solo móvil |
| New or updated dependencies | YES | `androidx.work:work-runtime` y `work-testing` 2.10.5 |
| Critical paths (sdd/security/critical-paths.md) | YES | `**/auth/**`, `**/identity/**`, `data/session/**` y `network_security_config.xml` (HIGH); manifiesto, `*.gradle*`, `MainActivity.kt` y `SolventaApplication.kt` |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | Un trabajo pendiente, solo con red y con espera exponencial desde 10 s | THR-01, DATA-04 | CTL-01 | MEDIUM |
| SR-02 | El trabajo no guarda datos de la cuenta y la sincronización no los registra | THR-02, THR-12, DATA-02, DATA-03 | CTL-02 | HIGH |
| SR-03 | Ninguna sincronización deja copia ni sigue con la sesión tras el cierre de sesión o de la actividad | THR-03, DATA-01 | CTL-03 | HIGH |
| SR-04 | La credencial vive solo en memoria, a lo sumo 15 minutos, y no se usa si la llave biométrica cambió | THR-04, DATA-03, RISK-01 | CTL-04 | HIGH |
| SR-05 | Una sincronización a la vez, y una renovación tardía no revive la sesión y su par se revoca | THR-05, DATA-02 | CTL-05 | HIGH |
| SR-06 | Solo un rechazo del servidor termina la sesión, y una renovación sin respuesta no hace borrar la copia | THR-06, RISK-02, DATA-01 | CTL-06 | MEDIUM |
| SR-07 | Una póliza repetida se guarda una vez | THR-07, DATA-01 | CTL-07 | LOW |
| SR-08 | Sin permisos que no se usan ni componentes exportados fuera de los del sistema | THR-08, DATA-04 | CTL-08 | LOW |
| SR-09 | Cada copia recibe solo las pólizas de su sesión, y un rechazo tardío no termina otra sesión | THR-09, DATA-01 | CTL-09 | MEDIUM |
| SR-10 | Con el ingreso suspendido, la renovación no termina la sesión ni borra la copia | THR-10, DATA-01 | CTL-10 | MEDIUM |
| SR-11 | La release solo usa HTTPS | THR-11, DATA-02, DATA-03 | CTL-11 | HIGH |

## 2. Design

### Components

- **Backend:** `refresh` lanza `AccountLockedError` con el ingreso suspendido, después de validar el jti y la versión del token y antes de revocar el jti; no audita el intento. El router responde 423 con `Retry-After`, como el ingreso.
- **`domain`:**
  - Puerto `PolicySync`: `sync(online)`, `outcomes`, `schedule()` y `cancel()`.
  - `SyncOutcome`: `Synced(email, copy)`, `Failed`, `SessionEnded(email, reason)` o `NoSession`.
  - `PolicyStore.save` devuelve si escribió.
  - `AuthApi.refresh` devuelve `Renewed(tokens)`, `Rejected` (401), `Unanswered` (la petición pudo salir y no hubo respuesta válida: lectura fallida, 200 inválido o cualquier estado que no sea 200, 401 ni 423) o `Unavailable` (falla al conectar, antes de enviar, o 423).
- **Motivos de `SessionEnded`:**

  | Motivo | Cuándo | Efecto | Aviso |
  |---|---|---|---|
  | `EXPIRED` | 401 de la renovación o segundo 401 de la consulta | Borra la copia | `SESSION_EXPIRED` |
  | `DEVICE_REJECTED` | El servidor rechaza la credencial | Borra la copia; desvincula la huella siempre | `SESSION_RESET` |
  | `RELOGIN` | 401 tras una renovación sin respuesta, o red de vuelta sin credencial | Conserva la copia | "Ingrese de nuevo para actualizar sus pólizas" |

- **`data/session/SecureSessionRepository`:**
  - La sesión es una instantánea inmutable `OpenSession(id, email, tokens, credential, credentialSince, renewalUnanswered)` en un `AtomicReference`. No es `data class` y su `toString` no muestra datos.
  - `id` y `credentialSince` se conservan al renovar y al soltar la credencial. `credentialSince` usa `elapsedRealtime()`, y un tiempo negativo cuenta como vencido.
  - Mutadores: `start`, `startOffline`, `replace(from, to)`, `endIf(from)` y `endSession(id)` (devuelve la instantánea que quitó), todos con `compareAndSet`; `end()`; `dropCredential`, que crea una instantánea nueva y también lo llaman `unbind`, `unbindAll` y `clear`.
  - `keyUsable(email)` no lanza y responde `USABLE`, `LOST` (llave invalidada, que pasa por `unbindAll`, o cuenta sin vincular) o `UNAVAILABLE`.
- **`data/policies/SessionPolicySync`:**
  1. Registra su `Job`, toma un `Mutex` único y después lee la instantánea. Sin sesión responde `NoSession`.
  2. **Sin tokens:**
     - Si la credencial venció o la llave dio `LOST`, la suelta. Sin credencial, termina con `RELOGIN` si `online`; si no, responde `NoSession`.
     - Con `UNAVAILABLE` responde `Failed`.
     - Con `USABLE` entra con `biometricLogin` sin cancelación y aplica `replace`; si la sesión cambió, revoca el par nuevo con `logout`.
  3. **Consulta:** ante un 401 renueva una vez, sin cancelación.
     - `Renewed`: `replace` limpia la marca (si la sesión cambió, revoca el par) y repite la consulta.
     - `Unanswered`: marca la renovación y responde `Failed`; `Unavailable` responde `Failed`.
     - `Rejected`, o un segundo 401: termina con `RELOGIN` si estaba marcada, y con `EXPIRED` si no.
  4. **Guardar:** comprueba que la sesión no cambió y guarda en su cuenta. Con `true` publica `Synced`; con `false` responde `Failed`.
  5. **Terminar:** `endIf(instantánea)`; solo si aplicó, hace el efecto del motivo y publica `SessionEnded`.
  - Solo lanza la cancelación: cualquier otra excepción responde `Failed`.
  - `cancel()` cancela al instante las sincronizaciones registradas y el trabajo pendiente.
  - `outcomes` es un `SharedFlow` sin repetición; se publica con `tryEmit`.
- **`PolicySyncWorker` y `WorkManagerSyncScheduler`:**
  - El trabajo pide `PolicySync` a la aplicación (`PolicySyncHost`, ADR 0014) y llama a `sync(online = true)`.
  - `Failed` o una excepción dan `Result.retry()`; lo demás, `Result.success()`.
  - El programador encola `policy_sync` con `KEEP`, `CONNECTED` y `EXPONENTIAL` de 10 s, sin datos de entrada ni etiquetas.
  - Las pruebas usan una aplicación de prueba y `WorkManagerTestInitHelper`.
- **`EncryptedPolicyStore`:** `save` devuelve si escribió y quita los números de póliza repetidos.
- **`HttpAuthApi.refresh`:** hace `connect()` antes de enviar y usa `setFixedLengthStreamingMode`, así la conexión no reenvía sola un token ya rotado.
- **`WalletViewModel`:**
  - Recibe el puerto en lugar de `PoliciesApi`.
  - `open(email)` solo muestra la carga con una cuenta nueva y luego sincroniza: `Synced` muestra las pólizas; `Failed` y `NoSession` muestran la copia y llaman a `schedule()`.
  - `refreshClock()` hace lo mismo, a lo sumo cada 30 s, si la billetera está sin conexión.
  - Escucha `outcomes` de la cuenta abierta y pasa el motivo de `SessionEnded`.
- **`AuthViewModel`:**
  - Recibe el puerto y `appScope`.
  - Los ingresos llaman a `start`; el ingreso sin red llama a `startOffline` y un temporizador suelta la credencial a los 15 minutos.
  - `confirmLogout` llama a `end()` y `cancel()`, y en `appScope` borra la copia y llama a `logout`.
  - `onSessionEnded(reason)` termina la sesión, cancela y muestra el aviso.
  - `onCleared` hace `endSession(id)`, que devuelve la instantánea que quitó, cancela y en `appScope` revoca el refresh token de esa instantánea, que pudo rotar desde el ingreso.
  - Se quita `accessToken()`.
- **`MainActivity` y `SolventaApplication`:** `appScope` y `policySync` en el contenedor; `open(email)` sin token.
- **Manifiesto y red:**
  - Se quitan `RECEIVE_BOOT_COMPLETED` y `FOREGROUND_SERVICE` con `tools:node="remove"`.
  - HTTP sin TLS solo en el `network_security_config.xml` de debug, y solo hacia `10.0.2.2` y `localhost`.
  - `preReleaseBuild` falla si la URL no empieza con `https://`.

### Data flows

- **Sin red:** la billetera muestra la copia y llama a `schedule()`; WorkManager espera la red.
- **Al volver la red o a la app:**
  1. Entra con la credencial (`auth/biometric`) si hace falta.
  2. Pide `insured/policies`; ante un 401 renueva (`auth/refresh`) y repite.
  3. Guarda y publica el resultado.
- **Cerrar sesión:** terminar → cancelar → borrar la copia → `auth/logout`, en `appScope`. No hace falta esperar a la sincronización: si una renovación termina después, falla el `compareAndSet` y la propia sincronización revoca el par.
- **Límites:** los secretos solo cruzan la red por HTTPS en release; al planificador solo llegan restricciones y estado.

### Data contract

- `POST /api/v1/auth/refresh`:
  - Cuerpo: `{"refresh_token"}`.
  - 200: `{"access_token", "refresh_token", "token_type", "expires_in", "full_name"}`; la app solo lee los tokens.
  - 401: `{"detail"}`.
  - 423 (nuevo): `Retry-After` y `{"detail", "locked_until", "retry_after"}`, como `auth/login`, sin revocar el token.
- Trabajo `policy_sync`: sin datos de entrada; único con `KEEP`; con red; exponencial desde 10 s, tope de 5 horas.

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| Trabajo único con `KEEP`, red y espera exponencial | CTL-01 | Callback de red propio: Android congela la app en segundo plano y las ráfagas no tendrían tope |
| La cuenta sale de la sesión en memoria, sin datos de entrada | CTL-02 | Pasar el correo al trabajo: quedaría en claro en su base |
| Terminar, cancelar y borrar enseguida, en `appScope` | CTL-03 | Esperar a la sincronización antes de borrar: si la app se cierra en la espera, la copia sobrevive |
| Credencial a lo sumo 15 minutos con `elapsedRealtime`; después, pedir el ingreso sin borrar la copia | CTL-04 | Sin tope: se usaría horas después de la huella. Reloj de pared: atrasarlo extendería el tope. Solo un aviso: la única salida, cerrar sesión, borraría la copia (DEC-03) |
| `Mutex` en toda la sincronización, `compareAndSet` y tokens sin cancelación | CTL-05 | `Mutex` solo en la renovación: la segunda renovaría con el token ya rotado. Comprobar y luego asignar: un cierre en otro hilo quedaría revivido |
| Solo un rechazo del servidor termina la sesión; tras una renovación sin respuesta no se borra la copia | CTL-06 | Toda falla como sesión vencida: borraría la copia sin red. Ventana de gracia en el backend: aceptaría tokens reutilizados (DEC-08) |
| Quitar repetidos al guardar | CTL-07 | Rechazar la respuesta: se reintentaría para siempre |
| Quitar los permisos de arranque y servicio en primer plano | CTL-08 | Dejarlos: tras reiniciar no hay sesión |
| Pedir, guardar y terminar con la instantánea | CTL-09 | Leer la sesión actual: un cambio de cuenta lo aplicaría a la otra |
| 423 en `auth/refresh` | CTL-10 | 401 sin borrar en la app: dejaría de borrar ante una revocación real (DEC-06) |
| HTTPS obligatorio en release | CTL-11 | Dejarlo como riesgo (DEC-09) |

### Architecture decisions

| ADR | Title | Status |
|---|---|---|
| 0022 | Mobile: background work with WorkManager | Proposed (nueva) |
| 0016 | Mobile: networking | Proposed; se actualiza: `refresh` sin interceptores, regla 5 cumplida, `registerDevice` y `revokeDevice` sin renovar |
| 0017 | Mobile: credential storage | Proposed; se actualiza: renovación en memoria y credencial a lo sumo 15 minutos |

### Configuration and deployment

- **Tiempos:** reintentos desde 10 s con tope de 5 horas; credencial 15 minutos con `elapsedRealtime()`; sincronizar al volver a la app a lo sumo cada 30 s.
- **Release:** `-PapiBaseUrl` debe ser `https://`. Debug sigue con `http://10.0.2.2:8000/api/v1/`.
- **Sin cambios:** variables del backend y versión de la base local.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Backend: 423 en `auth/refresh` con el ingreso suspendido | AC-14, SR-10 | CTL-10 | TODO | — |
| T-02 | Release solo HTTPS | AC-16, SR-11 | CTL-11 | TODO | — |
| T-03 | Sesión atómica, credencial con tope y `refresh` (#198) | AC-05, AC-07, SR-04, SR-05 | CTL-04, CTL-05 | TODO | — |
| T-04 | `SessionPolicySync` (#199) | AC-02, AC-05, AC-06, AC-07, AC-08, AC-10, AC-11, AC-12, SR-05, SR-06, SR-07, SR-09 | CTL-05, CTL-06, CTL-07, CTL-09 | TODO | — |
| T-05 | WorkManager: dependencia, trabajo, programador y permisos (#198, #199) | AC-01, AC-03, AC-11, QR-02, SR-01, SR-02, SR-08 | CTL-01, CTL-02, CTL-08 | TODO | — |
| T-06 | Pantallas: sincronizar, copia visible, avisos y fin de sesión (#198) | AC-01, AC-04, AC-09, AC-13, AC-15, AC-17, SR-03, SR-09 | CTL-03, CTL-09 | TODO | — |
| T-07 | ADR 0022 nueva y ADR 0016 y 0017 actualizadas | AC-05, AC-07, AC-16, SR-04, SR-05, SR-11 | CTL-04, CTL-05, CTL-11 | TODO | — |
| T-08 | Pruebas unitarias, de integración y del backend (#200) | AC-01, AC-02, AC-03, AC-04, AC-05, AC-06, AC-07, AC-08, AC-09, AC-10, AC-11, AC-12, AC-13, AC-14, AC-15, AC-16, AC-17, QR-02, SR-01, SR-02, SR-03, SR-04, SR-05, SR-06, SR-07, SR-08, SR-09, SR-10, SR-11 | CTL-01, CTL-02, CTL-03, CTL-04, CTL-05, CTL-06, CTL-07, CTL-08, CTL-09, CTL-10, CTL-11 | TODO | — |
| T-09 | Pruebas en el teléfono, revisión y evidencia | QR-01, QR-03, SR-02 | CTL-02 | TODO | — |

# Test cases · HU-MOV-07

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | UC-01 | Dada la billetera sin red con la copia, cuando vuelve la red, entonces muestra las pólizas con la fecha nueva y sin el aviso | TC-01, TC-12 |
| SC-02 | UC-01 | Dada la billetera que pide conexión, cuando vuelve la red, entonces muestra las pólizas | TC-01 |
| SC-03 | UC-01 | Dado el servidor caído con red, cuando falla, entonces se reintenta con espera creciente y sigue la copia | TC-03 |
| SC-04 | UC-01 | Dada la app en segundo plano, cuando vuelve la red, entonces al regresar está actualizada | TC-12 |
| SC-05 | AC-02 | Dada una copia, cuando llegan las mismas pólizas y una nueva, entonces queda la respuesta sin duplicados | TC-02 |
| SC-06 | AC-12 | Dada una respuesta con una póliza repetida, cuando se guarda, entonces queda una | TC-02 |
| SC-07 | UC-05 | Dado el teléfono bloqueado, cuando llegan las pólizas, entonces no se guardan, la fecha no cambia y al volver a la app se sincroniza | TC-01, TC-19, TC-12 |
| SC-08 | UC-02 | Dado el access token vencido, cuando vuelve la red, entonces se renueva una vez y se sincroniza | TC-04 |
| SC-09 | UC-02 | Dado el refresh token revocado, cuando se renueva, entonces se borra la copia y se pide el ingreso | TC-05 |
| SC-10 | UC-02 | Dadas la billetera y el trabajo a la vez con el token vencido, cuando sincronizan, entonces van en orden con una sola renovación | TC-04 |
| SC-11 | UC-02 | Dado el ingreso suspendido, cuando se renueva, entonces llega 423 y se reintenta sin borrar | TC-05, TC-17 |
| SC-12 | UC-02 | Dada una renovación sin respuesta, cuando la siguiente recibe 401, entonces se pide el ingreso sin borrar la copia | TC-05 |
| SC-13 | UC-02 | Dada una renovación exitosa, cuando la consulta vuelve a recibir 401, entonces se borra la copia | TC-05 |
| SC-14 | UC-03 | Dado un ingreso sin red, cuando vuelve la red antes de 15 minutos, entonces entra con la credencial y sincroniza | TC-06 |
| SC-15 | UC-03 | Dada la credencial revocada, cuando vuelve la red, entonces se borra la copia y se desvincula la huella | TC-05 |
| SC-16 | UC-03 | Dado un 429 o la cuenta bloqueada al entrar con la credencial, cuando falla, entonces se reintenta sin borrar | TC-05 |
| SC-17 | UC-03 | Dado un ingreso sin red, cuando pasan 15 minutos, entonces sin red todo sigue igual y con red se pide el ingreso sin borrar la copia | TC-06, TC-07 |
| SC-18 | UC-03 | Dado un ingreso sin red, cuando cambia o se desactiva la huella, entonces se suelta la credencial; la interfaz oculta "desactivar" sin conexión, así que se prueba en el ViewModel | TC-06 |
| SC-19 | UC-04 | Dada una sincronización pendiente o esperando su turno, cuando se cierra sesión, entonces no descarga ni guarda | TC-09 |
| SC-20 | UC-04 | Dada una descarga lenta, cuando se cierra sesión y la pantalla se cierra enseguida, entonces la copia queda borrada y el token revocado | TC-09 |
| SC-21 | UC-04 | Dada una renovación en curso, cuando se cierra sesión, entonces la sesión no revive y el par se revoca | TC-04 |
| SC-22 | UC-04 | Dada la sesión abierta, cuando la actividad termina sin cerrar sesión, entonces termina y se revoca solo esa sesión, sin borrar la copia | TC-08 |
| SC-23 | AC-08 | Dado que Android cerró la app o se reinició el teléfono, cuando vuelve la red, entonces no se llama al servidor | TC-08, TC-11 |
| SC-24 | AC-11 | Dado que la red va y viene muchas veces, cuando se pide la sincronización, entonces hay un solo trabajo | TC-03 |
| SC-25 | AC-03 | Dados muchos fallos seguidos, cuando se reintenta, entonces la espera se duplica hasta 5 horas | TC-03 |
| SC-26 | UC-01 | Dada otra cuenta abierta, cuando llega el resultado o el rechazo de la anterior, entonces no la afecta | TC-13 |
| SC-27 | AC-17 | Dada una sincronización en curso, cuando se rota el teléfono o se vuelve a la app, entonces la copia sigue visible | TC-19 |
| SC-28 | AC-16 | Dada una release sin URL `https`, cuando se compila, entonces falla | TC-18 |
| SC-29 | UC-01 | Dada una sincronización exitosa, cuando se pierde otra vez la red, entonces se siguen viendo esas pólizas sin el aviso | OUT OF SCOPE: el aviso indica que se ve la copia, no el estado de la red (HU-MOV-04) |
| SC-30 | UC-01 | Dada la billetera con red, cuando pasa el tiempo, entonces se sincroniza periódicamente | OUT OF SCOPE: la historia pide sincronizar al volver la red |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01 | — |
| Alternate flows | YES | SC-02, SC-03, SC-04, SC-14 | — |
| Invalid input and errors | YES | SC-06, SC-09, SC-11, SC-13, SC-15, SC-16, SC-28 | — |
| Limits and boundaries | YES | SC-17, SC-24, SC-25 | — |
| Related flows before and after | YES | SC-18, SC-19, SC-20, SC-21, SC-22, SC-23 | — |
| Several users, sessions, devices or roles | YES | SC-10, SC-26 | — |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-07, SC-08, SC-12, SC-27, SC-29, SC-30 | — |

## Test cases

### TC-01 · Sin red se programa y al sincronizar la billetera se actualiza

| Field | Value |
|---|---|
| Covers | AC-01, AC-04, AC-10 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | `WalletViewModel` con un `SessionPolicySync` real, API falsa, almacén en memoria y programador falso; con copia y sin copia |
| Procedure | 1. Abrir la billetera sin red. 2. Sincronizar desde fuera, como el trabajo, con pólizas nuevas. 3. Repetir el paso 2 con un almacén que no escribe |
| Expected result | 1: copia o pedido de conexión, y trabajo pedido. 2: pólizas nuevas, fecha nueva y sin aviso. 3: `Failed` y la fecha anterior |
| Tool or method | ./gradlew testDebugUnitTest (WalletViewModelTest, SessionPolicySyncTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"al sincronizar en segundo plano la billetera muestra las polizas sin el aviso"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"al sincronizar en segundo plano una billetera que pedia conexion muestra las polizas"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"si la copia no se pudo guardar la billetera conserva la fecha anterior"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"si la copia no se guarda responde Failed y no publica"; 242 de 242 unitarias, 2026-10-25 |
| Status | PASS |

### TC-02 · La copia se reemplaza sin duplicar

| Field | Value |
|---|---|
| Covers | AC-02, AC-12, SR-07 |
| Kind | SECURITY |
| Risk covered | THR-07 |
| Level | INTEGRATION |
| Preconditions | `EncryptedPolicyStore` con SQLite en Robolectric |
| Procedure | 1. Guardar dos pólizas. 2. Guardar una de ellas y una nueva. 3. Guardar una respuesta con una póliza repetida. 4. Leer tras cada paso |
| Expected result | Cada lectura tiene exactamente el último guardado, sin repetidos, y `save` devuelve `true` |
| Tool or method | ./gradlew testDebugUnitTest (EncryptedPolicyStoreFlowTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/policies/EncryptedPolicyStoreFlowTest.kt::"reemplaza sin duplicar, quita repetidas e informa si escribio"; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::una_poliza_repetida_se_guarda_una_vez_y_save_informa_que_escribio; Galaxy S22 Ultra con Android 16, 2026-10-25: 22 de 22 instrumentadas; 242 de 242 unitarias, 2026-10-25 |
| Status | PASS |

### TC-03 · Un solo trabajo, solo con red y con espera exponencial

| Field | Value |
|---|---|
| Covers | AC-03, AC-11, QR-02, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | WorkManager de pruebas en Robolectric con una aplicación de prueba que entrega un `PolicySync` doble |
| Procedure | 1. Pedir la sincronización cinco veces. 2. Revisar los trabajos, la restricción y la espera. 3. Cumplir las restricciones con `TestDriver` y responder `Failed`, y luego `Synced` |
| Expected result | Un solo trabajo, que no corre sin red, con `CONNECTED` y `EXPONENTIAL` de 10 s; con `Failed` queda para reintentar y con `Synced` termina |
| Tool or method | ./gradlew testDebugUnitTest (PolicySyncWorkerTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/policies/PolicySyncWorkerTest.kt::"un solo trabajo que espera la red y luego sincroniza"; movil/app/src/test/java/com/solventa/app/data/policies/PolicySyncWorkerTest.kt::"el trabajo exige red y reintenta con espera exponencial"; movil/app/src/test/java/com/solventa/app/data/policies/PolicySyncWorkerTest.kt::"una falla o una excepcion se reintentan y lo demas termina"; movil/app/src/test/java/com/solventa/app/data/policies/PolicySyncWorkerTest.kt::"cancelar quita el trabajo pendiente"; 242 de 242 unitarias, 2026-10-25 |
| Status | PASS |

### TC-04 · Una sincronización a la vez, una renovación y sin revivir la sesión

| Field | Value |
|---|---|
| Covers | AC-05, AC-11, SR-05 |
| Kind | SECURITY |
| Risk covered | THR-05, RISK-03 |
| Level | INTEGRATION |
| Preconditions | `SessionPolicySync` con una API que responde 401 al token viejo y una renovación que la prueba puede detener; `HttpAuthApi` contra MockWebServer |
| Procedure | 1. Sincronizar con el token viejo. 2. Lanzar dos sincronizaciones a la vez. 3. Con la renovación detenida, cerrar sesión como `confirmLogout` y dejar que responda. 4. Repetir el paso 3 con el ingreso con la credencial. 5. `refresh` con 200, 200 sin `refresh_token`, 401, 423, 500, 502, conexión rechazada y lectura vencida |
| Expected result | 1: una renovación y la consulta se repite. 2: van en orden con una renovación. 3 y 4: la sesión sigue cerrada, sin copia, y el par nuevo se revoca. 5: `Renewed`, `Unanswered`, `Rejected`, `Unavailable`, `Unanswered`, `Unanswered`, `Unavailable` y `Unanswered` |
| Tool or method | ./gradlew testDebugUnitTest (SessionPolicySyncTest, HttpAuthApiTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"un 401 renueva una vez y repite con el token nuevo"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"dos sincronizaciones a la vez van en orden con una sola renovacion"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"cerrar sesion durante la renovacion no revive la sesion y revoca el par nuevo"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"cerrar sesion durante el ingreso con la credencial revoca el par nuevo"; movil/app/src/test/java/com/solventa/app/data/auth/HttpAuthApiTest.kt::"renovar traduce cada respuesta del servidor"; movil/app/src/test/java/com/solventa/app/data/auth/HttpAuthApiTest.kt::"la renovacion va con longitud fija y no se reenvia si se corta la respuesta"; movil/app/src/test/java/com/solventa/app/data/session/SecureSessionRepositoryTest.kt::"la sesion solo cambia desde la instantanea vigente"; 242 de 242 unitarias, 2026-10-25 |
| Status | PASS |

### TC-05 · Solo un rechazo del servidor termina la sesión

| Field | Value |
|---|---|
| Covers | AC-06, AC-14, SR-06 |
| Kind | SECURITY |
| Risk covered | THR-06, RISK-02 |
| Level | UNIT |
| Preconditions | `SessionPolicySync` con una copia guardada y la sesión abierta |
| Procedure | 1. 401 en la renovación. 2. 401 en la consulta tras renovar. 3. Credencial rechazada, con la sesión igual y con la sesión cambiada. 4. Sin red, 500, 429 y 423. 5. `Unanswered` y después 401 en la renovación. 6. Repetir el paso 5 con una renovación exitosa en medio |
| Expected result | 1 y 2: copia borrada y `EXPIRED`. 3: además se desvincula la huella en los dos casos y el motivo es `DEVICE_REJECTED`. 4: `Failed` con la copia y la sesión intactas. 5: `RELOGIN` y la copia sigue. 6: `EXPIRED` |
| Tool or method | ./gradlew testDebugUnitTest (SessionPolicySyncTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"la renovacion rechazada borra la copia y termina la sesion"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"un segundo 401 despues de renovar borra la copia"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"la credencial rechazada desvincula la huella siempre"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"las fallas pasajeras se reintentan sin borrar ni cerrar"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"tras una renovacion sin respuesta el 401 siguiente conserva la copia"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"una renovacion exitosa limpia la marca y un rechazo posterior borra"; 242 de 242 unitarias, 2026-10-25 |
| Status | PASS |

### TC-06 · La credencial vive solo en memoria y a lo sumo 15 minutos

| Field | Value |
|---|---|
| Covers | AC-07, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-04, RISK-01 |
| Level | UNIT |
| Preconditions | `AuthViewModel` y `SessionPolicySync` con la huella vinculada, sin red y un reloj `elapsedRealtime` de prueba |
| Procedure | 1. Entrar sin red y revisar la sesión y el almacén de sesiones. 2. Sincronizar con el servidor de vuelta. 3. Entrar otra vez y cerrar sesión, o desactivar la huella desde el ViewModel. 4. Entrar, avanzar 15 minutos y sincronizar sin red. 5. Entrar, invalidar la llave y sincronizar. 6. Entrar con el Keystore fallando de forma pasajera y sincronizar desde la billetera |
| Expected result | 1: credencial sin tokens; el almacén no cambió. 2: entra sin pedir la huella y queda con tokens, sin credencial. 3: sin credencial. 4: sin credencial y `NoSession` sin llamar al servidor. 5: no se llama a `auth/biometric` y se pierden las vinculaciones. 6: `Failed`, sin cierre de la app y con las vinculaciones intactas |
| Tool or method | ./gradlew testDebugUnitTest (AuthViewModelTest, SessionPolicySyncTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"el ingreso sin red guarda la credencial solo en memoria y la suelta a los 15 minutos"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"cerrar sesion o desactivar la huella sueltan la credencial"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"con la credencial vigente entra sin pedir la huella y la suelta"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"la credencial vencida no se usa y con red pide el ingreso sin borrar"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"una llave invalidada o no disponible no usa la credencial"; movil/app/src/test/java/com/solventa/app/data/session/SecureSessionRepositoryTest.kt::"la credencial vence a los 15 minutos o si el reloj retrocede"; movil/app/src/test/java/com/solventa/app/data/session/SecureSessionRepositoryTest.kt::"el estado de la llave no lanza y una falla pasajera no borra las cuentas"; 242 de 242 unitarias, 2026-10-25 |
| Status | PASS |

### TC-07 · Vencida la credencial, al volver la red se pide el ingreso sin borrar

| Field | Value |
|---|---|
| Covers | AC-07 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Un ingreso sin red con la copia, 16 minutos después |
| Procedure | 1. Sincronizar desde la billetera. 2. Sincronizar como el trabajo, con `online`. 3. Revisar la sesión, la copia, la huella y el aviso |
| Expected result | 1: `NoSession` y la copia sigue a la vista. 2: `RELOGIN`. 3: sin sesión, con la copia y la huella, y el aviso "Ingrese de nuevo para actualizar sus pólizas" |
| Tool or method | ./gradlew testDebugUnitTest (SessionPolicySyncTest, AuthViewModelTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"la credencial vencida no se usa y con red pide el ingreso sin borrar"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"una sesion terminada por la sincronizacion pide ingresar con el aviso del motivo"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthScreensTest.kt::"cada estado de la huella muestra su mensaje y bloquea el sensor cuando corresponde"; 242 de 242 unitarias, 2026-10-25 |
| Status | PASS |

### TC-08 · Sin sesión no se llama al servidor y la actividad cierra solo la suya

| Field | Value |
|---|---|
| Covers | AC-08, AC-15, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | UNIT |
| Preconditions | `SessionPolicySync` sin sesión; `AuthViewModel` con la sesión abierta |
| Procedure | 1. Sincronizar sin sesión. 2. Limpiar el `AuthViewModel` como al terminar la actividad. 3. Repetir con otra sesión ya abierta por una pantalla nueva |
| Expected result | 1: `NoSession`, sin llamadas ni escrituras. 2: sin sesión, con la copia, sincronización cancelada y `logout` con su refresh token. 3: la sesión nueva sigue y no se revoca |
| Tool or method | ./gradlew testDebugUnitTest (SessionPolicySyncTest, AuthViewModelTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"sin sesion no llama al servidor ni escribe"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"al terminar la actividad termina y revoca solo su sesion"; movil/app/src/test/java/com/solventa/app/data/session/SecureSessionRepositoryTest.kt::"endSession termina solo su sesion y devuelve sus tokens vigentes"; en el teléfono, al terminar la actividad la sesión terminó y la copia cifrada siguió en databases/, 2026-10-25; 242 de 242 unitarias, 2026-10-25 |
| Status | PASS |

### TC-09 · Cerrar sesión durante la descarga del trabajo no deja copia

| Field | Value |
|---|---|
| Covers | AC-09, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | UNIT |
| Preconditions | `AuthViewModel` y `SessionPolicySync` reales con un `appScope` de prueba; una sincronización del trabajo esperando un servidor que no atiende la cancelación y otra esperando su turno |
| Procedure | 1. Cerrar sesión y limpiar el `AuthViewModel` enseguida. 2. Revisar la copia antes de que responda el servidor. 3. Completar la respuesta y revisar el almacén, el programador y las llamadas |
| Expected result | 2: la copia ya está borrada. 3: sin copia, ninguna guardó, la segunda no llamó a la API, el trabajo se canceló y se llamó a `logout` |
| Tool or method | ./gradlew testDebugUnitTest (AuthViewModelTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"cerrar sesion durante la descarga del trabajo no deja copia"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"el borrado y la revocacion terminan aunque la pantalla se cierre"; movil/app/src/test/java/com/solventa/app/MainActivityTest.kt::"cerrar sesion con la descarga pendiente no deja copia de la cuenta"; 242 de 242 unitarias, 2026-10-25 |
| Status | PASS |

### TC-10 · El trabajo no lleva datos de la cuenta ni deja escapar excepciones

| Field | Value |
|---|---|
| Covers | SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | UNIT |
| Preconditions | WorkManager de pruebas en Robolectric con una sesión abierta |
| Procedure | 1. Pedir la sincronización y revisar los datos de entrada y las etiquetas. 2. Ejecutar el trabajo con un `sync()` que lanza. 3. Revisar el `toString` de una `OpenSession` |
| Expected result | Sin datos de entrada ni etiquetas; reintento sin lanzar; `toString` sin correo ni tokens |
| Tool or method | ./gradlew testDebugUnitTest (PolicySyncWorkerTest, SecureSessionRepositoryTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/policies/PolicySyncWorkerTest.kt::"el trabajo no lleva datos de entrada ni etiquetas propias"; movil/app/src/test/java/com/solventa/app/data/policies/PolicySyncWorkerTest.kt::"una falla o una excepcion se reintentan y lo demas termina"; movil/app/src/test/java/com/solventa/app/data/session/SecureSessionRepositoryTest.kt::"la sesion abierta no expone el correo ni los secretos al imprimirse"; 242 de 242 unitarias, 2026-10-25 |
| Status | PASS |

### TC-11 · El manifiesto final no pide ni exporta de más

| Field | Value |
|---|---|
| Covers | SR-08 |
| Kind | SECURITY |
| Risk covered | THR-08 |
| Level | STATIC |
| Preconditions | Manifiesto combinado de la app en Robolectric |
| Procedure | 1. Leer los permisos. 2. Leer los servicios y receptores exportados |
| Expected result | `ACCESS_NETWORK_STATE` y `WAKE_LOCK` sí; `RECEIVE_BOOT_COMPLETED` y `FOREGROUND_SERVICE` no. Exportados de WorkManager: solo `SystemJobService` con `BIND_JOB_SERVICE` y `DiagnosticsReceiver` con `DUMP` |
| Tool or method | ./gradlew testDebugUnitTest (PolicySyncWorkerTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/policies/PolicySyncWorkerTest.kt::"el manifiesto final no pide ni exporta de mas"; en el teléfono, dumpsys package pide solo INTERNET, ACCESS_NETWORK_STATE, USE_FINGERPRINT, USE_BIOMETRIC y WAKE_LOCK, 2026-10-25; 242 de 242 unitarias, 2026-10-25 |
| Status | PASS |

### TC-12 · En el teléfono, la billetera se actualiza al volver la red

| Field | Value |
|---|---|
| Covers | QR-01, AC-01, AC-13 |
| Kind | QUALITY |
| Risk covered | — |
| Level | E2E |
| Preconditions | App de prueba, backend local por `adb reverse` y una cuenta de prueba. Con `adb reverse` el modo avión no corta el backend, así que la caída se simula con el modo avión y el backend detenido |
| Procedure | 1. Entrar con el backend arriba. 2. Activar el modo avión, detener el backend y rotar el teléfono para ver la copia sin conexión. 3. Levantar el backend, quitar el modo avión y medir hasta ver la fecha nueva. 4. Repetir 5 veces: una en segundo plano, una tras 16 minutos sin red y una con el teléfono bloqueado, desbloqueando 2 minutos después |
| Expected result | 5 de 5 dentro de 60 s en primer plano; actualizada al volver del segundo plano; tras 16 minutos renueva sin cerrar la sesión; bloqueado, dentro de 60 s después de volver a la app |
| Tool or method | Cronómetro y `adb shell dumpsys jobscheduler` |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | Galaxy S22 Ultra con Android 16 y backend local por adb reverse, 2026-10-25: en primer plano la billetera se actualizó sola a los 6,4 s, 6,4 s y 6,5 s de quitar el modo avión; tras 16 minutos sin red renovó el token (401, POST auth/refresh 200 y consulta 200) y se actualizó a los 8,0 s sin cerrar la sesión; con el trabajo esperando su reintento, al volver a la app se actualizó a los 2,3 s (5 de 5 en primer plano dentro de 60 s). En segundo plano el trabajo consultó el backend a los 2 s de volver la red y al regresar ya estaba actualizada. La variante con el teléfono bloqueado no se corrió porque no se puede desbloquear por adb; la cubren WalletViewModelTest y SessionPolicySyncTest |
| Status | PASS |

### TC-13 · La sincronización solo afecta a su cuenta y su sesión

| Field | Value |
|---|---|
| Covers | SR-09 |
| Kind | SECURITY |
| Risk covered | THR-09 |
| Level | UNIT |
| Preconditions | `SessionPolicySync` y `WalletViewModel` con dos cuentas de prueba |
| Procedure | 1. Sincronizar la cuenta A y, mientras espera, cambiar la sesión a B. 2. Completar con pólizas. 3. Repetir con la renovación de A respondiendo 401 |
| Expected result | La copia y la billetera de B no reciben la respuesta, y el rechazo de A no termina la sesión de B ni borra su copia |
| Tool or method | ./gradlew testDebugUnitTest (SessionPolicySyncTest, WalletViewModelTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"la sincronizacion solo afecta a su cuenta y su sesion"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"el resultado de otra cuenta no cambia la billetera"; 242 de 242 unitarias, 2026-10-25 |
| Status | PASS |

### TC-14 · En el teléfono, WorkManager y logcat sin datos de la cuenta

| Field | Value |
|---|---|
| Covers | SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | E2E |
| Preconditions | App de prueba con una sincronización pendiente, preparada como en TC-12 |
| Procedure | 1. Copiar `no_backup/androidx.work.workdb` con sus `-wal` y `-shm` usando `run-as`. 2. Buscar el correo, el titular, los números de póliza y `token`. 3. Guardar logcat de la app en una sincronización exitosa y en otra con el backend detenido, y buscar lo mismo |
| Expected result | Ninguna coincidencia |
| Tool or method | `adb shell run-as`, `sqlite3`, `strings` y `adb logcat` |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | Galaxy S22 Ultra, 2026-10-25: con el trabajo pendiente, androidx.work.workdb y sus archivos -wal y -shm no tienen el correo, el titular, los números de póliza ni la palabra token (0 coincidencias); el trabajo policy_sync tiene la entrada vacía, red CONNECTED y espera EXPONENTIAL de 10 000 ms. Logcat de la app durante una sincronización fallida y una exitosa: 0 coincidencias |
| Status | PASS |

### TC-15 · Revisión de registros, secretos y dependencias

| Field | Value |
|---|---|
| Covers | SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02, THR-12 |
| Level | STATIC |
| Preconditions | Diff de la rama contra su base |
| Procedure | 1. Buscar `Log.`, `println` y `printStackTrace` en lo cambiado, y revisar que `refresh` usa `setFixedLengthStreamingMode`. 2. `python sdd/tools/sdd.py scan secrets --diff <base>`. 3. OSV.dev con DEP-01 a DEP-04. 4. `python sdd/tools/sdd.py scan deps --base <base> --require-evaluation` |
| Expected result | Sin registros nuevos, secretos ni vulnerabilidades conocidas, y dependencias evaluadas |
| Tool or method | grep, revisión de código, `sdd.py scan secrets`, `sdd.py scan deps` y OSV.dev |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | revisión del 2026-10-25: sin Log., println ni printStackTrace en el diff; refresh usa setFixedLengthStreamingMode; sdd.py scan secrets --diff sin hallazgos; OSV.dev sin vulnerabilidades conocidas para DEP-01 a DEP-04; sdd.py scan deps con las 2 dependencias nuevas evaluadas |
| Status | PASS |

### TC-16 · La actualización con TalkBack

| Field | Value |
|---|---|
| Covers | QR-03 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | TalkBack y fuente al máximo; billetera sin red con la copia, preparada como en TC-12 |
| Procedure | 1. Poner el foco en una póliza. 2. Levantar el backend y quitar el modo avión. 3. Revisar el foco y recorrer hasta la fecha |
| Expected result | El foco no salta; la fecha nueva se lee completa y nada se corta |
| Tool or method | TalkBack en el teléfono |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | Parcial, Galaxy S22 Ultra 2026-10-25: con TalkBack activo y la fuente en 2.0, la billetera se actualizó sola y la fecha nueva se lee completa, en dos líneas y sin cortes. Falta confirmar que el foco de TalkBack no salte al actualizarse: TalkBack no se puede manejar por adb y su aviso de permisos de llamadas tapaba la app; queda para una prueba manual de David |
| Status | PENDING |

### TC-17 · Con el ingreso suspendido la renovación responde 423 sin rotar

| Field | Value |
|---|---|
| Covers | AC-14, SR-10 |
| Kind | SECURITY |
| Risk covered | THR-10 |
| Level | INTEGRATION |
| Preconditions | Backend con PostgreSQL de pruebas y una cuenta con un refresh token válido |
| Procedure | 1. Suspender el ingreso con 5 contraseñas malas. 2. Renovar y revisar la auditoría. 3. Vencer la suspensión con el reloj de prueba y renovar con el mismo token. 4. Repetir el paso 2 con un token revocado |
| Expected result | 2: 423 con `Retry-After`, sin intento fallido nuevo. 3: 200 con un par nuevo. 4: 401 |
| Tool or method | `poetry run pytest` (test_insured_logout.py, test_insured_auth_errors.py) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/unit/test_insured_logout.py::test_refresh_con_ingreso_suspendido_responde_bloqueo_sin_rotar; backend/tests/unit/test_insured_logout.py::test_refresh_revocado_con_ingreso_suspendido_sigue_rechazado; backend/tests/integration/test_insured_auth_errors.py::test_ingreso_suspendido_responde_423_con_tiempo_restante; 204 de 204 con PostgreSQL; en vivo contra el backend local, 423 con Retry-After 879 y 401 con un token inválido, 2026-10-25 |
| Status | PASS |

### TC-18 · La release solo usa HTTPS

| Field | Value |
|---|---|
| Covers | AC-16, SR-11 |
| Kind | SECURITY |
| Risk covered | THR-11 |
| Level | STATIC |
| Preconditions | Código de la rama |
| Procedure | 1. Leer las configuraciones de red de `main` y de debug desde una prueba unitaria. 2. `./gradlew assembleRelease` sin `-PapiBaseUrl`. 3. Con `-PapiBaseUrl=https://ejemplo.invalid/api/v1/` |
| Expected result | 1: `main` sin HTTP sin TLS; debug solo hacia `10.0.2.2` y `localhost`. 2: falla pidiendo `https://`. 3: compila |
| Tool or method | ./gradlew testDebugUnitTest (NetworkSecurityConfigTest) y `./gradlew assembleRelease` |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/data/NetworkSecurityConfigTest.kt::"la configuracion de release no permite HTTP sin TLS a ningun host"; movil/app/src/test/java/com/solventa/app/data/NetworkSecurityConfigTest.kt::"la de debug solo permite HTTP sin TLS hacia el backend local"; assembleRelease sin -PapiBaseUrl falla con "La release necesita -PapiBaseUrl=https://…"; con https compila y el APK de release solo trae base-config cleartextTrafficPermitted=false (aapt2), 2026-10-25; 242 de 242 unitarias, 2026-10-25 |
| Status | PASS |

### TC-19 · Al volver a la app se sincroniza sin ocultar la copia

| Field | Value |
|---|---|
| Covers | AC-13, AC-17, AC-03 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | `WalletViewModel` con la copia sin conexión y una sincronización del trabajo ocupando el turno (`PendingPoliciesApi`) |
| Procedure | 1. Llamar a `open` con la misma cuenta, como al rotar, y a `refreshClock()`. 2. Revisar el estado antes de la respuesta. 3. Completar y llamar a `refreshClock()` dos veces en menos de 30 s sin red. 4. Llamarlo con la billetera ya sincronizada |
| Expected result | 2: sigue la copia, sin carga. 3: sincroniza una sola vez. 4: no llama al servidor |
| Tool or method | ./gradlew testDebugUnitTest (WalletViewModelTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"al rotar o volver a la app sigue la copia mientras otra sincronizacion ocupa el turno"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"al volver a la app sin conexion sincroniza a lo sumo cada 30 s y con conexion no llama"; 242 de 242 unitarias, 2026-10-25 |
| Status | PASS |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-08 | — |
| User without permissions | NO | — | Un solo rol; el backend solo cambia el código del bloqueo |
| Access to another user's resources | YES | TC-13 | — |
| Cross-tenant access | NO | — | Sin inquilinos |
| Malicious input | YES | TC-02, TC-04 | — |
| Expired tokens | YES | TC-04, TC-05 | — |
| Revoked sessions | YES | TC-05, TC-09 | — |
| Tampered data | NO | — | Sin datos firmados ni cifrados nuevos; la copia la cubre HU-MOV-05 |
| Wrong keys | YES | TC-01, TC-06 | — |
| External service failures | YES | TC-03, TC-05, TC-17 | — |
| Race conditions | YES | TC-04, TC-09, TC-13 | — |
| Information exposure | YES | TC-10, TC-14, TC-15 | — |
| Secrets in code | YES | TC-15 | — |
| Insecure configuration | YES | TC-11, TC-18 | — |
| Vulnerable dependencies | YES | TC-15 | — |

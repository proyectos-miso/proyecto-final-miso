# Test cases · HU-MOV-05

Datos de prueba ficticios, con correos de `example.com`. Las pruebas instrumentadas corren en un teléfono o emulador con el almacén cifrado real.

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | UC-01 | Dada la llave borrada del Keystore, cuando se abre la billetera sin red, entonces se borra la copia y se ve el aviso | TC-02 |
| SC-02 | UC-01 | Dada una copia descartada, cuando se abre con red, entonces queda una copia nueva y desaparece el aviso | TC-02 |
| SC-03 | AC-08 | Dada una copia descartada, cuando se lee dos veces sin red, entonces las dos responden descartada, y tras un guardado ya no | TC-02, TC-07 |
| SC-04 | UC-02 | Dada una base alterada, cuando se abre con red, entonces se reemplaza sin cerrar la app | TC-03 |
| SC-05 | UC-02 | Dada una base alterada, cuando se abre sin red, entonces se descarta y se ve el aviso | TC-03 |
| SC-06 | AC-08 | Dada una cuenta nueva cuya base no se puede escribir, cuando se guarda, entonces no queda marca y la lectura responde sin copia | TC-03 |
| SC-07 | AC-06 | Dada una frase con Base64, IV o texto cifrado inválidos o alterada, cuando se usa, entonces cuenta como llave perdida | TC-04 |
| SC-08 | AC-06 | Dado un Keystore que falla al cargar, buscar o borrar la llave, cuando se lee, se guarda o se cierra sesión, entonces la app no se cierra | TC-04 |
| SC-09 | UC-04 | Dada una falla pasajera o el teléfono bloqueado, cuando se lee, entonces responde no disponible sin borrar, y al reintentar se ve la copia | TC-05 |
| SC-10 | AC-08 | Dada una cuenta nueva con el teléfono bloqueado, cuando se guarda, entonces no se crea nada | TC-05 |
| SC-11 | AC-07 | Dada una llave inválida con el teléfono desbloqueado, cuando falla, entonces cuenta como inválida y no como bloqueo | TC-05 |
| SC-12 | UC-04 | Dado el teléfono bloqueado de verdad, cuando se lee y se guarda, entonces nada se borra y al desbloquear se ve la copia | TC-06 |
| SC-13 | UC-03 | Dada una transferencia o una copia de seguridad, cuando se copian los datos, entonces no viaja ningún archivo de la app | TC-08, TC-09 |
| SC-14 | UC-01 | Dada una cuenta sin copia y sin red, cuando se abre la billetera, entonces se ve el aviso de primera descarga | TC-07 |
| SC-15 | AC-08 | Dada una copia descartada y el servidor caído, cuando se abre la billetera, entonces se ve el aviso de copia descartada | TC-07 |
| SC-16 | AC-10 | Dadas dos cuentas, cuando se reemplaza la base o se borra la llave de una, entonces la otra sigue intacta | TC-10 |
| SC-17 | UC-05 | Dada una lectura o un guardado en curso, cuando se rota el teléfono, entonces nada se borra y la base abre con su frase | TC-11 |
| SC-18 | UC-06 | Dada una descarga o un guardado en curso, cuando se confirma el cierre de sesión, entonces no queda copia | TC-12 |
| SC-19 | UC-01 | Dada una copia descartada, cuando se cierra sesión, entonces el borrado no falla | TC-02 |
| SC-20 | AC-08 | Dados TalkBack y la fuente al máximo, cuando aparece un aviso nuevo, entonces se anuncia y no se corta | TC-13 |
| SC-21 | UC-01 | Dada una copia descartada, cuando vuelve la red con la app abierta, entonces se descarga sola | OUT OF SCOPE: HU-MOV-07; aquí se descarga en la siguiente apertura con red |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-02 | — |
| Alternate flows | YES | SC-04, SC-05, SC-14, SC-15 | — |
| Invalid input and errors | YES | SC-06, SC-07, SC-08, SC-09 | — |
| Limits and boundaries | YES | SC-10, SC-11, SC-20 | — |
| Related flows before and after | YES | SC-18, SC-19, SC-21 | — |
| Several users, sessions, devices or roles | YES | SC-13, SC-16 | — |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-03, SC-12, SC-17 | — |

## Test cases

### TC-01 · Cifrado entregado por HU-MOV-04

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-03, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Copia guardada con un número de póliza conocido |
| Procedure | 1. Leer los bytes del archivo. 2. Abrirlo con otra frase. 3. Revisar la llave de la cuenta en `AndroidKeyStore`. 4. En Robolectric, revisar las preferencias tras crear la frase de dos cuentas |
| Expected result | 1: sin cabecera de SQLite ni el número de póliza. 2: no abre. 3: existe y no se puede exportar. 4: frases de 32 bytes distintas, guardadas solo envueltas |
| Tool or method | ./gradlew connectedDebugAndroidTest (EncryptedPolicyStoreTest); ./gradlew testDebugUnitTest (PassphraseVaultTest); los pasos 1 y 4 ya existen |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

### TC-02 · Llave perdida y marca de descarte

| Field | Value |
|---|---|
| Covers | AC-04, AC-08, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Copia guardada; WalletViewModel con dobles |
| Procedure | 1. Borrar la llave y leer dos veces. 2. Guardar y leer. 3. Borrar el archivo y leer. 4. Con otra copia, borrar solo la frase y leer. 5. Borrar la cuenta y leer. 6. En el ViewModel, abrir sin red con `Discarded` y luego con red |
| Expected result | 1: `Discarded` las dos veces y se borran archivo y frase. 2: la copia nueva. 3: `Missing`, porque el guardado borró la marca. 4: `Discarded`. 5: no falla y responde `Missing`. 6: primero `copyDiscarded`; luego las pólizas del servidor guardadas y sin `copyDiscarded` |
| Tool or method | ./gradlew connectedDebugAndroidTest (EncryptedPolicyStoreTest); ./gradlew testDebugUnitTest (WalletViewModelTest) |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

### TC-03 · Base dañada

| Field | Value |
|---|---|
| Covers | AC-05, AC-08, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02, RISK-01 |
| Level | INTEGRATION |
| Preconditions | Almacén cifrado real |
| Procedure | 1. Alterar bytes de la base y guardar pólizas nuevas. 2. Leer. 3. Alterarla de nuevo y leer. 4. En una cuenta nueva, quitar el permiso de escritura del directorio de bases, guardar y leer; restaurarlo en `@After`. 5. Cambiar con la frase de la cuenta el hash de identidad de `room_master_table` y leer |
| Expected result | 1: no lanza. 2: las pólizas nuevas. 3: `Discarded` y sin archivo. 4: no lanza, sin marca ni frase, y `Missing`. 5: `Discarded` |
| Tool or method | ./gradlew connectedDebugAndroidTest (EncryptedPolicyStoreTest; el paso 3 ya existe) |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

### TC-04 · Frase dañada, fallas del Keystore y frase en cero

| Field | Value |
|---|---|
| Covers | AC-06, SR-02, SR-05 |
| Kind | SECURITY |
| Risk covered | THR-02, THR-06 |
| Level | INTEGRATION |
| Preconditions | Robolectric con llaves de prueba y `KeyStore` inyectado; teléfono para el Keystore real |
| Procedure | 1. Leer con `PassphraseVault` frases con Base64 inválido, IV de 1 byte y texto cifrado de 8 bytes. 2. `KeystoreWalletKeys` sin el alias. 3. `KeyStore` que lanza al cargar, buscar y borrar. 4. Llaves que guardan el arreglo recibido en `wrap` y fallan; crear una frase. 5. En el teléfono, alterar el texto cifrado y desenvolver. 6. En el teléfono, guardar un Base64 inválido en `solventa_wallet`, leer, guardar y leer |
| Expected result | 1 y 2: `Invalid` sin lanzar. 3: `Unavailable`, `wrap` da `null` y `delete` no lanza. 4: `create` da `null` y el arreglo quedó en ceros. 5: `Invalid`. 6: `Discarded` y luego una copia nueva legible |
| Tool or method | ./gradlew testDebugUnitTest (PassphraseVaultTest, KeystoreWalletKeysTest); ./gradlew connectedDebugAndroidTest (EncryptedPolicyStoreTest) |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

### TC-05 · Copia no disponible

| Field | Value |
|---|---|
| Covers | AC-06, AC-07, AC-08, AC-13, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-05, THR-08, RISK-03, RISK-04 |
| Level | INTEGRATION |
| Preconditions | Robolectric con `KeyStore` inyectado; teléfono con llaves de prueba que responden `Unavailable`; WalletViewModel con dobles |
| Procedure | 1. Bloqueo verdadero antes de operar. 2. Falso antes y verdadero tras un fallo. 3. Falso antes y después, con alias inexistente. 4. Descifrado que lanza `ProviderException` y `KeyStoreException`. 5. En el teléfono, leer y guardar una copia existente con llaves `Unavailable`. 6. Igual, en una cuenta nueva. 7. Con la copia guardada, quitar el permiso de escritura del directorio de bases, guardar, restaurar y leer. 8. En el ViewModel, abrir sin red con `Unavailable`, cambiar a la copia y llamar a `retry`; repetir con `refreshClock` |
| Expected result | 1, 2 y 4: `Unavailable`. 3: `Invalid`. 5: `Unavailable`, sin guardar ni borrar. 6: no crea archivo, frase ni marca. 7: no lanza, sin marca, y la lectura da la copia anterior. 8: `copyUnavailable` sin el aviso de descarte, y la copia tras `retry` y tras `refreshClock` |
| Tool or method | ./gradlew testDebugUnitTest (KeystoreWalletKeysTest, WalletViewModelTest); ./gradlew connectedDebugAndroidTest (EncryptedPolicyStoreTest) |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

### TC-06 · Teléfono bloqueado de verdad

| Field | Value |
|---|---|
| Covers | AC-07, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-05, RISK-03 |
| Level | INTEGRATION |
| Preconditions | Teléfono con PIN y bloqueo inmediato; copia guardada; `KeystoreWalletKeys` que responde desbloqueado antes y consulta el `KeyguardManager` real después |
| Procedure | 1. Apagar la pantalla desde la prueba (`input keyevent KEYCODE_SLEEP`) y esperar el bloqueo. 2. Ejecutar `unwrap`, `wrap`, `read` y `save`. 3. Desbloquear y leer |
| Expected result | 2: el Keystore real falla, `unwrap` y `read` dan `Unavailable` (no `Invalid`), `wrap` da `null` y nada se guarda ni se borra. 3: la copia |
| Tool or method | ./gradlew connectedDebugAndroidTest (EncryptedPolicyStoreTest), con la persona desbloqueando el teléfono |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

### TC-07 · Avisos de la billetera

| Field | Value |
|---|---|
| Covers | AC-08, AC-13 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | WalletViewModel con dobles; WalletScreen con datos de prueba; PassphraseVault en Robolectric |
| Procedure | 1. Abrir sin red y con el servidor caído con `Discarded`. 2. Abrir sin red sin copia. 3. Mostrar la pantalla con `copyDiscarded` y con `copyUnavailable`, y pulsar Reintentar. 4. Marcar el descarte, consultarlo dos veces y borrarlo |
| Expected result | 1: `copyDiscarded`. 2: primera descarga, sin `copyDiscarded`. 3: los dos avisos en lugar del de primera descarga, y el botón llama a `retry`. 4: la marca sigue hasta borrarla |
| Tool or method | ./gradlew testDebugUnitTest (WalletViewModelTest, WalletScreenTest, PassphraseVaultTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | PENDING |
| Status | PENDING |

### TC-08 · Reglas de extracción

| Field | Value |
|---|---|
| Covers | AC-09, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03, RISK-02 |
| Level | STATIC |
| Preconditions | Manifiesto y `res/xml/data_extraction_rules.xml` |
| Procedure | 1. Leer el manifiesto. 2. Buscar cada dominio en cada sección de las reglas |
| Expected result | 1: `android:dataExtractionRules` y `android:allowBackup="false"`. 2: las tres secciones excluyen con `path="."` los nueve dominios y no incluyen nada |
| Tool or method | ./gradlew testDebugUnitTest (DataExtractionRulesTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | PENDING |
| Status | PENDING |

### TC-09 · Transferencia simulada

| Field | Value |
|---|---|
| Covers | AC-09, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | Teléfono con `com.android.localtransport/.LocalTransport`; compilación local solo de prueba con `allowBackup="true"`, porque `bmgr` hace una copia y no una migración, y con `false` la niega antes de aplicar las reglas; nunca se sube (TC-08 la rechazaría en el CI); archivos ficticios en la raíz, `files/`, `databases/`, `shared_prefs/` y el almacenamiento protegido |
| Procedure | 1. Instalar la compilación de prueba sin las reglas y abrir la app una vez. 2. Anotar `bmgr enabled` y el transporte; activar el servicio, elegir `LocalTransport`, `settings put secure backup_local_transport_parameters is_device_transfer=true` y `setprop log.tag.BackupXmlParserLogging VERBOSE`. 3. `bmgr backupnow com.solventa.app` y guardar el logcat. 4. Repetir con las reglas. 5. `./gradlew lintRelease`. 6. Restaurar lo anotado, `bmgr wipe` de la copia de prueba y reinstalar la compilación normal |
| Expected result | 3: las líneas `file_backup_helper: Name: apps/com.solventa.app/...` listan los archivos de prueba. 4: ninguna línea de archivos de la app. 5: sin la advertencia `DataExtractionRules` |
| Tool or method | `adb shell bmgr`, logcat y `./gradlew lintRelease` |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | Paso 3 hecho el 2026-10-25 en un Galaxy S22 Ultra con Android 16: viajaron los cinco archivos de prueba de `root`, `file`, `database`, `sharedpref` y `device_file`. Pasos 4 y 5 pendientes |
| Status | PENDING |

### TC-10 · Una cuenta no afecta a otra

| Field | Value |
|---|---|
| Covers | AC-10, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | INTEGRATION |
| Preconditions | Dos cuentas con copia |
| Procedure | 1. Copiar la base de A sobre la de B y leer las dos. 2. Con copias nuevas, borrar la llave de B y leer las dos |
| Expected result | B da `Discarded` y A su copia intacta, en los dos pasos |
| Tool or method | ./gradlew connectedDebugAndroidTest (EncryptedPolicyStoreTest) |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

### TC-11 · Cancelación

| Field | Value |
|---|---|
| Covers | AC-11, SR-06 |
| Kind | SECURITY |
| Risk covered | THR-07, THR-09 |
| Level | INTEGRATION |
| Preconditions | Reloj real, sin `runTest` para la espera |
| Procedure | 1. Medir cuánto tardan `read` y `save`. 2. Cancelar `read` sobre una copia existente tras esperas repartidas en esa duración, 50 veces. 3. Igual con `save` sobre una copia existente y con el primer `save` de una cuenta nueva. 4. Tras cada cancelación, abrir la base con la frase guardada y con 32 ceros |
| Expected result | Nada se borra; la base abre con su frase y no con ceros; la cancelación llega a quien la pidió |
| Tool or method | ./gradlew connectedDebugAndroidTest (EncryptedPolicyStoreTest) |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

### TC-12 · Cierre de sesión durante la descarga

| Field | Value |
|---|---|
| Covers | AC-12, SR-07 |
| Kind | SECURITY |
| Risk covered | THR-10, RISK-05 |
| Level | INTEGRATION |
| Preconditions | API de prueba que responde cuando se le indica; almacén que registra los guardados; almacén cifrado real |
| Procedure | 1. En el ViewModel, abrir con red, llamar a `close()` y dejar responder la API. 2. En `SolventaMobileRoot`, confirmar el cierre de sesión con la API pendiente y dejarla responder. 3. En el teléfono, lanzar un `save` y, mientras escribe, un `delete`; leer. 4. Repetir cancelando el `delete` mientras espera su turno |
| Expected result | 1 y 2: no hay `save` y la billetera vuelve al estado inicial. 3 y 4: el `delete` termina después del `save` y la lectura da `Missing` |
| Tool or method | ./gradlew testDebugUnitTest (WalletViewModelTest, MainActivityTest); ./gradlew connectedDebugAndroidTest (EncryptedPolicyStoreTest) |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

### TC-13 · Accesibilidad

| Field | Value |
|---|---|
| Covers | QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | TalkBack y fuente al máximo; copia descartada sin red; para la copia no disponible, una prueba de `androidTest` que deja abierta `WalletScreen` con `copyUnavailable` (nunca en release) |
| Procedure | 1. Abrir la billetera con la copia descartada. 2. Abrir la pantalla con la copia no disponible y activar Reintentar con TalkBack |
| Expected result | Se anuncian al aparecer y se leen completos; Reintentar se anuncia como botón y se activa; nada se corta |
| Tool or method | TalkBack; los avisos usan `WarningBanner` con región en vivo (WalletScreenTest) |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

### TC-14 · Revisión de registros, frase en cero y turnos

| Field | Value |
|---|---|
| Covers | SR-05 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | STATIC |
| Preconditions | Diff de la rama contra su base |
| Procedure | 1. Buscar `Log.`, `println` y `printStackTrace` en `data/policies`. 2. Revisar que se apagan los registros de SQLCipher. 3. Revisar que `withDatabase` usa la base sin cancelación y que su `finally` pone la frase en cero, incluido el primer `ensureActive()`. 4. Revisar que `read` y `save` comprueban `ensureActive()` al tomar el turno, antes de cualquier efecto; esa ventana es demasiado corta para una prueba que pueda fallar. 5. `python sdd/tools/sdd.py scan secrets` |
| Expected result | Sin registros propios, SQLCipher callado, frase siempre en cero, turnos correctos y sin secretos |
| Tool or method | grep, revisión de código y `python sdd/tools/sdd.py scan secrets` |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | NO | — | Sin pantallas ni endpoints nuevos sin sesión |
| User without permissions | NO | — | Sin permisos nuevos |
| Access to another user's resources | YES | TC-10 | — |
| Cross-tenant access | NO | — | Sin inquilinos |
| Malicious input | YES | TC-04 | — |
| Expired tokens | NO | — | No cambia la sesión |
| Revoked sessions | YES | TC-12 | — |
| Tampered data | YES | TC-03, TC-04, TC-10 | — |
| Wrong keys | YES | TC-02, TC-04, TC-05, TC-06 | — |
| External service failures | YES | TC-05, TC-07 | — |
| Race conditions | YES | TC-11, TC-12 | — |
| Information exposure | YES | TC-14 | — |
| Secrets in code | YES | TC-14 | — |
| Insecure configuration | YES | TC-08, TC-09 | — |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas; OSV.dev el 2026-10-25 (DEP-01, DEP-02) |

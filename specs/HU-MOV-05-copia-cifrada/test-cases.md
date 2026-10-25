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
| Evidence | movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::el_archivo_no_es_sqlite_legible_ni_contiene_el_numero_de_poliza; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::el_archivo_no_abre_con_otra_frase_y_la_llave_no_se_puede_exportar; movil/app/src/test/java/com/solventa/app/data/policies/PassphraseVaultTest.kt::"crea una frase aleatoria de 32 bytes por cuenta y solo guarda su version envuelta"; Galaxy S22 Ultra con Android 16, 2026-10-25: 21 de 21 instrumentadas |
| Status | PASS |

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
| Evidence | movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::sin_la_llave_se_descarta_hasta_el_siguiente_guardado; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::sin_la_frase_envuelta_la_copia_se_descarta_y_borrar_no_falla; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"tras una copia descartada, con red guarda las polizas y quita el aviso"; Galaxy S22 Ultra con Android 16, 2026-10-25: 21 de 21 instrumentadas |
| Status | PASS |

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
| Evidence | movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::una_base_alterada_se_reemplaza_al_guardar; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::una_base_alterada_no_devuelve_copia_ni_hora_y_se_borra; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::sin_permiso_de_escritura_el_primer_guardado_no_deja_nada; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::una_base_con_otro_esquema_se_descarta; Galaxy S22 Ultra con Android 16, 2026-10-25: 21 de 21 instrumentadas |
| Status | PASS |

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
| Evidence | movil/app/src/test/java/com/solventa/app/data/policies/PassphraseVaultTest.kt::"una frase guardada con Base64, IV o texto cifrado invalidos es invalida"; movil/app/src/test/java/com/solventa/app/data/policies/PassphraseVaultTest.kt::"si no se puede envolver, create devuelve null y deja la frase en ceros"; movil/app/src/test/java/com/solventa/app/data/policies/KeystoreWalletKeysTest.kt::"sin el alias o con el secreto alterado es invalida"; movil/app/src/test/java/com/solventa/app/data/policies/KeystoreWalletKeysTest.kt::"un Keystore que falla al cargar, buscar o borrar no lanza y deja la llave no disponible"; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::una_frase_envuelta_alterada_es_invalida_con_el_keystore_real; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::una_frase_ilegible_descarta_la_copia_y_el_guardado_la_regenera; Galaxy S22 Ultra con Android 16, 2026-10-25: 21 de 21 instrumentadas |
| Status | PASS |

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
| Evidence | movil/app/src/test/java/com/solventa/app/data/policies/KeystoreWalletKeysTest.kt::"con el telefono bloqueado antes o despues del fallo la llave no esta disponible"; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::con_la_llave_no_disponible_la_copia_se_conserva; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::con_la_llave_no_disponible_el_primer_guardado_no_crea_nada; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::una_falla_pasajera_al_escribir_conserva_la_copia_anterior; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"una copia no disponible muestra su aviso y al reintentar o volver a la app muestra la copia"; Galaxy S22 Ultra con Android 16, 2026-10-25: 21 de 21 instrumentadas |
| Status | PASS |

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
| Evidence | movil/app/src/androidTest/java/com/solventa/app/data/policies/RealLockTest.kt::con_el_telefono_bloqueado_la_copia_no_esta_disponible_y_se_conserva; corrida manual con `realLock=true` en el Galaxy S22 Ultra con PIN y bloqueo inmediato, 2026-10-25: pasó; David Gutiérrez desbloqueó el teléfono |
| Status | PASS |

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
| Evidence | movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"sin red o con el servidor caido y la copia descartada pide conexion con su aviso"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"sin red y sin copia pide conexion sin el aviso de copia descartada"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"la copia descartada muestra su aviso en lugar del de primera descarga"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"la copia no disponible muestra su aviso y reintenta con el boton"; movil/app/src/test/java/com/solventa/app/data/policies/PassphraseVaultTest.kt::"la marca de copia descartada sigue hasta borrarla"; 181 de 181 unitarias |
| Status | PASS |

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
| Evidence | movil/app/src/test/java/com/solventa/app/data/DataExtractionRulesTest.kt::"el manifiesto niega las copias y declara las reglas de extraccion"; movil/app/src/test/java/com/solventa/app/data/DataExtractionRulesTest.kt::"cada seccion excluye los nueve dominios completos y no incluye nada"; 181 de 181 unitarias |
| Status | PASS |

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
| Evidence | 2026-10-25, Galaxy S22 Ultra con Android 16: antes de las reglas viajaron los cinco archivos de prueba de `root`, `file`, `database`, `sharedpref` y `device_file`; con las reglas, el registro `BackupXmlParserLogging` lista los nueve dominios excluidos, ningún archivo de la app entra en la transferencia y `LocalTransport` rechaza el paquete vacío (−1002); `lintRelease` ya no reporta `DataExtractionRules`; el teléfono quedó con su transporte y su compilación normal |
| Status | PASS |

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
| Evidence | movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::una_base_de_otra_cuenta_no_abre_y_solo_se_descarta_esa; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::borrar_la_llave_de_una_cuenta_no_toca_la_otra; Galaxy S22 Ultra con Android 16, 2026-10-25: 21 de 21 instrumentadas |
| Status | PASS |

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
| Evidence | movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::cancelar_lecturas_y_guardados_no_borra_ni_deja_la_base_con_ceros; 50 lecturas y 100 guardados cancelados en el teléfono, 2026-10-25 |
| Status | PASS |

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
| Evidence | movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"cerrar la billetera antes de que responda el servidor no guarda la copia"; movil/app/src/test/java/com/solventa/app/MainActivityTest.kt::"cerrar sesion con la descarga pendiente no deja copia de la cuenta"; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::el_borrado_espera_al_guardado_en_curso_aunque_se_cancele; Galaxy S22 Ultra con Android 16, 2026-10-25: 21 de 21 instrumentadas |
| Status | PASS |

### TC-13 · Accesibilidad

| Field | Value |
|---|---|
| Covers | QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | TalkBack y fuente al máximo; la app real con la copia descartada sin red. El aviso de copia no disponible usa el mismo `WarningBanner` y un `SecondaryButton`, cuya región en vivo y cuyo clic verifica `WalletScreenTest`, sin agregar dependencias a `androidTest` |
| Procedure | 1. Abrir la billetera con la copia descartada. 2. Revisar el aviso con TalkBack y la fuente al máximo |
| Expected result | El aviso se anuncia al aparecer y se lee completo; nada se corta |
| Tool or method | TalkBack; los avisos usan `WarningBanner` con región en vivo (WalletScreenTest) |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | prueba manual de David Gutiérrez en su Galaxy S22 Ultra, 2026-10-25: con TalkBack y la fuente en 2.0, el aviso de copia descartada se anunció al aparecer y se leyó completo; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"la copia no disponible muestra su aviso y reintenta con el boton" |
| Status | PASS |

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
| Evidence | revisión del 2026-10-25: sin registros en `data/policies`; `SolventaApplication.kt` apaga los registros de SQLCipher; `read` y `save` comprueban `ensureActive()` al tomar el turno y antes de obtener la frase, así que no hay frase en memoria en esa comprobación; la base se usa sin cancelación y la frase se pone en cero tras cerrarla y al fallar `wrap`; `sdd.py scan secrets --diff` sin hallazgos |
| Status | PASS |

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

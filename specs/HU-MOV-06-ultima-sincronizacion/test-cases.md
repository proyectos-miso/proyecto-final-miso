# Test cases · HU-MOV-06

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | UC-01 | Dada una copia guardada hace 2 horas, cuando el asegurado abre la billetera sin red, entonces ve "Última sincronización" con esa fecha y hora local y ningún aviso | TC-01 |
| SC-02 | UC-03 | Dado un asegurado con red, cuando la sincronización termina bien, entonces la fecha pasa a la de ahora | TC-01 |
| SC-03 | UC-02 | Dada una copia de hace 25 horas, cuando abre la billetera sin red, entonces ve la fecha y el aviso de que puede no estar actualizada | TC-02 |
| SC-04 | AC-07 | Dada una copia de hace exactamente 24 horas, cuando abre la billetera, entonces no ve el aviso todavía | TC-02 |
| SC-05 | UC-03 | Dada una copia de hace 30 horas, cuando vuelve la red y la sincronización termina bien, entonces el aviso desaparece y la fecha es la de ahora | TC-01 |
| SC-06 | AC-05 | Dada una copia de hace 30 horas, cuando el servidor falla, entonces se conserva esa fecha y se ve el aviso | TC-02 |
| SC-07 | AC-06 | Dado el reloj del teléfono atrasado dos días después de sincronizar, cuando abre la billetera, entonces ve el aviso | TC-03 |
| SC-08 | AC-04 | Dadas dos cuentas con copias de distinta hora, cuando entra cada una, entonces ve su propia fecha | TC-04 |
| SC-09 | AC-04 | Dado el cierre de sesión, cuando se borra la copia, entonces se borra también su fecha | TC-04 |
| SC-10 | AC-04 | Dado el archivo de la base alterado, cuando se abre la billetera sin red, entonces no se muestra ninguna fecha y se pide conexión | TC-05 |
| SC-11 | UC-01 | Dado un asegurado sin copia y sin red, cuando abre la billetera, entonces no hay fecha y se pide conexión | TC-01 |
| SC-12 | AC-02 | Dada la fuente al máximo y TalkBack activo, cuando aparece el aviso, entonces se anuncia y la fecha se lee completa | TC-06 |
| SC-13 | UC-03 | Dada la red recuperada con la app abierta, cuando vuelve, entonces se sincroniza sola | OUT OF SCOPE: la sincronización automática al volver la red es de HU-MOV-07 |
| SC-14 | UC-02 | Dada la billetera abierta durante horas, cuando cruza las 24 horas sin cerrarse, entonces el aviso aparece al volver a abrirla | OUT OF SCOPE: el estado se calcula al abrir la billetera; refrescarlo en vivo llega con la sincronización de HU-MOV-07 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-02 | — |
| Alternate flows | YES | SC-03, SC-06, SC-11 | — |
| Invalid input and errors | YES | SC-07, SC-10 | — |
| Limits and boundaries | YES | SC-04, SC-12 | — |
| Related flows before and after | YES | SC-09, SC-13 | — |
| Several users, sessions, devices or roles | YES | SC-08 | — |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-05, SC-06, SC-14 | — |

## Test cases

### TC-01 · Fecha visible y actualizada

| Field | Value |
|---|---|
| Covers | AC-01, AC-03 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | WalletViewModel con dobles y un reloj fijo; WalletScreen con datos de prueba |
| Procedure | 1. Abrir sin red con una copia de hace 2 horas. 2. Abrir con red y respuesta correcta. 3. Abrir con red tras una copia de hace 30 horas. 4. Abrir sin red y sin copia. 5. Mostrar la pantalla con una fecha |
| Expected result | 1 muestra la hora de la copia sin aviso; 2 y 3 muestran la hora del reloj sin aviso; 4 no muestra fecha; 5 muestra "Última sincronización" con fecha y hora |
| Tool or method | ./gradlew testDebugUnitTest (WalletViewModelTest, WalletScreenTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-01 una sincronizacion exitosa guarda y muestra la hora actual y quita el aviso"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-01 sin red muestra la hora de la copia sin aviso si tiene menos de 24 horas"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"TC-01 muestra la ultima sincronizacion con fecha y hora"; movil/app/src/test/java/com/solventa/app/ui/wallet/MoneyTest.kt::"TC-01 la ultima sincronizacion usa fecha y hora en la zona del telefono" |
| Status | PASS |

### TC-02 · Aviso de desactualización

| Field | Value |
|---|---|
| Covers | AC-02, AC-05, AC-07 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | WalletViewModel con dobles y un reloj fijo |
| Procedure | 1. Copia de hace 25 horas sin red. 2. Copia de hace exactamente 24 horas. 3. Copia de hace 30 horas con el servidor caído |
| Expected result | 1 y 3 marcan la copia desactualizada y conservan su hora; 2 no |
| Tool or method | ./gradlew testDebugUnitTest (WalletViewModelTest, WalletScreenTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-02 una copia de mas de 24 horas se marca desactualizada"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-02 con exactamente 24 horas todavia no hay aviso"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-02 con el servidor caido se conserva la hora de la ultima sincronizacion exitosa"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"TC-02 una copia desactualizada muestra el aviso, anunciado por TalkBack" |
| Status | PASS |

### TC-03 · Reloj atrasado

| Field | Value |
|---|---|
| Covers | AC-06, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | UNIT |
| Preconditions | Copia guardada con una hora dos días posterior al reloj del ViewModel |
| Procedure | 1. Abrir la billetera sin red |
| Expected result | La copia se marca desactualizada |
| Tool or method | ./gradlew testDebugUnitTest (WalletViewModelTest) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-03 una hora guardada en el futuro por un reloj atrasado se marca desactualizada" |
| Status | PASS |

### TC-04 · Hora por cuenta y borrada con la copia

| Field | Value |
|---|---|
| Covers | AC-04, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Emulador; almacén cifrado real con dos cuentas |
| Procedure | 1. Guardar la copia de cada cuenta con horas distintas. 2. Leer cada una. 3. Borrar una y leerla |
| Expected result | Cada cuenta recupera su hora; la borrada no tiene copia ni hora |
| Tool or method | ./gradlew connectedDebugAndroidTest (EncryptedPolicyStoreTest) |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::cada_cuenta_recupera_su_hora_de_sincronizacion_y_se_borra_con_la_copia; emulador con Android 15, 2026-10-24: 6 de 6 |
| Status | PASS |

### TC-05 · Base alterada

| Field | Value |
|---|---|
| Covers | SR-01 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Emulador; copia guardada |
| Procedure | 1. Cambiar bytes dentro del archivo de la base. 2. Leer la copia |
| Expected result | La lectura no devuelve copia ni hora y el archivo se borra |
| Tool or method | ./gradlew connectedDebugAndroidTest (EncryptedPolicyStoreTest) |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::una_base_alterada_no_devuelve_copia_ni_hora_y_se_borra; emulador con Android 15, 2026-10-24: 6 de 6 |
| Status | PASS |

### TC-06 · Accesibilidad de la fecha y el aviso

| Field | Value |
|---|---|
| Covers | QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | Celular con TalkBack y fuente al máximo; copia de más de 24 horas |
| Procedure | 1. Abrir la billetera sin red con TalkBack. 2. Revisar la fecha y el aviso con la fuente al máximo |
| Expected result | TalkBack anuncia el aviso al aparecer y lee la fecha completa; nada se corta |
| Tool or method | TalkBack; en pruebas, el aviso tiene región en vivo (WalletScreenTest) |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | NO | — | No hay endpoint ni pantalla nueva sin sesión |
| User without permissions | NO | — | Sin permisos nuevos |
| Access to another user's resources | YES | TC-04 | — |
| Cross-tenant access | NO | — | Canal del asegurado sin inquilinos |
| Malicious input | YES | TC-03 | — |
| Expired tokens | NO | — | No cambia la sesión |
| Revoked sessions | NO | — | El borrado con 401 ya se prueba en HU-MOV-04 |
| Tampered data | YES | TC-05 | — |
| Wrong keys | NO | — | Cubierto en HU-MOV-04 (TC-06) |
| External service failures | YES | TC-02 | — |
| Race conditions | NO | — | Un solo escritor por cuenta |
| Information exposure | NO | — | La hora no identifica a nadie y no sale del teléfono |
| Secrets in code | NO | — | Sin secretos nuevos |
| Insecure configuration | NO | — | Sin configuración nueva |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas |

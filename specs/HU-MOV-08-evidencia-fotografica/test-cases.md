# Test cases · HU-MOV-08

<!-- Every acceptance criterion (AC), quality requirement (QR) and security requirement (SR) is covered by
     at least one test case. Kind: FUNCTIONAL, QUALITY or SECURITY; a SECURITY test cites the threat (THR)
     or risk (RISK) it covers. Level: UNIT, INTEGRATION, CONTRACT, E2E, PERFORMANCE, ACCESSIBILITY or
     STATIC (scans, linters, configuration checks); the test-design skill says when to use each.
     Status: PENDING, PASS, FAIL, BLOCKED or N/A. PASS needs evidence; when evidence cites path::test or
     path::"test name", the validator checks it exists. Environment: LOCAL, CI, DEV or STAGING.
     PRODUCTION needs an Authorization row. Never test against production or third parties without
     written authorization. -->

Las pruebas automáticas van en `movil/app/src/test/java/com/solventa/app/` con JUnit, coroutines-test y Robolectric, como las de HU-MOV-04 a HU-MOV-07. Robolectric no carga SQLCipher ni el Keystore real: las pruebas usan los dobles de `PolicyTestDoubles`, y lo real se prueba con la prueba instrumentada (TC-21) y en el teléfono (TC-16, TC-17 y TC-20).

## Scenarios

<!-- Derive what can happen around this story before writing test cases — not only what it says. Start
     from its own elements (actors, data, rules, states, steps, dependencies, what comes before and after,
     time) and look at each one through the kinds in the coverage table; the method is in the test-design
     skill. Disposition: the test case that covers it (TC-NN), or OUT OF SCOPE: <why, or the story that
     covers it>. A scenario in scope that the story does not state becomes a DERIVED criterion in spec.md.
     Only a LOW profile may write: N/A: <rationale>. -->

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | UC-01 | Dada una billetera con una póliza vigente, cuando el asegurado toma 3 fotos, elige la póliza, describe lo ocurrido y toca «Enviar», entonces el reporte queda guardado y listo para enviar | TC-11, TC-20 |
| SC-02 | AC-04 | Dado un reporte con fotos y sin descripción, cuando toca «Enviar», entonces el botón sigue inactivo y la app dice que falta la descripción | TC-09 |
| SC-03 | AC-04 | Dado un reporte sin fotos, cuando pasa al detalle, entonces la app dice que falta al menos una foto | TC-09 |
| SC-04 | AC-14 | Dado un reporte con 5 fotos, cuando intenta una sexta, entonces la app no la deja | TC-09 |
| SC-05 | AC-14 | Dada una descripción de 500 caracteres, cuando intenta escribir uno más, entonces no entra; con solo espacios cuenta como vacía | TC-09 |
| SC-06 | UC-02 | Dado el permiso de ubicación negado una vez o para siempre, cuando toca la cámara, entonces la app explica y ofrece los ajustes sin cerrarse | TC-03 |
| SC-07 | AC-17 | Dado que concede solo la ubicación aproximada, cuando toca la cámara, entonces la app lo trata como negado y pide la precisa | TC-03 |
| SC-08 | AC-07 | Dado el permiso de cámara negado, o un teléfono sin cámara, cuando toca «Tomar foto», entonces la app explica sin cerrarse | TC-03 |
| SC-09 | UC-03 | Dada la ubicación del teléfono apagada, cuando dispara, entonces no hay foto y la app dice cómo encenderla | TC-04 |
| SC-10 | UC-03 | Dado un lugar sin señal GPS, cuando pasan 30 s sin ubicación, entonces no hay foto y la app sugiere un lugar abierto | TC-04 |
| SC-11 | AC-01 | Dada una ubicación de más de 10 s o con precisión peor que 100 m, cuando dispara, entonces la app espera una nueva | TC-04 |
| SC-12 | AC-10 | Dada una app de ubicación simulada, cuando toma la foto, entonces la foto queda marcada | TC-05 |
| SC-13 | AC-10 | Dado el reloj del teléfono adelantado, cuando toma la foto, entonces se guardan la hora del teléfono y la independiente | TC-05 |
| SC-14 | UC-04 | Dado un reporte a medias, cuando Android cierra la app y la persona vuelve, entonces encuentra el reporte con sus fotos | TC-11 |
| SC-15 | UC-04 | Dado un reporte, cuando lo descarta, entonces se borran sus fotos y su fila | TC-10 |
| SC-16 | AC-13 | Dadas 3 fotos, cuando borra la segunda, entonces su archivo desaparece y quedan 2 en orden | TC-10 |
| SC-17 | AC-12 | Dado un reporte, cuando la persona cierra sesión, entonces se borran el reporte, las fotos, la frase y la llave | TC-08 |
| SC-18 | AC-12 | Dado un reporte, cuando el servidor desvincula la huella o la persona cambia la contraseña, entonces se borra igual | TC-08 |
| SC-19 | AC-11 | Dadas 2 cuentas en el teléfono, cuando la segunda abre el reporte, entonces no ve el de la primera | TC-08 |
| SC-20 | AC-09 | Dado un archivo de foto alterado o cambiado por otro, cuando la app lo abre, entonces la foto se marca como dañada y no se muestra | TC-07 |
| SC-21 | AC-11 | Dada una llave invalidada (por ejemplo, al quitar el bloqueo de pantalla), cuando abre el reporte, entonces la app dice que no se puede abrir, la persona decide descartarlo y la app no se cierra | TC-15 |
| SC-22 | AC-11 | Dado el teléfono bloqueado o sin espacio al guardar, cuando falla la escritura, entonces el reporte queda no disponible sin borrarse ni dejar archivos a medias | TC-15 |
| SC-23 | AC-06 | Dada una billetera sin pólizas vigentes ni reporte guardado, cuando se muestra, entonces no ofrece «Reportar un siniestro» | TC-12 |
| SC-24 | AC-06 | Dada la billetera sin conexión, cuando abre el reporte, entonces elige entre las pólizas vigentes de la copia | TC-12 |
| SC-25 | AC-16 | Dado el reporte en pantalla, cuando se intenta una captura en release, entonces sale en negro; en debug se permite | TC-13, TC-20 |
| SC-26 | UC-01 | Dado un reporte listo para enviar, cuando vuelve la red, entonces se envía al servidor | OUT OF SCOPE: el envío y la cola son de HU-MOV-09 |
| SC-27 | AC-12 | Dada la actividad que termina sin cerrar sesión (ADR 0015), cuando la persona vuelve y entra, entonces el reporte sigue ahí | TC-08 |
| SC-28 | UC-01 | Dada la misma cuenta en 2 teléfonos, cuando cada uno arma un reporte, entonces cada teléfono guarda el suyo | OUT OF SCOPE: cada teléfono guarda lo propio; los duplicados en el servidor son de HU-MOV-09 |
| SC-29 | AC-14 | Dadas 4 fotos, cuando toca el disparador 2 veces seguidas, entonces queda una sola quinta foto | TC-09 |
| SC-30 | UC-01 | Dada la cámara o el detalle abiertos, cuando la persona va atrás o gira el teléfono, entonces vuelve a la evidencia sin perder fotos ni texto | TC-11 |
| SC-31 | AC-03 | Dado el permiso «Solo esta vez», cuando Android lo revoca a mitad del reporte, entonces la app lo vuelve a pedir y conserva el reporte | TC-03 |
| SC-32 | AC-18 | Dada una póliza que vence mientras el reporte está a medias, cuando toca «Enviar», entonces la app pide otra póliza sin perder el reporte | TC-24 |
| SC-33 | AC-06 | Dado un reporte guardado y ninguna póliza vigente, o la copia descartada, cuando abre la billetera, entonces puede retomar o descartar el reporte | TC-12, TC-24 |
| SC-34 | AC-11 | Dada la sesión vencida, cuando la persona vuelve a entrar, entonces el reporte sigue ahí | TC-08 |
| SC-35 | AC-12 | Dado un reporte, cuando la sincronización detecta que el servidor desvinculó el teléfono, entonces se borra | TC-08 |
| SC-36 | AC-19 | Dado un reporte listo para enviar, cuando lo abre, entonces lo ve sin poder editarlo y puede descartarlo | TC-24 |
| SC-37 | AC-11 | Dada una versión nueva de la app con otro esquema, cuando se actualiza, entonces el reporte guardado se conserva | TC-23 |
| SC-38 | AC-20 | Dada la cámara abierta, cuando la persona sale de la cámara o la app pasa a segundo plano, entonces deja de leerse la ubicación | TC-22 |

## Scenario coverage

<!-- Review every kind. YES needs scenarios; NO needs a rationale. Authentication requires related flows
     and state and time; authorization or tenants require several users. -->

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01 | — |
| Alternate flows | YES | SC-06, SC-07, SC-08, SC-09, SC-10, SC-15, SC-16, SC-30, SC-31, SC-33, SC-36 | — |
| Invalid input and errors | YES | SC-02, SC-03, SC-05, SC-20, SC-22, SC-32 | — |
| Limits and boundaries | YES | SC-04, SC-05, SC-11, SC-29 | — |
| Related flows before and after | YES | SC-17, SC-18, SC-26, SC-27, SC-35, SC-37 | — |
| Several users, sessions, devices or roles | YES | SC-19, SC-28 | — |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-10, SC-13, SC-14, SC-21, SC-22, SC-24, SC-30, SC-32, SC-34, SC-38 | — |

## Test cases

### TC-01 · Cada foto guarda su ubicación, su precisión y sus horas

| Field | Value |
|---|---|
| Covers | AC-01, AC-10, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | UNIT |
| Preconditions | `ClaimReportViewModel` con una ubicación y relojes falsos |
| Procedure | 1. Tomar una foto con ubicación de 4 m y hora independiente de GNSS. 2. Repetir con solo la hora de red. 3. Repetir sin hora independiente |
| Expected result | La foto guarda latitud, longitud, precisión, hora del teléfono y la hora independiente con su fuente; sin hora independiente quedan vacías y la foto se guarda igual |
| Tool or method | JUnit y coroutines-test (`ClaimReportViewModelTest`, `AndroidTrustedClockTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-01 la foto se guarda con la ubicación, la hora del teléfono y la independiente"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-01 sin hora independiente la foto se guarda igual"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-01 guarda la foto con su ubicación, precisión y horas"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-01 sin hora independiente la foto se guarda igual"; movil/app/src/test/java/com/solventa/app/data/claims/AndroidCaptureLocatorTest.kt::"TC-01 con la cámara abierta usa la ubicación fresca y precisa"; movil/app/src/test/java/com/solventa/app/data/claims/AndroidTrustedClockTest.kt::"prefiere la hora del GNSS"; movil/app/src/test/java/com/solventa/app/data/claims/AndroidTrustedClockTest.kt::"sin GNSS usa la hora de red de Android"; movil/app/src/test/java/com/solventa/app/data/claims/AndroidTrustedClockTest.kt::"sin ninguna no hay hora independiente" |
| Status | PASS |

### TC-02 · La evidencia sale solo de la cámara de la app

| Field | Value |
|---|---|
| Covers | AC-02, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Pantallas del reporte en Robolectric con permisos concedidos |
| Procedure | 1. Recorrer la evidencia, la cámara y el detalle. 2. Tocar «Tomar otra foto». 3. Revisar las actividades que la app intentó abrir |
| Expected result | No hay opción de galería ni de archivos; «Tomar otra foto» abre la cámara de la app; no se lanza ningún intent de cámara, galería o selector de fotos |
| Tool or method | Robolectric y Compose UI test (`ClaimScreensTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-02 la evidencia sale solo de la cámara de la app, sin galería ni otras apps"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimPrivacySourceTest.kt::"el reporte no abre la galería ni otra app de cámara ni escribe la foto en claro" |
| Status | PASS |

### TC-03 · Permisos negados: ubicación, solo aproximada y cámara

| Field | Value |
|---|---|
| Covers | AC-03, AC-07, AC-17, SR-07 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Pantallas del reporte en Robolectric |
| Procedure | 1. Negar la ubicación una vez y para siempre. 2. Conceder solo la aproximada. 3. Negar la cámara. 4. Simular un teléfono sin cámara. 5. Tocar «Abrir ajustes» y «Volver al reporte». 6. Conceder «Solo esta vez» y revocarlo a mitad del reporte |
| Expected result | Cada caso muestra su explicación y el acceso a los ajustes de la app; la aproximada se trata como negada; al revocarse el permiso se vuelve a pedir; el reporte se conserva y la app no se cierra |
| Tool or method | Robolectric y Compose UI test (`ClaimScreensTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-03 el permiso negado explica los pasos y abre los ajustes de la app"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-03 sin cámara en el teléfono lo explica sin ofrecer ajustes"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-03 al volver de los ajustes con el permiso la cámara se abre"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-03 si revocan un permiso a mitad del reporte se vuelve a pedir y el reporte sigue"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-03 los permisos que faltan se explican y con todos se abre la cámara"; movil/app/src/test/java/com/solventa/app/data/claims/AndroidCaptureLocatorTest.kt::"TC-03 el permiso se lee como preciso, solo aproximado o negado"; movil/app/src/test/java/com/solventa/app/data/claims/AndroidCaptureLocatorTest.kt::"TC-03 sin ubicación precisa no hay foto y no se pide la ubicación" |
| Status | PASS |

### TC-04 · Sin ubicación no hay foto

| Field | Value |
|---|---|
| Covers | AC-08 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | `ClaimReportViewModel` con una ubicación falsa y tiempo virtual |
| Procedure | 1. Ubicación apagada. 2. Sin lectura durante 30 s. 3. Una lectura de 11 s y otra de 150 m, y después una buena. 4. Una lectura buena a los 29 s |
| Expected result | En 1 y 2 no se guarda la foto, se muestra cómo resolverlo y el reporte sigue; en 3 la app espera y toma la buena; en 4 guarda la foto |
| Tool or method | JUnit y coroutines-test (`ClaimReportViewModelTest`, `AndroidCaptureLocatorTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-04 sin ubicación no hay foto y se dice cómo resolverlo"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-04 mientras busca la ubicación cuenta los segundos y no deja disparar otra vez"; movil/app/src/test/java/com/solventa/app/data/claims/AndroidCaptureLocatorTest.kt::"TC-04 con la ubicación del teléfono apagada no hay foto"; movil/app/src/test/java/com/solventa/app/data/claims/AndroidCaptureLocatorTest.kt::"TC-04 sin una ubicación buena a tiempo no hay foto"; movil/app/src/test/java/com/solventa/app/data/claims/AndroidCaptureLocatorTest.kt::"TC-04 una ubicación de más de 10 s o peor que 100 m espera una buena"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-04 con la ubicación apagada ofrece los ajustes de ubicación" |
| Status | PASS |

### TC-05 · Ubicación simulada y reloj cambiado

| Field | Value |
|---|---|
| Covers | AC-10, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | UNIT |
| Preconditions | `AndroidCaptureLocator` con el `LocationManager` de Robolectric |
| Procedure | 1. Entregar una ubicación marcada como simulada. 2. Adelantar el reloj del teléfono una hora con la hora de red disponible |
| Expected result | La foto queda con `mock_location` en 1; se guardan las 2 horas distintas, sin corregir ninguna |
| Tool or method | JUnit y Robolectric (`AndroidCaptureLocatorTest`, `AndroidTrustedClockTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/claims/AndroidCaptureLocatorTest.kt::"TC-05 una ubicación simulada queda marcada"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-05 la ubicación simulada queda marcada en la foto"; movil/app/src/test/java/com/solventa/app/data/claims/AndroidTrustedClockTest.kt::"TC-05 no la corrige con el reloj del teléfono adelantado" |
| Status | PASS |

### TC-06 · Nada en claro en disco ni en las copias

| Field | Value |
|---|---|
| Covers | AC-05, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | INTEGRATION |
| Preconditions | `EncryptedClaimStore` en Robolectric con llaves de prueba |
| Procedure | 1. Guardar 3 fotos y la descripción. 2. Revisar los archivos de la app, la caché y MediaStore. 3. Revisar las reglas de copias de seguridad y transferencias |
| Expected result | Los archivos de fotos no empiezan como JPEG ni contienen los bytes originales; no hay archivos en la caché ni entradas en MediaStore; las reglas siguen excluyendo todos los dominios |
| Tool or method | Robolectric (`EncryptedClaimStoreTest`, `DataExtractionRulesTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-06 el archivo de la foto está cifrado y no hay copias en claro"; movil/app/src/test/java/com/solventa/app/data/DataExtractionRulesTest.kt::"cada seccion excluye los nueve dominios completos y no incluye nada"; movil/app/src/test/java/com/solventa/app/data/DataExtractionRulesTest.kt::"el manifiesto niega las copias y declara las reglas de extraccion"; Galaxy S22 Ultra: las fotos no empiezan como JPEG, la base no tiene el encabezado de SQLite, carpetas 700, sin entradas en MediaStore ni archivos del reporte en la caché |
| Status | PASS |

### TC-07 · Huella de cada foto y foto alterada

| Field | Value |
|---|---|
| Covers | AC-09, SR-08 |
| Kind | SECURITY |
| Risk covered | THR-05 |
| Level | UNIT |
| Preconditions | `PhotoCipher`, `EvidenceEncoder` y el almacén con llaves de prueba |
| Procedure | 1. Guardar una foto y comparar la huella con el SHA-256 de los bytes JPEG. 2. Cambiar un byte del archivo. 3. Intercambiar los archivos de 2 fotos. 4. Borrar un archivo |
| Expected result | La huella coincide; en 2, 3 y 4 la foto no descifra, se marca como dañada, no se muestra y se pide tomarla de nuevo |
| Tool or method | JUnit y Robolectric (`PhotoCipherTest`, `EvidenceEncoderTest`, `EncryptedClaimStoreTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/claims/PhotoCipherTest.kt::"la huella es el SHA-256 en hexadecimal"; movil/app/src/test/java/com/solventa/app/data/claims/PhotoCipherTest.kt::"cifra y descifra la misma foto"; movil/app/src/test/java/com/solventa/app/data/claims/PhotoCipherTest.kt::"un byte cambiado, otra llave u otro nonce no descifran"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-07 la huella es el SHA-256 de los bytes JPEG y la imagen vuelve igual"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-07 una foto alterada, cambiada por otra o sin archivo queda dañada"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-07 una foto que se descifra pero no coincide con su huella queda dañada" |
| Status | PASS |

### TC-08 · Un reporte por cuenta, borrado al terminar la sesión

| Field | Value |
|---|---|
| Covers | AC-11, AC-12, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | INTEGRATION |
| Preconditions | Almacén y `AuthViewModel` con dobles de prueba; 2 cuentas en el teléfono |
| Procedure | 1. Armar un reporte en la cuenta A y abrir el de la cuenta B. 2. Cerrar sesión en A. 3. Que el servidor desvincule el teléfono, al ingresar y en la sincronización. 4. Cambiar la contraseña. 5. Terminar la actividad sin cerrar sesión. 6. Que la sesión venza y la persona vuelva a entrar. 7. Cerrar sesión mientras se guarda una foto |
| Expected result | B no ve el reporte de A; en 2, 3 y 4 se borran la base, las fotos, la frase y la llave de esa cuenta; en 5 y 6 el reporte sigue; en 7 la foto no reaparece después del borrado |
| Tool or method | Robolectric y coroutines-test (`EncryptedClaimStoreTest`, `AuthViewModelTest`, `SessionPolicySyncTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-08 cada cuenta tiene su reporte, su frase y su llave"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-08 un borrado durante una foto no deja la foto después del borrado"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"HU-MOV-08 TC-08 cerrar sesion borra el reporte de siniestro de la cuenta"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"HU-MOV-08 TC-08 la huella desactivada en el servidor borra el reporte"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"HU-MOV-08 TC-08 cambiar la contrasena borra el reporte de esa cuenta"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"HU-MOV-08 TC-08 la sesion vencida y el nuevo ingreso conservan el reporte"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"HU-MOV-08 TC-08 solo el telefono desvinculado borra el reporte de siniestro"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-11 al volver a abrir encuentra las fotos, la póliza y la descripción"; Galaxy S22 Ultra: al cerrar sesión no quedan la base, las fotos ni la frase de la cuenta |
| Status | PASS |

### TC-09 · Reglas del reporte y lo que falta

| Field | Value |
|---|---|
| Covers | AC-04, AC-14, SR-10 |
| Kind | SECURITY |
| Risk covered | THR-11 |
| Level | UNIT |
| Preconditions | `ClaimRules` y `ClaimReportViewModel` |
| Procedure | 1. Sin fotos, 1 y 5 fotos, e intentar una sexta. 2. Descripción vacía, solo espacios, de 1, 500 y 501 caracteres, con emojis y saltos de línea. 3. Una imagen que en JPEG pasaría de 2 MB. 4. Tocar el disparador 2 veces con 4 fotos |
| Expected result | «Enviar» solo se habilita con 1 a 5 fotos y 1 a 500 caracteres útiles; los avisos dicen qué falta; no entra una sexta foto ni el carácter 501; la imagen queda en 2 MB o menos; con el doble toque solo entra una quinta foto |
| Tool or method | JUnit y coroutines-test (`ClaimRulesTest`, `ClaimReportViewModelTest`, `EvidenceEncoderTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/domain/claims/ClaimRulesTest.kt::"un reporte vacío pide foto, póliza y descripción, en ese orden"; movil/app/src/test/java/com/solventa/app/domain/claims/ClaimRulesTest.kt::"solo espacios cuenta como sin descripción"; movil/app/src/test/java/com/solventa/app/domain/claims/ClaimRulesTest.kt::"la descripción se recorta en 500 caracteres y con 500 queda igual"; movil/app/src/test/java/com/solventa/app/domain/claims/ClaimRulesTest.kt::"se pueden agregar fotos hasta 5 y no en un reporte listo"; movil/app/src/test/java/com/solventa/app/domain/claims/ClaimRulesTest.kt::"con una foto, póliza vigente y descripción no falta nada"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-09 sin fotos no pasa al detalle y dice que falta la foto"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-09 enviar sin descripción o sin póliza dice qué falta y no lo deja listo"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-09 la descripción se recorta en 500 y se guarda medio segundo después"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-09 con 5 fotos ya no abre la cámara ni guarda otra"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-09 el detalle dice qué falta mientras Enviar está desactivado"; movil/app/src/test/java/com/solventa/app/data/claims/EvidenceEncoderTest.kt::"TC-09 baja la calidad hasta que cabe en el límite"; movil/app/src/test/java/com/solventa/app/data/claims/EvidenceEncoderTest.kt::"TC-09 si ni con la calidad más baja cabe no hay foto"; movil/app/src/test/java/com/solventa/app/data/claims/EvidenceEncoderTest.kt::"una foto grande queda con el lado mayor en 1920 px"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-09 no entra una sexta foto ni una de más de 2 MB"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-09 dos fotos a la vez con 4 guardadas solo dejan entrar una quinta"; Galaxy S22 Ultra: con 510 letras escritas quedan 500, y solo espacios cuentan como sin descripción |
| Status | PASS |

### TC-10 · Borrar una foto y descartar el reporte

| Field | Value |
|---|---|
| Covers | AC-13 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | `EncryptedClaimStore` en Robolectric con 3 fotos |
| Procedure | 1. Borrar la segunda foto. 2. Descartar el reporte. 3. Dejar un archivo sin fila y abrir el almacén |
| Expected result | En 1 se borran su archivo y su fila y quedan las posiciones 1 y 2; en 2 no queda ningún archivo ni fila; en 3 el archivo huérfano se borra |
| Tool or method | Robolectric (`EncryptedClaimStoreTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-10 borrar la segunda foto borra su archivo y deja 2 en orden"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-10 descartar borra la base, las fotos, la frase y la llave"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-10 un archivo de foto sin fila se borra al abrir el reporte"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-24 un borrador con pólizas vigentes se descarta desde la evidencia, aun sin fotos" |
| Status | PASS |

### TC-11 · El reporte sobrevive y «Enviar» lo deja listo

| Field | Value |
|---|---|
| Covers | AC-11, AC-15 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Pantallas y ViewModel en Robolectric con el almacén de prueba |
| Procedure | 1. Armar un reporte a medias y recrear la actividad y el ViewModel. 2. Ir atrás desde la cámara y desde el detalle, y girar el teléfono. 3. Completar y tocar «Enviar». 4. Volver a tocar «Reportar un siniestro» |
| Expected result | En 1 y 2 vuelven las fotos, la póliza y la descripción; en 3 el reporte pasa a listo y se muestra «Reporte guardado en el teléfono»; en 4 la app abre ese reporte listo, sin crear otro |
| Tool or method | Robolectric y Compose UI test (`ClaimScreensTest`, `ClaimReportViewModelTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-11 al volver a abrir encuentra las fotos, la póliza y la descripción"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-11 enviar con todo deja el reporte listo y muestra el guardado"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"atrás va de la cámara y del detalle a la evidencia, y de la evidencia a la billetera"; Galaxy S22 Ultra: el reporte con 5 fotos sigue tras cerrar el proceso y volver a ingresar, y tras girar el teléfono |
| Status | PASS |

### TC-12 · Botón en la billetera y pólizas vigentes sin red

| Field | Value |
|---|---|
| Covers | AC-06 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | `WalletScreen` y el detalle en Robolectric |
| Procedure | 1. Billetera con una póliza vigente y una vencida. 2. Sin pólizas vigentes ni reporte. 3. Sin conexión, con la copia guardada. 4. Sin pólizas vigentes pero con un reporte guardado. 5. Con un reporte guardado y la billetera sin carné: copia descartada, sin conexión y sin copia, y sin pólizas |
| Expected result | En 1 aparece «Reportar un siniestro» y el selector ofrece solo la vigente; en 2 no aparece el botón; en 3 el reporte usa la copia; en 4 y en cada estado de 5 el botón aparece debajo del aviso de la billetera y retoma el reporte |
| Tool or method | Robolectric y Compose UI test (`WalletScreenTest`, `ClaimScreensTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-12 abre un borrador con solo las pólizas vigentes de la copia"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-12 la billetera ofrece reportar, retomar o ver el listo, y nada sin pólizas ni reporte"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"HU-MOV-08 TC-12 con una póliza vigente ofrece reportar un siniestro"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"HU-MOV-08 TC-12 sin pólizas vigentes ni reporte no ofrece nada"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"HU-MOV-08 TC-12 un reporte guardado se retoma aunque la billetera no muestre carnés"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"HU-MOV-08 TC-12 un reporte listo se ofrece para verlo"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-12 sin pólizas vigentes el detalle lo dice y deja descartar" |
| Status | PASS |

### TC-13 · Capturas bloqueadas en release

| Field | Value |
|---|---|
| Covers | AC-16, SR-05 |
| Kind | SECURITY |
| Risk covered | THR-07 |
| Level | INTEGRATION |
| Preconditions | `MainActivity` en Robolectric con la protección activada como en release y desactivada como en debug |
| Procedure | 1. Abrir el reporte. 2. Volver a la billetera. 3. Repetir como en debug |
| Expected result | Con el reporte en pantalla la ventana tiene `FLAG_SECURE`; al volver a la billetera se quita; en debug nunca se pone |
| Tool or method | Robolectric (`ClaimSecureWindowTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimSecureWindowTest.kt::"TC-13 la ventana es segura solo mientras se muestra el reporte, y nunca si está apagada"; Galaxy S22 Ultra con `-PsecureScreens=true`: la captura del reporte sale negra y la de la billetera no |
| Status | PASS |

### TC-14 · Sin datos en registros ni en el estado guardado

| Field | Value |
|---|---|
| Covers | SR-06 |
| Kind | SECURITY |
| Risk covered | THR-08 |
| Level | UNIT |
| Preconditions | Reporte con descripción, ubicación y fotos |
| Procedure | 1. Recorrer el reporte capturando logcat. 2. Guardar el estado de la actividad y revisar el paquete. 3. Buscar llamadas a `Log` en `data/claims` y `ui/claims` |
| Expected result | Ni logcat ni el estado guardado contienen la descripción, coordenadas, rutas ni llaves; no hay llamadas a `Log` en esos paquetes |
| Tool or method | Robolectric (`ClaimScreensTest`) y búsqueda en el código |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/claims/ClaimPrivacySourceTest.kt::"el reporte no escribe registros"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimPrivacySourceTest.kt::"el reporte no guarda nada en el estado que conserva el sistema"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"al cerrar sesión no queda nada del reporte en memoria"; Galaxy S22 Ultra: logcat del proceso sin líneas de la app durante el reporte |
| Status | PASS |

### TC-15 · Fallas de llave, base o almacenamiento sin cerrar la app

| Field | Value |
|---|---|
| Covers | SR-09 |
| Kind | SECURITY |
| Risk covered | THR-09 |
| Level | UNIT |
| Preconditions | Almacén con llaves y archivos de prueba que fallan |
| Procedure | 1. Llave invalidada. 2. Base dañada. 3. Teléfono bloqueado. 4. Falla pasajera. 5. Sin espacio al escribir una foto |
| Expected result | En 1 y 2 el reporte queda como «no se puede abrir» y solo se borra si la persona toca «Descartar»; en 3 y 4 queda no disponible sin borrarse; en 5 no queda archivo a medias y el reporte sigue; nunca se cierra la app |
| Tool or method | JUnit y Robolectric (`EncryptedClaimStoreTest`, `ClaimReportViewModelTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-15 con la llave invalidada no se puede abrir y no se borra"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-15 con la base dañada no se puede abrir y no se borra"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-15 con el teléfono bloqueado o una falla pasajera queda no disponible y se conserva"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-15 si no se puede escribir el archivo no queda nada a medias y el reporte sigue"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"sin poder usar la llave no se crea el borrador"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-15 no disponible o sin poder abrir se avisa y nada se borra solo" |
| Status | PASS |

### TC-16 · Tiempo de guardado de la foto

| Field | Value |
|---|---|
| Covers | QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | PERFORMANCE |
| Preconditions | Build release en el teléfono, ubicación disponible al aire libre |
| Procedure | 1. Tomar 20 fotos. 2. Medir desde el disparo hasta que la foto aparece guardada en la evidencia |
| Expected result | 20 de 20 por debajo de 2 s |
| Tool or method | Medición en el teléfono con marcas de tiempo de la build de prueba, sin registrar contenido |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | Galaxy S22 Ultra con Android 16, 2026-11-05, desde el toque del disparador hasta que el archivo cifrado queda escrito: 20 de 20 por debajo de 2 s (mediana 0,78 s, máximo 1,31 s); una segunda serie con el teléfono ya caliente: 20 de 20 (mediana 1,28 s, máximo 1,39 s) |
| Status | PASS |

### TC-17 · Accesibilidad con TalkBack

| Field | Value |
|---|---|
| Covers | QR-02 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | Teléfono con TalkBack y la fuente al máximo |
| Procedure | 1. Recorrer la billetera, la evidencia, la cámara, la hoja del permiso, el detalle y el guardado con TalkBack. 2. Medir los destinos táctiles |
| Expected result | El disparador, «Borrar foto», los avisos y lo que falta se anuncian con su nombre; destinos de 48 dp o más; la explicación del permiso se lee completa con la fuente al máximo |
| Tool or method | TalkBack en el teléfono y aserciones de semántica en `ClaimScreensTest` |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-17 cada pantalla del reporte anuncia su título"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-17 sin fotos, Continuar anuncia lo que falta en un nodo vivo que ya estaba"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-17 borrar una foto se anuncia con las que quedan"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-17 la fila de la foto se lee en un nodo y en palabras"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-17 la cámara anuncia solo su estado y el disparador es un botón"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-17 los campos obligatorios y el contador se leen en palabras"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-17 con la fuente al máximo la explicación del permiso se lee completa"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-17 el disparador y borrar la foto tienen nombre y miden al menos 48 dp"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-17 la barra de progreso es decorativa y no tapa el botón Atrás"; Galaxy S22 Ultra con Android 16, 2026-11-05: todos los controles miden 48 dp o más; recorrido con TalkBack y con la fuente al máximo hecho por David, sin problemas |
| Status | PASS |

### TC-18 · Dependencias y secretos

| Field | Value |
|---|---|
| Covers | SR-11 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | STATIC |
| Preconditions | Rama de la historia frente a `dev` |
| Procedure | 1. Consultar OSV.dev por `camera-core`, `camera-camera2`, `camera-lifecycle` y `camera-view` 1.5.3 y sus transitivas. 2. `python sdd/tools/sdd.py scan deps --base origin/dev --require-evaluation`. 3. `python sdd/tools/sdd.py scan secrets --diff origin/dev` |
| Expected result | Sin vulnerabilidades conocidas, dependencias evaluadas y 0 secretos |
| Tool or method | OSV.dev y `sdd.py` |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | OSV.dev sin vulnerabilidades para CameraX 1.5.3 (EV-01 en gate.md); `sdd.py scan deps --base origin/dev --require-evaluation`: 4 dependencias nuevas, 0 sin evaluación; `sdd.py scan secrets --diff origin/dev` y sobre los 39 archivos nuevos con pruebas: 0 secretos |
| Status | PASS |

### TC-19 · Permisos mínimos del manifiesto

| Field | Value |
|---|---|
| Covers | SR-07 |
| Kind | SECURITY |
| Risk covered | THR-10 |
| Level | STATIC |
| Preconditions | Manifiesto combinado de la app |
| Procedure | 1. Leer los permisos y las características declaradas. 2. Revisar los componentes exportados |
| Expected result | Pide `CAMERA`, `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION`, no `ACCESS_BACKGROUND_LOCATION` ni permisos de almacenamiento; la cámara y el GPS no son obligatorios; no hay componentes exportados nuevos |
| Tool or method | Robolectric sobre el manifiesto combinado (`ClaimManifestTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimManifestTest.kt::"TC-19 pide cámara y ubicación precisa y aproximada, sin segundo plano ni almacenamiento"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimManifestTest.kt::"TC-19 la cámara y el GPS no son obligatorios"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimManifestTest.kt::"TC-19 no hay actividades exportadas nuevas" |
| Status | PASS |

### TC-20 · Recorrido completo en el teléfono

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-05, AC-16 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | E2E |
| Preconditions | Teléfono Android real con cámara y GPS, build debug y build release |
| Procedure | 1. Reportar con 3 fotos, póliza y descripción. 2. Negar y conceder la ubicación. 3. Revisar la galería y los archivos de la app. 4. Intentar una captura de pantalla y ver las apps recientes en release y en debug |
| Expected result | Las fotos quedan con coordenadas y hora reales; no aparecen en la galería; la base y las fotos no se leen sin la app; en release la captura sale negra y las recientes ocultan el reporte; en debug se permite |
| Tool or method | Pruebas manuales en el teléfono con adb |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | Galaxy S22 Ultra con Android 16, 2026-11-05: reporte con fotos, póliza y descripción; cada foto con las coordenadas y la hora reales; con la ubicación solo aproximada y con la ubicación negada, el aviso con sus pasos y «Abrir ajustes» lleva a la información de la app, la cámara no se abre y el reporte se conserva; las fotos no aparecen en MediaStore; la base y las fotos no se leen sin la app; con `-PsecureScreens=true` la captura del reporte sale negra y la tarjeta de recientes queda vacía; en debug se permite |
| Status | PASS |

### TC-21 · Cifrado real con SQLCipher y el Keystore

| Field | Value |
|---|---|
| Covers | SR-03 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | INTEGRATION |
| Preconditions | Emulador o teléfono con Android 13 o superior y bloqueo de pantalla |
| Procedure | 1. Guardar un reporte con `EncryptedClaimStore` real. 2. Leer el encabezado de `claims_<cuenta>.db`. 3. Abrir la base con una frase errada. 4. Revisar la llave `solventa_claims_<cuenta>` en el Keystore. 5. Leer un archivo de foto |
| Expected result | La base no empieza con el encabezado de SQLite en claro y no abre con otra frase; la llave existe en el Keystore, no es exportable, exige el teléfono desbloqueado y es distinta de la de la billetera; la foto no es un JPEG legible |
| Tool or method | Prueba instrumentada con `connectedAndroidTest` (`EncryptedClaimStoreTest` en `androidTest`), como `EncryptedPolicyStoreTest` de HU-MOV-05 |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/androidTest/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"la_base_no_es_sqlite_legible_ni_abre_con_otra_frase"; movil/app/src/androidTest/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"la_llave_del_reporte_esta_en_el_keystore_no_se_exporta_y_no_es_la_de_la_billetera"; movil/app/src/androidTest/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"la_foto_queda_cifrada_y_vuelve_igual_con_su_huella"; movil/app/src/androidTest/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"descartar_borra_la_base_las_fotos_la_frase_y_la_llave"; movil/app/src/androidTest/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"sin_la_llave_no_se_puede_abrir_y_no_se_borra"; 5 de 5 en el Galaxy S22 Ultra con Android 16 |
| Status | PASS |

### TC-22 · La ubicación se lee solo con la cámara abierta

| Field | Value |
|---|---|
| Covers | AC-20, SR-07 |
| Kind | SECURITY |
| Risk covered | THR-10 |
| Level | UNIT |
| Preconditions | `AndroidCaptureLocator` con el `LocationManager` de Robolectric y el ciclo de vida de la cámara |
| Procedure | 1. Abrir la cámara. 2. Salir de la cámara. 3. Pasar la app a segundo plano con la cámara abierta. 4. Simular un teléfono sin proveedor `fused` |
| Expected result | Solo hay pedidos de ubicación mientras la cámara está abierta y en primer plano; al salir o pasar a segundo plano no queda ninguno activo; sin `fused` se usan `gps` y `network` |
| Tool or method | JUnit y Robolectric (`AndroidCaptureLocatorTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/claims/AndroidCaptureLocatorTest.kt::"TC-22 solo escucha la ubicación entre start y stop"; movil/app/src/test/java/com/solventa/app/data/claims/AndroidCaptureLocatorTest.kt::"TC-22 al detenerse olvida la última ubicación"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-22 la ubicación se escucha solo con la cámara a la vista"; Galaxy S22 Ultra: `dumpsys location` registra y quita el pedido al abrir y cerrar la cámara, sin pedidos con la cámara cerrada |
| Status | PASS |

### TC-23 · Esquema exportado y sin migraciones destructivas

| Field | Value |
|---|---|
| Covers | SR-12 |
| Kind | SECURITY |
| Risk covered | THR-12 |
| Level | STATIC |
| Preconditions | Proyecto compilado |
| Procedure | 1. Buscar el esquema exportado de la base del reporte en `movil/app/schemas/`. 2. Buscar `fallbackToDestructiveMigration` en `data/claims`. 3. Revisar que la anotación de la base exporte el esquema |
| Expected result | El esquema de la versión 1 existe y está en el repositorio; `data/claims` no usa migraciones destructivas; la base declara `exportSchema = true` |
| Tool or method | JUnit sobre los archivos del proyecto (`ClaimSchemaTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/data/claims/ClaimSchemaTest.kt::"el esquema de la versión 1 está exportado en el repositorio"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSchemaTest.kt::"la base declara exportSchema y el almacén no usa migraciones destructivas" |
| Status | PASS |

### TC-24 · Reporte listo, póliza vencida y reporte siempre alcanzable

| Field | Value |
|---|---|
| Covers | AC-18, AC-19, SR-13 |
| Kind | SECURITY |
| Risk covered | THR-13 |
| Level | UNIT |
| Preconditions | `ClaimReportViewModel` con el almacén y la copia de prueba |
| Procedure | 1. Completar un reporte cuya póliza vence antes de «Enviar». 2. Abrir un reporte listo e intentar editarlo. 3. Descartar el reporte listo. 4. Sin pólizas vigentes y con la copia descartada, abrir el reporte guardado y descartarlo |
| Expected result | En 1 la app pide otra póliza y el reporte sigue; en 2 no se puede editar; en 3 y 4 se borran el reporte y sus fotos |
| Tool or method | JUnit y coroutines-test (`ClaimReportViewModelTest`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-24 una póliza que dejó de estar vigente se pide de nuevo al enviar"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-24 un reporte listo se abre en el guardado y se puede descartar"; movil/app/src/test/java/com/solventa/app/domain/claims/ClaimRulesTest.kt::"TC-24 una póliza que ya no está vigente se pide de nuevo"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-24 un reporte listo no se edita ni recibe ni pierde fotos"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-24 un borrador con pólizas vigentes se descarta desde la evidencia, aun sin fotos"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-12 sin pólizas vigentes el detalle lo dice y deja descartar"; Galaxy S22 Ultra: el reporte listo se ve sin editar y se descarta con confirmación |
| Status | PASS |

### TC-25 · Teléfono bloqueado de verdad

| Field | Value |
|---|---|
| Covers | SR-09 |
| Kind | SECURITY |
| Risk covered | THR-09 |
| Level | INTEGRATION |
| Preconditions | Teléfono o emulador con bloqueo de pantalla y un reporte guardado |
| Procedure | 1. Lanzar la prueba instrumentada. 2. Bloquear el teléfono cuando la prueba lo pida. 3. Desbloquearlo |
| Expected result | Con el teléfono bloqueado la llave no se usa y el reporte queda no disponible sin borrarse; al desbloquear se vuelve a abrir |
| Tool or method | Prueba instrumentada con una persona que bloquea y desbloquea (`ClaimRealLockTest` en `androidTest`), como `RealLockTest` de HU-MOV-05 |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | movil/app/src/androidTest/java/com/solventa/app/data/claims/ClaimRealLockTest.kt::"con_el_telefono_bloqueado_el_reporte_no_esta_disponible_y_se_conserva"; Galaxy S22 Ultra con Android 16 y PIN, con David desbloqueando, 2026-11-05: 1 de 1 |
| Status | PASS |

## Security test coverage

<!-- Review every category. YES needs test cases; NO needs a rationale. -->

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | NO | — | Sin servidor en esta historia; el reporte solo se abre desde la billetera con la sesión de la cuenta |
| User without permissions | YES | TC-03 | Permisos de cámara o de ubicación precisa negados |
| Access to another user's resources | YES | TC-08 | Otra cuenta del mismo teléfono |
| Cross-tenant access | NO | — | Canal del asegurado, sin inquilinos |
| Malicious input | YES | TC-09 | Descripción vacía, larga, con emojis o saltos de línea; Room usa consultas con parámetros |
| Expired tokens | YES | TC-08 | La sesión vencida conserva el reporte cifrado hasta volver a entrar |
| Revoked sessions | YES | TC-08 | Teléfono desvinculado por el servidor o contraseña cambiada borran el reporte |
| Tampered data | YES | TC-07 | Archivo de foto alterado o cambiado |
| Wrong keys | YES | TC-07, TC-15, TC-21 | Llave de foto que no corresponde, llave invalidada y frase errada contra SQLCipher real |
| External service failures | YES | TC-04, TC-15 | Ubicación apagada o sin lectura; almacenamiento lleno |
| Race conditions | YES | TC-08, TC-09 | Cierre de sesión durante un guardado y doble toque del disparador |
| Information exposure | YES | TC-06, TC-13, TC-14, TC-21, TC-22 | Disco, copias, capturas, registros, estado guardado y ubicación fuera de la cámara |
| Secrets in code | YES | TC-18 | `scan secrets` sobre la rama |
| Insecure configuration | YES | TC-13, TC-19, TC-23 | `FLAG_SECURE` en release, permisos del manifiesto y migraciones sin destrucción |
| Vulnerable dependencies | YES | TC-18 | CameraX nueva |

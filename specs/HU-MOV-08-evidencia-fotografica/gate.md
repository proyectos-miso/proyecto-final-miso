# Definition of Done and security gate · HU-MOV-08

| Field | Value |
|---|---|
| Security status | RISK_ACCEPTED |
| Evaluated on | 2026-11-15 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-MOV-08-evidencia-fotografica |

<!-- Status: IN_PROGRESS, IMPLEMENTED, SECURITY_TESTS_PENDING, SECURITY_REVIEW_PENDING, RISK_ACCEPTED,
     SECURITY_VERIFIED or BLOCKED. The validator computes the status the evidence supports and rejects a
     higher declared one. An agent may prepare evidence; it never signs reviews or accepts risk.
     With "requireSignatures": false in sdd/config.json, the merge request approval is the security review
     and risk decisions recorded in the spec count as accepted; the two signature tables are optional. -->

## Definition of Done

- [x] Acceptance criteria implemented, each covered by a passing test case.
- [x] Security requirements mitigated by an applicable control and verified by a security test case.
- [x] No required control MISSING.
- [x] No secret in the code, logs, artifacts or this package.
- [x] New or updated dependencies evaluated.
- [x] Personal data handling reviewed (complete lifecycle, nothing UNKNOWN).
- [x] MEDIUM or higher residual risks mitigated or accepted by an authorized person.
- [x] Security review approved by a person other than the author.
- [x] Closeout: every acceptance criterion MET with evidence.
- [x] `python sdd/tools/sdd.py spec validate` reports no errors.

## Security reviews

| Reviewer | Role | Date | Scope | Result |
|---|---|---|---|---|

## Risk acceptance

| Risk | Decision | Approved by | Role | Date | Expires |
|---|---|---|---|---|---|
| RISK-01 | ACCEPT (DEC-04) | David Gutiérrez | Responsable de la historia | 2026-11-05 | 2026-12-31 |

## Evidence

| ID | Evidence | Supports | Location | Status |
|---|---|---|---|---|
| EV-01 | OSV.dev sin vulnerabilidades conocidas para CameraX 1.5.3 (core, camera2, lifecycle, view) y sus transitivas, SQLCipher 4.9.0 y Room 2.6.1, y requisitos del AAR de CameraX compatibles con compileSdk 35 y AGP 8.7.3, 2026-11-05 | SR-11, CTL-14 | security.md, sección 14 | AVAILABLE |
| EV-02 | Pruebas de la app en Robolectric: 352 de 352 | SR-01, SR-02, SR-03, SR-04, SR-06, SR-07, SR-08, SR-09, SR-10, SR-12, SR-13, CTL-01, CTL-02, CTL-03, CTL-04, CTL-05, CTL-06, CTL-07, CTL-08, CTL-09, CTL-10, CTL-11, CTL-12, CTL-13, CTL-15, CTL-16, TC-01, TC-02, TC-03, TC-04, TC-05, TC-06, TC-07, TC-08, TC-09, TC-10, TC-11, TC-12, TC-13, TC-14, TC-15, TC-19, TC-22, TC-23, TC-24 | movil/app/src/test/ | AVAILABLE |
| EV-03 | Pruebas de mutación: 41 de 41 mutantes detectados (tope de fotos, descripción en blanco, recorte a 500, huella, nonce, tamaño de la foto, imagen de 1920 px, borrados por cierre de sesión, contraseña, huella y teléfono desvinculado, sesión vencida, faltantes al enviar, doble disparo, ubicación solo con la cámara, precisión, ubicación simulada, hora independiente, reporte listo, frase de paso, ventana segura, vuelta de los ajustes, permisos del manifiesto, barra de progreso y descarte del borrador, más 16 de lo que lee TalkBack: títulos de pantalla, avisos vivos, filas en un nodo, coordenadas en palabras, obligatorios, contador, pasos, resumen y encabezados), 2026-11-05 | SR-01, SR-02, SR-04, SR-05, SR-07, SR-08, SR-10, SR-13, QR-02 | Ejecución local con `testDebugUnitTest` | AVAILABLE |
| EV-04 | Prueba instrumentada con SQLCipher y el Keystore reales: 5 de 5 en el Galaxy S22 Ultra con Android 16 | SR-03, CTL-04, TC-21 | movil/app/src/androidTest/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt | AVAILABLE |
| EV-05 | Teléfono (Galaxy S22 Ultra con Android 16), build con `-PsecureScreens=true` y revisión, 2026-11-05 | SR-02, SR-05, SR-06, SR-07, CTL-08, CTL-09, CTL-11, TC-13, TC-14, TC-16, TC-20, TC-22 | Resultado en cada caso de prueba | AVAILABLE |
| EV-06 | `sdd.py scan deps --require-evaluation` (4 dependencias evaluadas) y `sdd.py scan secrets` sobre el diff y los archivos nuevos (0 secretos), 2026-11-05 | SR-11, CTL-14, TC-18 | Resultado en TC-18 | AVAILABLE |
| EV-07 | Prueba con TalkBack y la fuente al máximo, con David en el Galaxy S22 Ultra, 2026-11-05 | QR-02, TC-17 | Resultado en TC-17 y movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt | AVAILABLE |
| EV-08 | Prueba del teléfono bloqueado de verdad, con David desbloqueando, 2026-11-05: 1 de 1 | SR-09, CTL-10, TC-25 | movil/app/src/androidTest/java/com/solventa/app/data/claims/ClaimRealLockTest.kt | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | TC-01: movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-01 la foto se guarda con la ubicación, la hora del teléfono y la independiente"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-01 guarda la foto con su ubicación, precisión y horas"; TC-20: Galaxy S22 Ultra con Android 16, 2026-11-05 (EV-05) |
| AC-02 | MET | TC-02: movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-02 la evidencia sale solo de la cámara de la app, sin galería ni otras apps"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimPrivacySourceTest.kt::"el reporte no abre la galería ni otra app de cámara ni escribe la foto en claro"; TC-20: Galaxy S22 Ultra con Android 16, 2026-11-05 (EV-05) |
| AC-03 | MET | TC-03: movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-03 el permiso negado explica los pasos y abre los ajustes de la app"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-03 si revocan un permiso a mitad del reporte se vuelve a pedir y el reporte sigue"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-03 los permisos que faltan se explican y con todos se abre la cámara" |
| AC-04 | MET | TC-09: movil/app/src/test/java/com/solventa/app/domain/claims/ClaimRulesTest.kt::"con una foto, póliza vigente y descripción no falta nada"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-09 enviar sin descripción o sin póliza dice qué falta y no lo deja listo"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-09 el detalle no avisa antes de llenar y al tocar Enviar dice qué falta" |
| AC-05 | MET | TC-06: movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-06 el archivo de la foto está cifrado y no hay copias en claro"; movil/app/src/test/java/com/solventa/app/data/DataExtractionRulesTest.kt::"el manifiesto niega las copias y declara las reglas de extraccion"; TC-20: Galaxy S22 Ultra con Android 16, 2026-11-05 (EV-05) |
| AC-06 | MET | TC-12: movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-12 abre un borrador con solo las pólizas vigentes de la copia"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-12 la billetera ofrece reportar, retomar o ver el de la cola, y nada sin pólizas ni reporte"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"HU-MOV-08 TC-12 con una póliza vigente ofrece reportar un siniestro" |
| AC-07 | MET | TC-03: movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-03 el permiso negado explica los pasos y abre los ajustes de la app"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-03 los permisos que faltan se explican y con todos se abre la cámara" |
| AC-08 | MET | TC-04: movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-04 sin ubicación no hay foto y se dice cómo resolverlo"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-04 mientras busca la ubicación cuenta los segundos y no deja disparar otra vez" |
| AC-09 | MET | TC-07: movil/app/src/test/java/com/solventa/app/data/claims/PhotoCipherTest.kt::"la huella es el SHA-256 en hexadecimal"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-07 la huella es el SHA-256 de los bytes JPEG y la imagen vuelve igual" |
| AC-10 | MET | TC-01: movil/app/src/test/java/com/solventa/app/data/claims/AndroidTrustedClockTest.kt::"prefiere la hora del GNSS"; movil/app/src/test/java/com/solventa/app/data/claims/AndroidTrustedClockTest.kt::"sin GNSS usa la hora de red de Android"; TC-05: movil/app/src/test/java/com/solventa/app/data/claims/AndroidCaptureLocatorTest.kt::"TC-05 una ubicación simulada queda marcada" |
| AC-11 | MET | TC-08: movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-08 cada cuenta tiene su reporte, su frase y su llave"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"HU-MOV-08 TC-08 la sesion vencida y el nuevo ingreso conservan el reporte"; TC-11: movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-11 al volver a abrir encuentra las fotos, la póliza y la descripción" |
| AC-12 | MET | TC-08: movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"HU-MOV-08 TC-08 cerrar sesion borra el reporte de siniestro de la cuenta"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"HU-MOV-08 TC-08 cambiar la contrasena borra el reporte de esa cuenta"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"HU-MOV-08 TC-08 solo el telefono desvinculado borra el reporte de siniestro" |
| AC-13 | MET | TC-10: movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-10 borrar la segunda foto borra su archivo y deja 2 en orden"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-10 descartar borra la base, las fotos, la frase y la llave" |
| AC-14 | MET | TC-09: movil/app/src/test/java/com/solventa/app/domain/claims/ClaimRulesTest.kt::"solo espacios cuenta como sin descripción"; movil/app/src/test/java/com/solventa/app/domain/claims/ClaimRulesTest.kt::"la descripción se recorta en 500 caracteres y con 500 queda igual"; movil/app/src/test/java/com/solventa/app/data/claims/EvidenceEncoderTest.kt::"TC-09 baja la calidad hasta que cabe en el límite"; movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-09 no entra una sexta foto ni una de más de 2 MB" |
| AC-15 | MET | TC-11: movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-11 enviar con todo deja el reporte en cola, agenda el envío y lo muestra". Desde HU-MOV-09 el reporte listo queda en cola de envío (DEC-02) |
| AC-16 | MET | TC-13: movil/app/src/test/java/com/solventa/app/ui/claims/ClaimSecureWindowTest.kt::"TC-13 la ventana es segura solo mientras se muestra el reporte, y nunca si está apagada"; Galaxy S22 Ultra con `-PsecureScreens=true`, 2026-11-05 (EV-05) |
| AC-17 | MET | TC-03: movil/app/src/test/java/com/solventa/app/data/claims/AndroidCaptureLocatorTest.kt::"TC-03 el permiso se lee como preciso, solo aproximado o negado"; movil/app/src/test/java/com/solventa/app/data/claims/AndroidCaptureLocatorTest.kt::"TC-03 sin ubicación precisa no hay foto y no se pide la ubicación" |
| AC-18 | MET | TC-24: movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-24 una póliza que dejó de estar vigente se pide de nuevo al enviar"; movil/app/src/test/java/com/solventa/app/domain/claims/ClaimRulesTest.kt::"TC-24 una póliza que ya no está vigente se pide de nuevo" |
| AC-19 | MET | TC-24: movil/app/src/test/java/com/solventa/app/data/claims/EncryptedClaimStoreTest.kt::"TC-24 un reporte en cola no se edita ni recibe ni pierde fotos"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-24 un reporte en cola se abre en su estado y se puede descartar". Desde HU-MOV-09 el reporte listo es el que está en cola |
| AC-20 | MET | TC-22: movil/app/src/test/java/com/solventa/app/data/claims/AndroidCaptureLocatorTest.kt::"TC-22 solo escucha la ubicación entre start y stop"; movil/app/src/test/java/com/solventa/app/data/claims/AndroidCaptureLocatorTest.kt::"TC-22 al detenerse olvida la última ubicación" |
| QR-01 | MET | TC-16: Galaxy S22 Ultra con Android 16, 2026-11-05: 20 de 20 por debajo de 2 s, mediana 0,78 s y máximo 1,31 s (EV-05) |
| QR-02 | MET | TC-17: movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-17 cada pantalla del reporte anuncia su título"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"TC-17 sin fotos, Continuar anuncia lo que falta en un nodo vivo que ya estaba"; TalkBack y la fuente al máximo en el Galaxy S22 Ultra, 2026-11-05 (EV-07) |

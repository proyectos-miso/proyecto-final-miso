# Definition of Done and security gate · HU-MOV-08

| Field | Value |
|---|---|
| Security status | SECURITY_REVIEW_PENDING |
| Evaluated on | 2026-11-05 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-MOV-08-evidencia-fotografica |

<!-- Status: IN_PROGRESS, IMPLEMENTED, SECURITY_TESTS_PENDING, SECURITY_REVIEW_PENDING, RISK_ACCEPTED,
     SECURITY_VERIFIED or BLOCKED. The validator computes the status the evidence supports and rejects a
     higher declared one. An agent may prepare evidence; it never signs reviews or accepts risk.
     With "requireSignatures": false in sdd/config.json, the merge request approval is the security review
     and risk decisions recorded in the spec count as accepted; the two signature tables are optional. -->

## Definition of Done

- [ ] Acceptance criteria implemented, each covered by a passing test case.
- [ ] Security requirements mitigated by an applicable control and verified by a security test case.
- [ ] No required control MISSING.
- [ ] No secret in the code, logs, artifacts or this package.
- [ ] New or updated dependencies evaluated.
- [ ] Personal data handling reviewed (complete lifecycle, nothing UNKNOWN).
- [ ] MEDIUM or higher residual risks mitigated or accepted by an authorized person.
- [ ] Security review approved by a person other than the author.
- [ ] Closeout: every acceptance criterion MET with evidence.
- [ ] `python sdd/tools/sdd.py spec validate` reports no errors.

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

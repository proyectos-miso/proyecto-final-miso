# Definition of Done and security gate · HU-MOV-06

| Field | Value |
|---|---|
| Security status | SECURITY_VERIFIED |
| Evaluated on | 2026-10-24 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-MOV-06-ultima-sincronizacion |

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

## Evidence

| ID | Evidence | Supports | Location | Status |
|---|---|---|---|---|
| EV-01 | Pruebas del ViewModel y de la pantalla de la billetera | SR-02, CTL-02, TC-01, TC-02, TC-03 | movil/app/src/test/java/com/solventa/app/ui/wallet/ | AVAILABLE |
| EV-02 | Prueba instrumentada del almacén con la hora y la base alterada | SR-01, CTL-01, TC-04, TC-05 | movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | TC-01: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"TC-01 muestra la ultima sincronizacion con fecha y hora"; movil/app/src/test/java/com/solventa/app/ui/wallet/MoneyTest.kt::"TC-01 la ultima sincronizacion usa fecha y hora en la zona del telefono" |
| AC-02 | MET | TC-02: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-02 una copia de mas de 24 horas se marca desactualizada"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"TC-02 una copia desactualizada muestra el aviso, anunciado por TalkBack" |
| AC-03 | MET | TC-01: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-01 una sincronizacion exitosa guarda y muestra la hora actual y quita el aviso" |
| AC-04 | MET | TC-04: movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::cada_cuenta_recupera_su_hora_de_sincronizacion_y_se_borra_con_la_copia |
| AC-05 | MET | TC-02: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-02 con el servidor caido se conserva la hora de la ultima sincronizacion exitosa" |
| AC-06 | MET | TC-03: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-03 una hora guardada en el futuro por un reloj atrasado se marca desactualizada" |
| AC-07 | MET | TC-02: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-02 con exactamente 24 horas todavia no hay aviso" |
| AC-08 | MET | TC-07: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-07 al volver a la app despues de 25 horas aparece el aviso sin cambiar la fecha mostrada"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-07 al volver a la app una poliza que vencio se marca vencida" |
| QR-01 | MET | TC-06: prueba manual de Juan Camilo Peña en su celular, 2026-10-25: fecha visible, aviso que aparece y se quita, y TalkBack que lo anuncia |
| Pruebas y calidad | MET | CI del #213 en verde (https://github.com/proyectos-miso/proyecto-final-miso/actions/runs/40247855340); SonarCloud 85,2 % de cobertura en el código nuevo, sin bugs ni vulnerabilidades; aprobado por bendeckdavid el 2026-10-25 |

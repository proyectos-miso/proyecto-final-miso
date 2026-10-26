# Definition of Done and security gate · HU-MOV-05

| Field | Value |
|---|---|
| Security status | SECURITY_VERIFIED |
| Evaluated on | 2026-10-25 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-MOV-05-copia-cifrada |

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
| EV-01 | Pruebas unitarias | SR-01, SR-02, SR-03, SR-04, SR-05, SR-07, CTL-01, CTL-02, CTL-03, CTL-05, CTL-07, CTL-09, CTL-10, TC-01, TC-02, TC-04, TC-05, TC-07, TC-08, TC-12 | movil/app/src/test/java/com/solventa/app/ | AVAILABLE |
| EV-02 | Pruebas instrumentadas en el teléfono | SR-01, SR-02, SR-04, SR-06, SR-07, CTL-01, CTL-02, CTL-04, CTL-05, CTL-08, CTL-09, CTL-10, TC-01, TC-02, TC-03, TC-04, TC-05, TC-10, TC-11, TC-12 | movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt | AVAILABLE |
| EV-03 | Pruebas manuales: bloqueo real, transferencia, accesibilidad y revisión de código | SR-03, SR-04, SR-05, CTL-03, CTL-05, CTL-06, CTL-07, TC-06, TC-09, TC-13, TC-14 | Resultado registrado en cada caso de prueba | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | TC-01: movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::el_archivo_no_es_sqlite_legible_ni_contiene_el_numero_de_poliza |
| AC-02 | MET | TC-01: movil/app/src/test/java/com/solventa/app/data/policies/PassphraseVaultTest.kt::"crea una frase aleatoria de 32 bytes por cuenta y solo guarda su version envuelta"; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::el_archivo_no_abre_con_otra_frase_y_la_llave_no_se_puede_exportar |
| AC-03 | MET | TC-01: movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::el_archivo_no_es_sqlite_legible_ni_contiene_el_numero_de_poliza; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::el_archivo_no_abre_con_otra_frase_y_la_llave_no_se_puede_exportar |
| AC-04 | MET | TC-02: movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::sin_la_llave_se_descarta_hasta_el_siguiente_guardado |
| AC-05 | MET | TC-03: movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::una_base_alterada_se_reemplaza_al_guardar |
| AC-06 | MET | TC-04: movil/app/src/test/java/com/solventa/app/data/policies/PassphraseVaultTest.kt::"una frase guardada con Base64, IV o texto cifrado invalidos es invalida"; TC-05: movil/app/src/test/java/com/solventa/app/data/policies/KeystoreWalletKeysTest.kt::"con el telefono bloqueado antes o despues del fallo la llave no esta disponible" |
| AC-07 | MET | TC-05: movil/app/src/test/java/com/solventa/app/data/policies/KeystoreWalletKeysTest.kt::"con el telefono bloqueado antes o despues del fallo la llave no esta disponible"; TC-06: movil/app/src/androidTest/java/com/solventa/app/data/policies/RealLockTest.kt::con_el_telefono_bloqueado_la_copia_no_esta_disponible_y_se_conserva |
| AC-08 | MET | TC-02: movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::sin_la_llave_se_descarta_hasta_el_siguiente_guardado; TC-03: movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::una_base_alterada_se_reemplaza_al_guardar; TC-05: movil/app/src/test/java/com/solventa/app/data/policies/KeystoreWalletKeysTest.kt::"con el telefono bloqueado antes o despues del fallo la llave no esta disponible"; TC-07: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"sin red o con el servidor caido y la copia descartada pide conexion con su aviso" |
| AC-09 | MET | TC-08: movil/app/src/test/java/com/solventa/app/data/DataExtractionRulesTest.kt::"el manifiesto niega las copias y declara las reglas de extraccion"; TC-09: 2026-10-25, Galaxy S22 Ultra con Android 16: antes de las reglas viajaron los cinco archivos de prueba de `root`, `file`, `database`, `sharedpref` y `device_file` |
| AC-10 | MET | TC-10: movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::una_base_de_otra_cuenta_no_abre_y_solo_se_descarta_esa |
| AC-11 | MET | TC-11: movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::cancelar_lecturas_y_guardados_no_borra_ni_deja_la_base_con_ceros |
| AC-12 | MET | TC-12: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"cerrar la billetera antes de que responda el servidor no guarda la copia" |
| AC-13 | MET | TC-05: movil/app/src/test/java/com/solventa/app/data/policies/KeystoreWalletKeysTest.kt::"con el telefono bloqueado antes o despues del fallo la llave no esta disponible"; TC-07: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"sin red o con el servidor caido y la copia descartada pide conexion con su aviso" |
| QR-01 | MET | TC-13: prueba manual de David Gutiérrez con TalkBack y la fuente en 2.0, 2026-10-25; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"la copia no disponible muestra su aviso y reintenta con el boton" |
| Pruebas y calidad | MET | CI del #216 en verde (https://github.com/proyectos-miso/proyecto-final-miso/actions/runs/40248810480); SonarCloud 94 % de cobertura en el código nuevo, sin bugs ni vulnerabilidades; aprobado por oscarsaraza el 2026-10-25 |

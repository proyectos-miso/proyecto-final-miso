# Definition of Done and security gate · HU-MOV-04

| Field | Value |
|---|---|
| Security status | RISK_ACCEPTED |
| Evaluated on | 2026-10-23 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-MOV-04-polizas-sin-conexion |

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
| RISK-01 | ACCEPT | Juan Camilo Peña | Owner | 2026-10-23 | — |
| RISK-02 | ACCEPT | Juan Camilo Peña | Owner | 2026-10-23 | — |
| RISK-03 | ACCEPT | Juan Camilo Peña | Owner | 2026-10-23 | — |

## Evidence

| ID | Evidence | Supports | Location | Status |
|---|---|---|---|---|
| EV-01 | Pruebas del endpoint de pólizas del asegurado | SR-01, SR-02, CTL-01, CTL-02, TC-01, TC-02, TC-03 | backend/tests/integration/test_insured_policies_api.py | AVAILABLE |
| EV-02 | Pruebas de la billetera, del almacén y del borrado | SR-02, SR-04, CTL-02, CTL-04, TC-03, TC-04, TC-05, TC-07 | movil/app/src/test/java/com/solventa/app/ui/wallet/ | AVAILABLE |
| EV-03 | Prueba instrumentada del cifrado y prueba manual de la entrada con huella en el celular | SR-03, SR-05, CTL-03, CTL-05, TC-06, TC-08 | movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt; test-cases.md, TC-08 | AVAILABLE |
| EV-04 | Revisión de accesibilidad con TalkBack y Accessibility Scanner | QR-01, TC-09 | https://github.com/proyectos-miso/proyecto-final-miso/pull/211; test-cases.md, TC-09 | AVAILABLE |
| EV-05 | Consulta en OSV.dev de las dependencias nuevas | SR-03, TC-10 | security.md, sección 14 | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | TC-04: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-04 con red muestra las polizas del servidor y guarda la copia de la cuenta" |
| AC-02 | MET | TC-05: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-05 sin red y con copia muestra la copia con el aviso de sin conexion" |
| AC-03 | MET | TC-05: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"TC-05 sin red y sin copia pide conexion para la primera descarga" |
| AC-04 | MET | TC-07: movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"TC-07 cerrar sesion borra la copia de la cuenta aunque el servidor no responda" |
| AC-05 | MET | TC-02: backend/tests/integration/test_insured_policies_api.py::test_tc_02_ignora_identificadores_del_cliente_y_no_devuelve_polizas_ajenas; backend/tests/integration/test_insured_policies_api.py::test_tc_02_sin_sesion_valida_responde_401 |
| AC-06 | MET | TC-07: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-07 un 401 borra la copia de la cuenta y marca la sesion vencida"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"TC-07 sesion vencida borra la copia, cierra la sesion y pide ingresar" |
| AC-07 | MET | TC-05: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-05 sin red y con copia muestra la copia con el aviso de sin conexion" |
| AC-08 | MET | TC-03: backend/tests/integration/test_insured_policies_api.py::test_tc_03_la_tarjeta_no_incluye_documento_beneficiarios_ni_prima; movil/app/src/test/java/com/solventa/app/data/policies/HttpPoliciesApiTest.kt::"TC-03 pide las polizas con el token y guarda solo los campos de la tarjeta" |
| AC-09 | MET | TC-07: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-07 cada cuenta ve solo su copia" |
| AC-10 | MET | TC-01: backend/tests/integration/test_insured_policies_api.py::test_tc_01_devuelve_solo_las_polizas_activas_y_vigentes_del_titular; TC-11: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"TC-11 el carne y la lista muestran la suma asegurada en COP" |
| AC-11 | MET | TC-06: movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::el_archivo_no_es_sqlite_legible_ni_contiene_el_numero_de_poliza; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::sin_la_llave_de_la_cuenta_la_copia_no_abre_y_se_borra |
| AC-12 | MET | TC-08: prueba manual de Juan Camilo Peña en su celular con huella real, 2026-10-24 |
| AC-13 | MET | TC-04: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-04 sin polizas vigentes lo indica y la copia queda vacia" |
| AC-14 | MET | TC-05: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"TC-05 una poliza de la copia que vencio sin red aparece como vencida" |
| AC-15 | MET | TC-11: movil/app/src/test/java/com/solventa/app/ui/wallet/MoneyTest.kt::"TC-11 muestra pesos sin centavos y con el codigo COP"; web/src/app/core/money.spec.ts |
| AC-16 | MET | TC-12: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"TC-12 tocar el carne muestra las coberturas y tocarlo otra vez vuelve al frente"; movil/app/src/test/java/com/solventa/app/ui/theme/BrandTopBarTest.kt::"TC-12 la barra muestra la marca y alterna entre tema claro y oscuro"; capturas comparadas con el mockup el 2026-10-24 |
| QR-01 | MET | TC-09: prueba manual de Juan Camilo Peña con TalkBack y la fuente al máximo, en tema claro y oscuro, 2026-10-24 |
| Pruebas y calidad | MET | CI del #211 en verde (https://github.com/proyectos-miso/proyecto-final-miso/actions/runs/40246565780); SonarCloud 87,9 % de cobertura en el código nuevo, sin bugs ni vulnerabilidades; aprobado por bendeckdavid y oscarsaraza el 2026-10-25; RISK-01 a RISK-03 aceptados en el spec |

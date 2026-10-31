# Definition of Done and security gate · HU-MOV-07

| Field | Value |
|---|---|
| Security status | RISK_ACCEPTED |
| Evaluated on | 2026-10-25 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-MOV-07-sincronizacion-automatica |

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
| RISK-01 | ACCEPT (DEC-01) | David Gutiérrez | Responsable de la historia | 2026-10-25 | 2026-12-31 |
| RISK-04 | ACCEPT (DEC-01, DEC-07) | David Gutiérrez | Responsable de la historia | 2026-10-25 | 2026-12-31 |

## Evidence

| ID | Evidence | Supports | Location | Status |
|---|---|---|---|---|
| EV-01 | Pruebas de la app en Robolectric: 243 de 243 | SR-01, SR-02, SR-03, SR-04, SR-05, SR-06, SR-07, SR-08, SR-09, CTL-01, CTL-02, CTL-03, CTL-04, CTL-05, CTL-06, CTL-07, CTL-08, CTL-09, TC-01, TC-02, TC-03, TC-04, TC-05, TC-06, TC-07, TC-08, TC-09, TC-10, TC-11, TC-13, TC-19 | movil/app/src/test/java/com/solventa/app/ | AVAILABLE |
| EV-02 | Pruebas del backend con PostgreSQL: 204 de 204 | SR-10, CTL-10, TC-17 | backend/tests/ | AVAILABLE |
| EV-03 | Teléfono (Galaxy S22 Ultra con Android 16), build de release y revisión, 2026-10-25 | SR-02, SR-11, CTL-02, CTL-11, TC-12, TC-14, TC-15, TC-18 | Resultado en cada caso de prueba | AVAILABLE |
| EV-04 | Prueba con TalkBack del foco al actualizarse, con David en el teléfono, 2026-10-25 | TC-16 | Resultado en TC-16 y WalletScreenTest.kt | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | TC-01: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"al sincronizar en segundo plano la billetera muestra las polizas sin el aviso"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"al sincronizar en segundo plano una billetera que pedia conexion muestra las polizas"; TC-03: movil/app/src/test/java/com/solventa/app/data/policies/PolicySyncWorkerTest.kt::"un solo trabajo que espera la red y luego sincroniza" |
| AC-02 | MET | TC-02: movil/app/src/test/java/com/solventa/app/data/policies/EncryptedPolicyStoreFlowTest.kt::"reemplaza sin duplicar, quita repetidas e informa si escribio" |
| AC-03 | MET | TC-03: movil/app/src/test/java/com/solventa/app/data/policies/PolicySyncWorkerTest.kt::"el trabajo exige red y reintenta con espera exponencial"; movil/app/src/test/java/com/solventa/app/data/policies/PolicySyncWorkerTest.kt::"una falla o una excepcion se reintentan y lo demas termina"; TC-19: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"al rotar o volver a la app sigue la copia mientras otra sincronizacion ocupa el turno" |
| AC-04 | MET | TC-01: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"al sincronizar en segundo plano la billetera muestra las polizas sin el aviso" |
| AC-05 | MET | TC-04: movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"un 401 renueva una vez y repite con el token nuevo"; movil/app/src/test/java/com/solventa/app/data/session/SecureSessionRepositoryTest.kt::"la sesion solo cambia desde la instantanea vigente" |
| AC-06 | MET | TC-05: movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"la renovacion rechazada borra la copia y termina la sesion"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"un segundo 401 despues de renovar borra la copia"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"la credencial rechazada desvincula la huella siempre"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"tras una renovacion sin respuesta el 401 siguiente conserva la copia" |
| AC-07 | MET | TC-06: movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"el ingreso sin red guarda la credencial solo en memoria y la suelta a los 15 minutos"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"cerrar sesion o desactivar la huella sueltan la credencial"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"una llave invalidada o no disponible no usa la credencial"; TC-07: movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"la credencial vencida no se usa y con red pide el ingreso sin borrar" |
| AC-08 | MET | TC-08: movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"sin sesion no llama al servidor ni escribe" |
| AC-09 | MET | TC-09: movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"cerrar sesion durante la descarga del trabajo no deja copia"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"el borrado y la revocacion terminan aunque la pantalla se cierre"; movil/app/src/test/java/com/solventa/app/MainActivityTest.kt::"cerrar sesion con la descarga pendiente no deja copia de la cuenta" |
| AC-10 | MET | TC-01: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"si la copia no se pudo guardar la billetera conserva la fecha anterior"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"si la copia no se guarda responde Failed y no publica" |
| AC-11 | MET | TC-03: movil/app/src/test/java/com/solventa/app/data/policies/PolicySyncWorkerTest.kt::"un solo trabajo que espera la red y luego sincroniza"; TC-04: movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"dos sincronizaciones a la vez van en orden con una sola renovacion" |
| AC-12 | MET | TC-02: movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt::una_poliza_repetida_se_guarda_una_vez_y_save_informa_que_escribio |
| AC-13 | MET | TC-19: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"al volver a la app sin conexion sincroniza a lo sumo cada 30 s y con conexion no llama" |
| AC-14 | MET | TC-17: backend/tests/unit/test_insured_logout.py::test_refresh_con_ingreso_suspendido_responde_bloqueo_sin_rotar; backend/tests/integration/test_insured_auth_errors.py::test_ingreso_suspendido_responde_423_con_tiempo_restante; TC-05: movil/app/src/test/java/com/solventa/app/data/auth/HttpAuthApiTest.kt::"renovar traduce cada respuesta del servidor"; movil/app/src/test/java/com/solventa/app/data/policies/SessionPolicySyncTest.kt::"las fallas pasajeras se reintentan sin borrar ni cerrar" |
| AC-15 | MET | TC-08: movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"al terminar la actividad termina y revoca solo su sesion"; movil/app/src/test/java/com/solventa/app/data/session/SecureSessionRepositoryTest.kt::"endSession termina solo su sesion y devuelve sus tokens vigentes" |
| AC-16 | MET | TC-18: movil/app/src/test/java/com/solventa/app/data/NetworkSecurityConfigTest.kt::"la configuracion de release no permite HTTP sin TLS a ningun host"; assembleRelease sin URL `https` falla, 2026-10-25 |
| AC-17 | MET | TC-19: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"al rotar o volver a la app sigue la copia mientras otra sincronizacion ocupa el turno" |
| QR-01 | MET | TC-12: prueba manual de David Gutiérrez en el Galaxy S22 Ultra con Android 16, 2026-10-25: 5 de 5 actualizaciones en primer plano dentro de 60 s, entre 2,3 s y 8,0 s |
| QR-02 | MET | TC-03: movil/app/src/test/java/com/solventa/app/data/policies/PolicySyncWorkerTest.kt::"el trabajo exige red y reintenta con espera exponencial"; TC-14: en el teléfono, espera EXPONENTIAL de 10 000 ms, 2026-10-25 |
| QR-03 | MET | TC-16: prueba manual de David Gutiérrez con TalkBack y la fuente en 2.0, 2026-10-25; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"TC-16 al sincronizar el carne, la poliza y la fecha conservan su nodo para TalkBack" |
| Pruebas y calidad | MET | CI del #217 en verde (https://github.com/proyectos-miso/proyecto-final-miso/actions/runs/40251393388); SonarCloud 93,7 % de cobertura en el código nuevo, sin bugs ni vulnerabilidades; aprobado por jc-pena-p el 2026-10-27 |

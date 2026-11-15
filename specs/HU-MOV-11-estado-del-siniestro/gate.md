# Definition of Done and security gate · HU-MOV-11

| Field | Value |
|---|---|
| Security status | SECURITY_VERIFIED |
| Evaluated on | 2026-11-11 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-MOV-11-estado-del-siniestro |

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
- [ ] Security review approved by a person other than the author.
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
| EV-01 | Pruebas de integración del backend (13) y suite completa (390) | SR-01, SR-02, SR-04, SR-05, SR-07, SR-08, TC-01, TC-05 | backend/tests/integration/test_insured_claims_api.py | AVAILABLE |
| EV-02 | Pruebas unitarias de la app | SR-03, TC-12, TC-13 | movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt | AVAILABLE |
| EV-03 | Llave de Firebase fuera del repositorio | SR-06, CTL-06, TC-14 | .gitignore | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | backend/tests/integration/test_insured_claims_api.py::test_lista_solo_los_siniestros_propios_del_mas_reciente_al_mas_antiguo; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsListScreenTest.kt::"AC-01 lista los siniestros con su estado y abre el elegido" |
| AC-02 | MET | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsListScreenTest.kt::"AC-02 el detalle muestra la linea de tiempo con fecha y explicacion"; movil/app/src/test/java/com/solventa/app/SolventaMobileRootClaimsTest.kt::"AC-01 y AC-02 desde la billetera se abre la lista, el detalle y se vuelve" |
| AC-03 | MET | backend/tests/integration/test_insured_claims_api.py::test_qr_01_p95_bajo_150_ms_con_10000_siniestros |
| AC-04 | MET | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsListScreenTest.kt::"AC-04 sin conexion muestra el aviso y la ultima actualizacion"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsViewModelTest.kt::"AC-04 sin conexion muestra la copia con la fecha de la ultima actualizacion"; prueba manual TC-16 (2026-11-11) |
| AC-05 | MET | backend/tests/integration/test_insured_claims_api.py::test_lista_solo_los_siniestros_propios_del_mas_reciente_al_mas_antiguo; backend/tests/integration/test_insured_claims_api.py::test_caratula_de_otra_persona_responde_404; backend/tests/integration/test_insured_claims_api.py::test_renovar_poliza_de_otra_persona_responde_404 |
| AC-06 | MET | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsListScreenTest.kt::"AC-06 sin siniestros lo indica y ofrece reportar uno"; backend/tests/integration/test_insured_claims_api.py::test_sin_siniestros_devuelve_lista_vacia |
| AC-07 | MET | backend/tests/integration/test_insured_claims_api.py::test_rechazado_trae_motivo_y_canal_de_revision; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsListScreenTest.kt::"AC-07 un rechazo muestra el motivo y como pedir revision" |
| AC-08 | MET | backend/tests/integration/test_insured_claims_api.py::test_registra_token_y_envia_push_sin_datos_sensibles; movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt::"AC-08 un cambio de estado avisa una vez y abre ese siniestro"; movil/app/src/test/java/com/solventa/app/SolventaMobileRootClaimsTest.kt::"AC-08 un aviso tocado abre el detalle de su siniestro"; prueba manual TC-16 (push real de Firebase) |
| AC-09 | MET | backend/tests/integration/test_insured_claims_api.py::test_aviso_de_vencimiento_se_envia_una_sola_vez; movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt::"AC-09 el aviso de vencimiento sale una sola vez por poliza y vigencia"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletCardActionsTest.kt::"AC-09 una poliza por vencer avisa la fecha y renueva" |
| AC-10 | MET | backend/tests/integration/test_insured_claims_api.py::test_caratula_propia_entrega_el_enlace_sin_cache; movil/app/src/test/java/com/solventa/app/SolventaMobileRootClaimsTest.kt::"AC-10 la caratula lista se descarga en Descargas"; prueba manual TC-16 (descarga por túnel) |
| AC-11 | MET | movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt::"AC-11 cerrar sesion borra los siniestros con la copia"; movil/app/src/test/java/com/solventa/app/SolventaMobileRootClaimsTest.kt::"AC-11 cerrar sesion borra los avisos y cierra Mis siniestros" |
| AC-12 | MET | backend/tests/integration/test_insured_claims_api.py::test_renovacion_solo_dentro_de_los_30_dias; backend/tests/integration/test_policy_renewal_api.py::test_el_analista_renueva_una_poliza_por_vencer_y_queda_en_la_auditoria; web/src/app/features/gestion/policy-operations.component.spec.ts::"HU-MOV-11 renueva por un año una póliza a 30 días o menos de vencer" |
| AC-13 | MET | backend/tests/integration/test_insured_claims_api.py::test_vencida_pasa_a_expired_y_al_renovar_vuelve_a_emitida; backend/tests/unit/test_notifications.py::test_la_tarea_diaria_vence_y_avisa_con_una_sesion |
| QR-01 | MET | backend/tests/integration/test_insured_claims_api.py::test_qr_01_p95_bajo_150_ms_con_10000_siniestros |
| QR-02 | MET | Prueba manual TC-16 con TalkBack (2026-11-11), en specs/HU-MOV-11-estado-del-siniestro/test-cases.md |

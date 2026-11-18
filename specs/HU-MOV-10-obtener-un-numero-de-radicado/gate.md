# Definition of Done and security gate · HU-MOV-10

| Field | Value |
|---|---|
| Security status | SECURITY_VERIFIED |
| Evaluated on | 2026-11-15 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-MOV-10-obtener-un-numero-de-radicado |

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
| EV-01 | Pruebas del oráculo, el outbox, las migraciones y el radicado | SR-01, SR-02, SR-03, SR-04, SR-05, CTL-01, CTL-02, CTL-03, CTL-04, CTL-05, TC-01, TC-06, TC-07, TC-09 | backend/tests/integration/test_oracle_events_api.py; backend/tests/integration/test_outbox_publisher.py | AVAILABLE |
| EV-02 | Pruebas de la constancia en la app | SR-06, CTL-06, TC-02, TC-03, TC-12 | movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | TC-03: movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"HU-MOV-10 AC-01 recibido guarda la constancia oficial que reemplaza al provisional"; movil/app/src/test/java/com/solventa/app/data/claims/HttpClaimSubmitApiTest.kt::"HU-MOV-10 AC-01 lee la hora oficial del registro con su zona y la tolera ausente"; TC-13: movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"HU-MOV-10 AC-01 la billetera muestra la constancia oficial, abre el siniestro y se cierra"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"HU-MOV-10 AC-01 muestra la constancia oficial hasta que el asegurado la cierra" |
| AC-02 | MET | TC-02: movil/app/src/test/java/com/solventa/app/domain/claims/ProvisionalNumberTest.kt::"HU-MOV-10 AC-02 el mismo reporte da siempre el mismo provisional y otro reporte da otro"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsListScreenTest.kt::"HU-MOV-10 AC-02 el pendiente muestra el radicado provisional"; TC-03: movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"HU-MOV-10 AC-02 en cola muestra la constancia provisional y al recibirse la oficial que la reemplaza" |
| AC-03 | MET | TC-01: backend/tests/integration/test_claim_report_api.py::test_reportes_distintos_enviados_al_mismo_tiempo_reciben_radicados_distintos; backend/tests/integration/test_claim_report_api.py::test_dos_envios_simultaneos_del_mismo_reporte_dejan_un_siniestro |
| AC-04 | MET | TC-04: movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsListScreenTest.kt::"HU-MOV-10 AC-04 el siniestro recibido muestra el oficial y el provisional que reemplaza" |
| AC-05 | MET | TC-05: backend/tests/integration/test_oracle_events_api.py::test_un_sismo_que_cumple_la_regla_crea_y_liquida_un_siniestro_por_poliza_de_la_ciudad; TC-08: backend/tests/integration/test_oracle_events_api.py::test_un_sismo_bajo_el_umbral_no_crea_siniestros |
| AC-06 | MET | TC-06: backend/tests/integration/test_oracle_events_api.py::test_un_evento_sin_firma_valida_o_viejo_se_rechaza; backend/tests/integration/test_oracle_events_api.py::test_un_cuerpo_cambiado_despues_de_firmar_se_rechaza; TC-07: backend/tests/integration/test_oracle_events_api.py::test_el_mismo_evento_no_crea_siniestros_otra_vez; backend/tests/integration/test_oracle_events_api.py::test_dos_entregas_simultaneas_del_mismo_evento_dejan_un_juego_de_siniestros |
| AC-07 | MET | TC-09: backend/tests/integration/test_outbox_publisher.py::test_publica_los_pendientes_y_los_marca; backend/tests/integration/test_outbox_publisher.py::test_si_sqs_falla_el_mensaje_queda_pendiente_y_se_reintenta; backend/tests/integration/test_outbox_publisher.py::test_llega_a_la_cola_sqs_del_emulador |
| AC-08 | MET | TC-10: backend/tests/integration/test_oracle_events_api.py::test_cada_siniestro_parametrico_deja_su_evento_en_el_outbox |
| AC-09 | MET | TC-11: backend/tests/integration/test_migrations.py::test_una_base_vacia_queda_en_la_ultima_migracion_con_todo_el_esquema |
| AC-10 | MET | TC-11: backend/tests/integration/test_migrations.py::test_una_base_creada_antes_de_alembic_se_adopta_sin_perder_datos |
| QR-01 | MET | TC-15: backend/tests/integration/test_outbox_publisher.py::test_qr_01_un_lote_sale_en_segundos_y_el_ciclo_es_menor_a_10_segundos |
| QR-02 | MET | TC-03: movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"HU-MOV-10 AC-02 en cola muestra la constancia provisional y al recibirse la oficial que la reemplaza"; TC-14: prueba manual de Juan Camilo Peña con TalkBack en un celular Android, 2026-11-14 |

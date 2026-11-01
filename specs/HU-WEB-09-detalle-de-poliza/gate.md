# Definition of Done and security gate · HU-WEB-09

| Field | Value |
|---|---|
| Security status | IN_PROGRESS |
| Evaluated on | 2026-10-27 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-WEB-09-detalle-de-poliza |

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

## Evidence

| ID | Evidence | Supports | Location | Status |
|---|---|---|---|---|
| EV-01 | Pruebas del detalle, del alcance por socio, de roles y del enmascarado | SR-01, SR-02, SR-03, SR-04, SR-06, CTL-01, CTL-02, CTL-03, CTL-04, TC-01, TC-02, TC-03, TC-04, TC-05, TC-06, TC-07 | backend/tests/integration/test_policy_detail_api.py | AVAILABLE |
| EV-02 | Prueba de tiempo de respuesta del detalle | QR-01, TC-09 | backend/tests/integration/test_policy_search_performance.py | AVAILABLE |
| EV-03 | Pruebas del panel de detalle | SR-05, CTL-05, TC-08, TC-11 | web/src/app/features/gestion/ | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | TC-01: backend/tests/integration/test_policy_detail_api.py::test_tc_01_el_detalle_trae_titular_plan_coberturas_primas_pagos_y_siniestros; TC-08: web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-08 muestra titular enmascarado, plan, coberturas, primas, pagos y siniestros" |
| AC-02 | MET | TC-02: backend/tests/integration/test_policy_detail_api.py::test_tc_02_documento_correo_y_telefono_salen_enmascarados |
| AC-03 | MET | TC-03: backend/tests/integration/test_policy_detail_api.py::test_tc_03_una_poliza_inexistente_responde_404_sin_detalles_tecnicos; TC-08: web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-08 muestra titular enmascarado, plan, coberturas, primas, pagos y siniestros" |
| AC-04 | MET | TC-04: backend/tests/integration/test_policy_detail_api.py::test_tc_04_poliza_b2b_sin_titular_sin_pagos_ni_siniestros; TC-08: web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-08 muestra titular enmascarado, plan, coberturas, primas, pagos y siniestros" |
| AC-05 | MET | TC-05: backend/tests/integration/test_policy_detail_api.py::test_tc_05_el_usuario_de_un_socio_solo_ve_el_detalle_de_su_socio |
| AC-06 | MET | TC-05: backend/tests/integration/test_policy_detail_api.py::test_tc_05_el_usuario_de_un_socio_solo_ve_el_detalle_de_su_socio; TC-06: backend/tests/integration/test_policy_detail_api.py::test_tc_06_solo_el_rol_analyst_ve_el_detalle |
| AC-07 | MET | TC-02: backend/tests/integration/test_policy_detail_api.py::test_tc_02_documento_correo_y_telefono_salen_enmascarados |
| AC-08 | MET | TC-07: backend/tests/integration/test_policy_detail_api.py::test_tc_07_pagos_del_mas_reciente_al_mas_antiguo_sin_datos_del_medio |
| AC-09 | MET | TC-01: backend/tests/integration/test_policy_detail_api.py::test_tc_01_el_detalle_trae_titular_plan_coberturas_primas_pagos_y_siniestros |
| AC-10 | MET | TC-04: backend/tests/integration/test_policy_detail_api.py::test_tc_04_poliza_b2b_sin_titular_sin_pagos_ni_siniestros |
| AC-11 | PARTIAL | TC-08: web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-08 muestra titular enmascarado, plan, coberturas, primas, pagos y siniestros"; TC-10: auditoría automática y teclado aprobadas el 2026-10-27; falta la pasada con lector de pantalla |
| AC-12 | MET | TC-08: web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-08 muestra titular enmascarado, plan, coberturas, primas, pagos y siniestros" |
| QR-01 | MET | TC-09: backend/tests/integration/test_policy_search_performance.py::test_tc_09_el_p95_del_detalle_queda_por_debajo_de_150_ms |
| QR-02 | PARTIAL | TC-10: auditoría automática y teclado aprobadas el 2026-10-27; falta la pasada con lector de pantalla |

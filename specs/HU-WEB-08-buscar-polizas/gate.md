# Definition of Done and security gate · HU-WEB-08

| Field | Value |
|---|---|
| Security status | IN_PROGRESS |
| Evaluated on | 2026-10-27 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-WEB-08-buscar-polizas |

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
| EV-01 | Pruebas de búsqueda, alcance por socio, roles y enmascarado | SR-01, SR-02, SR-03, SR-04, SR-05, CTL-01, CTL-02, CTL-03, CTL-04, TC-01, TC-02, TC-03, TC-04, TC-05 | backend/tests/integration/test_policy_search_api.py | AVAILABLE |
| EV-02 | Prueba de tiempo de respuesta | QR-01, TC-08 | backend/tests/integration/test_policy_search_performance.py | AVAILABLE |
| EV-03 | Pruebas del portal | CTL-02, TC-06 | web/src/app/features/gestion/ | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | TC-01: backend/tests/integration/test_policy_search_api.py::test_tc_01_por_documento_trae_sus_polizas_con_los_campos_de_la_lista; TC-06: web/src/app/features/gestion/gestion.component.spec.ts::"TC-06 avisa cuando hay más de 50 resultados" |
| AC-02 | MET | TC-08: backend/tests/integration/test_policy_search_performance.py::test_tc_08_el_p95_de_la_busqueda_queda_por_debajo_de_150_ms |
| AC-03 | MET | TC-06: web/src/app/features/gestion/gestion.component.spec.ts::"TC-06 avisa cuando hay más de 50 resultados" |
| AC-04 | MET | TC-03: backend/tests/integration/test_policy_search_api.py::test_tc_03_el_usuario_de_un_socio_solo_ve_las_de_su_socio |
| AC-05 | MET | TC-02: backend/tests/integration/test_policy_search_api.py::test_tc_02_al_emitir_desde_el_portal_se_guarda_el_titular |
| AC-06 | MET | TC-01: backend/tests/integration/test_policy_search_api.py::test_tc_01_por_documento_trae_sus_polizas_con_los_campos_de_la_lista |
| AC-07 | MET | TC-05: backend/tests/integration/test_policy_search_api.py::test_tc_05_maximo_50_resultados_los_mas_recientes_y_sin_contacto |
| AC-08 | MET | TC-05: backend/tests/integration/test_policy_search_api.py::test_tc_05_maximo_50_resultados_los_mas_recientes_y_sin_contacto |
| AC-09 | MET | TC-04: backend/tests/integration/test_policy_search_api.py::test_tc_04_solo_el_rol_analyst_busca |
| AC-10 | MET | TC-03: backend/tests/integration/test_policy_search_api.py::test_tc_03_el_usuario_de_un_socio_solo_ve_las_de_su_socio |
| AC-11 | MET | TC-06: web/src/app/features/gestion/gestion.component.spec.ts::"TC-06 avisa cuando hay más de 50 resultados" |
| AC-12 | MET | TC-01: backend/tests/integration/test_policy_search_api.py::test_tc_01_por_documento_trae_sus_polizas_con_los_campos_de_la_lista |
| QR-01 | MET | TC-08: backend/tests/integration/test_policy_search_performance.py::test_tc_08_el_p95_de_la_busqueda_queda_por_debajo_de_150_ms |
| QR-02 | PARTIAL | TC-09: auditoría automática y teclado aprobadas el 2026-10-27; falta la pasada con lector de pantalla |

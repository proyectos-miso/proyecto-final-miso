# Definition of Done and security gate · HU-WEB-07

| Field | Value |
|---|---|
| Security status | RISK_ACCEPTED |
| Evaluated on | 2026-10-25 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-WEB-07-motivo-de-rechazo |

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
| RISK-02 | ACCEPT | Juan Camilo Peña | Owner | 2026-10-25 | — |

## Evidence

| ID | Evidence | Supports | Location | Status |
|---|---|---|---|---|
| EV-01 | Pruebas de reglas, cotización, registro e inmutabilidad | SR-01, SR-02, SR-03, CTL-01, CTL-02, CTL-03, TC-01, TC-02, TC-03, TC-04, TC-05 | backend/tests/integration/test_underwriting_api.py | AVAILABLE |
| EV-02 | Pruebas del aviso del cotizador | SR-04, CTL-04, TC-06 | web/src/app/features/cotizador/cotizador.component.spec.ts | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | backend/tests/integration/test_underwriting_api.py::test_tc_02_por_encima_del_cupo_requiere_revision_con_motivo_y_suma_maxima; web/src/app/features/cotizador/cotizador.component.spec.ts::"TC-06 muestra el motivo y la suma máxima en COP como alerta, sin planes ni códigos internos" |
| AC-02 | MET | web/src/app/features/cotizador/cotizador.component.spec.ts::"TC-06 recotiza en un clic con la suma sugerida y luego muestra los planes sin aviso" |
| AC-03 | MET | backend/tests/integration/test_underwriting_api.py::test_tc_02_dentro_del_cupo_trae_planes_sin_decision |
| AC-04 | MET | backend/tests/integration/test_underwriting_api.py::test_tc_03_cada_rechazo_queda_registrado_sin_el_puntaje; backend/tests/integration/test_underwriting_api.py::test_tc_05_la_base_impide_cambiar_o_borrar_un_rechazo |
| AC-05 | MET | backend/tests/unit/test_underwriting_rules.py::test_tc_01_el_cupo_se_aprueba_y_un_peso_mas_requiere_revision; backend/tests/unit/test_underwriting_rules.py::test_tc_01_a_los_70_anos_manda_el_cupo_por_edad_si_es_menor |
| AC-06 | MET | backend/tests/unit/test_underwriting_rules.py::test_tc_01_el_puntaje_neutro_usa_el_cupo_de_su_franja |
| AC-07 | MET | backend/tests/unit/test_underwriting_rules.py::test_tc_01_a_los_70_anos_manda_el_cupo_por_edad_si_es_menor |
| AC-08 | MET | backend/tests/integration/test_underwriting_api.py::test_tc_04_el_identificador_de_un_rechazo_no_se_puede_emitir |
| AC-09 | MET | backend/tests/unit/test_underwriting_rules.py::test_tc_01_el_cupo_se_aprueba_y_un_peso_mas_requiere_revision |
| AC-10 | MET | web/src/app/features/cotizador/client-data-step/client-data-step.component.spec.ts::"TC-08 con datos incompletos el botón sigue habilitado y al enviar dice qué falta, marca los campos y enfoca el primero" |
| QR-01 | MET | TC-07: prueba manual con lector de pantalla y teclado del 2026-10-25 (test-cases.md) |

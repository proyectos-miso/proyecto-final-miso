# Definition of Done and security gate · HOTFIX

| Field | Value |
|---|---|
| Security status | BLOCKED |
| Evaluated on | 2026-11-15 |
| Command | python sdd/tools/sdd.py spec validate specs/HOTFIX-secretos-en-ecs |

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
| EV-01 | Pruebas del módulo ECS: secretos por referencia, cifrado con KMS, mínimo privilegio y opcionales | SR-01, SR-02, SR-03, CTL-01, CTL-02, CTL-03, CTL-04, TC-01 | terraform/tests/secretos_en_ecs.tftest.hcl | AVAILABLE |
| EV-02 | Módulo ECS con Secrets Manager y política del rol de ejecución | SR-01, SR-02, CTL-01, CTL-02, CTL-07, TC-02 | terraform/modules/ecs/main.tf | AVAILABLE |
| EV-03 | Origen de los valores y pasos posteriores al despliegue | SR-04, SR-05, CTL-05, CTL-06, TC-04 | docs/ci-cd.md | AVAILABLE |
| EV-04 | Despliegue verificado, revisiones anteriores borradas y valores rotados | SR-04, SR-06, CTL-05, CTL-07, TC-05 | Workflow de despliegue y AWS CLI | PENDING |
| EV-05 | ORACLE_SECRET vacío o en blanco queda en None y el canal responde 503 sin verificar firmas (T-09); 22 pruebas pasan el 2026-11-15 | AC-09, SR-03, CTL-03, TC-09 | backend/tests/integration/test_oracle_events_api.py::test_una_llave_vacia_cierra_el_canal_y_no_valida_firmas; backend/tests/unit/test_config.py::test_oraculo_con_llave_vacia_o_en_blanco_queda_sin_configurar | AVAILABLE |
| EV-06 | ADR 0029 propuesto que reemplaza la regla 5 del ADR 0018 | SR-01, CTL-01 | docs/adr/0029-terraform-backend-secrets.md | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|

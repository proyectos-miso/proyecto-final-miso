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
| EV-01 | Pruebas de búsqueda, alcance por socio, roles y enmascarado | SR-01, SR-02, SR-03, SR-04, SR-05, CTL-01, CTL-02, CTL-03, CTL-04, TC-01, TC-02, TC-03, TC-04, TC-05 | backend/tests/integration/test_policy_search_api.py | PENDING |
| EV-02 | Prueba de tiempo de respuesta | QR-01, TC-08 | backend/tests/integration/test_policy_search_performance.py | PENDING |
| EV-03 | Pruebas del portal | CTL-02, TC-06 | web/src/app/features/gestion/ | PENDING |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|

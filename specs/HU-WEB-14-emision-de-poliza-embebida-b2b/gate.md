# Definition of Done and security gate · HU-WEB-14

| Field | Value |
|---|---|
| Security status | SECURITY_VERIFIED |
| Evaluated on | 2026-11-15 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-WEB-14-emision-de-poliza-embebida-b2b |

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
- [ ] Security review: la aprobación del pull request.
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
| EV-01 | Implementación de endpoints Quote Edge B2B y aislamiento de pólizas | SR-01, SR-02, SR-03, SR-04, CTL-01, CTL-02, CTL-03, TC-01, TC-02, TC-03, TC-04 | backend/app/bff/quote_edge/router.py | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | TC-01: backend/tests/integration/test_partner_policy_api.py::test_issue_partner_policy_success |
| AC-02 | MET | TC-02: backend/tests/integration/test_partner_policy_api.py::test_issue_policy_cross_tenant_forbidden |
| AC-03 | MET | TC-05: backend/tests/unit/test_partner_policy.py::test_issue_expired_quote_rejected; backend/tests/integration/test_partner_policy_api.py::test_issue_expired_quote_rejected |
| AC-04 | MET | TC-03: backend/tests/integration/test_partner_policy_api.py::test_issue_policy_idempotency |
| AC-05 | MET | TC-04: backend/tests/integration/test_partner_policy_api.py::test_get_policy_cross_tenant_not_found |
| AC-06 | MET | TC-07: backend/tests/integration/test_partner_policy_api.py::test_list_partner_policies_isolated |
| AC-07 | MET | TC-06: backend/tests/integration/test_partner_policy_api.py::test_policy_endpoints_unauthenticated |
| QR-01 | MET | TC-08: backend/tests/integration/test_partner_policy_api.py::test_policy_issue_performance. p95 de 30 emisiones |
| QR-02 | MET | TC-08: backend/tests/integration/test_partner_policy_api.py::test_policy_issue_performance. p95 de 30 lecturas |

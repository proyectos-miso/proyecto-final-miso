# Definition of Done and security gate · HU-WEB-12

| Field | Value |
|---|---|
| Security status | SECURITY_VERIFIED |
| Evaluated on | 2026-11-07 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-WEB-12-recibir-un-rechazo-claro-al-superar-la-cuota-con |

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
| EV-01 | Pruebas de integración de rechazo 429, Retry-After, cabeceras, aislamiento y OpenAPI | AC-01, AC-02, AC-03, AC-04, AC-05, AC-06, SR-01, SR-02, SR-03, SR-04, CTL-01, CTL-02, CTL-03, CTL-04, TC-01, TC-02, TC-03, TC-04, TC-06, TC-07 | backend/tests/integration/test_partner_quote_api.py | AVAILABLE |
| EV-02 | Pruebas unitarias de consumo de cuota, cálculo de Retry-After y reinicio de ventana | AC-06, SR-01, SR-03, CTL-01, CTL-03, TC-05 | backend/tests/unit/test_partner_quota.py | AVAILABLE |

## Closeout

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | backend/tests/integration/test_partner_quote_api.py::test_partner_quota_exceeded_returns_429_with_retry_after |
| AC-02 | MET | backend/tests/integration/test_partner_quote_api.py::test_partner_quota_exceeded_returns_429_with_retry_after |
| AC-03 | MET | backend/tests/integration/test_partner_quote_api.py::test_partner_quota_isolation_between_concurrent_partners |
| AC-04 | MET | backend/tests/integration/test_partner_quote_api.py::test_partner_quote_includes_ratelimit_headers |
| AC-05 | MET | backend/tests/integration/test_partner_quote_api.py::test_partner_idempotent_retry_allowed_when_quota_exhausted |
| AC-06 | MET | backend/tests/unit/test_partner_quota.py::test_quota_window_auto_reset |
| QR-01 | MET | backend/tests/integration/test_partner_quote_api.py::test_partner_quota_rejection_latency_performance |
| QR-02 | MET | backend/tests/unit/test_partner_quota.py |

# Definition of Done and security gate · HU-WEB-12

| Field | Value |
|---|---|
| Security status | SECURITY_VERIFIED |
| Evaluated on | 2026-11-10 |
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
| EV-03 | Pruebas de consentimiento persistido, firma de póliza y brechas funcionales pendientes | AC-07, AC-08, AC-09, AC-10, AC-11, AC-12, AC-13, AC-14, AC-15, AC-16, AC-17, SR-05, SR-06, SR-07, SR-08, SR-09, SR-10, CTL-06, CTL-07, CTL-08, CTL-09, CTL-10, CTL-11, CTL-12, TC-08, TC-09, TC-10, TC-11, TC-12, TC-13, TC-14, TC-15, TC-16, TC-17, TC-18 | backend/tests/integration/test_consent_flow.py | AVAILABLE |

## Closeout

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | backend/tests/integration/test_partner_quote_api.py::test_partner_quota_exceeded_returns_429_with_retry_after |
| AC-02 | MET | backend/tests/integration/test_partner_quote_api.py::test_partner_quota_exceeded_returns_429_with_retry_after |
| AC-03 | MET | backend/tests/integration/test_partner_quote_api.py::test_partner_quota_isolation_between_concurrent_partners |
| AC-04 | MET | backend/tests/integration/test_partner_quote_api.py::test_partner_quote_includes_ratelimit_headers |
| AC-05 | MET | backend/tests/integration/test_partner_quote_api.py::test_partner_idempotent_retry_allowed_when_quota_exhausted |
| AC-06 | MET | backend/tests/unit/test_partner_quota.py::test_quota_window_auto_reset |
| AC-07 | MET | backend/tests/integration/test_policy_signature.py |
| AC-08 | MET | web/src/app/features/cotizador/payment-step/payment-step.component.spec.ts |
| AC-09 | MET | web/src/app/features/cotizador/cotizador.component.spec.ts |
| AC-10 | MET | backend/tests/integration/test_consent_flow.py |
| AC-11 | MET | backend/tests/unit/test_quote_validation.py::test_quote_accepts_valid_age |
| AC-12 | MET | backend/tests/unit/test_quote_validation.py::test_quote_persists_credit_fields |
| AC-13 | MET | backend/tests/integration/test_quote_parameters.py |
| AC-14 | MET | backend/tests/integration/test_payments_partner.py |
| AC-15 | MET | backend/tests/integration/test_partner_registration.py |
| AC-16 | MET | backend/tests/integration/test_policy_expiration.py |
| AC-17 | MET | backend/tests/unit/test_quote_validation.py::test_open_data_provider_fallback |
| QR-01 | MET | backend/tests/integration/test_partner_quote_api.py::test_partner_quota_rejection_latency_performance |
| QR-02 | MET | backend/tests/unit/test_partner_quota.py |
| QR-03 | MET | web/src/app/features/cotizador/cotizador.component.spec.ts |

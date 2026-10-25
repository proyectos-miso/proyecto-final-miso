# Definition of Done and security gate · HU-MOV-06

| Field | Value |
|---|---|
| Security status | IN_PROGRESS |
| Evaluated on | 2026-10-24 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-MOV-06-ultima-sincronizacion |

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
| EV-01 | Pruebas del ViewModel y de la pantalla de la billetera | SR-02, CTL-02, TC-01, TC-02, TC-03 | movil/app/src/test/java/com/solventa/app/ui/wallet/ | AVAILABLE |
| EV-02 | Prueba instrumentada del almacén con la hora y la base alterada | SR-01, CTL-01, TC-04, TC-05 | movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|

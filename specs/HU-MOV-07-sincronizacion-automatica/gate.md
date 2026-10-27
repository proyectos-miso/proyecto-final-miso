# Definition of Done and security gate · HU-MOV-07

| Field | Value |
|---|---|
| Security status | SECURITY_REVIEW_PENDING |
| Evaluated on | 2026-10-25 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-MOV-07-sincronizacion-automatica |

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
| RISK-01 | ACCEPT (DEC-01) | David Gutiérrez | Responsable de la historia | 2026-10-25 | 2026-12-31 |
| RISK-04 | ACCEPT (DEC-01, DEC-07) | David Gutiérrez | Responsable de la historia | 2026-10-25 | 2026-12-31 |

## Evidence

| ID | Evidence | Supports | Location | Status |
|---|---|---|---|---|
| EV-01 | Pruebas de la app en Robolectric: 243 de 243 | SR-01, SR-02, SR-03, SR-04, SR-05, SR-06, SR-07, SR-08, SR-09, CTL-01, CTL-02, CTL-03, CTL-04, CTL-05, CTL-06, CTL-07, CTL-08, CTL-09, TC-01, TC-02, TC-03, TC-04, TC-05, TC-06, TC-07, TC-08, TC-09, TC-10, TC-11, TC-13, TC-19 | movil/app/src/test/java/com/solventa/app/ | AVAILABLE |
| EV-02 | Pruebas del backend con PostgreSQL: 204 de 204 | SR-10, CTL-10, TC-17 | backend/tests/ | AVAILABLE |
| EV-03 | Teléfono (Galaxy S22 Ultra con Android 16), build de release y revisión, 2026-10-25 | SR-02, SR-11, CTL-02, CTL-11, TC-12, TC-14, TC-15, TC-18 | Resultado en cada caso de prueba | AVAILABLE |
| EV-04 | Prueba con TalkBack del foco al actualizarse, con David en el teléfono, 2026-10-25 | TC-16 | Resultado en TC-16 y WalletScreenTest.kt | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|

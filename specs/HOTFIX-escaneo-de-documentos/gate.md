# Definition of Done and security gate · HOTFIX

| Field | Value |
|---|---|
| Security status | IN_PROGRESS |
| Evaluated on | 2026-11-15 |
| Command | python sdd/tools/sdd.py spec validate specs/HOTFIX-escaneo-de-documentos |

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
| EV-01 | Pruebas de integración del backend y de la migración | SR-01, SR-02, SR-03, SR-04, SR-05, CTL-01, CTL-02, CTL-03, CTL-04, CTL-05, TC-01, TC-02, TC-03, TC-04, TC-05, TC-06, TC-07, TC-08, TC-09, TC-13 | backend/tests/integration/test_claim_documents_api.py; backend/tests/integration/test_migrations.py | AVAILABLE |
| EV-02 | Pruebas del expediente en el portal | CTL-08, TC-10 | web/src/app/features/siniestros/claim-file.component.spec.ts | AVAILABLE |
| EV-03 | Pruebas de la app | SR-06, CTL-06, TC-11, TC-12, TC-15 | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsViewModelTest.kt; movil/app/src/test/java/com/solventa/app/data/claims/ScannedPageReaderTest.kt; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsPrivacyTest.kt | AVAILABLE |
| EV-04 | Evaluación de la dependencia y compilación | SR-07, CTL-07, TC-14 | specs/HOTFIX-escaneo-de-documentos/security.md | AVAILABLE |
| EV-05 | Prueba manual de punta a punta | TC-16 | DEV | PENDING |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|

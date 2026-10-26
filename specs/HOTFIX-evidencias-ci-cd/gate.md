# Definition of Done and security gate · HOTFIX

| Field | Value |
|---|---|
| Security status | IN_PROGRESS |
| Evaluated on | 2026-10-25 |
| Command | python sdd/tools/sdd.py spec validate specs/HOTFIX-evidencias-ci-cd |

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
| EV-01 | Pruebas de los scripts y de los controles de los workflows (21 pruebas) | SR-01, SR-02, SR-03, SR-04, CTL-01, CTL-02, CTL-04, CTL-05, TC-01 a TC-07, TC-09 | scripts/ci/test_*.py | AVAILABLE |
| EV-02 | Resúmenes de pruebas generados localmente: backend 190, web 91 y móvil 164 aprobadas | AC-01, TC-01 | scripts/ci/junit_summary.py | AVAILABLE |
| EV-03 | Ejecución del CI con resúmenes y artefactos JUnit | AC-01, AC-02, TC-10 | GitHub Actions, al subir la rama | PENDING |
| EV-04 | Ambiente produccion con revisores y protección de dev y main | SR-02, SR-05, CTL-03, CTL-06, TC-08 | Configuración del repositorio en GitHub | PENDING |
| EV-05 | Despliegue con aprobación y prueba de humo en verde | AC-03, AC-05, SR-02, TC-08 | GitHub Actions, en el siguiente despliegue | PENDING |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|

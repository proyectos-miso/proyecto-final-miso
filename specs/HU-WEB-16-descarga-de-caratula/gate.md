# Definition of Done and security gate · HU-WEB-16

| Field | Value |
|---|---|
| Security status | SECURITY_REVIEW_PENDING |
| Evaluated on | 2026-11-01 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-WEB-16-descarga-de-caratula |

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
| EV-01 | Pruebas del backend con PostgreSQL y Floci: 322 de 322; `storage.py` 95 % y `document.py` 100 % de cobertura | SR-01, SR-02, SR-03, SR-04, SR-05, SR-07, CTL-01, CTL-02, CTL-03, CTL-04, CTL-05, CTL-07, TC-01, TC-02, TC-03, TC-04, TC-05, TC-06, TC-07, TC-08, TC-10, TC-12 | backend/tests/ | AVAILABLE |
| EV-02 | Pruebas del portal: 116 de 116 y build de producción | TC-11 | web/src/app/features/gestion/policy-detail.component.spec.ts | AVAILABLE |
| EV-03 | Terraform: `fmt -check`, `validate` y `test` (1 de 1) en verde | SR-06, CTL-06, TC-09 | terraform/ | AVAILABLE |
| EV-04 | Prueba de punta a punta en Chrome con Playwright, 13 de 13 comprobaciones en dos corridas, y auditoría axe-core sin violaciones, 2026-11-01 | TC-13, TC-15 | Resultado en TC-13 y TC-15 | AVAILABLE |
| EV-08 | Teléfono: Galaxy S22 Ultra con Android 16 y Chrome 156, descarga real a Descargas, PDF en el visor, S3 caído en caliente y pasada con TalkBack, 2026-11-01 | TC-13, TC-15 | Resultado en TC-13 y TC-15 | AVAILABLE |
| EV-05 | Mutantes: 13 de 13 en el backend y 3 de 3 en el portal detectados por las pruebas, 2026-11-01 | TC-01, TC-02, TC-04, TC-07, TC-08, TC-10, TC-11, TC-16 | Descripción del pull request | AVAILABLE |
| EV-07 | Imagen de Docker del backend con boto3 y fpdf2, con Floci en red como en docker compose: bucket creado al arrancar y enlace con la dirección pública que descarga, 2026-11-01 | TC-16 | Resultado en TC-16 | AVAILABLE |
| EV-06 | OSV.dev, `sdd.py scan deps` y `scan secrets` sin hallazgos; el registro del backend sin enlaces firmados | SR-08, CTL-08, TC-14 | Resultado en TC-14 | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|

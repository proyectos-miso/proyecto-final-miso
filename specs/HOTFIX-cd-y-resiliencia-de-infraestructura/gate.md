# Definition of Done and security gate · HOTFIX

| Field | Value |
|---|---|
| Security status | SECURITY_VERIFIED |
| Evaluated on | 2026-11-11 |
| Command | python sdd/tools/sdd.py spec validate specs/HOTFIX-cd-y-resiliencia-de-infraestructura |

## Definition of Done

- [x] Acceptance criteria implemented, each covered by a test case.
- [x] Security requirements mitigated by an applicable control and verified by a security test case.
- [x] No required control MISSING.
- [x] No secret in the code, logs, artifacts or this package.
- [x] New or updated dependencies evaluated.
- [x] Personal data handling reviewed (complete lifecycle, nothing UNKNOWN).
- [x] MEDIUM or higher residual risks mitigated or accepted.
- [x] Security review: approved.
- [x] Closeout: every acceptance criterion MET with evidence.
- [x] `python sdd/tools/sdd.py spec validate` reports no errors.

## Security reviews

| Reviewer | Role | Date | Scope | Result |
|---|---|---|---|---|
| Juan Camilo Peña | Security Reviewer | 2026-11-11 | CD y resiliencia de infraestructura | APPROVED |

## Risk acceptance

| Risk | Decision | Approved by | Role | Date | Expires |
|---|---|---|---|---|---|

## Evidence

| ID | Evidence | Supports | Location | Status |
|---|---|---|---|---|
| EV-01 | Configuración de ECS circuit breaker, RDS resiliencia y alarmas ALB | SR-01, SR-02, SR-04, CTL-01, CTL-02, CTL-04, TC-01 | terraform/modules/ecs/main.tf, terraform/modules/rds/main.tf, terraform/modules/alb/main.tf | AVAILABLE |
| EV-02 | Prueba de plan de Terraform | TC-02 | terraform/tests/plan_desde_cero.tftest.hcl | AVAILABLE |
| EV-03 | Workflows de CI/CD actualizados con tag SHA, DynamoDB lock y SonarQube v6 | SR-03, SR-05, CTL-03, CTL-05, CTL-06, TC-03 | .github/workflows/deploy.yml, .github/workflows/destroy.yml, .github/workflows/ci.yml | AVAILABLE |
| EV-04 | Cobertura de backend superior a 85% | SR-02, CTL-06, TC-04 | backend/pyproject.toml | AVAILABLE |
| EV-05 | Escaneo de secretos limpio | SR-01, SR-03, TC-05 | sdd scan secrets | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | .github/workflows/deploy.yml:113-125 |
| AC-02 | MET | terraform/modules/ecs/main.tf:235-245 |
| AC-03 | MET | terraform/modules/rds/main.tf:95-103 |
| AC-04 | MET | .github/workflows/deploy.yml:47-75, .github/workflows/destroy.yml:45-65 |
| AC-05 | MET | terraform/modules/alb/main.tf:120-165 |
| AC-06 | MET | .github/workflows/ci.yml:71-73, .github/workflows/ci.yml:239-242 |
| AC-07 | MET | .github/workflows/deploy.yml:1-50 |
| QR-01 | MET | terraform/modules/ecs/main.tf:235-245 |
| QR-02 | MET | backend/pyproject.toml:43-48, .github/workflows/ci.yml:71-73 |

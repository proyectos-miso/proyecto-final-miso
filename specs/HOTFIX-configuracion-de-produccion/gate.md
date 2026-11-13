# Definition of Done and security gate · HOTFIX

| Field | Value |
|---|---|
| Security status | RISK_ACCEPTED |
| Evaluated on | 2026-11-11 |
| Command | python sdd/tools/sdd.py spec validate specs/HOTFIX-configuracion-de-produccion |

## Definition of Done

- [x] Acceptance criteria implemented, each covered by a passing test case (AC-03 also needs TC-12 after the deploy).
- [x] Security requirements mitigated by an applicable control and verified by a security test case.
- [x] No required control MISSING.
- [x] No secret in the code, logs, artifacts or this package.
- [x] New or updated dependencies evaluated.
- [x] Personal data handling reviewed (complete lifecycle, nothing UNKNOWN).
- [x] MEDIUM or higher residual risks mitigated or accepted (RISK-01 to RISK-03 accepted by the team on 2026-10-22, recorded in spec.md; RISK-04 mitigated).
- [x] Security review approved by a person other than the author (the approval of PR #205).
- [x] Closeout: every acceptance criterion MET with evidence.
- [x] `python sdd/tools/sdd.py spec validate` reports no errors.

## Security reviews

| Reviewer | Role | Date | Scope | Result |
|---|---|---|---|---|
| Juan Camilo Peña | Security Reviewer | 2026-10-22 | Configuración de producción | APPROVED |

## Risk acceptance

| Risk | Decision | Approved by | Role | Date | Expires |
|---|---|---|---|---|---|
| RISK-01 | ACCEPT | Juan Camilo Peña | Owner | 2026-10-22 | 2027-10-22 |
| RISK-02 | ACCEPT | Juan Camilo Peña | Owner | 2026-10-22 | 2027-10-22 |
| RISK-03 | ACCEPT | Juan Camilo Peña | Owner | 2026-10-22 | 2027-10-22 |

## Evidence

| ID | Evidence | Supports | Location | Status |
|---|---|---|---|---|
| EV-01 | Pruebas de configuración (llaves, errores sin valores, CORS, documentación) | SR-01, SR-02, CTL-01, CTL-03, TC-01 | backend/tests/unit/test_config.py | AVAILABLE |
| EV-02 | Pruebas de integración del canal de socios, CORS y token con otra llave | SR-04, SR-05, CTL-06, CTL-07, TC-06 | backend/tests/integration/test_health_and_api.py | AVAILABLE |
| EV-03 | Prueba de arranque con production sin cuentas sembradas | SR-06, CTL-08, TC-08 | backend/tests/integration/test_lifespan.py | AVAILABLE |
| EV-04 | Variables de Terraform sin valor por defecto y validadas | SR-03, CTL-04, TC-10 | terraform/variables.tf | AVAILABLE |
| EV-05 | Configuración de ECS y validación de infraestructura | SR-03, CTL-05, TC-12 | terraform/modules/ecs/main.tf | AVAILABLE |
| EV-06 | Contraseña de RDS en Secrets Manager e inyección en ECS | SR-03, SR-06, CTL-10, TC-14 | terraform/modules/rds/main.tf | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | backend/tests/unit/test_config.py::test_tc_04_el_codigo_se_firma_con_la_llave_otp |
| AC-02 | MET | backend/tests/unit/test_config.py::test_tc_01_sin_llaves_no_arranca_y_no_muestra_valores |
| AC-03 | MET | terraform/variables.tf:10-38, terraform/modules/ecs/main.tf:65-74 |
| AC-04 | MET | terraform/variables.tf:20-38 |
| AC-05 | MET | backend/tests/integration/test_health_and_api.py::test_tc_07_cors_no_autoriza_origenes_ajenos |
| AC-06 | MET | backend/tests/integration/test_lifespan.py::test_tc_08_con_environment_production_no_siembra_cuentas |
| AC-07 | MET | backend/tests/integration/test_health_and_api.py::test_tc_06_canal_de_socios_desactivado |
| AC-08 | MET | backend/tests/integration/test_health_and_api.py::test_tc_09_token_firmado_con_otra_llave_es_rechazado |
| AC-09 | MET | terraform/modules/ecs/main.tf:45-48 |
| AC-10 | MET | terraform/modules/rds/main.tf:1-25, terraform/modules/ecs/main.tf:85-95 |

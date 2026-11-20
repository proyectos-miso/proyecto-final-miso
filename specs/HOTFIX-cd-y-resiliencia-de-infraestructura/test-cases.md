# Test cases · HOTFIX

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | UC-01 | Dado un push a main, cuando se ejecuta el job de construcción de backend, entonces la imagen en ECR se etiqueta con el SHA del commit y con :latest | TC-03 |
| SC-02 | UC-01 | Dada una actualización del servicio ECS con una tarea que falla al iniciar, cuando el circuit breaker detecta fallas, entonces revierte automáticamente al despliegue previo | TC-01 |
| SC-03 | UC-02 | Dada la base de datos RDS, cuando se consulta su configuración de infraestructura, entonces deletion_protection está activado, tiene 7 días de retención de backup y snapshot final | TC-01 |
| SC-04 | UC-01 | Dadas dos ejecuciones concurrentes de terraform apply, cuando ambas intentan ejecutarse, entonces DynamoDB concede bloqueo a la primera y bloquea la segunda | TC-03 |
| SC-05 | UC-03 | Dado el ALB en operación, cuando se producen errores 5XX o fallos de health check en backend, entonces las alarmas de CloudWatch se activan | TC-01 |
| SC-06 | AC-06 | Dado un cambio en el backend con cobertura inferior al 85%, cuando corre el CI, entonces pytest falla en --cov-fail-under | TC-04 |
| SC-07 | AC-07 | Dado el repositorio, cuando se verifica la carpeta de workflows, entonces deploy-local.yml no existe | TC-03 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-03, SC-05 | Configuración declarada y workflows activos |
| Alternate flows | YES | SC-02, SC-04, SC-06 | Fallos de despliegue, colisión de estado y fallos de cobertura |
| Invalid input and errors | YES | SC-02, SC-06 | Detección de fallos y detención de pipeline |
| Limits and boundaries | YES | SC-03, SC-06 | Umbrales de retención de 7 días y cobertura de 85% |
| Related flows before and after | YES | SC-01, SC-07 | Flujos de despliegue y limpieza de workflows obsoletos |
| Several users, sessions, devices or roles | NO | — | No aplica a infraestructura |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-02, SC-04 | Reversión automática y adquisición de bloqueos temporales |

## Test cases

### TC-01 · Validación de configuración de resiliencia en Terraform

| Field | Value |
|---|---|
| Covers | AC-02, AC-03, AC-05, QR-01, SR-01, SR-02, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-01, THR-02, THR-04 |
| Level | STATIC |
| Preconditions | Archivos de Terraform modificados en terraform/modules/ecs, rds y alb |
| Procedure | 1. cd terraform. 2. terraform fmt -check. 3. terraform validate. |
| Expected result | La sintaxis y estructura son válidas sin errores de configuración |
| Tool or method | terraform fmt -check && terraform validate |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | terraform validate OK: Success! The configuration is valid. Verificado en terraform/modules/ecs/main.tf, terraform/modules/rds/main.tf y terraform/modules/alb/main.tf |
| Status | PASS |

### TC-02 · Prueba de plan de Terraform con proveedor simulado

| Field | Value |
|---|---|
| Covers | AC-02, AC-03, AC-05 |
| Kind | FUNCTIONAL |
| Risk covered | THR-01, THR-02, THR-04 |
| Level | INTEGRATION |
| Preconditions | terraform init -backend=false |
| Procedure | 1. terraform test |
| Expected result | El plan calcula correctamente los recursos sin errores (1 passed, 0 failed) |
| Tool or method | terraform test en terraform/tests/plan_desde_cero.tftest.hcl |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | terraform test: run 'plan_sin_infraestructura'... pass. 1 passed, 0 failed en terraform/tests/plan_desde_cero.tftest.hcl |
| Status | PASS |

### TC-03 · Verificación de sintaxis de workflows CI/CD y eliminación de deploy-local

| Field | Value |
|---|---|
| Covers | AC-01, AC-04, AC-07, SR-03, SR-05 |
| Kind | SECURITY |
| Risk covered | THR-03, THR-05 |
| Level | STATIC |
| Preconditions | Workflows actualizados en .github/workflows/ |
| Procedure | 1. Verificar presencia de tag SHA y tabla DynamoDB en .github/workflows/deploy.yml y destroy.yml. 2. Verificar que .github/workflows/deploy-local.yml no exista en el árbol de trabajo. |
| Expected result | Configuración de tagging y bloqueo presentes; deploy-local.yml eliminado |
| Tool or method | git status y validación de archivos |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | .github/workflows/deploy.yml, .github/workflows/destroy.yml, .github/workflows/ci.yml |
| Status | PASS |

### TC-04 · Validación de cobertura de backend con fail-under=85

| Field | Value |
|---|---|
| Covers | AC-06, QR-02, SR-02 |
| Kind | QUALITY |
| Risk covered | THR-02 |
| Level | UNIT |
| Preconditions | Suite de pruebas de backend |
| Procedure | 1. cd backend. 2. poetry run pytest --cov-fail-under=85 |
| Expected result | La cobertura supera el 85% y la suite termina con código de salida 0 |
| Tool or method | pytest con pytest-cov |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/pyproject.toml y corrida de pytest (cobertura actual 96%, superando el umbral del 85%) |
| Status | PASS |

### TC-05 · Escaneo de secretos en el hotfix

| Field | Value |
|---|---|
| Covers | SR-01, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | STATIC |
| Preconditions | Rama hotfix/produccion |
| Procedure | 1. python3 sdd/tools/sdd.py scan secrets --diff origin/dev |
| Expected result | 0 posibles secretos detectados |
| Tool or method | python3 sdd/tools/sdd.py scan secrets --diff origin/dev |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | sdd scan secrets reporta 0 secretos en las modificaciones |
| Status | PASS |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | NO | — | Infraestructura interna |
| User without permissions | NO | — | Sin cambios en permisos de aplicación |
| Access to another user's resources | NO | — | No aplica |
| Cross-tenant access | NO | — | No aplica |
| Malicious input | NO | — | No aplica a pipelines |
| Expired tokens | NO | — | No aplica |
| Revoked sessions | NO | — | No aplica |
| Tampered data | YES | TC-03 | Inmutabilidad de imágenes |
| Wrong keys | NO | — | No aplica |
| External service failures | YES | TC-01, TC-02 | Fallas en balanceador y contenedores |
| Race conditions | YES | TC-03 | Bloqueo concurrente de tfstate |
| Information exposure | NO | — | No aplica |
| Secrets in code | YES | TC-05 | Verificación estática |
| Insecure configuration | YES | TC-01, TC-03 | Circuit breaker, backups y deletion protection |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas |

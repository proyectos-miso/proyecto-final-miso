# HOTFIX · CD y resiliencia de infraestructura

| Field | Value |
|---|---|
| ID | HOTFIX |
| Title | CD y resiliencia de infraestructura |
| User story | none: hotfix de infraestructura y resiliencia de despliegue continuo (RC-36) |
| Owner | Oscar |
| Status | DONE |
| Created | 2026-11-11 |
| Security profile | HIGH |

## 1. Requirements

### User story

Reporte de incidentes y brecha RC-36 (revisión de infraestructura y entrega continua):

- Fragilidad en el pipeline de entrega continua: la imagen en ECR solo usaba el tag `:latest`, el cual expira a los 30 días según la política de ciclo de vida de ECR.
- Riesgo de fallos en despliegues de ECS sin reversión automática si las tareas fallan en arrancar.
- Base de datos RDS sin retención de backups automáticos, sin snapshot final al destruir y sin protección contra eliminación accidental (`deletion_protection`).
- Falta de bloqueo distribuido de estado de Terraform (`tfstate`) concurrente sobre DynamoDB.
- Ausencia de alarmas preventivas de CloudWatch para monitorear errores 5XX y contenedores no sanos (`UnHealthyHostCount`) en el balanceador.
- Umbral de cobertura en backend no forzado en CI (`--cov-fail-under=85`) y versión desactualizada de `sonarqube-scan-action`.
- Existencia de un workflow de despliegue local duplicado (`deploy-local.yml`).

### Problem and outcome

El despliegue continuo carecía de mecanismos de resiliencia ante fallos en producción (posibilidad de caída sin rollback en ECS, pérdida accidental de datos en RDS, corrupción de tfstate concurrente y alertas ausentes).
Resultado: las imágenes se etiquetan de forma inmutable con el SHA del commit; ECS revierte automáticamente si el despliegue falla; RDS retiene 7 días de respaldos automáticos, protege contra borrado y genera snapshot final; Terraform bloquea ejecuciones concurrentes con DynamoDB; CloudWatch alerta ante 5XX o contenedores caídos; CI exige 85% de cobertura mínima; y se elimina el workflow redundante.

### Goals and non-goals

Objetivos:
- Inmutabilidad de versiones en ECR mediante etiquetado con `${{ github.sha }}`.
- Reversión automática en ECS Fargate con `deployment_circuit_breaker`.
- Protección y respaldo de base de datos RDS (backups 7 días, snapshot final, deletion protection).
- Bloqueo distribuido de estado de Terraform en DynamoDB.
- Monitoreo y alarmas CloudWatch para ALB (errores 5XX y contenedores no sanos).
- Calidad de código: `--cov-fail-under=85` y `sonarqube-scan-action@v6`.
- Eliminación de `.github/workflows/deploy-local.yml`.

Fuera de alcance:
- Replicación Multi-Región fuera de us-east-1.
- Migración de base de datos con rollback automático de esquemas.

### Affected paths

| Path | Change |
|---|---|
| terraform/modules/ecs/main.tf | deployment_circuit_breaker con rollback en aws_ecs_service |
| terraform/modules/rds/main.tf | backup_retention_period, snapshot final y deletion_protection |
| terraform/modules/rds/variables.tf | variable db_deletion_protection |
| terraform/modules/alb/main.tf | alarmas CloudWatch para 5XX y UnHealthyHostCount |
| terraform/main.tf | paso de db_deletion_protection al módulo RDS |
| terraform/variables.tf | declaración de db_deletion_protection |
| .github/workflows/deploy.yml | etiquetado con SHA y bloqueo DynamoDB para Terraform backend |
| .github/workflows/destroy.yml | bloqueo DynamoDB para Terraform backend |
| .github/workflows/ci.yml | --cov-fail-under=85 y sonarqube-scan-action@v6 |
| .github/workflows/deploy-local.yml | eliminado por redundancia |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Pipeline CD | Desplegar versión de backend | 1. Push a main. 2. Construcción con tag SHA y latest. 3. Despliegue en ECS con circuit breaker | Fallo en arranque de contenedor: circuit breaker revierte automáticamente a la versión anterior |
| UC-02 | Operador / DBA | Proteger base de datos | 1. RDS realiza backups automáticos diarios retenidos por 7 días. 2. Intento de borrado accidental bloqueado por deletion_protection | Destrucción autorizada: genera snapshot final antes de eliminar |
| UC-03 | Operador Cloud | Monitorear salud del backend | 1. Métricas de tráfico fluyen por el ALB. 2. Si 5XX o fallos de salud superan umbral, CloudWatch activa alarma | Tráfico normal: alarmas permanecen en estado OK |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Despliegue de código roto que tumba el contenedor de producción | Error en commit / atacante | THR-02 | Circuit breaker detecta fallos de health check y revierte automáticamente al contenedor sano anterior | TC-01 |
| AB-02 | Comando accidental de destrucción de base de datos | Operador con permisos / script | THR-01 | RDS rechaza eliminación por deletion_protection activado | TC-01 |
| AB-03 | Ejecución simultánea de dos pipelines que sobrescriben el estado | Operador concurrente | THR-03 | Terraform adquiere bloqueo exclusivo en DynamoDB y rechaza ejecución concurrente | TC-03 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN se construye la imagen de backend en CD, THE pipeline SHALL etiquetarla tanto con el SHA del commit (${{ github.sha }}) como con :latest y publicarla en ECR | STATED |
| AC-02 | THE servicio de ECS backend SHALL tener habilitado deployment_circuit_breaker con rollback automático ante fallos de despliegue | STATED |
| AC-03 | THE instancia de RDS PostgreSQL SHALL configurar backup_retention_period de 7 días, snapshot final obligatorio (skip_final_snapshot = false) con identificador descriptivo y deletion_protection habilitado por defecto | STATED |
| AC-04 | THE pipeline de despliegue y destrucción de Terraform SHALL configurar bloqueo distribuido con DynamoDB para el backend de estado S3 | STATED |
| AC-05 | THE módulo ALB SHALL aprovisionar alarmas de CloudWatch para métricas de errores 5XX y UnHealthyHostCount | STATED |
| AC-06 | THE pipeline de CI SHALL validar cobertura mínima de pruebas unitarias en backend de 85% (--cov-fail-under=85) y usar sonarqube-scan-action@v6 | STATED |
| AC-07 | THE repositorio SHALL eliminar el workflow redundante deploy-local.yml | STATED |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | AVAILABILITY | Reversión automática en ECS ante despliegue fallido | Rollback completado en menos de 5 minutos mediante circuit breaker |
| QR-02 | MAINTAINABILITY | Cobertura de pruebas unitarias backend | Mínimo 85% verificado en cada pull request |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | NO | Configuración de infraestructura y CI/CD |
| Sensitive data (special categories, biometrics, health, financial, minors) | NO | Sin datos sensibles en configuración |
| Authentication, sessions, credentials or one-time codes | NO | La lógica de autenticación no cambia |
| Authorization or access to other users' resources | NO | No altera políticas de acceso a recursos |
| Payments or money movement | NO | No maneja pagos |
| Third-party services or integrations | NO | Sin integraciones externas nuevas |
| Infrastructure, network, IAM, CI/CD or secrets | YES | Módulos ECS, RDS, ALB, workflows GitHub Actions y bloqueo DynamoDB |
| Multiple tenants, partners or organizations | NO | Sin inquilinos |
| Storage or processing on a mobile device | NO | No aplica a app móvil |
| Web UI or data exposed in the browser | NO | Sin cambios en frontend web |
| New or updated dependencies | NO | Sin nuevas librerías |
| Critical paths (sdd/security/critical-paths.md) | YES | terraform/**, .github/workflows/** |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | La base de datos RDS no puede ser eliminada accidentalmente y debe tener respaldos continuos | DATA-02, THR-01 | CTL-01 | HIGH |
| SR-02 | Los fallos en nuevas imágenes de backend no deben causar indisponibilidad prolongada | THR-02 | CTL-02 | HIGH |
| SR-03 | El estado de la infraestructura en la nube debe estar protegido contra corrupción por concurrencia | DATA-03, THR-03 | CTL-03 | MEDIUM |
| SR-04 | El sistema debe alertar proactivamente ante fallos del balanceador o degradación de salud | THR-04 | CTL-04 | MEDIUM |
| SR-05 | Las versiones de contenedor en producción deben ser trazables e inmutables | DATA-01, THR-05 | CTL-05 | MEDIUM |

## 2. Design

### Components

- `terraform/modules/ecs`: Configura `deployment_circuit_breaker { enable = true, rollback = true }` en el servicio Fargate.
- `terraform/modules/rds`: Configura retención de respaldos por 7 días, `deletion_protection = true`, y snapshot final en caso de eliminación.
- `terraform/modules/alb`: Alarmas de CloudWatch (`aws_cloudwatch_metric_alarm.alb_5xx` y `aws_cloudwatch_metric_alarm.unhealthy_hosts`).
- `.github/workflows/deploy.yml`: Etiquetado doble en ECR con `${{ github.sha }}` y `:latest`. Inicialización de Terraform con tabla DynamoDB `solventa-tfstate-lock-${ACCOUNT_ID}`.
- `.github/workflows/ci.yml`: Bandera `--cov-fail-under=85` y actualización de SonarCloud action a `@v6`.

### Data flows

El pipeline de CD genera la imagen y la publica en ECR con tag inmutable SHA. ECS Fargate ejecuta la tarea monitoreando los health checks del target group; ante fallas consecutivas, el circuit breaker detiene el despliegue e inicia rollback a la revisión previa sin intervención humana.

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| Deployment circuit breaker con rollback automático | CTL-02 | Rollback manual: aumentaría el tiempo fuera de servicio ante regresiones |
| Deletion protection y backups de 7 días en RDS | CTL-01 | Sin backups: riesgo inaceptable de pérdida de información en producción |
| Bloqueo distribuido en DynamoDB | CTL-03 | Backend S3 sin bloqueo: riesgo de corrupción de estado en despliegues paralelos |
| Tagging por SHA en ECR | CTL-05 | Solo :latest: se sobreescribe y expira tras 30 días según lifecycle policy |

### Configuration and deployment

- Requiere permisos en IAM de despliegue para gestionar DynamoDB (`dynamodb:CreateTable`, `dynamodb:DescribeTable`, `dynamodb:GetItem`, `dynamodb:PutItem`, `dynamodb:DeleteItem`).
- No requiere downtime para su aplicación.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Configurar etiquetado SHA en ECR y circuit breaker con rollback en ECS | AC-01, AC-02, SR-02, SR-05 | CTL-02, CTL-05 | DONE | .github/workflows/deploy.yml, terraform/modules/ecs/main.tf |
| T-02 | Configurar backups de 7 días, snapshot final y deletion protection en RDS | AC-03, SR-01 | CTL-01 | DONE | terraform/modules/rds/main.tf, terraform/modules/rds/variables.tf |
| T-03 | Configurar bloqueo distribuido DynamoDB para backend de Terraform | AC-04, SR-03 | CTL-03 | DONE | .github/workflows/deploy.yml, .github/workflows/destroy.yml |
| T-04 | Aprovisionar alarmas de CloudWatch para 5XX y hosts no sanos en ALB | AC-05, SR-04 | CTL-04 | DONE | terraform/modules/alb/main.tf |
| T-05 | Exigir cobertura mínima de 85% en pytest, actualizar SonarQube v6 y eliminar deploy-local.yml | AC-06, AC-07 | CTL-06 | DONE | .github/workflows/ci.yml, backend/pyproject.toml |

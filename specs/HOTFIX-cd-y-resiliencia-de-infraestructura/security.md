# Security and privacy assessment · HOTFIX

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Imágenes de contenedor Docker del backend en ECR | CONFIDENTIAL | NO | NO | Empaquetado inmutable del código fuente y dependencias del backend |
| DATA-02 | Respaldos automáticos y snapshots de la base de datos PostgreSQL | RESTRICTED | NO | NO | Recuperación ante desastres y continuidad del negocio |
| DATA-03 | Estado de Terraform y bloqueos distribuidos en DynamoDB | RESTRICTED | NO | NO | Gestión consistente y segura de la infraestructura en AWS |

## 2. Data lifecycle

### DATA-01 · Imágenes en ECR

| Question | Answer |
|---|---|
| What it contains | Binarios de ejecución, código Python y dependencias |
| Why it is needed | Desplegar contenedores en ECS Fargate |
| Who can access it | Roles de ejecución de ECS y pipeline de GitHub Actions |
| Where it is captured | Construcción en GitHub Actions |
| How it is transmitted | TLS hacia Amazon ECR |
| Where it is stored | Repositorio privado ECR en us-east-1 |
| Copies, caches and backups | Imágenes replicadas internamente por AWS ECR |
| How long it is kept | Tag SHA inmutable permanente |
| How access is audited | AWS CloudTrail |
| How it is deleted or anonymized | Políticas de ciclo de vida administradas |
| What happens if it leaks | Exposición de propiedad intelectual |
| Transfers to third parties | AWS |

### DATA-02 · Respaldos de PostgreSQL

| Question | Answer |
|---|---|
| What it contains | Tablas y esquemas de la aseguradora |
| Why it is needed | Resiliencia de datos y recuperación ante contingencias |
| Who can access it | Proceso interno de AWS RDS en la VPC |
| Where it is captured | Respaldos continuos automáticos de RDS |
| How it is transmitted | En la red interna de AWS cifrado |
| Where it is stored | Almacenamiento RDS cifrado con llave KMS de la base |
| Copies, caches and backups | Snapshots retenidos por 7 días |
| How long it is kept | 7 días para backups continuos |
| How access is audited | CloudTrail |
| How it is deleted or anonymized | Eliminación automática tras periodo de retención |
| What happens if it leaks | Exposición de base de datos; protegido por cifrado KMS |
| Transfers to third parties | AWS |

### DATA-03 · Estado de Terraform

| Question | Answer |
|---|---|
| What it contains | Metadatos y recursos aprovisionados de infraestructura |
| Why it is needed | Sincronización declarativa de Terraform |
| Who can access it | Pipeline de CI/CD autenticado con IAM |
| Where it is captured | Ejecución de terraform apply |
| How it is transmitted | TLS hacia S3 y DynamoDB |
| Where it is stored | S3 bucket con versionamiento y tabla DynamoDB para bloqueos |
| Copies, caches and backups | Versiones de objetos en S3 |
| How long it is kept | Vida útil del proyecto |
| How access is audited | CloudTrail y logs de S3 |
| How it is deleted or anonymized | Manual en migración o cierre |
| What happens if it leaks | Metadatos de red y nombres de recursos expuestos |
| Transfers to third parties | AWS |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Pipeline CD | GitHub Actions | Confiable | Construir imágenes, aplicar Terraform y actualizar ECS |
| AWS ECS Fargate | Orquestador | Confiable | Gestionar el ciclo de vida de los contenedores backend |
| AWS RDS | Base de datos | Confiable | Administrar almacenamiento, replicación y snapshots |
| Atacante externo | Ninguno | No confiable | Sin acceso a ECR, RDS ni DynamoDB |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| CI a AWS | GitHub Actions y ECR/ECS/S3/DynamoDB | Credenciales temporales AWS vía IAM y llaves de acceso |
| ECS a ALB | Tareas Fargate y Balanceador | Health check HTTP en /health cada 5s |
| RDS a VPC | PostgreSQL y ECS | Reglas de Security Group en puerto 5432 y KMS |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| GitHub Actions | ECR | DATA-01 | HTTPS (TLS 1.2+) | CRY-01 |
| RDS | Almacenamiento de backup | DATA-02 | Red interna AWS | Cifrado KMS |
| GitHub Actions | S3 / DynamoDB | DATA-03 | HTTPS (TLS 1.2+) | Cifrado en reposo y TLS |

## 6. Third-party services

N/A: El cambio opera sobre infraestructura propia en AWS y GitHub Actions.

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| ALB público / privado | Interno detrás de CloudFront | Ninguna directa |
| ECR / RDS / DynamoDB | Privado en AWS | Ninguna desde internet |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | D | DATA-02 | Pérdida irreparable de base de datos por borrado accidental o falta de snapshots | Operador / script con permisos | LOW | CRITICAL | HIGH | CTL-01 |
| THR-02 | D | Backend en ECS | Indisponibilidad prolongada por despliegue de imagen corrupta | Error de compilación / runtime | MEDIUM | HIGH | HIGH | CTL-02 |
| THR-03 | T, I | DATA-03 | Corrupción del archivo de estado tfstate por ejecuciones paralelas de Terraform | Operador concurrente | LOW | HIGH | MEDIUM | CTL-03 |
| THR-04 | D | Backend / ALB | Falla no detectada de contenedores que genera errores 5XX sin alerta | Falla de infraestructura | MEDIUM | MEDIUM | MEDIUM | CTL-04 |
| THR-05 | D | DATA-01 | Pérdida de imagen base por expiración de tag latest a los 30 días en ECR | Ciclo de vida ECR | HIGH | MEDIUM | HIGH | CTL-05 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | Retención de backups por 7 días, snapshot final y deletion protection en RDS | INFRA | EXISTING | THR-01 | TC-01 | T-02 | CIS AWS Benchmark 2.3 | — |
| CTL-02 | Deployment circuit breaker con rollback automático en ECS Fargate | INFRA | EXISTING | THR-02 | TC-01 | T-01 | AWS ECS Best Practices | — |
| CTL-03 | Bloqueo distribuido de estado de Terraform con tabla DynamoDB | INFRA | EXISTING | THR-03 | TC-03 | T-03 | Terraform S3 Backend Best Practices | — |
| CTL-04 | Alarmas de CloudWatch para errores 5XX y contenedores no sanos | INFRA | EXISTING | THR-04 | TC-01 | T-04 | Well-Architected Reliability | — |
| CTL-05 | Inmutabilidad de imágenes mediante etiquetado SHA en ECR | INFRA | EXISTING | THR-05 | TC-03 | T-01 | ECR Best Practices | — |
| CTL-06 | Verificación de umbral de cobertura mínima de 85% y SonarQube v6 en CI | PROCESS | EXISTING | THR-02 | TC-04 | T-05 | Reglas de calidad y cobertura | — |
| CTL-07 | Aislamiento entre inquilinos | DATA | NOT APPLICABLE | — | — | — | — | Sin inquilinos en la arquitectura |

## 10. Cryptography

### CRY-01 · Cifrado en tránsito y reposo de artefactos

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02, DATA-03 |
| Type | TLS |
| Algorithm or service | TLS 1.2 o superior en llamadas hacia AWS API; KMS AES-256 en reposo en RDS |
| Rationale | Proteger los datos y artefactos en reposo y en tránsito |
| Key management | Llaves administradas por AWS y KMS dedicado para RDS |
| Permissions | Roles de IAM de despliegue |
| Rotation | Anual automática en KMS |
| Recovery | Restauración desde snapshot KMS |
| Failure behavior | Rechazo de conexión o lectura sin permisos |
| Tests | TC-01 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| AWS_ACCESS_KEY_ID y AWS_SECRET_ACCESS_KEY | Secretos de GitHub | Administradores del repositorio y el job de despliegue | AWS | Manual desde IAM | Desactivar la llave en IAM | sdd scan secrets en cada pull request |
| SONAR_TOKEN | Secretos de GitHub | Job de SonarCloud en CI | CI | Manual en SonarCloud | Revocar token en SonarCloud | sdd scan secrets en cada pull request |

## 12. Authentication, authorization and sessions

N/A: No modifica lógica de sesiones ni credenciales de usuario.

## 13. Data isolation and multi-tenancy

N/A: Sin inquilinos múltiples.

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | hashicorp/aws (proveedor de Terraform) | 5.100.0 | terraform | Alarmas de CloudWatch, RDS y ECS | HIGH | OSV.dev 2026-10-22 | No known vulnerabilities | EVALUATED |
| DEP-02 | SonarSource/sonarqube-scan-action | v6 | CI/CD | Análisis de calidad de código | MEDIUM | GitHub Marketplace 2026-11-11 | No known vulnerabilities | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | La tabla DynamoDB para tfstate requiere permisos IAM de creación si no existe previamente | THR-03 | LOW | LOW | LOW | MITIGATE | Bajo: el workflow deploy.yml verifica y crea la tabla automáticamente | MITIGATED |

## 16. Pending decisions

N/A: Decisiones de arquitectura consensuadas en RC-36.

# Security and privacy assessment · HOTFIX

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Llave de firma de tokens (JWT_SECRET_KEY) | RESTRICTED | NO | NO | Firmar y verificar los tokens de asesores y asegurados |
| DATA-02 | Llave del MAC de los códigos de un solo uso (OTP_SECRET_KEY) | RESTRICTED | NO | NO | Guardar los códigos como MAC y verificarlos |
| DATA-03 | Cuenta de servicio de Firebase (FIREBASE_CREDENTIALS_JSON), con su llave privada | RESTRICTED | NO | NO | Enviar push al asegurado (HU-MOV-11) |
| DATA-04 | Llave compartida con el oráculo de eventos (ORACLE_SECRET) | RESTRICTED | NO | NO | Verificar el HMAC de los eventos paramétricos (HU-MOV-10, RC-26) |
| DATA-05 | Contraseña de RDS (DB_PASSWORD), ya en Secrets Manager | RESTRICTED | NO | NO | Conectar el backend con PostgreSQL; este hotfix solo comparte su política IAM |

## 2. Data lifecycle

### DATA-01, DATA-02, DATA-03, DATA-04, DATA-05 · Secretos del backend

| Question | Answer |
|---|---|
| What it contains | Dos llaves aleatorias de 32 caracteres o más, el JSON de una cuenta de servicio de Firebase, la llave compartida con el oráculo y la contraseña de RDS |
| Why it is needed | Firmar tokens, proteger los códigos, enviar push, verificar eventos del oráculo y conectar con la base |
| Who can access it | Administradores del repositorio (secretos de GitHub), el job de despliegue, quien lea el estado de Terraform, el rol de ejecución de ECS y el proceso del backend. Ya no quien solo lea la definición de tarea |
| Where it is captured | Una persona los genera y los guarda en GitHub sin mostrarlos; DB_PASSWORD puede generarla Terraform |
| How it is transmitted | De GitHub al runner como variables enmascaradas; del runner a Secrets Manager y del agente de ECS a Secrets Manager por las API de AWS sobre TLS |
| Where it is stored | Secretos de GitHub, Secrets Manager cifrado con la llave KMS de RDS, estado de Terraform en S3 y memoria del contenedor |
| Copies, caches and backups | Versiones anteriores del estado en el bucket versionado; versiones anteriores del secreto en Secrets Manager (AWSPREVIOUS); revisiones anteriores de la definición de tarea con los valores en claro hasta que se borren (T-05), incluidas las INACTIVE, que `DescribeTaskDefinition` sigue mostrando; posible copia en claro en CloudTrail, no verificada: los eventos `RegisterTaskDefinition` anteriores al hotfix pueden traer `containerDefinitions` con el bloque `environment` en sus parámetros, en el historial de eventos (90 días) y en cualquier trail que los guarde en S3. Borrar las revisiones no borra esa copia, así que es un argumento más para rotar (T-06) |
| How long it is kept | Hasta que se cambien en GitHub; las revisiones y versiones viejas conservan los valores anteriores |
| How access is audited | Historial de ejecuciones de GitHub Actions; CloudTrail registra GetSecretValue y kms:Decrypt |
| How it is deleted or anonymized | Se cambia el secreto en GitHub y se despliega; las revisiones viejas de la tarea se desregistran y borran; el apagado borra los secretos sin ventana de recuperación |
| What happens if it leaks | JWT: se fabrican tokens; OTP: se verifican códigos; Firebase: se envían push en nombre del proyecto; oráculo: se fabrican eventos paramétricos; RDS: se entra a la base desde la red del backend. Se genera un valor nuevo, se revoca la cuenta de servicio en Firebase o se acuerda una llave nueva con el oráculo, y se despliega |
| Transfers to third parties | GitHub guarda los secretos y AWS el estado y Secrets Manager; Google emite la cuenta de servicio de Firebase; el oráculo conoce su llave compartida |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Pipeline de despliegue | GitHub Actions con el usuario de despliegue de AWS | Confiable con los secretos de CI | Aplicar Terraform: crear secretos, políticas y la definición de tarea |
| Agente de ECS | Rol de ejecución de la tarea | Confiable | Leer los secretos de la tarea, descifrarlos e inyectarlos al arrancar |
| Proceso del backend | Rol de la tarea | Confiable mientras no se comprometa | Usar S3, SQS y la llave del bucket; no Secrets Manager |
| Operación con lectura de ECS | Identidad de AWS con ecs:Describe* | Parcialmente confiable | Leer la definición de tarea: ahora solo ve ARN |
| Atacante con credenciales filtradas o código en el contenedor | Ninguno | No confiable | Lo que permita la identidad que obtuvo |
| Atacante externo | Ninguno | No confiable | Llamar la API pública, incluido POST /oracle/events |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| CI hacia la nube | GitHub Actions y AWS | Credenciales del pipeline; las variables sensibles se validan en Terraform |
| Definición de tarea frente a Secrets Manager | Lectores de ECS y el valor del secreto | IAM: GetSecretValue solo para el rol de ejecución y solo sobre los ARN de la tarea; kms:Decrypt solo sobre la llave |
| Arranque del backend | Entorno del contenedor y la aplicación | Llaves presentes, de 32 caracteres o más y distintas |
| API pública | Internet y el oráculo | HMAC del evento con ORACLE_SECRET; sin llave, 503 |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Secretos de GitHub | Job de Terraform | DATA-01, DATA-02, DATA-03, DATA-04 | Variables TF_VAR_* enmascaradas | CTL-06 |
| Job de Terraform | Secrets Manager y estado en S3 | DATA-01, DATA-02, DATA-03, DATA-04 | API de AWS sobre TLS | CRY-01, CTL-04 |
| Secrets Manager | Agente de ECS | DATA-01, DATA-02, DATA-03, DATA-04, DATA-05 | API de AWS sobre TLS con el rol de ejecución | CRY-01, CTL-01, CTL-02 |
| Agente de ECS | Proceso del backend | DATA-01, DATA-02, DATA-03, DATA-04, DATA-05 | Variables de entorno del contenedor | CTL-01 |

## 6. Third-party services

| Service | Use | Data shared | Location | Controls |
|---|---|---|---|---|
| Firebase Cloud Messaging | Push al asegurado | DATA-03 la identifica ante Google; no cambia lo que se envía | Google, global | CTL-01, CTL-03 |
| Oráculo de eventos | Envía eventos paramétricos firmados | DATA-04 compartida por un canal privado | Fuera de AWS, operado por el oráculo | CTL-01, CTL-03 |
| AWS Secrets Manager y KMS | Guardar y descifrar los secretos | DATA-01, DATA-02, DATA-03, DATA-04, DATA-05 | us-east-1 | CTL-02, CTL-04 |

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| ecs:DescribeTaskDefinition y la consola de ECS | Interna, identidades de la cuenta | Ninguno; leen la definición |
| Revisiones anteriores de la definición de tarea, ACTIVE e INACTIVE | Interna, identidades de la cuenta | Ninguno; conservan valores en claro hasta borrarlas (T-05) |
| Eventos RegisterTaskDefinition en CloudTrail (no verificado) | Interna, lectores de CloudTrail y del bucket del trail | Ninguno; pueden conservar valores en claro anteriores al hotfix |
| secretsmanager:GetSecretValue | Interna, solo el rol de ejecución | Ninguno |
| Estado de Terraform en S3 | Interna, usuario de despliegue y administradores | Ninguno |
| POST /oracle/events | Pública | Cuerpo, marca de tiempo y firma |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | I | DATA-01, DATA-02, DATA-03, DATA-04 | Leer los secretos en la definición de tarea vigente | Operación con lectura de ECS o credenciales filtradas | MEDIUM | CRITICAL | CRITICAL | CTL-01, CTL-02 |
| THR-02 | I | DATA-01, DATA-02, DATA-03, DATA-04 | Leer los secretos en revisiones anteriores de la definición de tarea | Operación con lectura de ECS o credenciales filtradas | MEDIUM | CRITICAL | CRITICAL | CTL-05 |
| THR-03 | I | DATA-01, DATA-02, DATA-03, DATA-04, DATA-05 | Leer los secretos en el estado de Terraform | Identidad con lectura del bucket de estado | LOW | CRITICAL | HIGH | CTL-08 |
| THR-04 | E, I | DATA-01, DATA-02, DATA-03, DATA-04, DATA-05 | Usar un rol de la tarea para leer secretos que no son suyos | Atacante con código en el contenedor | LOW | HIGH | MEDIUM | CTL-02 |
| THR-05 | S, T | DATA-04 | Fabricar eventos del oráculo con HMAC de llave vacía cuando ORACLE_SECRET llega vacío | Atacante externo | MEDIUM | HIGH | HIGH | CTL-03 |
| THR-06 | D | Servicio del backend | La tarea no arranca porque el rol no puede leer o descifrar un secreto, o porque el secreto no tiene versión | Error de configuración | MEDIUM | MEDIUM | MEDIUM | CTL-07 |
| THR-07 | I | DATA-01, DATA-02, DATA-03, DATA-04 | Mostrar los valores en el plan, en los registros de CI o en el código | Persona con acceso a los registros de Actions | LOW | HIGH | MEDIUM | CTL-06 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | Secretos inyectados con secrets y valueFrom al ARN de Secrets Manager; ninguno en environment | INFRA, SECRETS | EXISTING | THR-01, DATA-01, DATA-02, DATA-03, DATA-04 | TC-01, TC-05 | T-01, T-03 | CWE-312, CWE-522, ASVS 5.0 V13 | — |
| CTL-02 | Rol de ejecución con GetSecretValue solo sobre los ARN de la tarea y kms:Decrypt solo sobre su llave y solo a través de Secrets Manager (kms:ViaService); rol de la tarea sin Secrets Manager | INFRA | EXISTING | THR-01, THR-04 | TC-02 | T-02, T-03 | CWE-250, CWE-269 | — |
| CTL-03 | Secretos opcionales vacíos sin secreto ni variable en la tarea; oracle_secret vacía o de 32 caracteres o más en Terraform; el backend trata ORACLE_SECRET vacío como ausente | INFRA, SECRETS | EXISTING | THR-05 | TC-03, TC-09 | T-01, T-03, T-09 | CWE-1188, CWE-321 | — |
| CTL-04 | Secretos cifrados en reposo con la llave KMS administrada por el cliente, con rotación | CRYPTO, SECRETS | EXISTING | THR-01, DATA-01, DATA-02, DATA-03, DATA-04 | TC-01 | T-01, T-03 | CWE-311 | — |
| CTL-05 | Después de un despliegue estable, desregistrar las revisiones ACTIVE anteriores, borrar todas las INACTIVE con delete-task-definitions (máximo 10 ARN por llamada), verificar que ya no se pueden describir y rotar los valores expuestos | PROCESS | MISSING | THR-02 | TC-06 | T-05, T-06 | CWE-312, CWE-324 | — |
| CTL-06 | Variables de Terraform sensibles, llaves sin valor por defecto, valores solo desde los secretos de GitHub | INFRA, SECRETS | EXISTING | THR-07 | TC-04, TC-07 | T-04 | CWE-798, CWE-532 | — |
| CTL-07 | Definición de tarea registrada después de las versiones de los secretos y circuit breaker con reversión | INFRA | EXISTING | THR-06 | TC-05 | T-01, T-07 | CWE-754 | — |
| CTL-08 | Valores fuera del estado de Terraform con argumentos write-only | INFRA, SECRETS | MISSING | THR-03 | TC-08 | T-08 | CWE-312 | — |

## 10. Cryptography

### CRY-01 · Secretos en reposo en Secrets Manager

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02, DATA-03, DATA-04, DATA-05 |
| Type | AT_REST |
| Algorithm or service | AWS Secrets Manager con cifrado de sobre de AWS KMS (AES-256-GCM) usando la llave administrada por el cliente de RDS |
| Rationale | Sacar los valores de la definición de tarea y condicionar su lectura a IAM y a la llave |
| Key management | Llave KMS `aws_kms_key.rds` con rotación anual automática y política por defecto de la cuenta; una llave para el secreto de RDS y los del backend (DEC-03) |
| Permissions | Rol de ejecución de ECS (kms:Decrypt); usuario de despliegue y administradores |
| Rotation | Llave KMS automática; valores manuales: secreto nuevo en GitHub y despliegue |
| Recovery | Generar valores nuevos y desplegar; con JWT_SECRET_KEY las sesiones inician de nuevo |
| Failure behavior | Sin permiso o sin versión, ECS no arranca la tarea y el circuit breaker revierte al despliegue anterior |
| Tests | TC-01, TC-05 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| JWT_SECRET_KEY | Secreto de GitHub, variable de Terraform, Secrets Manager (solventa-jwt-secret-key-ENV), estado de Terraform; backend/.env en local | Administradores del repositorio, job de despliegue, lectores del estado, rol de ejecución | AWS y cada ambiente local, con valores distintos | Manual: secreto nuevo y despliegue; cierra todas las sesiones | Cambiarla invalida todos los tokens | sdd scan secrets en cada pull request; CloudTrail de GetSecretValue |
| OTP_SECRET_KEY | Igual que JWT_SECRET_KEY (solventa-otp-secret-key-ENV) | Igual que JWT_SECRET_KEY | AWS y cada ambiente local, con valores distintos | Manual: secreto nuevo y despliegue | Cambiarla invalida los códigos pendientes | Igual que JWT_SECRET_KEY |
| FIREBASE_CREDENTIALS_JSON | Secreto de GitHub, variable de Terraform, Secrets Manager (solventa-firebase-credentials-json-ENV) si tiene valor, estado de Terraform | Igual que JWT_SECRET_KEY | AWS | Manual: llave nueva de la cuenta de servicio en Firebase, secreto nuevo, despliegue y borrado de la llave anterior de la cuenta de servicio, que mientras exista sigue sirviendo | Borrar la llave de la cuenta de servicio en la consola de Firebase | Igual que JWT_SECRET_KEY |
| ORACLE_SECRET | Secreto de GitHub, variable de Terraform, Secrets Manager (solventa-oracle-secret-ENV) si tiene valor, estado de Terraform | Igual que JWT_SECRET_KEY, y el oráculo | AWS | Manual y coordinada con el oráculo | Cambiarla en los dos lados | Igual que JWT_SECRET_KEY |
| DB_PASSWORD | Sin cambios: Secrets Manager (solventa-rds-password-ENV) | Rol de ejecución, ahora en la misma política | AWS | Sin cambios | Sin cambios | Sin cambios |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | NO | — | La validación de tokens no cambia; solo dónde se guarda la llave |
| Secure authentication | YES | CTL-01, CTL-04 | — |
| Session management | NO | — | Las sesiones no cambian; las llaves conservan su valor |
| Token expiration | NO | — | Los vencimientos no cambian |
| Revocation | YES | CTL-05 | — |
| Server-side permission checks | YES | CTL-02 | — |
| Per-resource access (ownership) | NO | — | Sin cambios en la API |
| Roles | NO | — | Los roles de la aplicación no cambian |
| Privilege separation | YES | CTL-02 | — |
| Brute-force protection | NO | — | Sin cambios |
| Rate limiting | NO | — | Sin cambios |
| One-time code (OTP) handling | YES | CTL-01 | — |
| User enumeration prevention | NO | — | Sin cambios en la API |
| CSRF protection | NO | — | Sin cambios en la API |

## 13. Data isolation and multi-tenancy

N/A: no hay inquilinos; los secretos son del único ambiente desplegado.

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | hashicorp/aws (proveedor de Terraform) | 5.100.0, sin cambios en .terraform.lock.hcl | terraform | aws_secretsmanager_secret, aws_secretsmanager_secret_version | HIGH | Revisión de .terraform.lock.hcl 2026-11-15: versión fijada existente; recursos ya usados por el módulo rds | Sin dependencia nueva ni actualizada | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | Los valores siguen en el estado de Terraform (aws_secretsmanager_secret_version) y en sus versiones anteriores en el bucket | THR-03 | LOW | CRITICAL | HIGH | MITIGATE | Alto hasta usar argumentos write-only (T-08) y endurecer el bucket de estado (ADR 0019) | OPEN |
| RISK-02 | Las revisiones anteriores de la definición de tarea conservan los valores en claro, también las INACTIVE (desregistrar no basta: DescribeTaskDefinition las sigue mostrando); CloudTrail puede tener otra copia (no verificado), y quien los haya leído los conoce | THR-02 | MEDIUM | CRITICAL | CRITICAL | MITIGATE | Crítico hasta borrar ACTIVE anteriores e INACTIVE y verificar que no se pueden describir (T-05, docs/ci-cd.md), y rotar los valores (T-06), que es lo único que cubre la posible copia en CloudTrail | OPEN |
| RISK-03 | Una sola llave KMS cifra la base, su contraseña y los secretos del backend: quien pueda descifrar con ella para un uso puede hacerlo para todos | THR-04 | LOW | HIGH | MEDIUM | MITIGATE | Medio; desaparece con una llave dedicada a secretos (DEC-03) | OPEN |
| RISK-04 | Si la tarea no logra leer los secretos, el despliegue falla y el circuit breaker revierte a la revisión anterior, que vuelve a tener los valores en claro; y después de borrar las revisiones anteriores (T-05) el circuit breaker ya no tiene a dónde volver | THR-06 | MEDIUM | MEDIUM | MEDIUM | MITIGATE | Medio: la definición depende de las versiones de los secretos y de la política del rol de ejecución; T-05 se hace solo con el despliegue estable (rolloutState COMPLETED y /health en 200, T-07), y una falla posterior se corrige con un despliegue nuevo, no con reversión | OPEN |

Observación fuera de alcance: si `DB_PASSWORD` llega vacía al contenedor, `assemble_db_connection`
(`backend/app/core/config.py`) no arma la URL de RDS y el backend se queda con el `DATABASE_URL` por
defecto, que apunta a `localhost` con las credenciales de desarrollo. En AWS no conecta, pero arranca con
una configuración engañosa en vez de fallar. Se propone un hotfix futuro: con `ENVIRONMENT` distinto de
local, exigir `DB_HOST`, `DB_USER`, `DB_PASSWORD` y `DB_NAME` o un `DATABASE_URL` explícito, y fallar el
arranque sin mostrar valores, como ya hace `load_settings` con las llaves.

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Generar JWT_SECRET_KEY y OTP_SECRET_KEY en Terraform con random_password, como DB_PASSWORD | Generarlas en Terraform; seguir con los secretos de GitHub | No por ahora: cambiar la llave al desplegar cierra todas las sesiones e invalida los códigos. Hacerlo en una ventana planificada, junto con la rotación de T-06, y retirar después los secretos de GitHub | Responsable del despliegue | NO | PENDING |
| DEC-02 | Sacar los valores del estado | secret_string_wo con Terraform 1.11 o superior; cargar los valores con la CLI fuera de Terraform; aceptar el riesgo | secret_string_wo: los workflows fijan Terraform 1.10.5, así que requiere subir la versión y llevar un número de versión por secreto | Responsable del despliegue | NO | PENDING |
| DEC-03 | Llave KMS dedicada a los secretos | Reusar la de RDS; llave nueva con política limitada al rol de ejecución | Llave nueva cuando se escriba el ADR de DEC-04 | Responsable del despliegue | NO | PENDING |
| DEC-04 | ADR que reemplaza la regla 5 del ADR 0018 (secretos como variables de entorno) | Nuevo ADR de gestión de secretos; enmendar el 0018 | Resuelto con un ADR nuevo: docs/adr/0029-terraform-backend-secrets.md, en estado Proposed (los secretos viven en Secrets Manager con KMS y llegan por secrets); la regla 5 del 0018 queda marcada como reemplazada. Aceptarlo es del equipo | Equipo | NO | PENDING |

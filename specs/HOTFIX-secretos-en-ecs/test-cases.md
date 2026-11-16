# Test cases · HOTFIX

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | UC-01 | Dadas las llaves en GitHub, cuando Terraform genera la definición de tarea, entonces environment no tiene secretos y secrets apunta a los ARN de Secrets Manager | TC-01 |
| SC-02 | AC-04 | Dado el rol de ejecución, cuando se revisa su política, entonces solo lee los ARN de la tarea y solo descifra con la llave; el rol de la tarea no tiene Secrets Manager | TC-02 |
| SC-03 | UC-02 | Dado FIREBASE_CREDENTIALS_JSON u ORACLE_SECRET vacío, cuando se despliega, entonces no se crea su secreto ni su variable | TC-03 |
| SC-04 | UC-02 | Dados Firebase y oráculo con valor, cuando se despliega, entonces la tarea recibe los cinco secretos y el rol lee exactamente esos cinco | TC-03 |
| SC-05 | UC-01 | Dado que falta JWT_SECRET_KEY o es corta, cuando corre el despliegue, entonces Terraform falla en la validación sin cambiar nada | TC-04 |
| SC-06 | UC-01 | Dado el despliegue en AWS, cuando arranca la tarea, entonces el backend responde /health, se inicia sesión y DescribeTaskDefinition no muestra valores | TC-05 |
| SC-07 | UC-01 | Dadas revisiones anteriores con valores en claro, cuando el equipo termina el despliegue, entonces solo queda activa la revisión nueva y las anteriores se borran | TC-06 |
| SC-08 | UC-03 | Dado un secreto nuevo en GitHub, cuando se despliega, entonces Secrets Manager guarda una versión nueva y las tareas nuevas la leen | TC-05 |
| SC-09 | UC-01 | Dadas sesiones abiertas antes del despliegue, cuando cambia la definición de tarea sin cambiar las llaves, entonces los tokens siguen siendo válidos | TC-05 |
| SC-10 | AC-06 | Dado ORACLE_SECRET sin valor, cuando llega un evento firmado con una llave vacía, entonces la API responde 503 | TC-03 |
| SC-11 | UC-01 | Dado el estado de Terraform, cuando alguien lo lee, entonces no encuentra los valores | TC-08 |
| SC-12 | UC-01 | Dado un secreto creado a mano con el mismo nombre, cuando se despliega, entonces el apply falla y no cambia la tarea | OUT OF SCOPE: comportamiento estándar de Secrets Manager; el paso previo está en docs/ci-cd.md |
| SC-13 | AC-07 | Dada una versión de secreto que aún no existe, cuando ECS registra la tarea, entonces espera a la versión | TC-05 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-06 | — |
| Alternate flows | YES | SC-03, SC-04, SC-12 | — |
| Invalid input and errors | YES | SC-05, SC-10 | — |
| Limits and boundaries | YES | SC-03, SC-04 | — |
| Related flows before and after | YES | SC-07, SC-08, SC-11 | — |
| Several users, sessions, devices or roles | YES | SC-02, SC-09 | — |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-07, SC-09, SC-13 | — |

## Test cases

### TC-01 · Secretos inyectados por referencia y cifrados con KMS

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-03, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | STATIC |
| Preconditions | Módulo ECS con proveedor de AWS simulado y valores ficticios |
| Procedure | 1. terraform test. 2. Revisar environment y secrets de la definición de tarea. 3. Revisar kms_key_id de los secretos |
| Expected result | environment sin JWT_SECRET_KEY, OTP_SECRET_KEY, FIREBASE_CREDENTIALS_JSON, ORACLE_SECRET ni DB_PASSWORD, y sin el valor de ninguna llave; cada entrada de secrets apunta al ARN de su secreto; los secretos usan la llave de RDS |
| Tool or method | terraform test, run llaves_obligatorias_como_secretos de terraform/tests/secretos_en_ecs.tftest.hcl |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | terraform/tests/secretos_en_ecs.tftest.hcl; corrida local 2026-11-15 con Terraform 1.14.9: 3 passed, 0 failed. Al volver a poner JWT_SECRET_KEY en environment, la prueba falla con dos aserciones |
| Status | PASS |

### TC-02 · Mínimo privilegio en los roles

| Field | Value |
|---|---|
| Covers | AC-04, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | STATIC |
| Preconditions | Igual que TC-01 |
| Procedure | 1. terraform test. 2. Comparar los recursos de la política del rol de ejecución con los ARN de la tarea. 3. Revisar la política del rol de la tarea |
| Expected result | GetSecretValue solo sobre el secreto de RDS y los del backend; kms:Decrypt solo sobre la llave; la política del rol de la tarea no menciona secretsmanager |
| Tool or method | terraform test, runs llaves_obligatorias_como_secretos y secretos_opcionales_cuando_existen |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | terraform/tests/secretos_en_ecs.tftest.hcl; corrida local 2026-11-15: 3 passed, 0 failed |
| Status | PASS |

### TC-03 · Secretos opcionales solo cuando tienen valor

| Field | Value |
|---|---|
| Covers | AC-06, AC-09, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-05 |
| Level | STATIC |
| Preconditions | Igual que TC-01, con y sin FIREBASE_CREDENTIALS_JSON y ORACLE_SECRET |
| Procedure | 1. Sin valores: revisar los nombres de secrets. 2. Con valores: revisar los nombres de secrets, los recursos del rol y el nombre del secreto del oráculo. 3. Confirmar con Settings del backend que una variable ausente, vacía o en blanco queda en None y que una llave vacía responde 503 |
| Expected result | Sin valores, solo DB_PASSWORD, JWT_SECRET_KEY y OTP_SECRET_KEY; con valores, los cinco y el rol lee exactamente cinco ARN; ORACLE_SECRET ausente, vacío o en blanco queda en None y el backend responde 503 sin verificar firmas |
| Tool or method | terraform test y Settings del backend cargado con llaves ficticias |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | terraform/tests/secretos_en_ecs.tftest.hcl; backend/tests/integration/test_oracle_events_api.py::test_sin_llave_configurada_el_canal_esta_cerrado; backend/tests/integration/test_oracle_events_api.py::test_una_llave_vacia_cierra_el_canal_y_no_valida_firmas |
| Status | PASS |

### TC-04 · Variables sensibles y configuración válida

| Field | Value |
|---|---|
| Covers | AC-05, SR-05 |
| Kind | SECURITY |
| Risk covered | THR-07 |
| Level | STATIC |
| Preconditions | Rama del hotfix con terraform init -backend=false |
| Procedure | 1. terraform fmt -check -recursive. 2. terraform validate. 3. Revisar que las cuatro variables sean sensitive en la raíz y en el módulo, y que deploy.yml y destroy.yml sigan pasando los mismos TF_VAR_* |
| Expected result | validate OK; fmt sin diferencias en los archivos del cambio; variables sensitive; JWT y OTP sin valor por defecto y validadas; mismos secretos de GitHub |
| Tool or method | Terraform 1.14.9 en local |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | Corrida local 2026-11-15: validate "Success! The configuration is valid."; fmt solo señala terraform/modules/rds/main.tf, alineación previa fuera del cambio; terraform/variables.tf y terraform/modules/ecs/variables.tf con sensitive = true; .github/workflows/deploy.yml sin cambios |
| Status | PASS |

### TC-05 · Despliegue con los secretos desde Secrets Manager

| Field | Value |
|---|---|
| Covers | AC-02, AC-07, AC-08, SR-01, SR-06 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | E2E |
| Preconditions | Cambio fusionado a main y despliegue aprobado |
| Procedure | 1. Esperar el workflow de despliegue. 2. Consultar /health. 3. Iniciar sesión como asesor de prueba y pedir un código. 4. Llamar una ruta con un token emitido antes del despliegue. 5. aws ecs describe-task-definition de la revisión nueva, revisando solo los nombres de environment y secrets |
| Expected result | La tarea arranca; /health responde 200; el inicio de sesión y el código funcionan; el token anterior sigue válido; la definición solo tiene ARN en secrets |
| Tool or method | Workflow de despliegue y AWS CLI |
| Automation | MANUAL |
| Environment | DEV |
| Evidence | PENDING |
| Status | PENDING |

### TC-06 · Revisiones anteriores borradas y valores rotados

| Field | Value |
|---|---|
| Covers | SR-04 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | E2E |
| Preconditions | TC-05 en PASS |
| Procedure | 1. aws ecs list-task-definitions --family-prefix solventa-backend-production --status ACTIVE. 2. Desregistrar las anteriores y borrarlas con aws ecs delete-task-definitions. 3. Confirmar la rotación decidida en T-06 |
| Expected result | Solo la revisión nueva queda activa; las anteriores no se pueden describir; los valores rotados no coinciden con los expuestos |
| Tool or method | AWS CLI |
| Automation | MANUAL |
| Environment | DEV |
| Evidence | PENDING |
| Status | PENDING |

### TC-07 · Sin secretos en el cambio

| Field | Value |
|---|---|
| Covers | SR-05 |
| Kind | SECURITY |
| Risk covered | THR-07 |
| Level | STATIC |
| Preconditions | Rama del hotfix |
| Procedure | 1. python sdd/tools/sdd.py scan secrets --diff origin/dev |
| Expected result | 0 posibles secretos en las líneas nuevas; las pruebas usan valores ficticios |
| Tool or method | python sdd/tools/sdd.py scan secrets --diff origin/dev |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | Corrida local 2026-11-15: 0 posibles secretos |
| Status | PASS |

### TC-08 · Valores fuera del estado

| Field | Value |
|---|---|
| Covers | SR-07 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | STATIC |
| Preconditions | T-08 hecho: secret_string_wo y Terraform 1.11 o superior |
| Procedure | 1. terraform show -json de un plan con valores ficticios. 2. Buscar el nombre del argumento del valor |
| Expected result | El plan y el estado no contienen el valor del secreto |
| Tool or method | terraform show -json en local con proveedor simulado |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-03 | — |
| User without permissions | YES | TC-02 | — |
| Access to another user's resources | NO | — | La API no cambia |
| Cross-tenant access | NO | — | No hay inquilinos |
| Malicious input | NO | — | No se agregan entradas; el oráculo conserva su validación |
| Expired tokens | NO | — | Los vencimientos no cambian |
| Revoked sessions | YES | TC-05 | — |
| Tampered data | YES | TC-03 | — |
| Wrong keys | YES | TC-03 | — |
| External service failures | YES | TC-05 | — |
| Race conditions | NO | — | El orden entre versión del secreto y tarea lo fija depends_on (TC-05) |
| Information exposure | YES | TC-01, TC-06, TC-08 | — |
| Secrets in code | YES | TC-07 | — |
| Insecure configuration | YES | TC-01, TC-02, TC-04 | — |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas ni actualizadas (DEP-01) |

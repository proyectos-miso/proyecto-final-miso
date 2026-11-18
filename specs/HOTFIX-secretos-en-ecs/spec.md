# HOTFIX · Secretos en ECS

| Field | Value |
|---|---|
| ID | HOTFIX |
| Title | Secretos en ECS |
| User story | none: hotfix de seguridad, hallazgo de la revisión del tablero operativo del 2026-11-15 |
| Owner | Juan Camilo Peña |
| Status | IN_PROGRESS |
| Created | 2026-11-15 |
| Security profile | HIGH |

## 1. Requirements

### User story

Hallazgo de la revisión del tablero operativo del 2026-11-15:

- En `terraform/modules/ecs/main.tf`, `JWT_SECRET_KEY`, `OTP_SECRET_KEY`, `FIREBASE_CREDENTIALS_JSON` y `ORACLE_SECRET` llegan al contenedor en el bloque `environment` de la definición de tarea, en texto plano.
- Cualquiera con `ecs:DescribeTaskDefinition` los lee, y quedan en el historial de revisiones de la definición de tarea y en el estado de Terraform.
- `DB_PASSWORD` ya se resolvió con Secrets Manager y KMS en `specs/HOTFIX-configuracion-de-produccion` (RC-33): los otros cuatro deben seguir el mismo camino.

Restricciones del encargo:

- Mantener la entrada actual (`TF_VAR_*` desde los secretos de GitHub) para que el equipo no tenga que cambiar los secretos.
- El backend no cambia la forma de leer los secretos: sigue leyendo variables de entorno, sin código para consultar Secrets Manager. El único cambio de backend es el tratamiento de `ORACLE_SECRET` vacío (T-09, AC-09).
- No generar `JWT_SECRET_KEY` ni `OTP_SECRET_KEY` con `random_password`: cambiar la llave al desplegar cerraría todas las sesiones e invalidaría los códigos pendientes. Queda como recomendación (DEC-01).

### Problem and outcome

Las llaves de firma de tokens, del MAC de los códigos, la cuenta de servicio de Firebase y la llave del oráculo son legibles por cualquier identidad con permisos de lectura sobre ECS. Resultado: la definición de tarea solo contiene referencias (ARN) a secretos de Secrets Manager cifrados con la llave KMS del proyecto; solo el rol de ejecución de la tarea puede leerlos y descifrarlos, y el contenedor los recibe como las mismas variables de entorno de siempre.

### Goals and non-goals

Objetivos: sacar los cuatro secretos del bloque `environment`; guardarlos en Secrets Manager cifrados con KMS; inyectarlos con `secrets`; limitar el rol de ejecución a esos ARN y a esa llave; no crear secretos vacíos.

Fuera de alcance: sacar los valores del estado de Terraform (requiere argumentos write-only y Terraform 1.11 o superior, DEC-02); rotar los valores ya expuestos y borrar las revisiones viejas de la definición de tarea (acción del equipo, T-05 y T-06); generar las llaves dentro de AWS (DEC-01); una llave KMS dedicada a secretos (DEC-03); aceptar el ADR 0029 propuesto, que decide el equipo; credenciales OIDC en CI.

### Affected paths

| Path | Change |
|---|---|
| terraform/modules/ecs/main.tf | Secretos del backend en Secrets Manager, inyección con secrets, política del rol de ejecución limitada a sus ARN |
| terraform/modules/ecs/variables.tf | Descripción de db_kms_key_arn, que ahora cifra también los secretos del backend; validación de oracle_secret |
| terraform/variables.tf | Validación de oracle_secret: vacía o de 32 caracteres o más |
| terraform/tests/secretos_en_ecs.tftest.hcl | Pruebas del módulo ECS con proveedor simulado |
| docs/ci-cd.md | Sección de secretos: dónde terminan los valores y pasos posteriores al despliegue |
| backend/app/core/config.py | ORACLE_SECRET vacío o en blanco se trata como ausente |
| backend/app/modules/claims/oracle.py | Nunca verifica firmas con una llave vacía |
| backend/tests/integration/test_oracle_events_api.py | Prueba de la llave vacía: el canal responde 503 |
| backend/tests/unit/test_config.py | Settings deja ORACLE_SECRET vacío o en blanco en None |
| docs/adr/0029-terraform-backend-secrets.md | ADR propuesto que reemplaza la regla 5 del ADR 0018 |
| docs/adr/0018-terraform-root-and-modules.md | Regla 5 marcada como reemplazada por el ADR 0029 e impacto en seguridad corregido |
| docs/adr/0027-backend-parametric-oracle.md | ORACLE_SECRET llega por Secrets Manager, no por el entorno de la tarea |
| docs/adr/README.md | ADR 0029 en el índice |
| AGENTS.md | Rango de ADR de Terraform con el 0029 |
| CHANGELOG.md | Entrada del hotfix |
| specs/HOTFIX-secretos-en-ecs/** | Paquete del hotfix |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Pipeline de despliegue | Desplegar el backend con sus secretos | 1. Push a main y aprobación del ambiente produccion. 2. Terraform recibe TF_VAR_* desde los secretos de GitHub. 3. Crea o actualiza los secretos en Secrets Manager. 4. Registra la definición de tarea con referencias. 5. ECS arranca la tarea y el agente inyecta los valores | Falta JWT_SECRET_KEY u OTP_SECRET_KEY o es inválida: Terraform falla la validación sin cambiar nada. El rol no puede leer un secreto: la tarea no arranca y el circuit breaker revierte |
| UC-02 | Responsable del despliegue | Activar Firebase o el oráculo | 1. Crea el secreto en GitHub. 2. Despliega. 3. Terraform crea el secreto y lo agrega a la tarea | El secreto de GitHub está vacío: no se crea en Secrets Manager y el backend apaga esa función |
| UC-03 | Responsable del despliegue | Rotar una llave | 1. Cambia el secreto en GitHub. 2. Despliega. 3. Terraform crea una versión nueva. 4. deploy.yml fuerza un despliegue nuevo del servicio y las tareas leen el valor nuevo | Las tareas ya corriendo conservan el valor viejo hasta reemplazarse |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Leer las llaves con ecs:DescribeTaskDefinition | Identidad de AWS con permisos de lectura de ECS o credenciales filtradas | THR-01 | La definición solo trae ARN; sin GetSecretValue y kms:Decrypt no se obtiene el valor | TC-01, TC-05 |
| AB-02 | Leer las llaves en revisiones anteriores de la definición de tarea | Identidad de AWS con permisos de lectura de ECS | THR-02 | Las revisiones anteriores se desregistran y borran, y las llaves expuestas se rotan | TC-06 |
| AB-03 | Usar el rol de la tarea comprometida para leer otros secretos de la cuenta | Atacante con ejecución de código en el contenedor | THR-04 | El rol de la tarea no tiene permisos de Secrets Manager; el de ejecución solo lee los ARN de la tarea | TC-02 |
| AB-04 | Enviar eventos del oráculo firmados con una llave vacía cuando ORACLE_SECRET no está configurado | Atacante externo | THR-05 | Sin el secreto la variable no existe y POST /oracle/events responde 503 | TC-03 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | THE definición de tarea de ECS SHALL NOT incluir JWT_SECRET_KEY, OTP_SECRET_KEY, FIREBASE_CREDENTIALS_JSON, ORACLE_SECRET ni DB_PASSWORD en environment | STATED |
| AC-02 | THE tarea de ECS SHALL recibir esos secretos por el bloque secrets, con valueFrom igual al ARN de su secreto en Secrets Manager | STATED |
| AC-03 | THE secretos del backend SHALL guardarse en Secrets Manager cifrados con la llave KMS que ya cifra el secreto de RDS | STATED |
| AC-04 | THE rol de ejecución SHALL poder leer solo los ARN de los secretos de la tarea y descifrar solo con esa llave, y THE rol de la tarea SHALL NOT tener permisos de Secrets Manager | STATED |
| AC-05 | THE valores SHALL seguir llegando como TF_VAR_* desde los mismos secretos de GitHub, a variables de Terraform con sensitive = true | STATED |
| AC-06 | IF FIREBASE_CREDENTIALS_JSON u ORACLE_SECRET llegan vacíos, THE despliegue SHALL NOT crear su secreto ni la variable en la tarea | DERIVED: Secrets Manager no acepta un valor vacío, y con ORACLE_SECRET vacío el backend verifica firmas con una llave vacía en vez de cerrar el canal |
| AC-07 | WHEN se despliega, THE tarea SHALL registrarse después de que exista la versión de cada secreto | DERIVED: sin la versión, la tarea no arranca |
| AC-08 | WHEN la tarea arranca en AWS, THE backend SHALL leer los secretos de las mismas variables de entorno, sin código para consultar Secrets Manager (el único cambio de backend es el de AC-09) | STATED |
| AC-09 | IF ORACLE_SECRET llega vacío o en blanco, THE backend SHALL tratarlo como ausente y responder 503 en /oracle/events, sin verificar firmas con una llave vacía | DERIVED: defensa en profundidad del AC-06 en el backend |

### Quality requirements

N/A: cambio de configuración de infraestructura; no altera latencia, disponibilidad ni pantallas. El arranque de la tarea se verifica en TC-05.

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | NO | Solo llaves y credenciales de servicio; ningún dato de personas |
| Sensitive data (special categories, biometrics, health, financial, minors) | NO | No hay datos de categorías especiales |
| Authentication, sessions, credentials or one-time codes | YES | Llave de firma de tokens, llave del MAC de los códigos y credenciales de Firebase |
| Authorization or access to other users' resources | NO | Roles de la aplicación sin cambios |
| Payments or money movement | NO | No toca pagos |
| Third-party services or integrations | YES | La cuenta de servicio de Firebase y la llave compartida con el oráculo cambian de almacenamiento |
| Infrastructure, network, IAM, CI/CD or secrets | YES | Secrets Manager, KMS, política IAM del rol de ejecución y definición de tarea de ECS |
| Multiple tenants, partners or organizations | NO | No hay inquilinos |
| Storage or processing on a mobile device | NO | La app no cambia |
| Web UI or data exposed in the browser | NO | El portal no cambia |
| New or updated dependencies | NO | Sin dependencias nuevas; el proveedor de AWS sigue en 5.100.0 |
| Critical paths (sdd/security/critical-paths.md) | YES | terraform/** |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | Ningún secreto del backend aparece en la definición de tarea; solo referencias a Secrets Manager | DATA-01, DATA-02, DATA-03, DATA-04, THR-01 | CTL-01, CTL-04 | HIGH |
| SR-02 | Solo el rol de ejecución lee esos secretos y solo descifra con su llave | DATA-05, THR-04 | CTL-02 | HIGH |
| SR-03 | Un secreto opcional vacío no se crea ni se inyecta | THR-05 | CTL-03 | HIGH |
| SR-04 | Los valores expuestos en revisiones anteriores dejan de servir | THR-02, RISK-02 | CTL-05 | HIGH |
| SR-05 | Los valores no se muestran en el plan, los registros de CI ni el código | DATA-01, DATA-02, DATA-03, DATA-04, THR-07 | CTL-06 | HIGH |
| SR-06 | La tarea arranca con los secretos o el despliegue se revierte | THR-06 | CTL-07 | MEDIUM |
| SR-07 | Los valores salen del estado de Terraform | THR-03, RISK-01 | CTL-08 | MEDIUM |

## 2. Design

### Components

- `terraform/modules/ecs/main.tf`:
  - `local.backend_secret_values` reúne las cuatro variables sensibles.
  - `local.backend_secret_names` deja solo las que no están vacías; `nonsensitive` se aplica solo a la comparación con `""`, que revela si el valor existe, nunca el valor.
  - `aws_secretsmanager_secret.backend` (uno por nombre, `${project}-<nombre>-${environment}`, cifrado con `var.db_kms_key_arn`, `recovery_window_in_days = 0` como el de RDS para que el apagado y la recreación no choquen con nombres en borrado).
  - `aws_secretsmanager_secret_version.backend` con el valor.
  - `aws_iam_role_policy.execution_secrets`: `GetSecretValue` sobre el ARN del secreto de RDS y los de `aws_secretsmanager_secret.backend`; `kms:Decrypt` sobre la llave solo con `kms:ViaService = secretsmanager.<región>.amazonaws.com`, con la región de `data.aws_region.current`. El secreto de RDS (`DB_PASSWORD`) también se descifra a través de Secrets Manager, así que la condición no lo afecta.
  - La definición de tarea pierde las cuatro entradas de `environment`, agrega una entrada en `secrets` por secreto y depende de las versiones y de la política del rol de ejecución.
- `aws_iam_role_policy.task_documents` (rol de la tarea) no cambia: S3, SQS y la llave del bucket, sin Secrets Manager.
- `backend/app/core/config.py` sigue leyendo `JWT_SECRET_KEY`, `OTP_SECRET_KEY`, `FIREBASE_CREDENTIALS_JSON` y `ORACLE_SECRET` del entorno, que ECS llena desde `secrets` igual que desde `environment`. Sí cambia por T-09: un `ORACLE_SECRET` vacío o en blanco queda en `None`, y `oracle.py` nunca verifica con una llave vacía.
- La longitud mínima de `ORACLE_SECRET` se valida en Terraform (`oracle_secret` vacía o de 32 caracteres o más, en la raíz y en el módulo), no en el backend: una validación en `Settings` tumbaría el arranque de toda la API por una función opcional; en Terraform falla el plan antes de cambiar nada.

### Data flows

Secretos de GitHub → `TF_VAR_*` del job de Terraform (enmascarados) → variables sensibles de Terraform → `aws_secretsmanager_secret_version` (API de AWS sobre TLS; cifrado con la llave KMS) y estado de Terraform en S3 → al arrancar la tarea, el agente de ECS con el rol de ejecución llama `GetSecretValue`, Secrets Manager descifra con KMS → variables de entorno del contenedor → `Settings`. Límites de confianza: GitHub a AWS, y la definición de tarea (legible por operación) frente a Secrets Manager (legible solo por el rol de ejecución).

### Data contract

N/A: la API no cambia. El contenedor recibe las mismas variables de entorno; `FIREBASE_CREDENTIALS_JSON` y `ORACLE_SECRET` ya no existen cuando su secreto de GitHub está vacío (antes existían vacías), lo que el backend trata como no configurado.

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| Secretos en Secrets Manager inyectados con secrets y valueFrom | CTL-01 | Seguir en environment: es el hallazgo. Parameter Store SecureString: el proyecto ya usa Secrets Manager para RDS |
| Reusar la llave KMS de RDS, con rotación activa | CTL-04, CRY-01 | Una llave nueva solo para secretos: mejor separación, pero agrega una llave y su política; queda como DEC-03 |
| Política del rol de ejecución con la lista exacta de ARN y la llave | CTL-02 | Un comodín sobre `secret:solventa-*`: abarcaría secretos futuros que la tarea no usa |
| kms:Decrypt condicionado a kms:ViaService de Secrets Manager | CTL-02 | Decrypt sin condición: el rol podría descifrar con la llave de RDS fuera de Secrets Manager |
| Longitud de ORACLE_SECRET validada en Terraform | CTL-03 | Validarla en Settings: un valor corto tumbaría el arranque de toda la API por una función opcional |
| Crear los opcionales solo si traen valor | CTL-03 | Crearlos siempre: Secrets Manager no acepta un valor vacío y el backend trataría un valor vacío como llave del oráculo |
| Mantener la entrada TF_VAR_* desde los secretos de GitHub | CTL-06 | Cargar los valores con la CLI en deploy.yml para sacarlos del estado: cambia el pipeline y expone argumentos del proceso; la vía correcta es write-only (DEC-02) |
| No generar JWT_SECRET_KEY ni OTP_SECRET_KEY con random_password | — | Generarlas en Terraform: cambiaría la llave en el primer despliegue y cerraría todas las sesiones (DEC-01) |

### Architecture decisions

- docs/adr/0029-terraform-backend-secrets.md (Proposed): los secretos del backend viven en Secrets Manager con una llave KMS y llegan por `secrets` de ECS. Reemplaza la regla 5 de docs/adr/0018-terraform-root-and-modules.md, que los dejaba como variables de entorno (DEC-04). El equipo lo acepta o lo cambia.

### Configuration and deployment

- No cambia ningún secreto de GitHub: siguen `JWT_SECRET_KEY`, `OTP_SECRET_KEY`, `FIREBASE_CREDENTIALS_JSON` y `ORACLE_SECRET`, que deploy.yml y destroy.yml pasan como `TF_VAR_*`.
- El primer despliegue crea los secretos `solventa-jwt-secret-key-production`, `solventa-otp-secret-key-production` y, si tienen valor, `solventa-firebase-credentials-json-production` y `solventa-oracle-secret-production`. Si ya existe en la cuenta un secreto con ese nombre creado a mano, el apply falla: hay que borrarlo o importarlo antes.
- Registra una revisión nueva de la definición de tarea; el servicio reemplaza las tareas y deploy.yml fuerza otro despliegue después. Los valores no cambian, así que no se cierran sesiones.
- Después de un despliegue estable, el equipo desregistra las revisiones ACTIVE que no son la vigente, borra todas las INACTIVE con `delete-task-definitions` (máximo 10 ARN por llamada) y verifica que una revisión vieja ya no se puede describir (T-05, procedimiento en docs/ci-cd.md). Desde ahí el circuit breaker ya no puede volver a esas revisiones. Luego rota los valores expuestos (T-06).
- `recovery_window_in_days = 0`: vaciar `FIREBASE_CREDENTIALS_JSON` u `ORACLE_SECRET` en GitHub y desplegar borra su secreto de inmediato; se recupera volviendo a cargar el valor desde su origen (docs/ci-cd.md).
- Si `ORACLE_SECRET` no existe en GitHub, el canal del oráculo pasa de aceptar firmas con llave vacía a responder 503, que es el comportamiento documentado.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Secretos del backend en Secrets Manager cifrados con KMS e inyectados con secrets; opcionales vacíos sin secreto; oracle_secret vacía o de 32 caracteres o más | AC-01, AC-02, AC-03, AC-06, AC-07, SR-01, SR-03 | CTL-01, CTL-03, CTL-04 | DONE | terraform/modules/ecs/main.tf |
| T-02 | Política del rol de ejecución limitada a los ARN de la tarea y a la llave, con kms:ViaService de Secrets Manager; rol de la tarea sin Secrets Manager | AC-04, SR-02 | CTL-02 | DONE | terraform/modules/ecs/main.tf |
| T-03 | Pruebas del módulo ECS con proveedor simulado | AC-01, AC-02, AC-03, AC-04, AC-06, SR-01, SR-02, SR-03 | CTL-01, CTL-02, CTL-03, CTL-04 | DONE | terraform/tests/secretos_en_ecs.tftest.hcl |
| T-04 | Documentar el origen de los valores y los pasos posteriores en docs/ci-cd.md y CHANGELOG.md | AC-05, AC-08, SR-05 | CTL-06 | DONE | docs/ci-cd.md |
| T-05 | Después de un despliegue estable: desregistrar las revisiones ACTIVE que no son la vigente, listar las INACTIVE, borrarlas con delete-task-definitions en lotes de máximo 10 ARN y verificar que describe-task-definition de una revisión vieja falla (docs/ci-cd.md) | SR-04 | CTL-05 | TODO | — |
| T-06 | Decidir y ejecutar la rotación de los valores expuestos (JWT_SECRET_KEY, OTP_SECRET_KEY, cuenta de servicio de Firebase con borrado de la llave anterior, ORACLE_SECRET); borrar las revisiones no alcanza, porque CloudTrail puede guardar otra copia en claro (no verificado) | SR-04 | CTL-05 | TODO | — |
| T-07 | Desplegar y verificar que la tarea arranca con los secretos y que la definición no tiene valores | AC-02, AC-07, AC-08, SR-06 | CTL-07 | TODO | — |
| T-09 | ORACLE_SECRET vacío cierra el canal también en el backend | AC-09, SR-03 | CTL-03 | DONE | backend/app/core/config.py |
| T-10 | ADR 0029 propuesto que reemplaza la regla 5 del ADR 0018 | AC-02, SR-01 | CTL-01 | DONE | docs/adr/0029-terraform-backend-secrets.md |
| T-08 | Sacar los valores del estado con argumentos write-only (Terraform 1.11 o superior en los workflows) | SR-07 | CTL-08 | TODO | — |

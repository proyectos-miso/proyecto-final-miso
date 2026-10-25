# Security and privacy assessment · HOTFIX

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Reportes JUnit y resúmenes de pruebas: nombres de pruebas, conteos y mensajes de falla | INTERNAL | NO | NO | Evidencia de la ejecución de pruebas en cada integración |
| DATA-02 | Secretos del despliegue ya existentes: credenciales de AWS, contraseña de la base de datos y llaves del backend | RESTRICTED | NO | NO | Aplicar Terraform y desplegar; este cambio no agrega secretos |
| DATA-03 | Respuestas públicas de la prueba de humo: portal, estado de salud y error 405 de la API | PUBLIC | NO | NO | Verificar que el despliegue quedó funcionando |

## 2. Data lifecycle

### DATA-02 · Secretos del despliegue

| Question | Answer |
|---|---|
| What it contains | AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, DB_PASSWORD, JWT_SECRET_KEY y OTP_SECRET_KEY (solo nombres) |
| Why it is needed | Los trabajos de despliegue los usan para llamar a AWS y configurar el backend |
| Who can access it | Administradores del repositorio; los trabajos de despliegue los reciben en tiempo de ejecución |
| Where it is captured | Secretos del repositorio en GitHub |
| How it is transmitted | GitHub los inyecta en el runner; nunca llegan a los trabajos de pruebas ni a la prueba de humo |
| Where it is stored | Secretos cifrados de GitHub; los de la aplicación también en el estado de Terraform en S3 |
| Copies, caches and backups | GitHub enmascara sus valores en los registros; los reportes de pruebas no los contienen |
| How long it is kept | Hasta su rotación manual |
| How access is audited | Registro de auditoría de GitHub y CloudTrail en AWS |
| How it is deleted or anonymized | Se borran del repositorio y se revocan en IAM |
| What happens if it leaks | Acceso a la cuenta de AWS y a la base de datos; con este cambio, además, un despliegue requiere aprobación |
| Transfers to third parties | GitHub y AWS como proveedores de la plataforma |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Integrante del equipo | Escritura en el repositorio | Confiable | Subir ramas, abrir pull requests y lanzar el CD a mano |
| Revisor del ambiente produccion | Aprobador designado | Confiable | Aprobar o rechazar un despliegue |
| Administrador del repositorio | Administrador | Confiable | Configurar protección de ramas, ambientes y secretos |
| Runner de GitHub Actions | Automatización | Confiable con permisos mínimos | Ejecutar el código de la rama con el token del flujo |
| Atacante en la cadena de suministro | Ninguno | No confiable | Modificar una acción o dependencia para robar el token o los secretos |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| Pull request → CI | Código de una rama y el runner | El token del flujo es de solo lectura y los trabajos de pruebas no reciben secretos |
| main → ambiente produccion | La rama y AWS | La ejecución viene de main y un revisor la aprueba |
| Prueba de humo → producción | El runner y CloudFront | Solo GET, sin credenciales, y solo se lee la respuesta |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Pruebas en el runner | Resumen y artefactos de la ejecución | DATA-01 | API de GitHub Actions | CTL-01, CTL-05 |
| Secretos de GitHub | Trabajos de despliegue | DATA-02 | Inyección del runner | CTL-02, CTL-03 |
| Runner | CloudFront | DATA-03 | HTTPS, solo GET | CRY-01, CTL-04 |

## 6. Third-party services

N/A: no se agregan acciones ni servicios; los reportes los generan pytest, Vitest y Gradle, y los resume un script propio.

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| Workflows de CI en pull requests | Integrantes con escritura | El código de la rama que corre en el runner |
| Lanzamiento manual del CD | Integrantes con escritura | La rama desde la que se lanza |
| Reportes JUnit | Internos a la ejecución | Los mensajes de falla que escriben las pruebas |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | E, I | Repositorio y DATA-02 | Código de una rama o una acción comprometida usa el token del flujo para escribir en el repositorio o leer secretos | Atacante en la cadena de suministro | LOW | HIGH | MEDIUM | CTL-01 |
| THR-02 | E, T | Producción en AWS | Desplegar sin aprobación o lanzar el CD desde una rama distinta de main | Integrante con escritura | MEDIUM | HIGH | HIGH | CTL-02, CTL-03 |
| THR-03 | T | Producción | La prueba de humo crea, modifica o borra datos en producción | Error del pipeline | LOW | MEDIUM | LOW | CTL-04 |
| THR-04 | I | DATA-01 | Un reporte de pruebas expone secretos o datos personales | Integrante con escritura | LOW | MEDIUM | LOW | CTL-05 |
| THR-05 | R, T | dev y main | Fusionar sin CI en verde ni revisión de otra persona | Integrante con escritura | MEDIUM | MEDIUM | MEDIUM | CTL-06 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | Token del flujo de solo lectura en CI y CD, sin acciones nuevas y sin secretos en los trabajos de pruebas | PROCESS | EXISTING | THR-01 | TC-07 | T-01 | CWE-250, CICD-SEC-5 | — |
| CTL-02 | El despliegue solo corre desde main y su primer trabajo usa el ambiente produccion | INFRA | EXISTING | THR-02 | TC-06 | T-04 | CICD-SEC-1 | — |
| CTL-03 | Ambiente produccion con revisores obligatorios y solo la rama main permitida | INFRA | MISSING | THR-02 | TC-08 | T-07 | CICD-SEC-1 | Se configura en GitHub; lo aplica el administrador |
| CTL-04 | Prueba de humo solo con GET, sin credenciales y sin cuerpo | PROCESS | EXISTING | THR-03 | TC-04 | T-05 | CWE-749 | — |
| CTL-05 | Las pruebas usan datos ficticios y el resumen solo muestra nombres y mensajes recortados | DATA | EXISTING | THR-04, DATA-01 | TC-02 | T-02 | CWE-532 | — |
| CTL-06 | Protección de dev y main: pull request, checks obligatorios y una aprobación | PROCESS | MISSING | THR-05 | TC-08 | T-07 | CICD-SEC-1 | Se configura en GitHub; lo aplica el administrador |
| CTL-07 | Aislamiento entre inquilinos | DATA | NOT APPLICABLE | — | — | — | — | No hay inquilinos en este cambio |

## 10. Cryptography

### CRY-01 · TLS entre el runner y CloudFront

| Field | Value |
|---|---|
| Protects | DATA-03 |
| Type | TLS |
| Algorithm or service | TLS 1.2 o 1.3 de CloudFront con el certificado por defecto de cloudfront.net |
| Rationale | La prueba de humo llama la URL pública por HTTPS; el cliente de Python (OpenSSL) negocia TLS 1.2 o 1.3 |
| Key management | AWS administra el certificado |
| Permissions | Solo AWS |
| Rotation | Automática por AWS |
| Recovery | No aplica; lo reemplaza AWS |
| Failure behavior | La conexión falla y la prueba de humo queda en rojo |
| Tests | TC-05 |

### CRY-02 · Secretos cifrados del repositorio

| Field | Value |
|---|---|
| Protects | DATA-02 |
| Type | AT_REST |
| Algorithm or service | Secretos cifrados de GitHub Actions (sobres sellados de libsodium) |
| Rationale | Los secretos del despliegue nunca se guardan en el código, los workflows ni los artefactos |
| Key management | GitHub administra la llave del repositorio; los valores solo se escriben, no se leen |
| Permissions | Administradores del repositorio; los trabajos de despliegue los reciben en tiempo de ejecución |
| Rotation | Manual al rotar la credencial en AWS |
| Recovery | Se vuelve a crear el secreto desde su origen (IAM o el generador de llaves) |
| Failure behavior | Sin el secreto, el paso de AWS falla y no se despliega |
| Tests | TC-07 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, DB_PASSWORD, JWT_SECRET_KEY, OTP_SECRET_KEY (sin cambios) | Secretos de GitHub | Administradores y los trabajos de despliegue | AWS | Manual | Borrar el secreto y revocar en IAM | sdd scan secrets en cada pull request; enmascarado de GitHub |

## 12. Authentication, authorization and sessions

N/A: el cambio no toca la autenticación ni los permisos de la aplicación; los permisos del pipeline están en los controles CTL-01 a CTL-06.

## 13. Data isolation and multi-tenancy

N/A: no hay inquilinos en este cambio.

## 14. Dependencies

No se agregan dependencias ni acciones. Estas son las acciones existentes que el cambio usa o reconfigura:

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | actions/upload-artifact | v4 | CI | Subir los reportes JUnit | MEDIUM | OSV.dev 2026-10-25 | No known vulnerabilities | EVALUATED |
| DEP-02 | actions/checkout | v4 | CI y CD | Obtener el código, también en la prueba de humo | HIGH | OSV.dev 2026-10-25 | No known vulnerabilities | EVALUATED |
| DEP-03 | hashicorp/setup-terraform | v3 | CD | Terraform sin envoltorio para leer la salida | HIGH | OSV.dev 2026-10-25 | No known vulnerabilities | EVALUATED |
| DEP-04 | aws-actions/configure-aws-credentials | v4 | CD | Credenciales de AWS para desplegar y esperar a ECS | CRITICAL | OSV.dev 2026-10-25 | No known vulnerabilities | EVALUATED |
| DEP-05 | actions/setup-python | v5 | CI | Python para las pruebas de los scripts | MEDIUM | OSV.dev 2026-10-25 | No known vulnerabilities | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | Mientras el administrador no configure el ambiente produccion y la protección de ramas, se puede desplegar sin aprobación y fusionar sin revisión | THR-02, THR-05 | MEDIUM | HIGH | HIGH | MITIGATE | Se cierra cuando se completa T-07 | OPEN |
| RISK-02 | Las acciones existentes del pipeline usan etiquetas de versión y no SHA fijos | THR-01 | LOW | HIGH | MEDIUM | MITIGATE | Bajo con el token de solo lectura; fijarlas por SHA queda para un cambio aparte | OPEN |

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Quién aprueba los despliegues a producción | Un integrante fijo; cualquiera menos el autor del cambio | Al menos dos revisores designados, para no depender de una sola persona | Administrador del repositorio | NO | PENDING |

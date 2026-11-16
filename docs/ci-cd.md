# Proceso de integración y despliegue continuos

Cómo se construye, se prueba, se revisa y se despliega Solventa, y qué condiciones debe cumplir un cambio
en cada paso. Plataforma: GitHub (repositorio, pull requests, Actions y Projects) y AWS (Terraform, ECR,
ECS Fargate, RDS, S3 y CloudFront).

## Recorrido de un cambio

```mermaid
flowchart LR
    A["Rama feature/HU-… o hotfix/…"] -->|push| B["CI en la rama"]
    B --> C["Pull request hacia dev"]
    C -->|"CI en verde, SonarCloud y SDD gate aprobados, 1 revisión"| D["dev"]
    D --> E["Pull request dev → main"]
    E -->|"CI en verde y 1 revisión"| F["main"]
    F --> G{"Aprobación del ambiente produccion"}
    G -->|aprobado| H["Terraform"]
    H --> I["Backend en ECS"]
    H --> J["Portal en S3 y CloudFront"]
    I --> K["Prueba de humo"]
    J --> K
    K --> L["Etiqueta y release vX.Y.Z"]
```

## Ramas y versiones

| Rama | Para qué | Se integra en | Cómo |
|---|---|---|---|
| `feature/HU-…-slug` | Una historia del tablero, con su paquete en `specs/` | `dev` | Pull request con CI en verde y una revisión de otro integrante |
| `fix/HU-…-slug` | Corrección de una historia aún no publicada | `dev` | Igual que una historia |
| `hotfix/slug` | Corrección urgente o de proceso | `dev`, y luego `main` | Pull request; el paquete de spec es obligatorio si toca una ruta crítica |
| `dev` | Integración del sprint | `main` | Pull request de release al cerrar una entrega |
| `main` | Lo que está en producción | — | Cada fusión dispara el despliegue, con aprobación |

Al cerrar cada entrega se etiqueta `main` con `vX.Y.Z` y se publica la release con la sección del
`CHANGELOG.md`. El detalle del flujo está en la página GitFlow de la wiki y en `sdd/rules/git.md`.

## Integración continua

Archivo: `.github/workflows/ci.yml`. Corre en cada push y en cada pull request, con permisos de solo lectura
sobre el repositorio.

| Trabajo | Qué verifica | Evidencia que deja |
|---|---|---|
| Backend CI | Pruebas unitarias y de integración con pytest contra PostgreSQL 16 | Resumen de pruebas en la ejecución, reporte JUnit `junit-backend` y cobertura `coverage-backend` |
| Web CI | Pruebas con Vitest y compilación de producción del portal | Resumen de pruebas, reporte JUnit `junit-web` y cobertura `coverage-web` |
| Mobile CI | Pruebas de la app: unitarias, Robolectric, Compose UI y MockWebServer | Resumen de pruebas, reporte JUnit `junit-movil` y cobertura Kover `coverage-movil` |
| Terraform CI | Formato y validez de la infraestructura, y que el plan se calcule aunque la infraestructura esté apagada | Resultado de `terraform fmt -check`, `terraform validate` y `terraform test` |
| SonarCloud | Calidad, seguridad y cobertura del código nuevo con el quality gate «Sonar way» (cobertura ≥ 80 %, duplicación ≤ 3 %, calificaciones A) | Análisis por rama y por pull request en SonarCloud |
| SDD gate | Paquetes de spec, secretos, rutas críticas y dependencias | Resultado del validador `sdd/tools/sdd.py gate` |

El resumen de pruebas de cada componente aparece en la página de la ejecución (pestaña **Summary**): total,
aprobadas, fallidas y omitidas, y el nombre de cada prueba que falló. Lo genera `scripts/ci/junit_summary.py`
a partir de los reportes JUnit que ya producen pytest, Vitest y Gradle, sin acciones de terceros.

## Condiciones para fusionar

Reglas de protección que deben estar activas en GitHub (**Settings → Branches**):

| Regla | `dev` | `main` |
|---|---|---|
| Exigir pull request antes de fusionar | Sí | Sí |
| Aprobaciones requeridas | 1, de alguien distinto del autor | 1 |
| Descartar aprobaciones al subir nuevos commits | Sí | Sí |
| Checks obligatorios en verde | Backend CI, Web CI, Mobile CI, Terraform CI, SonarCloud, SDD gate | Los mismos |
| Rama actualizada con la base antes de fusionar | Sí | Sí |
| Bloquear force push y borrado | Sí | Sí |

## Despliegue continuo

Archivo: `.github/workflows/deploy.yml`. Se dispara con cada fusión en `main` o a mano desde Actions.

1. **Control de rama.** Si la ejecución no viene de `main`, no se despliega nada.
2. **Aprobación.** El trabajo de infraestructura usa el ambiente `produccion`, que exige la aprobación de
   un revisor autorizado y solo admite la rama `main` (**Settings → Environments → produccion**).
3. **Infraestructura.** Terraform aplica la configuración con el estado remoto en S3.
4. **Backend.** Se construye la imagen, se publica en ECR, se actualiza el servicio de ECS y se espera a que
   quede estable.
5. **Portal.** Se compila, se sincroniza con S3 y se invalida la caché de CloudFront.
6. **Prueba de humo.** `scripts/ci/smoke_test.py` hace solo peticiones GET, sin credenciales: el portal
   responde, `/health` del backend responde `healthy` y una ruta de `/api` llega al backend. Si algo falla,
   la ejecución queda en rojo; el resultado se ve en el resumen de la ejecución.

Las credenciales de AWS y las llaves del backend son secretos del repositorio en GitHub; nunca se
escriben en el código ni en los registros.

## Secretos y variables del repositorio

Se configuran en **Settings → Secrets and variables → Actions**. Los valores nunca van al repositorio, a
los registros ni a los paquetes de spec.

| Nombre | Tipo | Para qué | Si falta |
|---|---|---|---|
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION` | Secretos | Despliegue con Terraform y ECR | No se despliega |
| `DB_PASSWORD`, `JWT_SECRET_KEY`, `OTP_SECRET_KEY` | Secretos | Base de datos, firma de los tokens y de los códigos | No se despliega |
| `SONAR_TOKEN` | Secreto | Análisis de SonarCloud | Falla el check de SonarCloud |
| `FIREBASE_CREDENTIALS_JSON` | Secreto | Push al asegurado (HU-MOV-11) | El backend funciona sin push; la app avisa al sincronizar |
| `ORACLE_SECRET` | Secreto | Firma de los eventos del oráculo (HU-MOV-10, RC-26) | `POST /oracle/events` responde 503: no entran siniestros paramétricos |
| `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | Secretos | Firma del APK de release (HU-MOV-09, RC-12) | El job `mobile-release` se omite |
| `ANDROID_API_BASE_URL` | Variable | URL `https` del API para el APK de release | El job `mobile-release` se omite |

**Dónde terminan los secretos del backend.** `deploy.yml` y `destroy.yml` pasan `DB_PASSWORD`,
`JWT_SECRET_KEY`, `OTP_SECRET_KEY`, `FIREBASE_CREDENTIALS_JSON` y `ORACLE_SECRET` a Terraform como
`TF_VAR_*`, a variables con `sensitive = true`. Terraform los guarda en AWS Secrets Manager, cifrados con la
llave KMS de RDS: `solventa-rds-password-production` para `DB_PASSWORD` y `solventa-<nombre>-production`
para los demás (`solventa-jwt-secret-key-production`, `solventa-otp-secret-key-production`,
`solventa-firebase-credentials-json-production`, `solventa-oracle-secret-production`). La tarea de ECS los
recibe con el bloque `secrets`: la definición de tarea solo contiene el ARN de cada uno, y solo el rol de
ejecución puede leerlos y descifrarlos, y solo a través de Secrets Manager (`kms:ViaService`). El backend
los sigue leyendo como variables de entorno (ADR 0029, `specs/HOTFIX-secretos-en-ecs`).
`FIREBASE_CREDENTIALS_JSON` y `ORACLE_SECRET` vacíos no crean secreto ni variable; `ORACLE_SECRET` con
valor debe tener al menos 32 caracteres o Terraform falla la validación. Los valores siguen en el estado de
Terraform: el bucket de estado es tan sensible como los secretos.

- **Para rotar** un secreto se cambia en GitHub y se despliega: Terraform guarda una versión nueva y el
  despliegue del backend reemplaza las tareas. Cambiar `JWT_SECRET_KEY` cierra todas las sesiones y cambiar
  `OTP_SECRET_KEY` invalida los códigos pendientes. Rotar `FIREBASE_CREDENTIALS_JSON` es crear una llave
  nueva de la cuenta de servicio en la consola de Firebase (o de Google Cloud), cargarla en GitHub,
  desplegar y, cuando las tareas nuevas estén arriba, **borrar la llave anterior de la cuenta de servicio**:
  mientras exista, el JSON viejo sigue sirviendo. `ORACLE_SECRET` se cambia a la vez en los dos lados.
- **Vaciar un secreto opcional en GitHub lo borra de inmediato.** Los secretos usan
  `recovery_window_in_days = 0`, como el de RDS, para que el apagado y la recreación no choquen con nombres
  en borrado. Si alguien vacía `FIREBASE_CREDENTIALS_JSON` u `ORACLE_SECRET` y despliega, Terraform borra el
  secreto sin ventana de recuperación y la tarea arranca sin esa variable (sin push, o el oráculo en 503).
  No hay copia que restaurar en Secrets Manager: se vuelve a cargar el valor en GitHub y se despliega, y el
  secreto se recrea con el mismo nombre. Si nadie conserva el valor, se emite una llave nueva de la cuenta
  de servicio (y se borra la anterior) o se acuerda una llave nueva con el oráculo. Vaciar
  `JWT_SECRET_KEY` u `OTP_SECRET_KEY` no borra nada: la validación de Terraform falla antes del apply.
- **Antes del primer despliegue con este cambio**, si en la cuenta ya existe a mano un secreto con alguno
  de esos nombres, se borra o se importa al estado; si no, el apply falla.

**Después del primer despliegue: borrar las revisiones con valores en claro.** Las revisiones anteriores
de la definición de tarea tienen los valores en claro. Desregistrar una revisión no basta: queda
`INACTIVE` y `DescribeTaskDefinition` la sigue mostrando completa. Hay que borrarla. Se hace **solo
cuando el despliegue nuevo está estable** (el servicio con `rolloutState` `COMPLETED` y `/health` en 200),
porque después el circuit breaker ya no puede volver a esas revisiones: si la revisión nueva falla, la
reversión automática no tiene a dónde ir y hay que corregir y desplegar de nuevo. Desde una sesión con
permisos de ECS, sin imprimir el contenido de ninguna definición:

1. Anotar la revisión vigente:
   `aws ecs describe-services --cluster solventa-cluster-production --services solventa-backend-service-production --query "services[0].[taskDefinition,deployments[0].rolloutState]"`.
2. Listar las activas y desregistrar todas menos la vigente:
   `aws ecs list-task-definitions --family-prefix solventa-backend-production --status ACTIVE`, y por cada
   ARN distinto del vigente `aws ecs deregister-task-definition --task-definition <arn>`.
3. Listar las inactivas, que incluyen las recién desregistradas y las de despliegues anteriores:
   `aws ecs list-task-definitions --family-prefix solventa-backend-production --status INACTIVE`.
4. Borrarlas con `aws ecs delete-task-definitions --task-definitions <arn1> <arn2> ...`, **máximo 10 ARN
   por llamada**; con más, se repite en lotes de 10. Revisar `failures` en la respuesta. El borrado pasa por
   `DELETE_IN_PROGRESS` y termina cuando ninguna tarea usa la revisión.
5. Verificar con una revisión vieja que ya no se puede leer:
   `aws ecs describe-task-definition --task-definition solventa-backend-production:<revisión> --query taskDefinition.status`
   debe fallar (`ClientException`) o mostrar `DELETE_IN_PROGRESS` hasta que termine, nunca `INACTIVE`.
6. Rotar los valores que estuvieron expuestos (T-06 del hotfix), porque quien leyó una revisión antes del
   borrado los conoce.

**`ORACLE_SECRET`.** Texto aleatorio de al menos 32 caracteres que comparten el backend y el oráculo. Se
genera con `python -c "import secrets; print(secrets.token_urlsafe(48))"`, se pega directamente en GitHub y
se entrega al oráculo por un canal privado. Para rotarla se cambia en los dos lados a la vez.

**APK de release (opcional, apagado).** El job `mobile-release` de `ci.yml` firma `assembleRelease` en cada
push a `dev` y publica el APK como artefacto, pero solo si existen los cuatro secretos de la llave y la
variable `ANDROID_API_BASE_URL`; sin ellos se omite sin fallar. Hoy el equipo prueba con el APK debug local,
así que queda apagado. Para activarlo:

1. Crear la llave fuera del repositorio:
   `keytool -genkeypair -v -keystore solventa-release.jks -alias solventa -keyalg RSA -keysize 2048 -validity 10000`.
2. Convertirla a texto y copiarla: en PowerShell,
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes("solventa-release.jks")) | Set-Clipboard`.
3. Crear los cuatro secretos y la variable de la tabla.
4. Guardar el `.jks` y sus contraseñas en un lugar seguro: si se pierden, las actualizaciones de la app ya
   no se pueden firmar con la misma llave.

## Apagado y recreación de la infraestructura

Para no generar costos entre entregas, la infraestructura se destruye con el flujo
`.github/workflows/destroy.yml`, que se lanza a mano y exige escribir `DESTRUIR` para confirmar. Se
recrea lanzando a mano el despliegue desde `main`, que pasa por la misma aprobación y la misma prueba de humo.

## Evidencias

| Qué | Dónde |
|---|---|
| Ejecuciones del CI y sus resúmenes de pruebas | https://github.com/proyectos-miso/proyecto-final-miso/actions/workflows/ci.yml |
| Despliegue exitoso a AWS | https://github.com/proyectos-miso/proyecto-final-miso/actions/runs/40032353896 |
| Apagado de la infraestructura | https://github.com/proyectos-miso/proyecto-final-miso/actions/runs/40034396667 |
| Pull requests revisados y fusionados | https://github.com/proyectos-miso/proyecto-final-miso/pulls?q=is%3Apr+is%3Amerged |
| Calidad y cobertura | https://sonarcloud.io/project/overview?id=proyectos-miso_proyecto-final-miso |
| Releases | https://github.com/proyectos-miso/proyecto-final-miso/releases |

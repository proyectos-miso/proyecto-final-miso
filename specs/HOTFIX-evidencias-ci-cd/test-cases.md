# Test cases · HOTFIX

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | UC-01 | Dado un pull request con todas las pruebas en verde, cuando termina el CI, entonces cada componente muestra su total y ninguna falla | TC-01, TC-10 |
| SC-02 | UC-01 | Dado un pull request con una prueba que falla, cuando termina el CI, entonces el resumen nombra la prueba y el check queda en rojo | TC-01 |
| SC-03 | AC-01 | Dado que una suite se cae antes de escribir su reporte, cuando corre el resumen, entonces avisa que no hubo reporte sin ocultar el error del paso de pruebas | TC-01 |
| SC-04 | AC-02 | Dado un CI terminado, cuando alguien revisa la ejecución días después, entonces descarga los reportes JUnit de los tres componentes | TC-03, TC-10 |
| SC-05 | UC-02 | Dada una fusión en main, cuando arranca el CD, entonces espera la aprobación del ambiente produccion | TC-06, TC-08 |
| SC-06 | AC-04 | Dado un lanzamiento manual del CD desde otra rama, cuando corre, entonces no toca AWS | TC-06 |
| SC-07 | UC-02 | Dado un despliegue terminado, cuando corre la prueba de humo, entonces verifica portal, salud y API solo con GET | TC-04, TC-05 |
| SC-08 | UC-02 | Dado un backend que tarda en arrancar, cuando corre la prueba de humo, entonces reintenta y pasa cuando responde | TC-05 |
| SC-09 | UC-02 | Dado un backend caído, cuando corre la prueba de humo, entonces la ejecución queda en rojo y nombra la verificación que falló | TC-05 |
| SC-10 | UC-01 | Dado un cambio que agrega una acción desconocida o pide permiso de escritura, cuando corre el CI, entonces las pruebas de los workflows fallan | TC-07 |
| SC-11 | UC-03 | Dado un evaluador, cuando abre el documento del proceso, entonces encuentra etapas, condiciones y enlaces a evidencias | TC-09 |
| SC-12 | AC-03 | Dado que el ambiente no tiene revisores configurados, cuando se despliega, entonces GitHub no pide aprobación | OUT OF SCOPE: depende de la configuración en GitHub, que hace el administrador; TC-08 lo verifica |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-05, SC-07 | — |
| Alternate flows | YES | SC-02, SC-09 | — |
| Invalid input and errors | YES | SC-03, SC-06 | — |
| Limits and boundaries | YES | SC-08 | Reintentos de la prueba de humo |
| Related flows before and after | YES | SC-04, SC-11 | Revisión posterior de la evidencia |
| Several users, sessions, devices or roles | YES | SC-05, SC-12 | Autor del cambio y revisor del ambiente |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-08 | Arranque lento del servicio |

## Test cases

### TC-01 · Resumen de pruebas por componente

| Field | Value |
|---|---|
| Covers | AC-01 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Reportes JUnit de ejemplo con pruebas aprobadas, fallidas, con error y omitidas |
| Procedure | 1. Resumir los reportes. 2. Generar el Markdown. 3. Resumir sin reportes |
| Expected result | Conteos correctos, nombre de cada prueba fallida y aviso cuando no hay reporte, sin fallar |
| Tool or method | python -m unittest discover -s scripts/ci -t scripts/ci |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | scripts/ci/test_junit_summary.py::test_cuenta_aprobadas_fallidas_errores_y_omitidas |
| Status | PASS |

### TC-02 · El resumen no expone más que nombres y mensajes recortados

| Field | Value |
|---|---|
| Covers | SR-04 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | UNIT |
| Preconditions | Un reporte con un mensaje de falla de 500 caracteres |
| Procedure | 1. Resumir el reporte. 2. Revisar el largo del mensaje |
| Expected result | El mensaje queda recortado a 200 caracteres y solo se muestran nombres y mensajes |
| Tool or method | python -m unittest discover -s scripts/ci -t scripts/ci |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | scripts/ci/test_junit_summary.py::test_mensajes_largos_se_recortan |
| Status | PASS |

### TC-03 · Cada componente publica su resumen y su reporte aunque fallen las pruebas

| Field | Value |
|---|---|
| Covers | AC-01, AC-02 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | STATIC |
| Preconditions | `.github/workflows/ci.yml` del cambio |
| Procedure | 1. Revisar los trabajos de backend, web y móvil |
| Expected result | Cada uno ejecuta el resumen y sube `junit-<componente>` con `if: always()` |
| Tool or method | python -m unittest discover -s scripts/ci -t scripts/ci |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | scripts/ci/test_workflows.py::test_cada_componente_publica_resumen_y_reporte_junit |
| Status | PASS |

### TC-04 · La prueba de humo solo hace peticiones GET

| Field | Value |
|---|---|
| Covers | AC-05, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | Servidor local que imita el portal, la salud y la API, y registra los métodos |
| Procedure | 1. Correr la prueba de humo contra el servidor local. 2. Revisar los métodos recibidos |
| Expected result | Solo llegan peticiones GET y no se envían credenciales ni cuerpos |
| Tool or method | python -m unittest discover -s scripts/ci -t scripts/ci |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | scripts/ci/test_smoke_test.py::test_solo_hace_peticiones_get |
| Status | PASS |

### TC-05 · La prueba de humo detecta fallas y reintenta

| Field | Value |
|---|---|
| Covers | AC-05 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Servidor local sano, con el backend caído, y una URL sin respuesta |
| Procedure | 1. Ambiente sano. 2. Backend caído. 3. Sin respuesta con reintentos. 4. Arranque lento. 5. HTML del portal en lugar de JSON de la API. 6. URL sin esquema |
| Expected result | 1 pasa; 2 falla y nombra la verificación; 3 falla después de los reintentos; 4 pasa; 5 no cuenta como backend; 6 se rechaza |
| Tool or method | python -m unittest discover -s scripts/ci -t scripts/ci |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | scripts/ci/test_smoke_test.py::test_backend_caido_falla_y_lo_nombra |
| Status | PASS |

### TC-06 · El despliegue solo corre desde main y con el ambiente produccion

| Field | Value |
|---|---|
| Covers | AC-03, AC-04, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | STATIC |
| Preconditions | `.github/workflows/deploy.yml` del cambio |
| Procedure | 1. Revisar la condición de rama y el ambiente del primer trabajo. 2. Revisar que los demás dependen de él |
| Expected result | `deploy-infra` exige `refs/heads/main` y el ambiente `produccion`; backend y portal dependen de él |
| Tool or method | python -m unittest discover -s scripts/ci -t scripts/ci |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | scripts/ci/test_workflows.py::test_el_despliegue_solo_corre_desde_main_con_el_ambiente_produccion |
| Status | PASS |

### TC-07 · Token de solo lectura, sin acciones nuevas y sin secretos en las pruebas

| Field | Value |
|---|---|
| Covers | SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | STATIC |
| Preconditions | Workflows de CI y CD del cambio |
| Procedure | 1. Revisar los permisos. 2. Comparar las acciones usadas con la lista conocida. 3. Buscar secretos en los trabajos de pruebas |
| Expected result | Solo `contents: read`, ninguna acción fuera de la lista y ningún secreto en los trabajos de pruebas |
| Tool or method | python -m unittest discover -s scripts/ci -t scripts/ci |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | scripts/ci/test_workflows.py::test_ci_y_cd_tienen_token_de_solo_lectura |
| Status | PASS |

### TC-08 · Ambiente con revisores y ramas protegidas en GitHub

| Field | Value |
|---|---|
| Covers | AC-03, SR-02, SR-05 |
| Kind | SECURITY |
| Risk covered | THR-02, THR-05 |
| Level | INTEGRATION |
| Preconditions | El administrador configuró el ambiente `produccion` y la protección de `dev` y `main` (T-07) |
| Procedure | 1. Abrir un pull request de prueba con un check en rojo y confirmar que no deja fusionar. 2. Confirmar que exige una aprobación de otra persona. 3. En el siguiente despliegue, confirmar que el CD espera la aprobación |
| Expected result | No se puede fusionar sin checks en verde ni aprobación, y el despliegue espera a un revisor |
| Tool or method | Configuración de GitHub y una ejecución del CD |
| Automation | MANUAL |
| Environment | CI |
| Evidence | PENDING |
| Status | PENDING |

### TC-09 · El documento describe el proceso y las condiciones

| Field | Value |
|---|---|
| Covers | AC-06 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | STATIC |
| Preconditions | `docs/ci-cd.md` |
| Procedure | 1. Revisar las secciones del documento |
| Expected result | Recorrido, ramas, CI, condiciones para fusionar, CD, apagado y evidencias |
| Tool or method | python -m unittest discover -s scripts/ci -t scripts/ci |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | scripts/ci/test_workflows.py::test_el_documento_describe_el_proceso_y_las_condiciones |
| Status | PASS |

### TC-10 · Resúmenes y reportes en una ejecución real del CI

| Field | Value |
|---|---|
| Covers | AC-01, AC-02 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | La rama del cambio subida a GitHub |
| Procedure | 1. Abrir la ejecución del CI. 2. Revisar el resumen de los tres componentes. 3. Descargar los artefactos `junit-*` |
| Expected result | Tres tablas de resultados y tres artefactos JUnit |
| Tool or method | GitHub Actions |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | PENDING |
| Status | PENDING |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | NO | — | La prueba de humo no se autentica y el cambio no expone rutas |
| User without permissions | YES | TC-06, TC-08 | Desplegar sin ser revisor del ambiente |
| Access to another user's resources | NO | — | Sin recursos de usuarios |
| Cross-tenant access | NO | — | No hay inquilinos |
| Malicious input | YES | TC-05 | URL inválida y respuestas inesperadas en la prueba de humo |
| Expired tokens | NO | — | No se usan tokens de la aplicación |
| Revoked sessions | NO | — | Sin sesiones |
| Tampered data | YES | TC-05 | Respuesta HTML donde se espera la API |
| Wrong keys | NO | — | Sin llaves nuevas |
| External service failures | YES | TC-05 | Backend caído o sin respuesta |
| Race conditions | NO | — | El CD ya usa un grupo de concurrencia sin cancelación |
| Information exposure | YES | TC-02 | Mensajes de falla en los reportes |
| Secrets in code | YES | TC-07 | Secretos fuera de los trabajos de pruebas; sdd scan secrets en el gate |
| Insecure configuration | YES | TC-06, TC-07 | Permisos del token, rama y ambiente del despliegue |
| Vulnerable dependencies | NO | — | No se agregan dependencias ni acciones |

# HOTFIX · Evidencias CI/CD

| Field | Value |
|---|---|
| ID | HOTFIX |
| Title | Evidencias CI/CD |
| User story | none: hotfix de la calificación del Sprint 1 (CI/CD 4/10 y ejecución de pruebas 8/10) |
| Owner | Juan Camilo Peña |
| Status | DRAFT |
| Created | 2026-10-25 |
| Security profile | HIGH |

<!-- A spec is a contract: what, why, how, and how we will know it is done. Fill the sections in
     order and stop for agreement between them: Requirements → Design → Tasks.
     Status: DRAFT → READY (after spec-review) → IN_PROGRESS → IN_REVIEW → DONE | BLOCKED. -->

## 1. Requirements

### User story

Observaciones de la calificación del Sprint 1:

- Parametrización de integración continua / despliegue continuo, 4/10: «Se debe escoger la plataforma y herramientas para desplegar la solución y fijar las condiciones para que los componentes que vayan entregando se puedan desplegar de manera controlada y automatizada. De esto se espera evidencia del proceso, no solo el resultado. Corregir para semana 3».
- Ejecución de pruebas, 8/10: «Se evidencia que los desarrollos incluyen pruebas unitarias y, una vez se consolida cada versión, las pruebas de módulo e integrales y otras que apliquen según el plan. Se esperan más evidencias funcionales. Corregir para semana 3».

Restricción del equipo (Juan Camilo Peña, 2026-10-25): esta semana no se puede desplegar en AWS. Lo que necesite AWS queda escrito para que Oscar Saraza lo pruebe en el siguiente despliegue.

### Problem and outcome

El pipeline existe y funciona, pero no deja ver el proceso que lo controla. Los resultados de las pruebas solo aparecen en los registros, cada push a `main` despliega a producción sin aprobación ni verificación posterior, y nada impide fusionar sin el CI en verde.

Resultado esperado: cada ejecución del CI publica el resumen de las pruebas de backend, web y móvil; el despliegue a producción pasa por un ambiente con aprobación y termina con una prueba de humo; y las condiciones para fusionar y desplegar quedan escritas.

### Goals and non-goals

Objetivos:

- Reporte de resultados de pruebas por componente en cada ejecución del CI.
- Ambiente `produccion` con aprobación manual y prueba de humo no intrusiva después del despliegue.
- Documento del proceso de CI/CD y de las reglas de protección de ramas, para que el administrador las active.

Fuera de alcance:

- Activar la protección de ramas: la activa el administrador del repositorio en GitHub.
- Desplegar en AWS esta semana.
- Pruebas de punta a punta del portal: paquete aparte.
- Arreglar el despliegue local con el emulador Floci: paquete aparte si se decide.
- La página de la wiki, que vive fuera del repositorio.

### Affected paths

| Path | Change |
|---|---|
| .github/workflows/ci.yml | Token de solo lectura; reportes JUnit de pytest, Vitest y Gradle publicados como resumen y como artefactos; pruebas de los scripts del CI |
| .github/workflows/deploy.yml | Token de solo lectura; solo desde `main`; ambiente `produccion`; espera a ECS estable; trabajo final de prueba de humo |
| scripts/ci/** | Resumen de reportes JUnit, prueba de humo y sus pruebas, y pruebas de los controles de los workflows |
| docs/ci-cd.md | Proceso de CI/CD y condiciones de fusión y despliegue |
| README.md | Enlace al documento del proceso |
| .gitignore | Carpetas `test-results/` de los reportes |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Integrante del equipo | Ver el resultado de las pruebas de un PR | 1. Abre el PR. 2. Abre la ejecución del CI. 3. Ve por componente cuántas pruebas pasaron, fallaron u omitieron, y cuáles fallaron | Una prueba falla: el resumen la nombra y el check del PR queda en rojo |
| UC-02 | Responsable del despliegue | Aprobar un despliegue a producción | 1. Se fusiona en `main`. 2. El CD espera aprobación en el ambiente `produccion`. 3. Un revisor autorizado aprueba. 4. Se despliega. 5. La prueba de humo verifica el portal y la API | Nadie aprueba: el despliegue no corre. La prueba de humo falla: la ejecución queda en rojo |
| UC-03 | Evaluador del curso | Revisar el proceso de CI/CD | 1. Lee el documento del proceso. 2. Abre las ejecuciones de CI y CD enlazadas. 3. Ve las condiciones y su evidencia | — |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Código de una rama o una acción comprometida usa el token del flujo para escribir en el repositorio o leer secretos | Atacante en la cadena de suministro | THR-01 | Token de solo lectura, ninguna acción nueva y sin secretos en los trabajos de pruebas | TC-07 |
| AB-02 | Desplegar a producción saltándose la aprobación con un lanzamiento manual desde otra rama | Integrante con permiso de escritura | THR-02 | El despliegue solo corre desde `main` y el ambiente exige aprobación | TC-06, TC-08 |
| AB-03 | La prueba de humo cambia datos o expone información en producción | Error del pipeline | THR-03 | Solo peticiones GET de lectura, sin credenciales ni datos | TC-04 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN corre el CI, THE pipeline SHALL publicar por componente (backend, web, móvil) el total de pruebas aprobadas, fallidas y omitidas, con el nombre de cada prueba fallida | DERIVED: la observación pide evidencia funcional de las pruebas, no solo cobertura |
| AC-02 | WHEN corre el CI, THE pipeline SHALL conservar los reportes JUnit de los tres componentes como artefactos de la ejecución | DERIVED: la evidencia debe poder revisarse después |
| AC-03 | WHEN se fusiona en `main` o se lanza el CD manualmente, THE despliegue SHALL esperar la aprobación de un revisor del ambiente `produccion` antes de cambiar AWS | STATED |
| AC-04 | IF el CD se lanza desde una rama distinta de `main`, THE despliegue SHALL rechazarse sin tocar AWS | DERIVED: hoy un dispatch manual acepta cualquier rama |
| AC-05 | WHEN termina el despliegue, THE CD SHALL verificar con peticiones de lectura que el portal responde, que `/health` responde y que una ruta de la API llega al backend, y SHALL fallar si alguna no responde | STATED |
| AC-06 | THE repositorio SHALL documentar el proceso de CI/CD (disparadores, etapas, condiciones de fusión y de despliegue, apagado y recreación de la infraestructura) y las reglas de protección de `dev` y `main` | STATED |

### Quality requirements

N/A: no cambia el comportamiento del producto; el CI puede tardar unos segundos más por los reportes.

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | NO | Los reportes contienen nombres de pruebas y datos ficticios, no datos de personas |
| Sensitive data (special categories, biometrics, health, financial, minors) | NO | Sin datos sensibles |
| Authentication, sessions, credentials or one-time codes | NO | La prueba de humo no se autentica ni pide códigos |
| Authorization or access to other users' resources | NO | Sin cambios de permisos de la aplicación |
| Payments or money movement | NO | No toca pagos |
| Third-party services or integrations | NO | Los reportes los generan pytest, Vitest y Gradle y los resume un script propio; no se agregan acciones |
| Infrastructure, network, IAM, CI/CD or secrets | YES | Workflows de CI y CD, ambiente de despliegue y sus aprobaciones |
| Multiple tenants, partners or organizations | NO | No hay inquilinos |
| Storage or processing on a mobile device | NO | La app no cambia |
| Web UI or data exposed in the browser | NO | El portal no cambia |
| New or updated dependencies | NO | Reporteros JUnit ya incluidos en las herramientas; los scripts usan solo la librería estándar de Python |
| Critical paths (sdd/security/critical-paths.md) | YES | .github/workflows/** |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | El pipeline corre con el token de solo lectura, sin acciones nuevas y sin secretos en los trabajos de pruebas | THR-01 | CTL-01 | MEDIUM |
| SR-02 | Producción solo se despliega desde `main` y con la aprobación de un revisor del ambiente `produccion` | THR-02, DATA-02 | CTL-02, CTL-03 | HIGH |
| SR-03 | La prueba de humo no modifica producción ni usa credenciales | THR-03 | CTL-04 | MEDIUM |
| SR-04 | Los reportes y resúmenes de pruebas no exponen secretos ni datos personales | THR-04, DATA-01 | CTL-05 | MEDIUM |
| SR-05 | `dev` y `main` solo aceptan cambios por pull request, con los checks en verde y una aprobación de otra persona | THR-05 | CTL-06 | MEDIUM |

## 2. Design

### Components

- `.github/workflows/ci.yml`:
  - `permissions: contents: read` para todo el flujo.
  - pytest con `--junitxml`, Vitest con el reportero `junit` integrado de Angular y los reportes que Gradle ya genera.
  - En cada componente, con `if: always()`: resumen en `$GITHUB_STEP_SUMMARY` y artefacto `junit-<componente>`.
  - El trabajo `sdd-gate` corre las pruebas de `scripts/ci`.
- `.github/workflows/deploy.yml`:
  - `permissions: contents: read`.
  - `deploy-infra` con `if: github.ref == 'refs/heads/main'`, `environment: produccion` y salida `cloudfront_domain`.
  - `deploy-backend` espera `aws ecs wait services-stable`.
  - Trabajo nuevo `smoke-test` después de los tres trabajos de despliegue.
- `scripts/ci/junit_summary.py`: lee los JUnit y escribe una tabla Markdown por componente.
- `scripts/ci/smoke_test.py`: tres GET con reintentos y tabla Markdown de resultados.
- `scripts/ci/test_*.py`: pruebas de los dos scripts y de los controles de los workflows.
- `docs/ci-cd.md`: proceso, condiciones y evidencias.

### Data flows

1. Pull request o push → CI en el runner con token de solo lectura → pruebas → reportes JUnit → resumen de la ejecución y artefactos.
2. Fusión en `main` → CD → espera de aprobación en el ambiente `produccion` → Terraform, backend y portal con los secretos del repositorio.
3. Fin del despliegue → prueba de humo por HTTPS a CloudFront, solo GET → resumen de la ejecución.

Los límites de confianza son el runner, que ejecuta código de las ramas, y el ambiente `produccion`, que separa los cambios aprobados de AWS.

### Data contract

N/A: el producto no cambia. Los formatos son JUnit XML, que ya generan las herramientas, y Markdown en el resumen de la ejecución.

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| Resumir los JUnit con un script propio de la librería estándar | CTL-01, CTL-05 | Acciones del marketplace para reportes: agregan una dependencia y piden permiso `checks: write` |
| Token de solo lectura en todo el flujo de CI y CD | CTL-01 | Permisos por defecto del repositorio: pueden incluir escritura |
| Control de rama en el workflow además de la política del ambiente | CTL-02, CTL-03 | Solo la política del ambiente: si no se configura, el despliegue corre desde cualquier rama |
| Prueba de humo con un GET a una ruta que solo acepta POST | CTL-04 | Llamar endpoints con datos o credenciales: modifica producción o expone secretos |

### Architecture decisions

N/A: no cambia la arquitectura de la aplicación ni de la infraestructura; el proceso queda en `docs/ci-cd.md`.

### Configuration and deployment

- En GitHub, el administrador crea el ambiente `produccion` con revisores obligatorios y solo la rama `main`. Si no existe, GitHub lo crea solo al primer despliegue, sin protección.
- En GitHub, el administrador activa la protección de `dev` y `main` con las reglas de `docs/ci-cd.md`.
- No hay variables ni secretos nuevos.
- La prueba de humo solo se puede ver funcionando cuando haya un despliegue en AWS.

## 3. Tasks

<!-- Small, ordered, each tracing to acceptance criteria (AC) or security requirements (SR).
     Every MISSING control has a task. A DONE task cites evidence (commit, PR, path::test).
     While a functional task (with AC) is open the gate stays IN_PROGRESS. -->

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Token de solo lectura en CI y CD | SR-01 | CTL-01 | DONE | scripts/ci/test_workflows.py::test_ci_y_cd_tienen_token_de_solo_lectura |
| T-02 | Script de resumen de reportes JUnit | AC-01, SR-04 | CTL-05 | DONE | scripts/ci/test_junit_summary.py |
| T-03 | Reportes JUnit, resumen y artefactos por componente en el CI | AC-01, AC-02 | — | DONE | scripts/ci/test_workflows.py::test_cada_componente_publica_resumen_y_reporte_junit |
| T-04 | Despliegue solo desde main, ambiente produccion y espera a ECS estable | AC-03, AC-04, SR-02 | CTL-02 | DONE | scripts/ci/test_workflows.py::test_el_despliegue_solo_corre_desde_main_con_el_ambiente_produccion |
| T-05 | Prueba de humo y su trabajo en el CD | AC-05, SR-03 | CTL-04 | DONE | scripts/ci/test_smoke_test.py |
| T-06 | Documento del proceso y enlace en el README | AC-06 | — | DONE | docs/ci-cd.md |
| T-07 | Configurar en GitHub el ambiente produccion y la protección de dev y main | AC-03, SR-02, SR-05 | CTL-03, CTL-06 | TODO | — |

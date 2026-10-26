# HOTFIX · Validador rutas concretas

| Field | Value |
|---|---|
| ID | HOTFIX |
| Title | Validador rutas concretas |
| User story | none: falla del validador encontrada al crear el spec de HU-MOV-06 el 2026-10-24 |
| Owner | Juan Camilo Peña |
| Status | IN_REVIEW |
| Created | 2026-10-24 |
| Security profile | HIGH |

## 1. Requirements

### User story

Reporte del incidente (2026-10-24): al declarar en "Affected paths" un archivo concreto del móvil, por ejemplo `movil/app/src/main/java/com/solventa/app/domain/policies/PolicyCard.kt`, el validador lo marca como la ruta crítica `movil/app/src/main/java/**/data/session/**` y exige el perfil HIGH (E24). La función `overlap` de `sdd/tools/sddlib/paths.py` compara prefijos fijos y el del patrón crítico (`movil/app/src/main/java/`) es prefijo de cualquier archivo del móvil. Le pasa a toda historia MEDIUM del móvil o del portal que declare archivos concretos.

### Problem and outcome

El validador obliga a subir el perfil de historias que no tocan rutas críticas, o a declarar rutas falsas. Resultado: un archivo concreto solo cuenta como ruta crítica cuando un patrón crítico lo cubre; una carpeta concreta sigue cubriendo lo que contiene y los patrones se siguen comparando como antes.

### Goals and non-goals

Objetivo: que `overlap` distinga archivos concretos, carpetas concretas y patrones.

Fuera de alcance: el escaneo de archivos cambiados (`scan paths`), que ya usa la coincidencia exacta; los enlaces relativos que el validador no resuelve (E20), que siguen anotados como mejora.

### Affected paths

| Path | Change |
|---|---|
| sdd/tools/sddlib/paths.py | `overlap` trata los archivos concretos por coincidencia y las carpetas concretas como `carpeta/**` |
| sdd/tools/tests/test_modules.py | Pruebas de archivos y carpetas concretas |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Autor de un spec | Declarar archivos concretos que no son críticos | 1. Lista los archivos en Affected paths. 2. El validador no los marca como críticos | Un archivo concreto sí es crítico: el validador lo marca y exige su perfil |
| UC-02 | Autor de un spec | Declarar una carpeta concreta | 1. Lista la carpeta sin comodines. 2. El validador la cruza con las rutas críticas que contiene | — |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Declarar un archivo crítico concreto para que el validador no exija el perfil de la ruta crítica | Autor de un spec | THR-01 | El archivo cubierto por un patrón crítico se sigue marcando | TC-01 |
| AB-02 | Declarar la carpeta que contiene rutas críticas sin comodines para esquivar el perfil | Autor de un spec | THR-02 | La carpeta concreta cubre lo que contiene y se marca | TC-02 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN un spec declara un archivo concreto que ningún patrón crítico cubre, THE validador SHALL no marcarlo como ruta crítica | STATED |
| AC-02 | WHEN un spec declara un archivo concreto que un patrón crítico cubre, THE validador SHALL marcarlo y exigir el perfil mínimo | DERIVED: la corrección no debe abrir un hueco en el gate |
| AC-03 | WHEN un spec declara una carpeta concreta, THE validador SHALL cruzarla con las rutas críticas que contiene | DERIVED: antes el prefijo cubría este caso |
| AC-04 | WHEN un spec declara patrones con comodines, THE validador SHALL compararlos como antes | DERIVED: no cambiar lo que ya funcionaba |

### Quality requirements

N/A: cambio interno del validador sin requisitos de rendimiento, accesibilidad ni disponibilidad.

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | NO | El validador no maneja datos personales |
| Sensitive data (special categories, biometrics, health, financial, minors) | NO | No aplica |
| Authentication, sessions, credentials or one-time codes | NO | No aplica |
| Authorization or access to other users' resources | NO | No aplica |
| Payments or money movement | NO | No aplica |
| Third-party services or integrations | NO | No aplica |
| Infrastructure, network, IAM, CI/CD or secrets | NO | No cambia los workflows; solo la lógica que ellos ejecutan |
| Multiple tenants, partners or organizations | NO | No aplica |
| Storage or processing on a mobile device | NO | No aplica |
| Web UI or data exposed in the browser | NO | No aplica |
| New or updated dependencies | NO | Solo biblioteca estándar |
| Critical paths (sdd/security/critical-paths.md) | YES | `sdd/tools/sddlib/**` es el validador y los escáneres |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | Un archivo concreto cubierto por un patrón crítico sigue exigiendo el perfil de esa ruta | THR-01 | CTL-01 | HIGH |
| SR-02 | Una carpeta concreta que contiene rutas críticas sigue exigiendo su perfil | THR-02 | CTL-02 | HIGH |

## 2. Design

### Components

- `sdd/tools/sddlib/paths.py`:
  - `_as_pattern` convierte una carpeta concreta (sin comodines y sin extensión en el último tramo) en `carpeta/**` y deja igual los archivos y los patrones;
  - `overlap` compara primero por coincidencia; si uno de los dos es concreto, no usa el prefijo fijo.
- `sdd/tools/sddlib/spec.py` no cambia: sigue llamando a `overlap` para E24.

### Data flows

N/A: el validador lee los archivos del repositorio y no mueve datos.

### Data contract

N/A: no cambia la línea de comandos ni los formatos.

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| Un archivo concreto se cruza solo por coincidencia con el patrón | CTL-01 | Mantener el prefijo: falsos positivos en todo el móvil y el portal |
| Una carpeta concreta se trata como `carpeta/**` | CTL-02 | Tratarla como archivo: una carpeta con rutas críticas pasaría sin marca |

### Architecture decisions

N/A: corrección puntual del validador.

### Configuration and deployment

Ninguna; los hooks y el job `sdd-gate` usan el validador del repositorio.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Distinguir archivos concretos, carpetas concretas y patrones en `overlap` | AC-01, AC-02, AC-03, AC-04, SR-01, SR-02 | CTL-01, CTL-02 | DONE | sdd/tools/sddlib/paths.py |
| T-02 | Pruebas de archivos y carpetas concretas | AC-01, AC-02, AC-03, AC-04 | CTL-01, CTL-02 | DONE | sdd/tools/tests/test_modules.py |

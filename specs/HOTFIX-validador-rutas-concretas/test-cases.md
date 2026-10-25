# Test cases · HOTFIX

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | AC-01 | Dado un archivo del móvil fuera de `data/session`, cuando se declara, entonces no se marca como crítico | TC-01 |
| SC-02 | AC-02 | Dado un archivo dentro de `data/session`, cuando se declara, entonces se marca y exige HIGH | TC-01 |
| SC-03 | AC-01 | Dado `strings.xml` frente al patrón `**/AndroidManifest.xml`, cuando se declara, entonces no se marca | TC-01 |
| SC-04 | UC-02 | Dada la carpeta `movil/app/src/main/java`, con o sin `/` final, cuando se declara, entonces se marca porque contiene la sesión | TC-02 |
| SC-05 | AC-03 | Dada la carpeta `web/src`, cuando se declara frente a la sesión del móvil, entonces no se marca | TC-02 |
| SC-06 | AC-04 | Dados dos patrones con comodines que comparten prefijo, cuando se comparan, entonces se cruzan como antes | TC-03 |
| SC-07 | UC-01 | Dado el spec de HU-MOV-06 con archivos concretos del móvil, cuando se valida, entonces no hay E24 | TC-04 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-07 | — |
| Alternate flows | YES | SC-02 | — |
| Invalid input and errors | YES | SC-03 | — |
| Limits and boundaries | YES | SC-04, SC-05 | — |
| Related flows before and after | YES | SC-06 | — |
| Several users, sessions, devices or roles | NO | — | El validador no distingue usuarios |
| State and time (expiry, retries, interruptions, going back, offline) | NO | — | Función pura sin estado |

## Test cases

### TC-01 · Archivos concretos

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | UNIT |
| Preconditions | Ninguna |
| Procedure | 1. Cruzar la ruta crítica de la sesión con un archivo de `domain/policies` y con uno de `data/session`. 2. Cruzar `**/AndroidManifest.xml` con `strings.xml` |
| Expected result | Solo el archivo de `data/session` se cruza |
| Tool or method | python -m unittest (test_modules) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | sdd/tools/tests/test_modules.py::test_overlap_de_un_archivo_concreto_exige_que_el_patron_lo_cubra |
| Status | PASS |

### TC-02 · Carpetas concretas

| Field | Value |
|---|---|
| Covers | AC-03, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | UNIT |
| Preconditions | Ninguna |
| Procedure | 1. Cruzar la sesión del móvil con `movil/app/src/main/java`, con y sin `/` final. 2. Cruzarla con `web/src` |
| Expected result | La carpeta del móvil se cruza; `web/src` no |
| Tool or method | python -m unittest (test_modules) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | sdd/tools/tests/test_modules.py::test_overlap_de_una_carpeta_concreta_cubre_lo_que_contiene |
| Status | PASS |

### TC-03 · Patrones como antes

| Field | Value |
|---|---|
| Covers | AC-04 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Ninguna |
| Procedure | 1. Correr la prueba existente de `overlap` y toda la suite del framework |
| Expected result | Las 176 pruebas pasan |
| Tool or method | python -m unittest discover (sdd/tools/tests) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | sdd/tools/tests/test_modules.py::test_overlap; 176 pruebas OK el 2026-10-24 |
| Status | PASS |

### TC-04 · Spec real del móvil

| Field | Value |
|---|---|
| Covers | AC-01 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Paquete de HU-MOV-06 con archivos concretos del móvil y perfil MEDIUM |
| Procedure | 1. Abrir la rama de HU-MOV-06 con el validador corregido. 2. Ejecutar la validación del paquete. 3. Buscar errores E24 en la salida |
| Expected result | Sin E24 por la sesión del móvil |
| Tool or method | python sdd/tools/sdd.py spec validate specs/HU-MOV-06-ultima-sincronizacion |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | Validación en la rama de HU-MOV-06 el 2026-10-24: sin errores E24 |
| Status | PASS |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | NO | — | Sin autenticación |
| User without permissions | NO | — | Sin permisos |
| Access to another user's resources | NO | — | Sin recursos por usuario |
| Cross-tenant access | NO | — | Sin inquilinos |
| Malicious input | YES | TC-01, TC-02 | — |
| Expired tokens | NO | — | Sin tokens |
| Revoked sessions | NO | — | Sin sesiones |
| Tampered data | NO | — | Las rutas vienen del repositorio revisado |
| Wrong keys | NO | — | Sin llaves |
| External service failures | NO | — | Sin servicios externos |
| Race conditions | NO | — | Función pura |
| Information exposure | NO | — | No expone datos |
| Secrets in code | NO | — | Sin secretos |
| Insecure configuration | NO | — | Sin configuración |
| Vulnerable dependencies | NO | — | Sin dependencias |

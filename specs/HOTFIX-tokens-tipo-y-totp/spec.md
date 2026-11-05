# HOTFIX · Tokens tipo y TOTP

| Field | Value |
|---|---|
| ID | HOTFIX |
| Title | Tokens tipo y TOTP |
| User story | none: brecha RC-32 de rutas críticas (`specs/pendientes/README.md`), señalada en el reporte de estado del 19 de octubre |
| Owner | Juan Camilo Peña |
| Status | IN_REVIEW |
| Created | 2026-11-04 |
| Security profile | HIGH |

## 1. Requirements

### User story

Reporte del incidente (RC-32, 2026-11-04). Hay dos fallas en la autenticación del backend:

1. **Token sin tipo.** `require_roles` y `get_current_partner` leen el tipo del token con `payload.get("type", "access")`. Un JWT firmado sin el claim `type` pasa como token de acceso. Hoy todos los tokens que emite el backend traen `type`, pero el guard no debe depender de eso.
2. **TOTP reutilizable.** El ingreso del asesor y del analista acepta el mismo código TOTP varias veces mientras sigue vigente (unos 90 segundos con la tolerancia de ±1 paso). Quien vea u obtenga un código lo puede volver a usar con la contraseña.

### Problem and outcome

Problema: dos debilidades en la autenticación del portal y de la API.

Resultado:
- solo un token con `type="access"` abre la API, tanto con roles como en el canal de socios;
- un código TOTP se acepta una sola vez, y tampoco sirve uno anterior al último aceptado.

### Goals and non-goals

Objetivos: exigir el tipo `access` en los dos guards; guardar el último paso TOTP aceptado por usuario y rechazar los que no sean posteriores.

Fuera de alcance:
- el cierre de sesión del portal con revocación (RC-20, en HU-WEB-15);
- el SMS real de la app (RC-34, que se deja simulado);
- los tokens del asegurado, que ya traen `type` y una versión que se revoca.

### Affected paths

| Path | Change |
|---|---|
| backend/app/core/security.py | `require_roles` y `get_current_partner` exigen `type="access"`; `totp_step` devuelve el paso del código |
| backend/app/modules/auth/service.py | El ingreso rechaza un paso TOTP ya usado y guarda el último aceptado |
| backend/app/modules/auth/models.py | Columna `last_totp_step` en `users` |
| backend/app/main.py | La columna nueva en bases existentes con `ADD COLUMN IF NOT EXISTS` |
| backend/tests/integration/test_tokens_tipo_y_totp.py | Pruebas del hotfix |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Asesor o analista | Entrar al portal | 1. Escribe usuario, contraseña y el código TOTP actual. 2. Entra | Si usa un código que ya sirvió o uno anterior, recibe el mismo 401 que con un código errado, y cuenta como intento fallido |
| UC-02 | Cliente de la API | Llamar un endpoint protegido | 1. Envía el token de acceso. 2. Obtiene la respuesta | Con un token de refresco o sin tipo: 401 |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Firmar o reutilizar un JWT sin el claim `type` para usarlo como token de acceso | Atacante con un token sin tipo | THR-01 | 401 | TC-01, TC-02 |
| AB-02 | Usar el token de refresco (7 días) como token de acceso | Atacante con un refresh robado | THR-01 | 401 | TC-01 |
| AB-03 | Repetir un código TOTP visto u obtenido mientras sigue vigente | Atacante con la contraseña | THR-02 | 401 y el intento cuenta para el bloqueo | TC-03, TC-04 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | IF el token no trae `type="access"`, THE API SHALL responder 401 en los endpoints con rol | STATED |
| AC-02 | IF el token de socio no trae `type="access"`, THE canal B2B SHALL responder 401 | STATED |
| AC-03 | WHEN un código TOTP ya fue aceptado para un usuario, THE ingreso SHALL rechazarlo aunque siga vigente | STATED |
| AC-04 | IF el código TOTP corresponde a un paso anterior al último aceptado, THE ingreso SHALL rechazarlo | DERIVED: si no, un código viejo de la ventana de tolerancia serviría después de uno nuevo |
| AC-05 | WHEN el token de acceso es válido o el código TOTP es nuevo, THE sistema SHALL funcionar como antes | DERIVED: el hotfix no debe romper el ingreso ni los endpoints |

### Quality requirements

N/A: cambia la validación de tokens y del segundo factor; no hay metas de rendimiento ni de interfaz nuevas.

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | NO | No cambia datos personales |
| Sensitive data (special categories, biometrics, health, financial, minors) | NO | No aplica |
| Authentication, sessions, credentials or one-time codes | YES | Validación del token de acceso y del código TOTP |
| Authorization or access to other users' resources | YES | Guards de rol y del canal de socios |
| Payments or money movement | NO | No aplica |
| Third-party services or integrations | NO | No aplica |
| Infrastructure, network, IAM, CI/CD or secrets | NO | No aplica |
| Multiple tenants, partners or organizations | YES | Guard del canal de socios |
| Storage or processing on a mobile device | NO | No aplica |
| Web UI or data exposed in the browser | NO | Sin cambios en el portal |
| New or updated dependencies | NO | Sin dependencias nuevas |
| Critical paths (sdd/security/critical-paths.md) | YES | backend/app/modules/auth/** |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | Solo un token con `type="access"` abre los endpoints protegidos y el canal de socios | DATA-01, THR-01 | CTL-01 | HIGH |
| SR-02 | Un código TOTP se acepta una sola vez por usuario, y nunca uno anterior al último aceptado | THR-02 | CTL-02 | HIGH |

## 2. Design

### Components

- `core/security.py`:
  - `require_roles` rechaza todo token cuyo `type` no sea `access`;
  - `get_current_partner` deja de suponer `access` cuando falta el tipo;
  - `totp_step` devuelve el paso de tiempo que validó el código, y `verify_totp` queda como envoltura booleana para quien no lo necesite.
- `modules/auth/models.py`: `UserDB.last_totp_step` (entero, opcional).
- `modules/auth/service.py`: `authenticate_user` acepta el código solo si su paso es posterior a `last_totp_step`. Si no, lo trata como un código errado (mismo 401 y suma al contador de bloqueo). Al entrar, guarda el paso.

### Data flows

Sin flujos nuevos. El paso TOTP se guarda en `users` dentro de la misma transacción del ingreso.

### Data contract

N/A: los endpoints y sus respuestas no cambian. Solo cambia el detalle del 401 para un token que no es de acceso: «Token no permitido como token de acceso».

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| Exigir `type == "access"` en lugar de rechazar solo `refresh` | CTL-01 | Lista de tipos prohibidos: deja pasar cualquier tipo nuevo o ausente |
| Guardar el último paso aceptado y exigir uno posterior | CTL-02 | Guardar los códigos usados: más datos y no evita los pasos anteriores de la ventana |
| El código repetido responde igual que uno errado | CTL-02 | Un mensaje propio confirmaría que la contraseña era correcta |

### Architecture decisions

N/A: corrección puntual dentro de la arquitectura hexagonal existente (ADR 0004).

### Configuration and deployment

- **Columna nueva** `users.last_totp_step`: el arranque la agrega con `ADD COLUMN IF NOT EXISTS`.
- **Variables:** ninguna nueva.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Exigir `type="access"` en `require_roles` y en el canal de socios | AC-01, AC-02, AC-05, SR-01 | CTL-01 | DONE | backend/app/core/security.py |
| T-02 | Rechazar códigos TOTP repetidos o anteriores y guardar el último paso | AC-03, AC-04, AC-05, SR-02 | CTL-02 | DONE | backend/app/modules/auth/service.py; backend/app/modules/auth/models.py; backend/app/main.py |
| T-03 | Pruebas del hotfix | AC-01, AC-02, AC-03, AC-04, AC-05 | CTL-01, CTL-02 | DONE | backend/tests/integration/test_tokens_tipo_y_totp.py |

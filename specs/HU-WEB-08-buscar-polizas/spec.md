# HU-WEB-08 · Buscar polizas

| Field | Value |
|---|---|
| ID | HU-WEB-08 |
| Title | Buscar polizas |
| User story | https://github.com/proyectos-miso/proyecto-final-miso/issues/62 |
| Owner | Juan Camilo Peña |
| Status | IN_PROGRESS |
| Created | 2026-10-27 |
| Security profile | HIGH |

## 1. Requirements

### User story

**HU-WEB-08 · Buscar pólizas por documento, número o socio** (Sprint 2, Must have, 2 puntos, FE-WEB-03, ESC-LAT-04 y ESC-SEG-02).

**Como** analista de operaciones **quiero** buscar pólizas por documento del cliente, número de póliza o socio de distribución **para** atender una consulta sin cambiar de herramienta.

Criterios de aceptación de la historia:

1. **Búsqueda por cualquier criterio.** *Dado* el analista en la consulta de pólizas, *cuando* escribe un documento, un número de póliza o el nombre de un socio, *entonces* ve la lista de pólizas que coinciden, con número, titular, plan, estado y vigencia.
2. **Respuesta rápida.** *Dada* una búsqueda, *cuando* se ejecuta, *entonces* el servidor responde en menos de 150 ms en el percentil 95.
3. **Sin resultados.** *Dada* una búsqueda sin coincidencias, *cuando* se ejecuta, *entonces* se muestra un mensaje claro y la opción de limpiar el filtro.
4. **Solo lo autorizado.** *Dado* un usuario asociado a un socio, *cuando* busca, *entonces* solo ve las pólizas de ese socio.

Requisitos no funcionales: HA-04 (servir la consulta desde una vista de lectura), HA-09 (mantener la cotización ante la falla de una réplica), HA-14 (confinar cada socio a sus propios datos). Mockup: https://solventa-web.vercel.app/?pestana=gestion. Tareas: #169 endpoint de búsqueda con filtros e índices, #170 pantalla de consulta, #171 pruebas unitarias, de integración y de tiempo de respuesta.

### Problem and outcome

El portal solo tiene el cotizador del asesor; para atender una consulta, operaciones no tiene dónde buscar una póliza, y la póliza ni siquiera guarda el nombre del titular. Resultado: la emisión guarda los datos del titular; el portal tiene una consulta de pólizas para el analista de operaciones, que busca por documento, número, socio o titular; y un usuario ligado a un socio solo ve las pólizas de ese socio.

### Goals and non-goals

Objetivos: guardar los datos del titular al emitir desde el portal; rol `analyst` y usuarios del portal ligados a un socio; endpoint de búsqueda con alcance por socio; pestaña "Gestión" con buscador, filtro de estado y tabla de resultados.

Fuera de alcance: el detalle de la póliza (HU-WEB-09); paginación más allá de los primeros 50 resultados; administración de usuarios del portal (los de prueba se siembran en desarrollo); la vista "socios" del mockup.

Decisiones de Juan Camilo Peña (2026-10-27): los datos del titular se guardan al emitir; buscan el analista de operaciones (todas las pólizas) y los usuarios ligados a un socio (solo las de su socio).

### Affected paths

| Path | Change |
|---|---|
| backend/app/modules/auth/models.py | Usuario del portal ligado a un socio |
| backend/app/modules/auth/service.py | Siembra de los usuarios de prueba de operaciones |
| backend/app/modules/policy/models.py | Datos del titular en la póliza y modelos de la búsqueda |
| backend/app/modules/policy/service.py | Guardar el titular al emitir y buscar con alcance |
| backend/app/modules/policy/search.py | Consulta de búsqueda y enmascarado del documento |
| backend/app/bff/experience/router.py | Endpoint de búsqueda y titular en la emisión |
| backend/app/main.py | Columnas nuevas en bases existentes y siembra de usuarios después de los socios |
| backend/tests/integration/policy_search_fixtures.py | Datos de prueba de la búsqueda |
| backend/tests/integration/test_policy_search_api.py | Pruebas de la búsqueda y del alcance por socio |
| backend/tests/integration/test_policy_search_performance.py | Prueba de tiempo de respuesta |
| backend/tests/unit/test_policy_search.py | Pruebas del enmascarado y del filtro |
| web/src/app/app.routes.ts | Ruta de gestión por rol |
| web/src/app/core/guards/role.guard.ts | Acceso a cada pestaña según el rol |
| web/src/app/core/guards/role.guard.spec.ts | Pruebas del acceso por rol |
| web/src/app/core/policies/policy.service.ts | Búsqueda y titular en la emisión |
| web/src/app/core/models/policy.model.ts | Contratos de la búsqueda y del titular |
| web/src/app/features/auth/login.component.ts | Al entrar, cada rol va a su pestaña |
| web/src/app/features/auth/login.component.spec.ts | Prueba de la entrada del analista a Gestión |
| web/src/app/features/gestion/** | Pantalla de consulta de pólizas |
| web/src/app/features/cotizador/cotizador.component.ts | Envía el titular al emitir |
| web/src/app/app.component.html | Nombre del rol en el encabezado |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Analista de operaciones | Buscar una póliza | 1. Entra al portal y llega a Gestión. 2. Escribe un documento, número, socio o titular. 3. Ve las pólizas que coinciden con número, titular, documento enmascarado, plan, estado, vigencia y socio | Sin coincidencias: mensaje y botón para limpiar el filtro. Filtra además por estado |
| UC-02 | Usuario de un socio | Buscar las pólizas de su socio | 1. Entra al portal. 2. Busca. 3. Ve solo las pólizas de su socio | Su socio está revocado: no puede buscar |
| UC-03 | Asesor | Emitir con los datos del titular | 1. Cotiza y emite como antes. 2. La póliza guarda nombre, documento, correo y teléfono del titular | La emisión B2B de socios no trae titular: la póliza muestra solo el documento |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Un usuario de un socio busca el documento o el número de una póliza de otro socio | Usuario de un socio | THR-01 | No aparece: el alcance sale de su usuario en la base, no de la consulta | TC-03 |
| AB-02 | Un asesor llama directo al endpoint de búsqueda | Asesor | THR-02 | 403 | TC-04 |
| AB-03 | Usar la búsqueda para listar documentos completos | Analista | THR-03 | La lista enmascara el documento; la consulta tiene un tope de 50 resultados | TC-05 |
| AB-04 | Inyectar comodines o SQL en el texto de búsqueda | Analista | THR-04 | La consulta es parametrizada y escapa los comodines | TC-05 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN el analista busca por documento, número de póliza o nombre de socio, THE portal SHALL listar las pólizas que coinciden con número, titular, plan, estado y vigencia | STATED |
| AC-02 | THE búsqueda SHALL responder en menos de 150 ms en el percentil 95 | STATED |
| AC-03 | WHEN no hay coincidencias, THE portal SHALL mostrar un mensaje claro y un botón para limpiar el filtro | STATED |
| AC-04 | WHEN busca un usuario ligado a un socio, THE búsqueda SHALL devolver solo pólizas de ese socio | STATED |
| AC-05 | WHEN el asesor emite una póliza, THE sistema SHALL guardar nombre, tipo y número de documento, correo y teléfono del titular | DERIVED: el titular no se guardaba y la historia pide mostrarlo (decisión del 2026-10-27) |
| AC-06 | THE búsqueda SHALL encontrar también por nombre del titular y filtrar por estado | DERIVED: el mockup busca por cliente y filtra por estado |
| AC-07 | THE lista SHALL mostrar el documento enmascarado (solo los últimos 4 dígitos) | DERIVED: minimizar datos personales en pantalla (HA-16); el detalle llega en HU-WEB-09 |
| AC-08 | THE búsqueda SHALL devolver como máximo 50 pólizas, las más recientes primero, e indicar si hay más | DERIVED: acota el tiempo de respuesta y evita listar la base completa |
| AC-09 | IF el usuario no tiene el rol analyst, THE endpoint SHALL responder 403; sin sesión, 401 | DERIVED: solo operaciones consulta (decisión del 2026-10-27) |
| AC-10 | IF el socio del usuario está revocado, THE búsqueda SHALL responder 403 | DERIVED: un socio revocado no debe seguir consultando (HA-14) |
| AC-11 | WHEN el analista o el usuario de un socio entra al portal, THE portal SHALL llevarlo a Gestión, y al asesor al cotizador | DERIVED: cada rol tiene su pestaña, como en el mockup |
| AC-12 | WHEN una póliza no tiene datos del titular, THE lista SHALL mostrar solo el documento enmascarado | DERIVED: las pólizas B2B y las anteriores no traen titular |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | PERFORMANCE | Tiempo de respuesta de la búsqueda | p95 por debajo de 150 ms con 10.000 pólizas, 200 búsquedas mezcladas (documento, número, socio y titular), PostgreSQL local |
| QR-02 | ACCESSIBILITY | La consulta se usa con teclado y lector de pantalla | WCAG 2.2 AA: campo con etiqueta, tabla con encabezados, resultado y "sin resultados" anunciados |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | Nombre, documento, correo y teléfono del titular |
| Sensitive data (special categories, biometrics, health, financial, minors) | NO | Plan y estado de la póliza, sin datos de salud ni financieros |
| Authentication, sessions, credentials or one-time codes | YES | Usuarios de prueba nuevos y redirección por rol al entrar |
| Authorization or access to other users' resources | YES | Rol analyst y alcance por socio |
| Payments or money movement | NO | No aplica |
| Third-party services or integrations | NO | No aplica |
| Infrastructure, network, IAM, CI/CD or secrets | NO | No aplica |
| Multiple tenants, partners or organizations | YES | Cada socio ve solo sus pólizas |
| Storage or processing on a mobile device | NO | No aplica |
| Web UI or data exposed in the browser | YES | La lista muestra datos del titular |
| New or updated dependencies | NO | Sin dependencias nuevas |
| Critical paths (sdd/security/critical-paths.md) | YES | backend/app/modules/auth/** y web/src/app/core/guards/** |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | El alcance por socio sale del usuario en la base en cada búsqueda, no de la consulta ni del token | DATA-01, THR-01 | CTL-01 | HIGH |
| SR-02 | Solo el rol analyst busca; sin sesión 401 y con otro rol 403 | THR-02 | CTL-02 | HIGH |
| SR-03 | La lista enmascara el documento, tiene tope de resultados y no devuelve correo ni teléfono | DATA-01, THR-03 | CTL-03 | HIGH |
| SR-04 | La consulta es parametrizada y escapa los comodines del texto | THR-04 | CTL-04 | HIGH |
| SR-05 | Un socio revocado no consulta | THR-01 | CTL-01 | MEDIUM |
| SR-06 | Los usuarios de prueba solo existen en desarrollo y reutilizan la credencial del asesor semilla, sin secretos nuevos | THR-05 | CTL-05 | MEDIUM |

## 2. Design

### Components

Backend (ADR 0004 y 0005):

- `modules/auth/models.py`: `UserDB.partner_id`, opcional y con llave foránea a `partners`.
- `modules/auth/service.py`: en desarrollo siembra `operaciones@solventa.example` (rol `analyst`, sin socio) y `analista@bancoaliado.example` (rol `analyst`, socio `banco-aliado-1`). Usan la misma contraseña y el mismo secreto TOTP del asesor semilla.
- `modules/policy/models.py`:
  - `PolicyDB` suma `holder_name`, `holder_document_type`, `holder_email` y `holder_phone`, opcionales;
  - `PolicyIssueRequest` suma `holder`;
  - modelos `PolicySearchItem` y `PolicySearchResponse`.
- `modules/policy/search.py`: consulta parametrizada sobre `policies` con `partners`. Busca:
  - el número por prefijo;
  - el documento exacto o por prefijo;
  - el nombre del socio y el del titular por contenido, sin distinguir mayúsculas.

  Escapa `%` y `_`, filtra por estado y aplica el alcance. Trae 51 filas para saber si hay más. También enmascara el documento.
- `bff/experience/router.py`:
  - `GET /api/v1/experience/policies/search`, con el rol `analyst`; busca al usuario por el `sub` del token y toma su `partner_id`;
  - la emisión recibe `holder`.

Portal:

- `features/gestion/`: `GestionComponent`, con buscador, filtro de estado, contador, tabla accesible, "sin resultados" con "Limpiar filtro" y el aviso de que hay más.
- `core/guards/role.guard.ts`: cada ruta declara los roles que la usan.
- `login.component.ts`: el asesor va a `/cotizador`; el analista, a `/gestion`.
- `cotizador.component.ts`: envía el titular (nombre, documento, correo y teléfono del paso 1) al emitir.

### Data flows

Analista → portal → `GET /api/v1/experience/policies/search?q=…&status=…` (token del analista) → backend (rol, usuario y socio en la base) → PostgreSQL (`policies` con `partners`) → lista con el documento enmascarado.

### Data contract

`GET /api/v1/experience/policies/search`, con rol `analyst`.

| Parámetro | Tipo | Notas |
|---|---|---|
| q | string, 0 a 80 caracteres | Documento, número, socio o titular; vacío trae las más recientes |
| status | ACTIVE, ISSUED, CANCELLED u omitido | Filtro de estado |

Respuesta `200`:

| Campo | Tipo | Notas |
|---|---|---|
| items[].policy_number | string | |
| items[].holder_name | string o null | null si la póliza no tiene titular |
| items[].holder_document | string | Enmascarado: `******4050` |
| items[].plan | basic, standard o premium | |
| items[].status | ACTIVE, ISSUED o CANCELLED | |
| items[].valid_from y valid_until | fecha y hora ISO 8601 | |
| items[].partner_name | string o null | null en pólizas del portal |
| has_more | boolean | true si había más de 50 |

Errores: `401` sin sesión; `403` con otro rol o con el socio revocado; `422` si `q` pasa de 80 caracteres o el estado no es válido.

`POST /api/v1/experience/policies` suma el objeto opcional `holder` con `name`, `document_type`, `email` y `phone`; el documento sale de la cotización. Clasificación: CONFIDENTIAL (DATA-01).

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| El socio sale del usuario en la base en cada búsqueda | CTL-01 | Leerlo del token: un cambio de socio o una revocación tardaría en aplicarse |
| Rol `analyst` en el endpoint | CTL-02 | Abrirlo al asesor: no es parte de su trabajo (decisión del 2026-10-27) |
| Documento enmascarado, tope de 50, sin correo ni teléfono | CTL-03 | Devolver la póliza completa: expone datos que la lista no necesita |
| Consulta parametrizada con comodines escapados | CTL-04 | Armar el filtro con texto: inyección y búsquedas que lo traen todo |
| Usuarios de prueba solo en desarrollo, con la credencial existente | CTL-05 | Credenciales nuevas en el código: más secretos que cuidar |

### Architecture decisions

N/A: reutiliza la arquitectura hexagonal (ADR 0004) y la estructura por funcionalidad del portal (ADR 0009); la consulta lee directo de las tablas, que hoy sirven de vista de lectura (HA-04).

### Configuration and deployment

- **Columnas nuevas** en tablas existentes: `policies.partner_id` (HU-WEB-14 la agregó al modelo, pero no al arranque, y las bases creadas antes fallaban al leer o emitir pólizas), `users.partner_id`, y `holder_name`, `holder_document_type`, `holder_email` y `holder_phone` en `policies`. El proyecto no tiene migraciones; el arranque las agrega con `ALTER TABLE … ADD COLUMN IF NOT EXISTS`, el mismo patrón que dejó HU-WEB-10 para `quotes.partner_id`, así que las bases existentes no hay que recrearlas.
- **Índice** `ix_policies_insured_document`, creado con la tabla o con `CREATE INDEX IF NOT EXISTS` al arrancar.
- **Siembra:** en desarrollo, los usuarios se siembran después de los socios, para que el analista de Banco Aliado encuentre a su socio.
- **Variables:** ninguna nueva.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Datos del titular en la póliza y en la emisión del portal | AC-05, AC-12 | — | DONE | backend/tests/integration/test_policy_search_api.py::test_tc_02_al_emitir_desde_el_portal_se_guarda_el_titular; web/src/app/features/cotizador/cotizador.component.spec.ts |
| T-02 | Rol analyst, usuario ligado a socio y usuarios de prueba | AC-04, AC-09, AC-10, AC-11, SR-06 | CTL-01, CTL-02, CTL-05 | DONE | backend/tests/integration/test_policy_search_api.py::test_tc_07_los_analistas_de_prueba_quedan_con_su_rol_y_su_socio; backend/app/main.py |
| T-03 | Endpoint de búsqueda con filtros, índice, alcance y enmascarado (#169) | AC-01, AC-02, AC-04, AC-06, AC-07, AC-08, SR-01, SR-02, SR-03, SR-04, SR-05 | CTL-01, CTL-02, CTL-03, CTL-04 | DONE | backend/app/modules/policy/search.py; backend/app/bff/experience/router.py |
| T-04 | Pantalla de consulta, guardia por rol y redirección al entrar (#170) | AC-01, AC-03, AC-11, QR-02 | CTL-02 | DONE | web/src/app/features/gestion/gestion.component.ts; web/src/app/core/guards/role.guard.ts |
| T-05 | Pruebas unitarias, de integración y de tiempo de respuesta (#171) | AC-01, AC-02, AC-03, AC-04, AC-09, AC-10, QR-01 | CTL-01, CTL-02, CTL-03, CTL-04 | DONE | backend/tests/integration/test_policy_search_api.py; backend/tests/integration/test_policy_search_performance.py; backend/tests/unit/test_policy_search.py; web/src/app/features/gestion/gestion.component.spec.ts; web/src/app/core/guards/role.guard.spec.ts |

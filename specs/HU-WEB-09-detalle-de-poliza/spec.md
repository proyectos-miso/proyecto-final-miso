# HU-WEB-09 · Detalle de póliza

| Field | Value |
|---|---|
| ID | HU-WEB-09 |
| Title | Detalle de póliza |
| User story | https://github.com/proyectos-miso/proyecto-final-miso/issues/63 |
| Owner | Juan Camilo Peña |
| Status | IN_PROGRESS |
| Created | 2026-10-27 |
| Security profile | HIGH |

## 1. Requirements

### User story

**HU-WEB-09 · Consultar el detalle completo de una póliza** (Sprint 2, Must have, 3 puntos, FE-WEB-03, ESC-LAT-04; depende de HU-WEB-08).

**Como** analista de operaciones **quiero** ver coberturas, sumas aseguradas, vigencia, prima, estado y siniestros asociados de una póliza **para** responder al cliente con información completa en una sola pantalla.

Criterios de aceptación de la historia:

1. **Detalle completo.** *Dada* una póliza de la búsqueda, *cuando* el analista la abre, *entonces* ve titular, plan, coberturas con sus sumas aseguradas, vigencia, prima, estado, historial de pagos y siniestros asociados.
2. **Datos protegidos.** *Dado* el detalle, *cuando* se muestra, *entonces* el documento y los datos de contacto del titular aparecen parcialmente enmascarados.
3. **Póliza inexistente.** *Dado* un número de póliza que no existe, *cuando* se consulta, *entonces* se informa que no se encontró, sin mostrar errores técnicos.
4. **Sin siniestros.** *Dada* una póliza sin siniestros, *cuando* se ve el detalle, *entonces* la sección indica que no hay siniestros registrados.

Requisitos no funcionales: HA-04 (servir la consulta desde una vista de lectura), HA-11 (preservar la integridad de cobros y pólizas ante fallos y reintentos), HA-16 (proteger los datos personales almacenados). Mockup: https://solventa-web.vercel.app/?pestana=gestion, panel "Detalle de póliza seleccionada". Tareas: #172 endpoint de detalle con coberturas, pagos y siniestros; #173 pantalla de detalle con enmascaramiento; #174 pruebas unitarias y de integración.

### Problem and outcome

Con HU-WEB-08 el analista encuentra la póliza, pero para responder al cliente necesita sus coberturas, la prima, los pagos y los siniestros, y eso no está en ninguna pantalla. Además, los siniestros todavía no existen en el sistema: los crea HU-MOV-10 y los liquida HU-WEB-15. Resultado: desde la lista de Gestión el analista abre el detalle de una póliza en un panel, con todo en una sola vista y los datos personales enmascarados; la sección de siniestros lee una tabla mínima que HU-MOV-10 llenará.

### Goals and non-goals

Objetivos: endpoint de detalle con el mismo alcance por socio de la búsqueda; tabla mínima de siniestros de solo lectura; panel de detalle en Gestión, que se abre desde la tabla o con el enlace `?poliza=`.

Fuera de alcance: reportar, editar o liquidar siniestros (HU-MOV-10 y HU-WEB-15); descripción y ubicación del siniestro; ver el documento o el contacto sin enmascarar; beneficiarios; cancelar la póliza (FE-WEB-04); descargar la carátula (HU-WEB-16).

Decisiones de Juan Camilo Peña (2026-10-27): los siniestros se leen de una tabla mínima (número de radicado, póliza, estado y fecha de reporte) que HU-MOV-10 amplía; el detalle no muestra la descripción ni la ubicación del siniestro, porque en un seguro de vida pueden traer datos de salud.

### Affected paths

| Path | Change |
|---|---|
| backend/app/modules/claims/** | Tabla mínima de siniestros y su resumen |
| backend/app/modules/policy/models.py | Modelos del detalle |
| backend/app/modules/policy/detail.py | Consulta del detalle y enmascarado del contacto |
| backend/app/modules/policy/service.py | Suma asegurada compartida con la tarjeta del asegurado |
| backend/app/bff/experience/router.py | Endpoint de detalle y alcance por socio compartido con la búsqueda |
| backend/app/main.py | Registro de la tabla de siniestros |
| backend/tests/integration/policy_search_fixtures.py | Datos de prueba de pagos y siniestros |
| backend/tests/integration/test_policy_detail_api.py | Pruebas del detalle, del alcance y del enmascarado |
| backend/tests/integration/test_policy_search_performance.py | Tiempo de respuesta del detalle |
| backend/tests/unit/test_policy_detail.py | Pruebas del enmascarado del contacto |
| web/src/app/core/models/policy.model.ts | Contrato del detalle |
| web/src/app/core/policies/policy.service.ts | Consulta del detalle |
| web/src/app/features/gestion/** | Panel de detalle y selección desde la tabla |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Analista de operaciones | Ver el detalle de una póliza | 1. Busca en Gestión. 2. Pulsa el número de una póliza. 3. Ve titular con documento y contacto enmascarados, plan, estado, vigencia, socio, suma asegurada, prima mensual y anual, coberturas con su suma, pagos y siniestros. 4. Cierra el panel y vuelve a la tabla | Póliza sin siniestros: "No hay siniestros registrados". Sin pagos: "No hay pagos registrados". Póliza B2B sin titular: "Sin nombre registrado" y sin contacto |
| UC-02 | Analista de operaciones | Abrir una póliza por enlace | 1. Abre `/gestion?poliza=POL-…`. 2. Ve el detalle | La póliza no existe: "No encontramos la póliza …", sin errores técnicos |
| UC-03 | Usuario de un socio | Ver el detalle de una póliza de su socio | 1. Busca. 2. Abre una póliza de su socio. 3. Ve el detalle | Pide una póliza de otro socio: la misma respuesta que si no existiera. Su socio está revocado: no consulta |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Un usuario de un socio pide por número el detalle de una póliza de otro socio o del portal | Usuario de un socio | THR-01 | 404 igual al de una póliza inexistente: no confirma que exista | TC-05 |
| AB-02 | Un asesor o alguien sin sesión llama al endpoint de detalle | Asesor, atacante externo | THR-02 | 403 o 401 | TC-06 |
| AB-03 | Usar el detalle para obtener documento, correo y teléfono completos o datos del medio de pago | Analista | THR-03 | Documento, correo y teléfono enmascarados; los pagos no traen token ni cuenta | TC-02, TC-07 |
| AB-04 | Recorrer números de póliza consecutivos o mandar texto arbitrario en la ruta | Analista | THR-04 | El número se valida con un patrón; 404 para lo que no existe o no está en su alcance | TC-03, TC-05 |
| AB-05 | Guardar marcado HTML en el nombre del titular para que se ejecute en el panel | Asesor al emitir | THR-05 | El portal lo muestra como texto | TC-08 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN el analista abre una póliza de la búsqueda, THE portal SHALL mostrar titular, plan, coberturas con sus sumas aseguradas, vigencia, prima, estado, historial de pagos y siniestros asociados | STATED |
| AC-02 | WHEN se muestra el detalle, THE portal SHALL mostrar el documento y los datos de contacto del titular parcialmente enmascarados | STATED |
| AC-03 | WHEN se consulta un número de póliza que no existe, THE portal SHALL informar que no se encontró, sin errores técnicos | STATED |
| AC-04 | WHEN la póliza no tiene siniestros, THE sección de siniestros SHALL indicar que no hay siniestros registrados | STATED |
| AC-05 | WHEN un usuario ligado a un socio pide el detalle de una póliza de otro socio o del portal, THE endpoint SHALL responder igual que si no existiera (404) | DERIVED: mismo alcance que la búsqueda (HA-14), sin confirmar que la póliza existe |
| AC-06 | IF el usuario no tiene el rol analyst, THE endpoint SHALL responder 403; sin sesión, 401; con el socio revocado, 403 | DERIVED: mismas reglas de acceso que la búsqueda de HU-WEB-08 |
| AC-07 | THE detalle SHALL enmascarar así: documento con los últimos 4 dígitos, correo con la primera letra y el dominio, teléfono con los últimos 4 dígitos | DERIVED: la historia pide enmascarar "parcialmente"; esto deja reconocer al cliente al teléfono sin exponer el dato (HA-16) |
| AC-08 | THE historial de pagos SHALL listar los cobros del más reciente al más antiguo, con fecha, valor en COP, medio, estado, recibo y, si fue rechazado, el motivo; sin token ni cuenta del medio de pago | DERIVED: el detalle de qué es "historial de pagos"; los cobros guardan solo el resultado (HA-11, HA-15) |
| AC-09 | THE sección de siniestros SHALL listar número de radicado, estado y fecha de reporte, del más reciente al más antiguo, sin descripción ni ubicación | DERIVED: decisión del 2026-10-27 para no exponer datos de salud |
| AC-10 | WHEN la póliza no tiene titular registrado, THE detalle SHALL mostrar "Sin nombre registrado", el documento enmascarado y ningún dato de contacto | DERIVED: las pólizas B2B y las anteriores a HU-WEB-08 no traen titular |
| AC-11 | WHEN el analista abre o cierra el detalle con el teclado, THE portal SHALL llevar el foco al título del panel al abrir y devolverlo al número de la póliza al cerrar | DERIVED: accesibilidad del panel (WCAG 2.2 AA) |
| AC-12 | WHEN se abre el detalle, THE portal SHALL reflejar la póliza en la dirección (`?poliza=`) para poder compartirla con otro analista | DERIVED: da un camino real al caso "número que no existe" y evita copiar datos en otros canales |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | PERFORMANCE | Tiempo de respuesta del detalle | p95 por debajo de 150 ms con 10.000 pólizas, 100 consultas de detalle de pólizas con pagos y siniestros, PostgreSQL local |
| QR-02 | ACCESSIBILITY | El panel se usa con teclado y lector de pantalla | WCAG 2.2 AA: botón por póliza, foco al título al abrir y de vuelta al cerrar, secciones con encabezados, listas y "sin siniestros" legibles |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | Nombre, documento, correo y teléfono del titular |
| Sensitive data (special categories, biometrics, health, financial, minors) | NO | El siniestro se muestra sin descripción ni ubicación; los pagos no traen datos del medio de pago |
| Authentication, sessions, credentials or one-time codes | NO | Usa el ingreso y la sesión existentes |
| Authorization or access to other users' resources | YES | Rol analyst y acceso a una póliza por su número |
| Payments or money movement | NO | Solo lee el historial de cobros; no cobra ni mueve dinero |
| Third-party services or integrations | NO | No aplica |
| Infrastructure, network, IAM, CI/CD or secrets | NO | No aplica |
| Multiple tenants, partners or organizations | YES | Cada socio ve solo el detalle de sus pólizas |
| Storage or processing on a mobile device | NO | No aplica |
| Web UI or data exposed in the browser | YES | El panel muestra datos del titular |
| New or updated dependencies | NO | Sin dependencias nuevas |
| Critical paths (sdd/security/critical-paths.md) | YES | backend/app/main.py (configuración), solo para registrar la tabla de siniestros; no toca autenticación ni pagos |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | El alcance por socio sale del usuario en la base en cada consulta; una póliza fuera del alcance responde igual que una inexistente | DATA-01, THR-01 | CTL-01 | HIGH |
| SR-02 | Solo el rol analyst consulta el detalle; sin sesión 401, con otro rol o con el socio revocado 403 | THR-02 | CTL-02 | HIGH |
| SR-03 | El detalle enmascara documento, correo y teléfono, y no devuelve beneficiarios ni datos del medio de pago | DATA-01, DATA-02, THR-03 | CTL-03 | HIGH |
| SR-04 | El número de póliza de la ruta se valida con un patrón y la consulta es parametrizada | THR-04 | CTL-04 | MEDIUM |
| SR-05 | El portal muestra los datos de la póliza como texto, sin interpretar HTML | THR-05 | CTL-05 | MEDIUM |
| SR-06 | El siniestro se muestra sin descripción ni ubicación | DATA-03, THR-03 | CTL-03 | MEDIUM |

## 2. Design

### Components

Backend (ADR 0004 y 0005):

- `modules/claims/models.py` (nuevo): `ClaimDB` en la tabla `claims` con `claim_number` (radicado), `policy_number` (llave foránea e índice), `status` y `reported_at`; y `ClaimSummary`. HU-MOV-10 la amplía con lo que necesite el reporte.
- `modules/policy/models.py`: `PolicyDetail` con el titular enmascarado, plan, estado, vigencia, socio, suma asegurada, primas, coberturas, pagos y siniestros.
- `modules/policy/detail.py` (nuevo):
  - `get_policy_detail(session, policy_number, partner_id)`: la póliza con su socio dentro del alcance, sus cobros y sus siniestros; `None` si no existe o no está en el alcance;
  - `mask_email` y `mask_phone`; el documento usa `mask_document` de la búsqueda.
- `modules/policy/service.py`: `insured_amount(conditions)`, la regla de la suma asegurada que ya usa la tarjeta del asegurado.
- `bff/experience/router.py`:
  - `GET /api/v1/experience/policies/{policy_number}`, con el rol `analyst`;
  - el alcance (usuario activo y socio habilitado) pasa a una dependencia que comparten la búsqueda y el detalle.

Portal:

- `features/gestion/policy-detail.component.ts` (nuevo): panel con el resumen, el titular, las coberturas, los pagos y los siniestros en secciones con encabezado, todo en una sola vista. El mockup separa coberturas y siniestros en pestañas; aquí van en secciones seguidas, porque la historia pide todo en una sola pantalla y así el lector de pantalla las recorre sin un patrón de pestañas.
- `features/gestion/gestion.component.ts`: el número de cada póliza es un botón que abre el detalle y pone `?poliza=` en la dirección; con la dirección cargada abre el detalle al entrar; al cerrar devuelve el foco al botón.
- `core/policies/policy.service.ts`: `getPolicyDetail(number)`.

### Data flows

Analista → portal (`/gestion?poliza=…`) → `GET /api/v1/experience/policies/{policy_number}` (token del analista) → backend (rol, usuario y socio en la base) → PostgreSQL (`policies` con `partners`, `payment_transactions` y `claims`) → detalle con documento, correo y teléfono enmascarados.

### Data contract

`GET /api/v1/experience/policies/{policy_number}`, con rol `analyst`. `policy_number` cumple `^POL-\d{4}-\d{5}$`.

Respuesta `200`:

| Campo | Tipo | Notas |
|---|---|---|
| policy_number | string | |
| status | ACTIVE, ISSUED o CANCELLED | |
| plan | basic, standard o premium | |
| valid_from y valid_until | fecha y hora ISO 8601 | |
| partner_name | string o null | null en pólizas del portal |
| holder.name | string o null | null si la póliza no tiene titular |
| holder.document_type | string o null | |
| holder.document | string | Enmascarado: `******4050` |
| holder.email | string o null | Enmascarado: `c*****@example.com` |
| holder.phone | string o null | Enmascarado: `******4567` |
| insured_amount | número | Suma asegurada en COP |
| monthly_premium y annual_premium | número | En COP |
| coverages[] | name, limit, deductible | Solo las incluidas en el plan |
| payments[] | paid_at, amount, currency, method, status, receipt_number, decline_reason | Del más reciente al más antiguo |
| claims[] | claim_number, status, reported_at | Del más reciente al más antiguo |

Errores: `401` sin sesión; `403` con otro rol o con el socio revocado; `404` con `"No encontramos la póliza."` si no existe o no está en el alcance; `422` si el número no cumple el patrón. Clasificación: CONFIDENTIAL (DATA-01).

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| El socio sale del usuario en la base; fuera del alcance responde 404 | CTL-01 | Responder 403: confirmaría que la póliza existe en otro socio |
| Rol `analyst` y la misma dependencia de alcance que la búsqueda | CTL-02 | Copiar la validación en cada endpoint: se desalinean con el tiempo |
| Documento, correo y teléfono enmascarados en el backend; sin beneficiarios ni datos del medio de pago | CTL-03 | Enmascarar solo en el portal: el dato completo viajaría y quedaría en las herramientas del navegador |
| Patrón del número en la ruta y consulta parametrizada | CTL-04 | Aceptar cualquier texto: más superficie sin necesidad |
| Interpolación de Angular, sin `innerHTML` | CTL-05 | Plantillas con HTML armado: abre la puerta a XSS almacenado |

### Architecture decisions

N/A: reutiliza la arquitectura hexagonal (ADR 0004) y la estructura por funcionalidad del portal (ADR 0009). El detalle lee directo de las tablas, que hoy sirven de vista de lectura (HA-04); el módulo `claims` nace como lectura y HU-MOV-10 decide su escritura.

### Configuration and deployment

- **Tabla nueva** `claims`: el arranque la crea con `create_all` en bases nuevas y existentes; no cambia tablas existentes.
- **Variables:** ninguna nueva.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Tabla mínima de siniestros de solo lectura | AC-04, AC-09, SR-06 | CTL-03 | TODO | — |
| T-02 | Endpoint de detalle con alcance, enmascarado, pagos y siniestros (#172) | AC-01, AC-02, AC-03, AC-05, AC-06, AC-07, AC-08, AC-10, SR-01, SR-02, SR-03, SR-04 | CTL-01, CTL-02, CTL-03, CTL-04 | TODO | — |
| T-03 | Panel de detalle en Gestión, con enlace y foco (#173) | AC-01, AC-03, AC-04, AC-10, AC-11, AC-12, QR-02, SR-05 | CTL-05 | TODO | — |
| T-04 | Pruebas unitarias, de integración y de tiempo de respuesta (#174) | AC-01, AC-02, AC-03, AC-05, AC-06, AC-07, AC-08, QR-01 | CTL-01, CTL-02, CTL-03, CTL-04 | TODO | — |

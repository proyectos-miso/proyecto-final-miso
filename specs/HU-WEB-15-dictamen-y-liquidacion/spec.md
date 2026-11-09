# HU-WEB-15 · Dictamen y liquidación

| Field | Value |
|---|---|
| ID | HU-WEB-15 |
| Title | Dictamen y liquidación |
| User story | https://github.com/proyectos-miso/proyecto-final-miso/issues/115 |
| Owner | Juan Camilo Peña |
| Status | IN_REVIEW |
| Created | 2026-11-06 |
| Security profile | HIGH |

<!-- A spec is a contract: what, why, how, and how we will know it is done. Fill the sections in
     order and stop for agreement between them: Requirements → Design → Tasks.
     Status: DRAFT → READY (after spec-review) → IN_PROGRESS → IN_REVIEW → DONE | BLOCKED. -->

## 1. Requirements

### User story

**HU-WEB-15 · Dictamen y liquidación de siniestros** (Sprint 3, Must have, 3 puntos, FE-WEB-04, ESC-LAT-04 y ESC-SEG-06; depende de HU-MOV-08). Responsable: Juan Camilo Peña (reasignada el 2026-11-04 con el reparto del Sprint 3).

**Como** analista de operaciones de Solventa **quiero** listar y ver los detalles de los siniestros reportados mediante la aplicación móvil **para** liquidar o rechazar el amparo de la póliza del usuario que reporta el siniestro con trazabilidad auditable y soporte probatorio.

Criterios de aceptación de la historia:

1. **Listado de siniestros reportados.** *Dado* un analista de operaciones en el portal web, *cuando* accede al módulo de siniestros, *entonces* visualiza la lista de siniestros reportados mediante la aplicación móvil con radicado, póliza, fecha de reporte y estado actual.
2. **Expediente y detalle del siniestro.** *Dado* un siniestro radicado en la lista, *cuando* el analista abre su detalle, *entonces* ve la información de la póliza, el amparo afectado, los datos del usuario asegurado que reporta, la descripción del siniestro, las fotos con su ubicación geográfica y hora, y el historial de estados.
3. **Evidencia íntegra.** *Dada* una foto del expediente, *cuando* se muestra en el detalle, *entonces* el portal indica si coincide con la huella (hash) registrada al recibirla desde el dispositivo móvil, y la marca como alterada si no coincide.
4. **Dictamen con justificación.** *Dado* un siniestro en evaluación, *cuando* el analista decide liquidar o rechazar el amparo, *entonces* debe registrar una justificación obligatoria y el siniestro cambia al estado correspondiente.
5. **Liquidación dentro del amparo.** *Dado* un siniestro aprobado para liquidación, *cuando* el analista registra el valor a pagar, *entonces* el sistema valida que el valor no supere la suma asegurada del amparo de la póliza.
6. **Decisión auditable.** *Dado* un dictamen registrado (liquidación o rechazo), *cuando* se consulta posteriormente en el expediente, *entonces* se presenta de solo lectura indicando quién lo tomó, en qué fecha y hora, y con qué justificación, sin posibilidad de alteración.

Requisitos no funcionales: HA-04 (vista de lectura), HA-16 (datos personales almacenados), HA-17 (registros cerrados inmutables), HA-18 (reconstruir decisiones). Mockup: seis pantallas en el issue (bandeja, expediente, verificación de huella, dictamen, liquidación y dictamen en solo lectura). Tareas: #248 expediente y huella, #249 dictamen y liquidación, #250 pantallas, #251 pruebas.

**Brechas de rutas críticas que absorbe** (`specs/pendientes/HU-WEB-15.md`): RC-01 beneficiarios, RC-05 pruebas de servicios web, RC-17 cancelación y terminación, RC-18 asignación a analista y perito, RC-19 decisiones reconstruibles, RC-20 cierre de sesión del portal, RC-21 endosos, RC-22 reportes al regulador y al reasegurador, RC-28 liquidación paramétrica y RC-29 pago de la indemnización.

### Problem and outcome

El asegurado ya puede tomar las fotos de un siniestro en la app (HU-MOV-08), pero nadie en Solventa puede revisarlas, decidir ni pagar. Además, la póliza no tiene beneficiarios a quién pagar, no se puede cancelar ni modificar, y no hay reportes para el regulador.

Resultado: el analista tiene en el portal una bandeja de siniestros y el expediente de cada uno, con las fotos, su ubicación y la verificación de su huella. Toma el caso, dictamina con justificación, liquida dentro del amparo y el sistema paga a los beneficiarios. Los siniestros paramétricos se liquidan solos. Sobre la póliza, el analista registra beneficiarios y endosos y la cancela, y cada decisión queda en un registro inmutable que se puede reconstruir.

### Goals and non-goals

Objetivos:
- **Siniestros:** bandeja, expediente, verificación de huella, asignación, dictamen, liquidación, pago a los beneficiarios y liquidación automática de los paramétricos.
- **Póliza:** beneficiarios al emitir, endosos, cancelación y terminación.
- **Trazabilidad:** registro inmutable de cada decisión y vista de auditoría por póliza.
- **Reportes:** reportes mensuales al regulador y al reasegurador.
- **Portal:** cierre de sesión con revocación y pruebas de los servicios web.

Fuera de alcance:
- **Recibir el reporte y darle radicado:** son HU-MOV-09 y HU-MOV-10. Esta historia lee lo que ellas guardan.
- **Avisar al asegurado:** es HU-MOV-11 (RC-15).
- **Terceros:** el pago real al banco y el envío al regulador no se hacen. El pago usa la pasarela simulada del módulo de pagos y los reportes se generan para descarga.
- **Peritos:** solo hay un catálogo para asignar la inspección; no se integra ningún sistema externo.
- **Cambios de tarifa:** el endoso no recalcula la prima; solo cambia beneficiarios y suma asegurada dentro de los límites de suscripción.

**Decisiones de Juan Camilo Peña (2026-11-06):** la historia se hace completa, sin partirla. Esta historia fija el modelo del siniestro en el backend (`claims`, `claim_photos`, `claim_events` y `experts`) y deja `claim_service.register_claim` como punto de entrada: HU-MOV-09 lo llama al recibir el reporte y HU-MOV-10 al dar el radicado o al recibir un evento paramétrico. Los campos de cada foto son los que el teléfono ya guarda (ADR 0024). Se les informa a David y a Oscar en el PR.

### Affected paths

| Path | Change |
|---|---|
| backend/app/modules/claims/** | Expediente, fotos con huella, historial inmutable, peritos, dictamen, liquidación y regla paramétrica |
| backend/app/modules/policy/** | Beneficiarios, versión, endosos, cancelación, terminación, auditoría y carátula con beneficiarios |
| backend/app/modules/payments/** | Pagos de indemnización y devolución por la pasarela simulada |
| backend/app/modules/reports/** | Reporte mensual al regulador y al reasegurador |
| backend/app/modules/auth/service.py | Revocación del refresh al renovar y al cerrar sesión |
| backend/app/core/security.py | `jti` en el refresh |
| backend/app/core/storage.py | Lectura y enlace en línea de las fotos en S3 |
| backend/app/core/immutable.py | Disparador que impide cambiar o borrar registros cerrados |
| backend/app/bff/experience/router.py | Cierre de sesión del portal |
| backend/app/bff/experience/operations_router.py | Endpoints del analista |
| backend/app/main.py | Tablas, columnas y peritos en bases existentes |
| backend/tests/** | Pruebas nuevas y beneficiarios en las pruebas de emisión |
| terraform/modules/ecs/main.tf | Lectura de `siniestros/*` en S3 para el backend |
| web/src/app/features/siniestros/** | Bandeja y expediente |
| web/src/app/features/gestion/** | Beneficiarios, operaciones, auditoría y reporte en Gestión |
| web/src/app/features/shared/** | Editor de beneficiarios |
| web/src/app/features/cotizador/** | Beneficiarios al emitir |
| web/src/app/core/** | Modelos, servicios, cierre de sesión y renovación única |
| web/src/app/app.routes.ts | Rutas de siniestros para el analista |
| web/src/app/app.config.ts | Parámetros de ruta como entradas del componente |
| web/src/app/app.component.* | Navegación del analista entre Pólizas y Siniestros |
| specs/pendientes/** | La nota de HU-WEB-15 se retira: sus brechas quedaron en este spec |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Analista de operaciones | Atender un siniestro | 1. Abre la bandeja. 2. Abre el expediente. 3. Revisa fotos, ubicación, hora y huellas. 4. Toma el caso o asigna un perito. 5. Dictamina con justificación. 6. Si aprueba, registra el valor dentro del amparo. 7. El sistema ordena el pago a los beneficiarios | Foto alterada o ubicación simulada: el expediente lo marca y el dictamen lo registra. Valor mayor al amparo: se rechaza. Pago fallido: se reintenta sin duplicar |
| UC-02 | Sistema (regla paramétrica) | Liquidar un siniestro paramétrico | 1. Llega un siniestro de origen paramétrico (HU-MOV-10). 2. La regla verifica la condición. 3. Liquida y ordena el pago sin analista | Si no cumple la regla, queda en la bandeja para un analista |
| UC-03 | Asesor | Emitir con beneficiarios | 1. En el cotizador registra los beneficiarios. 2. La póliza se emite con ellos | Porcentajes que no suman 100 %: no deja emitir |
| UC-04 | Analista de operaciones | Modificar o cancelar una póliza | 1. Desde el detalle registra un endoso (beneficiarios o suma) o la cancelación con motivo. 2. El sistema guarda la versión, emite la carátula nueva o calcula la devolución | Siniestro de muerte liquidado: la póliza pasa a terminada y no admite endosos |
| UC-05 | Analista de operaciones | Reconstruir decisiones y generar reportes | 1. Abre la auditoría de una póliza y ve en orden sus decisiones. 2. Descarga el reporte mensual al regulador y al reasegurador | Mes sin datos: el reporte sale vacío con sus encabezados |
| UC-06 | Asesor o analista | Cerrar sesión | 1. Pulsa «Cerrar sesión». 2. El backend revoca su refresh | Refresh concurrente: una sola renovación en vuelo |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Un usuario de un socio abre o dictamina un siniestro de otro socio | Usuario de un socio | THR-01 | 404 igual que si no existiera | TC-07 |
| AB-02 | Liquidar por encima del amparo o dos veces el mismo siniestro | Analista | THR-02 | 422; un siniestro liquidado no admite otra liquidación | TC-03 |
| AB-03 | Cambiar o borrar un dictamen ya registrado | Analista o atacante con acceso a la base | THR-03 | La API no lo permite y la base rechaza UPDATE y DELETE | TC-09 |
| AB-04 | Pagar dos veces la indemnización por un reintento | Sistema o analista | THR-02 | Una sola orden por siniestro y beneficiario | TC-04 |
| AB-05 | Sustituir una foto del expediente | Atacante | THR-04 | La huella no coincide y la foto se marca alterada | TC-02 |
| AB-06 | Un asesor o alguien sin sesión usa los endpoints del analista | Asesor, atacante | THR-05 | 403 o 401 | TC-07 |
| AB-07 | Fingir un evento paramétrico para cobrar sin analista | Atacante | THR-06 | La regla vuelve a verificar la condición y solo liquida pólizas vigentes; la firma del evento la valida HU-MOV-10 al recibirlo | TC-05 |
| AB-08 | Seguir usando un refresh después de cerrar sesión | Atacante con el refresh | THR-07 | 401 | TC-08 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN el analista abre el módulo de siniestros, THE portal SHALL listar los siniestros con radicado, póliza, fecha de reporte y estado | STATED |
| AC-02 | WHEN el analista abre un siniestro, THE portal SHALL mostrar la póliza, el amparo afectado, los datos del asegurado, la descripción, las fotos con ubicación y hora, y el historial de estados | STATED |
| AC-03 | WHEN se muestra una foto, THE portal SHALL indicar si coincide con la huella registrada al recibirla y marcarla alterada si no coincide | STATED |
| AC-04 | WHEN el analista liquida o rechaza, THE sistema SHALL exigir una justificación y cambiar el estado del siniestro | STATED |
| AC-05 | WHEN el analista registra el valor a pagar, THE sistema SHALL rechazar un valor mayor que la suma asegurada del amparo | STATED |
| AC-06 | WHEN se consulta un dictamen registrado, THE portal SHALL mostrarlo de solo lectura con quién, fecha y hora, y justificación, sin posibilidad de alterarlo | STATED |
| AC-07 | WHEN el analista toma un siniestro, THE sistema SHALL registrarlo como responsable y permitir asignar un perito del catálogo de la ciudad | DERIVED: RC-18, el siniestro asistido (J3) se asigna a un responsable y, si hace falta, a un perito |
| AC-08 | WHEN se aprueba una liquidación, THE sistema SHALL ordenar el pago a los beneficiarios según su porcentaje, una sola vez por la pasarela simulada, con recibo | DERIVED: RC-29, liquidar sin pagar deja el recorrido J3 incompleto |
| AC-09 | WHEN se emite una póliza de vida, THE sistema SHALL exigir beneficiarios con nombre, documento, parentesco y porcentajes que sumen 100 %, y mostrarlos en el detalle | DERIVED: RC-01, sin beneficiarios no hay a quién pagar la indemnización |
| AC-10 | WHEN el analista registra un endoso de beneficiarios o de suma asegurada, THE sistema SHALL guardar la versión anterior y emitir una carátula nueva | DERIVED: RC-21, el ciclo de vida (J6) incluye modificar una póliza vigente |
| AC-11 | WHEN el analista cancela una póliza a pedido del cliente, THE sistema SHALL exigir el motivo y calcular la devolución de prima proporcional; WHEN se liquida un siniestro de muerte, THE póliza SHALL pasar a terminada | DERIVED: RC-17, FE-WEB-04 pide cancelar y un siniestro de muerte liquidado termina el amparo |
| AC-12 | WHEN llega un siniestro de origen paramétrico que cumple su regla, THE sistema SHALL liquidarlo y ordenar el pago sin intervención humana, registrando la regla como responsable | DERIVED: RC-28, el recorrido paramétrico (J4) paga sin intervención humana |
| AC-13 | WHEN el analista pide el reporte de un mes, THE sistema SHALL generar en PDF y CSV las pólizas emitidas, primas, siniestros y cesión al reasegurador | DERIVED: RC-22, el ciclo de vida (J6) incluye reportar al regulador y al reasegurador |
| AC-14 | WHEN el analista abre la auditoría de una póliza, THE portal SHALL mostrar en orden y de solo lectura sus decisiones: emisión, endosos, dictámenes, pagos y cancelación | DERIVED: RC-19, HA-18 pide reconstruir cualquier decisión |
| AC-15 | WHEN un usuario del portal cierra sesión, THE backend SHALL revocar su refresh; y THE portal SHALL hacer una sola renovación cuando varias peticiones la necesitan a la vez | DERIVED: RC-20, un refresh robado seguía sirviendo 7 días después de cerrar sesión |
| AC-16 | THE servicios web de pagos y de pólizas SHALL tener pruebas unitarias | DERIVED: RC-05, los servicios que esta historia amplía no tenían pruebas |
| AC-17 | WHEN la ubicación de una foto viene de una app de ubicación simulada o la hora no es confiable, THE expediente SHALL advertirlo | DERIVED: el teléfono ya lo registra (HU-MOV-08, ADR 0024) y el analista debe verlo para dictaminar |
| AC-18 | IF el usuario es de un socio, THE bandeja y el expediente SHALL limitarse a los siniestros de pólizas de su socio, y responder 404 para los demás | DERIVED: mismo alcance de HU-WEB-08 y HU-WEB-09 (HA-14) |
| AC-19 | IF el usuario no tiene el rol analyst, THE endpoints del analista SHALL responder 403; sin sesión, 401 | DERIVED: solo operaciones dictamina |
| AC-20 | WHILE un siniestro está liquidado, rechazado o pagado, THE sistema SHALL impedir un nuevo dictamen o liquidación | DERIVED: evita doble pago (HA-17) |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | PERFORMANCE | Bandeja y expediente desde una vista de lectura (HA-04) | p95 por debajo de 150 ms con 10.000 siniestros, PostgreSQL local; las fotos se sirven con enlace prefirmado y no cuentan en el tiempo |
| QR-02 | ACCESSIBILITY | Bandeja, expediente, dictamen y formularios de la póliza | WCAG 2.2 AA: teclado, etiquetas, errores anunciados y fotos con texto alternativo (ubicación y hora) |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | Asegurado, beneficiarios (nombre, documento, parentesco) y ubicación de las fotos |
| Sensitive data (special categories, biometrics, health, financial, minors) | YES | Las fotos y la descripción pueden mostrar lesiones o la causa de una muerte (datos de salud); pagos a beneficiarios |
| Authentication, sessions, credentials or one-time codes | YES | Cierre de sesión con revocación (RC-20) |
| Authorization or access to other users' resources | YES | Rol analyst y siniestros por socio |
| Payments or money movement | YES | Pago de la indemnización y devolución de prima |
| Third-party services or integrations | NO | Pasarela y oráculo simulados internos; S3 ya existe |
| Infrastructure, network, IAM, CI/CD or secrets | YES | La política IAM del backend suma lectura de `siniestros/*` en el bucket |
| Multiple tenants, partners or organizations | YES | Alcance por socio |
| Storage or processing on a mobile device | NO | La historia es del portal y del backend |
| Web UI or data exposed in the browser | YES | Fotos, ubicación y datos del asegurado en el portal |
| New or updated dependencies | NO | Los reportes usan fpdf2 y las fotos boto3, que ya están |
| Critical paths (sdd/security/critical-paths.md) | YES | backend/app/modules/auth/**, backend/app/modules/payments/** y terraform/**/*.tf |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | El alcance por socio sale del usuario en la base; un siniestro o una póliza de otro socio responde 404 y los reportes son solo para Solventa | DATA-01, THR-01 | CTL-01 | HIGH |
| SR-02 | Solo el rol analyst usa los endpoints de operaciones; sin sesión 401, con otro rol o socio revocado 403 | THR-05 | CTL-02 | HIGH |
| SR-03 | La liquidación no supera el amparo, exige póliza vigente y al día, y un siniestro decidido no admite otra decisión; cada pago es único por siniestro y beneficiario | DATA-03, THR-02 | CTL-03 | HIGH |
| SR-04 | Dictámenes, historial del siniestro y operaciones de la póliza son de solo inserción: la base rechaza UPDATE y DELETE | DATA-04, THR-03 | CTL-04 | HIGH |
| SR-05 | Cada foto se verifica contra la huella que registró el teléfono, y el expediente advierte ubicación simulada y hora sin confirmar | DATA-02, THR-04 | CTL-05 | HIGH |
| SR-06 | El documento del asegurado y de los beneficiarios sale enmascarado, las fotos solo con enlaces prefirmados de 15 minutos y los reportes sin datos personales | DATA-01, DATA-02, THR-08 | CTL-06 | HIGH |
| SR-07 | La liquidación paramétrica solo aplica si la regla confirma la condición y la póliza está vigente | THR-06 | CTL-07 | HIGH |
| SR-08 | Cerrar sesión revoca el refresh y un refresh usado no vuelve a renovar | THR-07 | CTL-08 | MEDIUM |
| SR-09 | El backend solo lee `siniestros/*` en S3; no escribe ni borra fotos | THR-04 | CTL-09 | MEDIUM |

## 2. Design

### Components

Backend (ADR 0004 y 0005):

- **`modules/claims`:**
  - `models.py` amplía `claims` (origen, amparo, descripción, evento paramétrico, responsable, perito, valor liquidado) y crea `claim_photos`, `claim_events` (solo inserción) y `experts`;
  - `service.py` tiene la bandeja, el expediente con la verificación de huella, la asignación, el dictamen, la liquidación con el pago, el reintento y `register_claim` para HU-MOV-09 y 10;
  - `parametric.py` guarda la regla `SISMO-6.5`: magnitud 6,5 o más paga el 5 % de la cobertura de incapacidad (HA-20).
- **`modules/payments/payouts.py`:** pagos de Solventa (indemnización y devolución) por la pasarela simulada, uno por referencia, tipo y beneficiario, con recibo `REC-`.
- **`modules/policy`:**
  - `Beneficiary` con porcentajes que suman 100, obligatorio al emitir desde el portal;
  - `version` y `policy_events` (solo inserción);
  - `operations.py` con endoso, cancelación con devolución proporcional, terminación por muerte y auditoría;
  - la carátula de un endoso lleva su versión, con una clave por versión, y sigue sin beneficiarios, como decidió HU-WEB-16 (AC-07).
- **`modules/reports`:** reporte mensual en CSV y PDF con cifras agregadas; la cesión al reasegurador es una cuota parte del 40 %.
- **`core`:**
  - `immutable.py` crea el disparador de solo inserción;
  - `storage.py` lee la foto y firma un enlace en línea;
  - `security.py` pone un `jti` al refresh.
- **`modules/auth/service.py`:** el refresh usado o cerrado queda en `revoked_tokens`.
- **`bff/experience/operations_router.py`:** endpoints del analista con la dependencia de alcance de HU-WEB-08.

Portal:

- **`features/siniestros/`:** bandeja y expediente, con fotos, huella, advertencias, historial, toma del caso, dictamen, liquidación, pagos y dictamen de solo lectura.
- **`features/gestion/`:** beneficiarios en el detalle; `policy-operations` para endosos, cancelación y auditoría; `monthly-report` en el encabezado.
- **`features/shared/beneficiaries-editor`:** se usa en el cotizador y en el endoso.
- **`core/`:** `claim.service`, modelos, cierre de sesión que avisa al backend y una sola renovación en vuelo.
- **Navegación:** Pólizas y Siniestros para el analista.

### Data flows

- **Expediente:** analista → portal → `GET /claims/{radicado}` → backend (rol, alcance) → PostgreSQL y S3 (`siniestros/*`). La huella se compara en el backend y la foto viaja por un enlace prefirmado.
- **Dictamen:** `POST /claims/{radicado}/decision` → registro inmutable y pagos por la pasarela simulada; si el amparo es de muerte, la póliza termina.
- **Siniestro paramétrico:** HU-MOV-10 → `register_claim(origin=PARAMETRIC)` → regla → liquidación y pago.

### Data contract

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| GET /experience/claims?status= | — | `items[]` (radicado, póliza, fecha, estado, origen, responsable), `has_more` |
| GET /experience/claims/{SIN-AAAA-NNNNN} | — | Expediente con póliza, asegurado enmascarado, amparos, fotos (`url`, ubicación, hora, `integrity`), historial y pagos |
| POST /experience/claims/{n}/assign | `expert_id` opcional | 204; 409 si ya tiene dictamen |
| POST /experience/claims/{n}/decision | `decision`, `justification` (20 a 2000), `coverage_id` y `amount` al liquidar | 204; 422 si supera el amparo; 409 si ya tiene dictamen |
| POST /experience/claims/{n}/payouts/retry | — | 204 |
| GET /experience/experts?city= | — | Peritos |
| POST /experience/policies/{n}/endorsements | `kind`, `justification`, `beneficiaries` o `insured_amount` | Versión nueva; 409 si está cerrada |
| POST /experience/policies/{n}/cancellation | `reason` | Estado y devolución |
| GET /experience/policies/{n}/audit | — | Entradas en orden |
| GET /experience/reports/{AAAA-MM}?format=csv o pdf | — | Archivo; 403 para usuarios de un socio |
| POST /experience/auth/logout | `refresh_token` | 204 |
| POST /experience/policies (cambia) | `beneficiaries` obligatorio, porcentajes que suman 100 | — |

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| Fuera del alcance responde 404 y los reportes son solo de Solventa | CTL-01 | Responder 403: confirmaría que el siniestro existe |
| Dependencia de alcance común a búsqueda, detalle y operaciones | CTL-02 | Validar en cada endpoint: se desalinean |
| Liquidación con bloqueo de fila, estados cerrados y pago único por beneficiario | CTL-03 | Confiar en el portal: un doble clic o un reintento pagaría dos veces |
| Disparador en PostgreSQL que impide UPDATE y DELETE | CTL-04 | Solo la API: un acceso directo a la base cambiaría la decisión |
| Huella SHA-256 calculada en el backend sobre lo guardado en S3 | CTL-05 | Confiar en una marca guardada: no detectaría una foto cambiada después |
| Documentos enmascarados, enlaces de 15 minutos y reportes agregados | CTL-06 | Enviar los datos completos y ocultarlos en el portal |
| La regla paramétrica vuelve a verificar la medida y la vigencia | CTL-07 | Liquidar todo evento que llegue |
| Refresh con `jti`, rotación y revocación | CTL-08 | Cerrar sesión solo en el navegador: un refresh robado seguiría sirviendo 7 días |
| IAM de solo lectura sobre `siniestros/*` | CTL-09 | Permiso sobre todo el bucket |

### Architecture decisions

N/A: reutiliza la arquitectura hexagonal (ADR 0004), la estructura por funcionalidad del portal (ADR 0009), el almacenamiento de documentos de HU-WEB-16 (ADR 0023) y el modelo de evidencias del teléfono (ADR 0024). El endoso no recalcula la prima: solo baja la suma asegurada, para no volver a suscribir.

### Configuration and deployment

- **Tablas nuevas:** `claim_photos`, `claim_events`, `experts`, `policy_events` y `payouts`. `create_all` las crea con sus disparadores.
- **Columnas nuevas:** `claims.*` y `policies.version` se agregan con `ADD COLUMN IF NOT EXISTS` al arrancar.
- **Peritos:** el catálogo se siembra al arrancar.
- **IAM:** el backend suma `s3:GetObject` sobre `siniestros/*` en `terraform/modules/ecs`.
- **Variables:** ninguna nueva.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Modelo del siniestro, expediente con huella y bandeja (#248) | AC-01, AC-02, AC-03, AC-17, AC-18, SR-05, SR-06 | CTL-01, CTL-05, CTL-06 | DONE | backend/app/modules/claims/service.py; backend/tests/integration/test_claims_api.py |
| T-02 | Asignación, dictamen, liquidación, pago y regla paramétrica (#249) | AC-04, AC-05, AC-06, AC-07, AC-08, AC-12, AC-20, SR-03, SR-04, SR-07 | CTL-03, CTL-04, CTL-07 | DONE | backend/app/modules/claims/service.py; backend/app/modules/payments/payouts.py; backend/app/modules/claims/parametric.py |
| T-03 | Beneficiarios, endosos, cancelación, terminación y auditoría | AC-09, AC-10, AC-11, AC-14 | CTL-04 | DONE | backend/app/modules/policy/operations.py; backend/tests/integration/test_policy_operations_api.py |
| T-04 | Reporte mensual al regulador y al reasegurador | AC-13 | CTL-06 | DONE | backend/app/modules/reports/service.py |
| T-05 | Cierre de sesión con revocación y renovación única | AC-15, SR-08 | CTL-08 | DONE | backend/app/modules/auth/service.py; web/src/app/core/auth/auth.service.ts |
| T-06 | Pantallas de bandeja, expediente, operaciones, reporte y beneficiarios (#250) | AC-01, AC-02, AC-04, AC-05, AC-06, AC-09, AC-10, AC-11, AC-13, AC-14, QR-02 | CTL-06 | DONE | web/src/app/features/siniestros/claim-file.component.ts; web/src/app/features/gestion/policy-operations.component.ts |
| T-07 | Permisos y endpoints del analista | AC-18, AC-19, SR-01, SR-02, SR-09 | CTL-01, CTL-02, CTL-09 | DONE | backend/app/bff/experience/operations_router.py; terraform/modules/ecs/main.tf |
| T-08 | Pruebas unitarias, de integración, del registro inmutable, de rendimiento y del portal (#251) | AC-16, QR-01 | CTL-03, CTL-04 | DONE | backend/tests/integration/test_claims_api.py; backend/tests/unit/test_claims_rules.py; web/src/app/core/policies/policy.service.spec.ts |

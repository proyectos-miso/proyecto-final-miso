# HU-WEB-07 · Motivo de rechazo

| Field | Value |
|---|---|
| ID | HU-WEB-07 |
| Title | Motivo de rechazo |
| User story | https://github.com/proyectos-miso/proyecto-final-miso/issues/61 |
| Owner | Juan Camilo Peña |
| Status | IN_PROGRESS |
| Created | 2026-10-25 |
| Security profile | MEDIUM |

## 1. Requirements

### User story

**HU-WEB-07 · Conocer el motivo de un rechazo de suscripción** (Sprint 2, Must have, 2 puntos, FE-WEB-02, ESC-SEG-06; depende de HU-WEB-05).

**Como** asesor comercial **quiero** ver en lenguaje de negocio por qué se rechazó una suscripción **para** explicárselo al cliente y ofrecerle una alternativa en la misma sesión.

Criterios de aceptación de la historia:

1. **Rechazo con motivo claro.** *Dado* un cliente cuya solicitud supera una regla de suscripción, *cuando* el asesor solicita la cotización, *entonces* el portal muestra que la solicitud no puede aprobarse automáticamente y el motivo en lenguaje de negocio, sin códigos técnicos.
2. **Alternativa sugerida.** *Dado* un rechazo por monto, *cuando* se muestra el motivo, *entonces* el portal sugiere la suma asegurada máxima aprobable y permite recotizar con ella en un clic.
3. **Sin rechazo, sin aviso.** *Dada* una solicitud que cumple las reglas, *cuando* se cotiza, *entonces* se muestran los planes normalmente y no aparece ningún aviso de rechazo.
4. **Registro auditable.** *Dado* un rechazo, *cuando* se produce, *entonces* queda registrado el motivo, la regla aplicada y la fecha, sin posibilidad de modificarlo.

Requisitos no funcionales: HA-05 (degradar de forma explícita ante un tercero lento), HA-17 (hacer inmutables los registros cerrados), HA-20 (confinar el cambio de reglas de rating). Mockup: https://solventa-web.vercel.app/?pestana=cotizador&paso=3, aviso "Suscripción automática requiere revisión". Tareas: #165 reglas con motivo y alternativa, #166 registro inmutable, #167 aviso y recotización, #168 pruebas.

### Problem and outcome

Hoy el motor cotiza cualquier suma asegurada y no hay reglas de suscripción: una solicitud fuera del cupo de aprobación directa se emite igual o se rechaza fuera del sistema sin explicación. Resultado: la cotización aplica reglas de cupo; si una se supera, el portal lo explica en lenguaje de negocio, ofrece recotizar con la suma máxima aprobable y el rechazo queda registrado sin posibilidad de cambiarlo.

### Goals and non-goals

Objetivos: reglas de cupo de aprobación directa por perfil crediticio y por edad; motivo y alternativa en la respuesta de cotización; registro inmutable de los rechazos; aviso y recotización en un clic en el cotizador.

Fuera de alcance: la revisión manual por un suscriptor (el botón "Solicitar revisión manual" del mockup); las reglas en el canal B2B de socios (HU-WEB-10 y HU-WEB-14, de Oscar), que reutilizarán el mismo módulo de reglas; consultar o exportar los rechazos registrados.

### Affected paths

| Path | Change |
|---|---|
| backend/app/modules/underwriting/__init__.py | Módulo nuevo de suscripción |
| backend/app/modules/underwriting/rules.py | Cupos de aprobación directa y su evaluación |
| backend/app/modules/underwriting/models.py | Decisión de suscripción y tabla inmutable de rechazos |
| backend/app/modules/underwriting/service.py | Registro de rechazos |
| backend/app/modules/rating/models.py | La respuesta de cotización lleva la decisión de suscripción |
| backend/app/modules/rating/service.py | Evalúa las reglas antes de guardar la cotización |
| backend/tests/unit/test_underwriting_rules.py | Pruebas de las reglas |
| backend/tests/integration/test_underwriting_api.py | Pruebas de la cotización con rechazo y del registro |
| web/src/app/core/models/quote.model.ts | Estado y decisión de suscripción en el contrato |
| web/src/app/features/cotizador/cotizador.component.ts | Aviso de rechazo y recotización en un clic |
| web/src/app/features/cotizador/cotizador.component.spec.ts | Pruebas del aviso |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Asesor | Cotizar dentro del cupo | 1. Completa los datos y el consentimiento. 2. Pide la cotización. 3. Ve los planes | El buró no responde a tiempo: se cotiza con el puntaje neutro y el aviso de perfil parcial (HU-WEB-04) |
| UC-02 | Asesor | Entender un rechazo y ofrecer una alternativa | 1. Pide la cotización con una suma por encima del cupo. 2. Ve que la solicitud requiere revisión, el motivo y la suma máxima aprobable. 3. Recotiza con esa suma en un clic. 4. Ve los planes | Supera el cupo por edad y por perfil: el motivo es el de la regla que fija el cupo menor |
| UC-03 | Auditor | Revisar por qué se rechazó una solicitud | 1. Consulta el registro de rechazos en la base. 2. Ve el motivo, la regla, los montos y la fecha | Intento de cambiar o borrar un registro: la base lo impide |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Emitir una póliza con la cotización rechazada, llamando directo al endpoint de emisión | Asesor malicioso | THR-01 | El rechazo no crea una cotización emitible; la emisión responde 404 | TC-04 |
| AB-02 | Borrar o cambiar un rechazo para ocultarlo | Persona con acceso a la base | THR-02 | La base rechaza UPDATE y DELETE sobre la tabla de rechazos | TC-05 |
| AB-03 | Partir el monto o cambiar la fecha de nacimiento para pasar el cupo | Asesor malicioso | THR-03 | Cada solicitud se evalúa y cada rechazo queda registrado con su documento, lo que deja ver los intentos | TC-03 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN la suma asegurada supera el cupo de aprobación directa, THE cotización SHALL responder que requiere revisión, con el motivo en lenguaje de negocio y sin planes | STATED |
| AC-02 | WHEN el rechazo es por monto, THE portal SHALL mostrar la suma asegurada máxima aprobable y recotizar con ella en un clic | STATED |
| AC-03 | WHEN la solicitud cumple las reglas, THE portal SHALL mostrar los planes sin aviso de rechazo | STATED |
| AC-04 | WHEN se produce un rechazo, THE sistema SHALL registrar el motivo, la regla, la suma pedida, la suma máxima aprobable y la fecha, y la base SHALL impedir modificarlo o borrarlo | STATED |
| AC-05 | THE cupo de aprobación directa SHALL ser el menor entre el cupo por puntaje del buró (≥800: 1.200 millones; 650–799: 700 millones; 500–649: 350 millones; <500: 150 millones) y el cupo por edad (66 a 75 años: 300 millones) | DERIVED: la historia y el mockup hablan del "cupo de aprobación directa para este perfil" sin fijarlo; valores propuestos pendientes de validar (DEC-01) |
| AC-06 | WHEN el buró no responde a tiempo, THE reglas SHALL usar el puntaje neutro, igual que la tarifa | DERIVED: HA-05 pide degradar de forma explícita; usar otro puntaje cambiaría el cupo según la latencia |
| AC-07 | WHEN la suma supera el cupo por las dos reglas, THE motivo SHALL ser el de la regla con el cupo menor | DERIVED: un solo motivo claro para el cliente |
| AC-08 | THE cotización rechazada SHALL no poder emitirse | DERIVED: un rechazo que se pueda emitir anula la regla |
| AC-09 | THE suma asegurada igual al cupo SHALL aprobarse | DERIVED: fija el borde ("supera" es estrictamente mayor) |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | ACCESSIBILITY | El aviso de rechazo se anuncia y su botón se usa con teclado | WCAG 2.2 AA: el aviso tiene rol de alerta, el botón es accesible por teclado y su texto incluye la suma |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | El registro de rechazos guarda el documento del solicitante |
| Sensitive data (special categories, biometrics, health, financial, minors) | NO | No se guarda el puntaje del buró; solo la suma pedida, que ya forma parte de la cotización |
| Authentication, sessions, credentials or one-time codes | NO | El endpoint ya exige el rol advisor; no cambia |
| Authorization or access to other users' resources | NO | Sin recursos nuevos por usuario |
| Payments or money movement | NO | No aplica |
| Third-party services or integrations | NO | El buró ya se usa en la tarifa; no cambia |
| Infrastructure, network, IAM, CI/CD or secrets | NO | No aplica |
| Multiple tenants, partners or organizations | NO | Canal del asesor; el canal de socios queda fuera |
| Storage or processing on a mobile device | NO | No aplica |
| Web UI or data exposed in the browser | YES | El aviso muestra el motivo y las sumas en el portal |
| New or updated dependencies | NO | Sin dependencias nuevas |
| Critical paths (sdd/security/critical-paths.md) | NO | No cambia main.py, config, seguridad ni interceptores; la tabla se registra al importar el módulo |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | Una cotización rechazada no queda guardada como cotización emitible | THR-01 | CTL-01 | HIGH |
| SR-02 | La base impide modificar o borrar los rechazos registrados | DATA-01, THR-02 | CTL-02 | HIGH |
| SR-03 | El registro guarda solo lo necesario para auditar: documento, regla, motivo, sumas y fecha; no el puntaje del buró | DATA-01 | CTL-03 | MEDIUM |
| SR-04 | El motivo que ve el asesor no expone códigos internos ni el puntaje | DATA-01 | CTL-04 | MEDIUM |

## 2. Design

### Components

Backend (módulo nuevo, ADR 0004):

- `modules/underwriting/rules.py`: tablas de cupos y `evaluate(insured_amount, age, score)`, una función pura que devuelve la decisión o `None`. Reglas `UW-01` (perfil crediticio) y `UW-02` (edad). Es el único lugar donde cambian las reglas (HA-20).
- `modules/underwriting/models.py`:
  - `UnderwritingDecision`: `decision`, `rule_id`, `reason`, `max_approvable_amount`;
  - `UnderwritingRejectionDB`: tabla `underwriting_rejections`, con un disparador de PostgreSQL que impide UPDATE y DELETE.
- `modules/underwriting/service.py`: `record_rejection`, que solo inserta.
- `modules/rating`:
  - `calculate_quote` evalúa las reglas con el puntaje y la edad que ya calcula;
  - si hay rechazo, `create_quote` guarda el rechazo y no la cotización;
  - `QuoteResponse` suma `underwriting` y el estado `review_required`.

Portal:

- `quote.model.ts`: estado `review_required` y la decisión.
- `CotizadorComponent`: aviso con rol de alerta, el motivo, la suma máxima aprobable en COP y el botón "Recotizar con …", que cambia la suma y vuelve a cotizar.

### Data flows

Asesor → portal → `POST /api/v1/experience/quotes` (rol advisor) → motor (buró y reglas) → PostgreSQL (cotización, o rechazo inmutable) → respuesta con planes, o con la decisión.

### Data contract

`POST /api/v1/experience/quotes`: la solicitud no cambia. La respuesta suma:

| Campo | Tipo | Notas |
|---|---|---|
| status | active, degraded o review_required | `review_required` cuando una regla se supera |
| underwriting | objeto o null | Solo con `review_required` |
| underwriting.decision | `REVIEW_REQUIRED` | |
| underwriting.rule_id | `UW-01` o `UW-02` | Para auditoría; el portal no lo muestra |
| underwriting.reason | string | Motivo en lenguaje de negocio |
| underwriting.max_approvable_amount | number | Suma asegurada máxima aprobable en COP |

Con `review_required`, `tiers` viene vacío y `quote_id` es el identificador del rechazo (`UWR-…`), que no sirve para emitir. Código 200: el rechazo es un resultado de negocio, no un error.

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| El rechazo no crea fila en `quotes` | CTL-01 | Guardar la cotización con estado rechazado: la emisión tendría que filtrarla y un olvido la haría emitible |
| Disparador de PostgreSQL que impide UPDATE y DELETE | CTL-02 | Solo no exponer métodos de cambio: cualquier acceso a la base podría alterar el registro |
| Registro sin puntaje del buró | CTL-03 | Guardar el puntaje: dato financiero que no hace falta para auditar la regla |
| El portal muestra el motivo y la suma, no `rule_id` | CTL-04 | Mostrar códigos: la historia pide lenguaje de negocio |

### Architecture decisions

N/A: módulo nuevo dentro de la arquitectura hexagonal (ADR 0004); las reglas quedan aisladas en el archivo de reglas del módulo de suscripción, como pide HA-20.

### Configuration and deployment

- Sin variables nuevas: los cupos están en `rules.py` y se cambian con un cambio revisado.
- La tabla `underwriting_rejections` y su disparador se crean al arrancar, como las demás tablas (`create_all`).

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Reglas de cupo con motivo y alternativa en la respuesta de cotización (#165) | AC-01, AC-05, AC-06, AC-07, AC-08, AC-09, SR-01, SR-04 | CTL-01, CTL-04 | TODO | — |
| T-02 | Registro inmutable de los rechazos (#166) | AC-04, SR-02, SR-03 | CTL-02, CTL-03 | TODO | — |
| T-03 | Aviso de rechazo con motivo y recotización en un clic (#167) | AC-01, AC-02, AC-03, QR-01 | CTL-04 | TODO | — |
| T-04 | Pruebas de las reglas, del registro y del aviso (#168) | AC-01, AC-02, AC-03, AC-04, AC-05, AC-06, AC-07, AC-08, AC-09 | CTL-01, CTL-02 | TODO | — |

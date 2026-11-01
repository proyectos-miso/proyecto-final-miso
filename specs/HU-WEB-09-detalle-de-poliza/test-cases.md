# Test cases · HU-WEB-09

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | UC-01 | Dado el analista, cuando abre una póliza con pagos y un siniestro, entonces ve titular, plan, estado, vigencia, socio, suma asegurada, primas, coberturas con su suma, pagos y siniestros | TC-01 |
| SC-02 | AC-02 | Dado el detalle, cuando se muestra, entonces documento, correo y teléfono salen enmascarados | TC-02 |
| SC-03 | UC-02 | Dado un número de póliza que no existe, cuando se consulta, entonces el portal dice que no la encontró, sin errores técnicos | TC-03, TC-08 |
| SC-04 | AC-04 | Dada una póliza sin siniestros, cuando se ve el detalle, entonces dice "No hay siniestros registrados" | TC-04, TC-08 |
| SC-05 | UC-03 | Dado un usuario de Banco Aliado, cuando pide el detalle de una póliza de otro socio o del portal, entonces recibe la misma respuesta que para una inexistente | TC-05 |
| SC-06 | AC-06 | Dado un usuario de un socio revocado, cuando pide un detalle, entonces recibe 403 | TC-05 |
| SC-07 | AC-06 | Dado un asesor o un pedido sin sesión, cuando llama al detalle, entonces recibe 403 o 401 | TC-06 |
| SC-08 | AC-08 | Dada una póliza con un cobro rechazado y uno aprobado, cuando se ve el detalle, entonces salen del más reciente al más antiguo, el rechazado con su motivo y ninguno con token ni cuenta | TC-07 |
| SC-09 | AC-10 | Dada una póliza B2B sin titular, cuando se ve el detalle, entonces sale "Sin nombre registrado" y no hay contacto | TC-04 |
| SC-10 | AC-03 | Dado un número con otro formato o con texto arbitrario, cuando se consulta, entonces responde 422 y el portal dice que no encontró la póliza | TC-03 |
| SC-11 | AC-11 | Dado el analista con teclado, cuando abre y cierra el detalle, entonces el foco va al título del panel y vuelve al número de la póliza | TC-08, TC-10 |
| SC-12 | AC-12 | Dado un enlace `/gestion?poliza=…`, cuando el analista lo abre, entonces ve el detalle de esa póliza | TC-08 |
| SC-13 | AC-01 | Dado un titular con HTML en el nombre, cuando se ve el detalle, entonces se muestra como texto | TC-11 |
| SC-14 | AC-01 | Dadas 10.000 pólizas, cuando se consultan 100 detalles, entonces el p95 queda por debajo de 150 ms | TC-09 |
| SC-15 | AC-09 | Dada una póliza con siniestros, cuando se ve el detalle, entonces cada siniestro trae radicado, estado y fecha, sin descripción ni ubicación | TC-01 |
| SC-16 | UC-01 | Dado el analista, cuando quiere reportar o liquidar un siniestro desde el detalle | OUT OF SCOPE: reportar es HU-MOV-10 y liquidar es HU-WEB-15 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-15 | — |
| Alternate flows | YES | SC-04, SC-09, SC-12 | — |
| Invalid input and errors | YES | SC-03, SC-10, SC-13 | — |
| Limits and boundaries | YES | SC-14 | — |
| Related flows before and after | YES | SC-16 | — |
| Several users, sessions, devices or roles | YES | SC-05, SC-06, SC-07 | — |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-08, SC-11 | — |

## Test cases

### TC-01 · Detalle completo

| Field | Value |
|---|---|
| Covers | AC-01, AC-09 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas con una póliza del portal con titular, dos cobros y dos siniestros; token de analista sin socio |
| Procedure | 1. Pedir el detalle. 2. Revisar cada campo |
| Expected result | Titular, plan, estado, vigencia, suma asegurada, primas, coberturas incluidas con su suma, pagos y siniestros con radicado, estado y fecha, sin descripción |
| Tool or method | pytest backend/tests/integration/test_policy_detail_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_detail_api.py::test_tc_01_el_detalle_trae_titular_plan_coberturas_primas_pagos_y_siniestros |
| Status | PASS |

### TC-02 · Datos del titular enmascarados

| Field | Value |
|---|---|
| Covers | AC-02, AC-07, SR-03, SR-06 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | Póliza con titular completo |
| Procedure | 1. Pedir el detalle. 2. Buscar en la respuesta el documento, el correo y el teléfono completos. 3. Probar el enmascarado con correos y teléfonos de distintos largos |
| Expected result | Documento `******4050`, correo con la primera letra y el dominio, teléfono con los últimos 4 dígitos; ningún dato completo en la respuesta; sin beneficiarios; los siniestros sin descripción ni ubicación |
| Tool or method | pytest backend/tests/integration/test_policy_detail_api.py; pytest backend/tests/unit/test_policy_detail.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_detail_api.py::test_tc_02_documento_correo_y_telefono_salen_enmascarados; backend/tests/unit/test_policy_detail.py::test_tc_02_el_correo_deja_ver_la_primera_letra_y_el_dominio; backend/tests/unit/test_policy_detail.py::test_tc_02_el_telefono_deja_ver_los_ultimos_cuatro_digitos |
| Status | PASS |

### TC-03 · Póliza inexistente y número inválido

| Field | Value |
|---|---|
| Covers | AC-03, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | INTEGRATION |
| Preconditions | Token de analista |
| Procedure | 1. Pedir `POL-2026-99999`, que no existe. 2. Pedir `abc`, `POL-2026-1' or '1'='1` y un número de 40 caracteres |
| Expected result | 1 responde 404 con "No encontramos la póliza."; 2 responde 422 |
| Tool or method | pytest backend/tests/integration/test_policy_detail_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_detail_api.py::test_tc_03_una_poliza_inexistente_responde_404_sin_detalles_tecnicos; backend/tests/integration/test_policy_detail_api.py::test_tc_03_un_numero_con_otro_formato_responde_422 |
| Status | PASS |

### TC-04 · Sin siniestros y sin titular

| Field | Value |
|---|---|
| Covers | AC-04, AC-10 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Póliza B2B sin titular, sin cobros ni siniestros |
| Procedure | 1. Pedir el detalle |
| Expected result | claims y payments vacíos; holder.name, email y phone null; documento enmascarado |
| Tool or method | pytest backend/tests/integration/test_policy_detail_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_detail_api.py::test_tc_04_poliza_b2b_sin_titular_sin_pagos_ni_siniestros |
| Status | PASS |

### TC-05 · Aislamiento por socio

| Field | Value |
|---|---|
| Covers | AC-05, AC-06, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Pólizas de dos socios y del portal; usuario de Banco Aliado; usuario de un socio revocado |
| Procedure | 1. Como usuario de Banco Aliado, pedir una póliza de Banco Aliado, una del otro socio y una del portal. 2. Comparar con la respuesta de una póliza inexistente. 3. Como usuario del socio revocado, pedir un detalle |
| Expected result | 1 trae la suya y 404 en las otras, con el mismo cuerpo que la inexistente; 3 responde 403 |
| Tool or method | pytest backend/tests/integration/test_policy_detail_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_detail_api.py::test_tc_05_el_usuario_de_un_socio_solo_ve_el_detalle_de_su_socio; backend/tests/integration/test_policy_detail_api.py::test_tc_05_el_usuario_de_un_socio_revocado_recibe_403 |
| Status | PASS |

### TC-06 · Solo el rol analyst

| Field | Value |
|---|---|
| Covers | AC-06, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Tokens de asesor y de analista |
| Procedure | 1. Pedir el detalle con token de asesor. 2. Sin token. 3. Con un token de analista de un usuario que ya no existe |
| Expected result | 403, 401 y 401 |
| Tool or method | pytest backend/tests/integration/test_policy_detail_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_detail_api.py::test_tc_06_solo_el_rol_analyst_ve_el_detalle |
| Status | PASS |

### TC-07 · Historial de pagos

| Field | Value |
|---|---|
| Covers | AC-08, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | Póliza con un cobro rechazado y luego uno aprobado |
| Procedure | 1. Pedir el detalle. 2. Revisar el orden y los campos de cada pago |
| Expected result | El aprobado primero con su recibo; el rechazado con su motivo; ningún campo de token, cuenta o clave de idempotencia |
| Tool or method | pytest backend/tests/integration/test_policy_detail_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_detail_api.py::test_tc_07_pagos_del_mas_reciente_al_mas_antiguo_sin_datos_del_medio |
| Status | PASS |

### TC-08 · Panel de detalle en el portal

| Field | Value |
|---|---|
| Covers | AC-01, AC-03, AC-04, AC-11, AC-12 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Componentes del portal con el servicio simulado |
| Procedure | 1. Pulsar el número de una póliza. 2. Revisar las secciones. 3. Abrir una póliza sin siniestros. 4. Abrir un número que responde 404. 5. Entrar con `?poliza=`. 6. Cerrar el panel |
| Expected result | El panel muestra todo; "No hay siniestros registrados"; "No encontramos la póliza …" sin texto técnico; el enlace abre el detalle; al abrir el foco va al título y al cerrar vuelve al número |
| Tool or method | npm test (policy-detail.component.spec.ts, gestion.component.spec.ts) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-08 muestra titular enmascarado, plan, coberturas, primas, pagos y siniestros"; web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-08 sin siniestros ni pagos lo dice, y sin titular no muestra contacto"; web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-08 una póliza que no existe o con otro formato dice que no se encontró, sin errores técnicos"; web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-08 al abrir lleva el foco al título; Cerrar y Escape avisan que se cierra"; web/src/app/features/gestion/gestion.component.spec.ts::"TC-08 el número de cada póliza es un botón que abre el detalle con ?poliza="; web/src/app/features/gestion/gestion.component.spec.ts::"TC-08 con ?poliza= en la dirección abre el panel y al cerrarlo el foco vuelve al número"; prueba de punta a punta local con Chrome el 2026-10-27 (analista de operaciones, analista de Banco Aliado y asesor contra el backend con PostgreSQL): 34 de 34 verificaciones |
| Status | PASS |

### TC-09 · Tiempo de respuesta del detalle

| Field | Value |
|---|---|
| Covers | QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | PERFORMANCE |
| Preconditions | PostgreSQL local con 10.000 pólizas, cobros y siniestros |
| Procedure | 1. Consultar 100 detalles al azar contra el endpoint. 2. Calcular el p95 |
| Expected result | p95 por debajo de 150 ms |
| Tool or method | pytest backend/tests/integration/test_policy_search_performance.py |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_policy_search_performance.py::test_tc_09_el_p95_del_detalle_queda_por_debajo_de_150_ms; 10.000 pólizas con cobros y siniestros, 100 detalles: p50 21,8 ms y p95 27,5 ms el 2026-10-27 |
| Status | PASS |

### TC-10 · Accesibilidad del panel

| Field | Value |
|---|---|
| Covers | QR-02, AC-11 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | Portal en el navegador con lector de pantalla |
| Procedure | 1. Llegar con Tab al número de una póliza y abrirla con Enter. 2. Escuchar el título y recorrer las secciones. 3. Cerrar con el botón y con Escape |
| Expected result | El foco va al título del panel; las secciones tienen encabezados; "No hay siniestros registrados" se lee; al cerrar el foco vuelve al número |
| Tool or method | Narrador o NVDA, teclado |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | PENDING: auditoría automática con Chrome el 2026-10-27. axe-core 4.12.1 (WCAG 2.2 AA) sin violaciones con el detalle abierto, sin siniestros, con una póliza no encontrada y a 320 px; se corrigieron el contraste de la fila abierta (1.4.3) y el alto del botón del número (2.5.8). Árbol de accesibilidad: botón «Ver detalle de la póliza POL-…», región «Póliza POL-…», título h2 y secciones h3, fila abierta con aria-current, «No encontramos la póliza» como alerta. Teclado: Enter abre y lleva el foco al título, Tab llega a «Cerrar detalle», Escape cierra y devuelve el foco al número. Falta la pasada con lector de pantalla |
| Status | PENDING |

### TC-11 · Datos de la póliza como texto

| Field | Value |
|---|---|
| Covers | SR-05 |
| Kind | SECURITY |
| Risk covered | THR-05 |
| Level | UNIT |
| Preconditions | Panel de detalle con el servicio simulado |
| Procedure | 1. Mostrar un titular llamado `<img src=x onerror=alert(1)>` |
| Expected result | El nombre sale como texto y el panel no crea ningún elemento img |
| Tool or method | npm test (policy-detail.component.spec.ts) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-11 muestra como texto el HTML guardado en el nombre del titular" |
| Status | PASS |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-06 | — |
| User without permissions | YES | TC-06 | — |
| Access to another user's resources | YES | TC-05 | — |
| Cross-tenant access | YES | TC-05 | — |
| Malicious input | YES | TC-03, TC-11 | — |
| Expired tokens | NO | — | La validación del token no cambia (HU-WEB-13) |
| Revoked sessions | YES | TC-05, TC-06 | — |
| Tampered data | NO | — | Sin datos firmados nuevos |
| Wrong keys | NO | — | Sin llaves nuevas |
| External service failures | NO | — | Sin servicios externos |
| Race conditions | NO | — | Consulta de solo lectura |
| Information exposure | YES | TC-02, TC-05, TC-07 | — |
| Secrets in code | NO | — | Sin secretos nuevos |
| Insecure configuration | NO | — | Sin configuración nueva |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas |

# Test cases · HU-WEB-08

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | UC-01 | Dado el analista, cuando busca por un documento, entonces ve las pólizas de ese documento con número, titular, documento enmascarado, plan, estado, vigencia y socio | TC-01 |
| SC-02 | UC-01 | Dado el analista, cuando busca por número de póliza o un prefijo, entonces ve esa póliza | TC-01 |
| SC-03 | UC-01 | Dado el analista, cuando busca por el nombre de un socio o de un titular sin importar mayúsculas, entonces ve sus pólizas | TC-01 |
| SC-04 | AC-06 | Dado el analista, cuando filtra por estado, entonces solo ve pólizas en ese estado | TC-01 |
| SC-05 | AC-03 | Dada una búsqueda sin coincidencias, cuando se ejecuta, entonces ve el mensaje y el botón para limpiar el filtro, que vuelve a la lista inicial | TC-06 |
| SC-06 | UC-02 | Dado un usuario de Banco Aliado, cuando busca el documento o el número de una póliza de otro socio o del portal, entonces no la ve | TC-03 |
| SC-07 | AC-10 | Dado un usuario de un socio revocado, cuando busca, entonces recibe 403 | TC-03 |
| SC-08 | AC-09 | Dado un asesor o un pedido sin sesión, cuando llama a la búsqueda, entonces recibe 403 o 401 | TC-04 |
| SC-09 | AC-08 | Dadas 60 pólizas que coinciden, cuando se busca, entonces llegan 50, las más recientes, y el aviso de que hay más | TC-05 |
| SC-10 | AC-01 | Dado un texto con `%`, `_` o una comilla, cuando se busca, entonces se trata como texto literal | TC-05 |
| SC-11 | AC-07 | Dado un resultado, cuando se muestra, entonces el documento sale enmascarado y no vienen correo ni teléfono | TC-05 |
| SC-12 | UC-03 | Dado el asesor, cuando emite desde el portal, entonces la póliza guarda el titular | TC-02 |
| SC-13 | AC-12 | Dada una póliza B2B sin titular, cuando aparece en la búsqueda, entonces muestra solo el documento enmascarado | TC-01 |
| SC-14 | AC-11 | Dado el analista o el asesor, cuando entra al portal, entonces llega a Gestión o al cotizador; y si abre la ruta del otro rol, vuelve a la suya | TC-06 |
| SC-15 | AC-02 | Dadas 10.000 pólizas, cuando se hacen 200 búsquedas mezcladas, entonces el p95 queda por debajo de 150 ms | TC-08 |
| SC-16 | UC-01 | Dado el lector de pantalla, cuando busca con teclado, entonces se anuncian el resultado y el "sin resultados" | TC-09 |
| SC-17 | UC-02 | Dado un usuario de un socio en producción, cuando la base no tiene usuarios de prueba, entonces no se siembran | TC-07 |
| SC-18 | UC-01 | Dado el analista, cuando abre una póliza para ver su detalle, entonces la ve completa | OUT OF SCOPE: el detalle de la póliza es HU-WEB-09 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-02, SC-03 | — |
| Alternate flows | YES | SC-04, SC-05, SC-13 | — |
| Invalid input and errors | YES | SC-08, SC-10 | — |
| Limits and boundaries | YES | SC-09, SC-15 | — |
| Related flows before and after | YES | SC-12, SC-18 | — |
| Several users, sessions, devices or roles | YES | SC-06, SC-07, SC-08, SC-14 | — |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-07, SC-17 | — |

## Test cases

### TC-01 · Búsqueda por cada criterio

| Field | Value |
|---|---|
| Covers | AC-01, AC-06, AC-12 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas con pólizas del portal y de dos socios; token de analista sin socio |
| Procedure | 1. Buscar por documento. 2. Por prefijo de número. 3. Por nombre del socio en minúsculas. 4. Por nombre del titular. 5. Filtrar por estado. 6. Buscar una póliza B2B sin titular |
| Expected result | Cada búsqueda trae las pólizas esperadas con número, titular, documento enmascarado, plan, estado, vigencia y socio; la B2B trae titular null |
| Tool or method | pytest backend/tests/integration/test_policy_search_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_search_api.py::test_tc_01_por_documento_trae_sus_polizas_con_los_campos_de_la_lista; backend/tests/integration/test_policy_search_api.py::test_tc_01_por_prefijo_de_numero_por_socio_y_por_titular; backend/tests/integration/test_policy_search_api.py::test_tc_01_filtra_por_estado_y_la_poliza_b2b_no_trae_titular |
| Status | PASS |

### TC-02 · Titular guardado al emitir

| Field | Value |
|---|---|
| Covers | AC-05 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Cotización vigente |
| Procedure | 1. Emitir con holder. 2. Leer la póliza en la base |
| Expected result | La póliza guarda nombre, tipo de documento, correo y teléfono |
| Tool or method | pytest backend/tests/integration/test_policy_search_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_search_api.py::test_tc_02_al_emitir_desde_el_portal_se_guarda_el_titular; web/src/app/features/cotizador/cotizador.component.spec.ts::"emite el plan elegido y confirma el número y la vigencia" |
| Status | PASS |

### TC-03 · Aislamiento por socio

| Field | Value |
|---|---|
| Covers | AC-04, AC-10, SR-01, SR-05 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Pólizas de dos socios y del portal; usuario ligado a Banco Aliado; usuario ligado a un socio revocado |
| Procedure | 1. Como usuario de Banco Aliado, buscar el documento y el número de una póliza del otro socio y del portal. 2. Buscar sin texto. 3. Como usuario del socio revocado, buscar |
| Expected result | 1 no devuelve nada; 2 solo trae pólizas de Banco Aliado; 3 responde 403 |
| Tool or method | pytest backend/tests/integration/test_policy_search_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_search_api.py::test_tc_03_el_usuario_de_un_socio_solo_ve_las_de_su_socio; backend/tests/integration/test_policy_search_api.py::test_tc_03_el_usuario_de_un_socio_revocado_recibe_403 |
| Status | PASS |

### TC-04 · Solo el rol analyst

| Field | Value |
|---|---|
| Covers | AC-09, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Tokens de asesor y de analista |
| Procedure | 1. Buscar con token de asesor. 2. Buscar sin token. 3. Buscar con un token de analista de un usuario que ya no existe |
| Expected result | 403, 401 y 401 |
| Tool or method | pytest backend/tests/integration/test_policy_search_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_search_api.py::test_tc_04_solo_el_rol_analyst_busca |
| Status | PASS |

### TC-05 · Enmascarado, tope y texto literal

| Field | Value |
|---|---|
| Covers | AC-07, AC-08, SR-03, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-03, THR-04 |
| Level | INTEGRATION |
| Preconditions | 60 pólizas que coinciden |
| Procedure | 1. Buscar y revisar la respuesta. 2. Buscar `%`, `_` y `' or 1=1 --`. 3. Buscar con más de 80 caracteres |
| Expected result | 50 resultados, los más recientes, has_more true, documento enmascarado, sin correo ni teléfono; 2 no trae nada; 3 responde 422 |
| Tool or method | pytest backend/tests/integration/test_policy_search_api.py; pytest backend/tests/unit/test_policy_search.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_search_api.py::test_tc_05_maximo_50_resultados_los_mas_recientes_y_sin_contacto; backend/tests/integration/test_policy_search_api.py::test_tc_05_comodines_y_comillas_se_buscan_literales; backend/tests/integration/test_policy_search_api.py::test_tc_05_texto_de_mas_de_80_caracteres_o_estado_invalido_responde_422; backend/tests/unit/test_policy_search.py::test_tc_05_el_documento_solo_deja_ver_los_ultimos_cuatro_digitos |
| Status | PASS |

### TC-06 · Pantalla de gestión y acceso por rol

| Field | Value |
|---|---|
| Covers | AC-01, AC-03, AC-11 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Componentes del portal con el servicio simulado |
| Procedure | 1. Buscar y ver la tabla. 2. Buscar sin coincidencias y pulsar "Limpiar filtro". 3. Entrar como analista y como asesor. 4. Abrir la ruta del otro rol |
| Expected result | La tabla muestra los campos; el "sin resultados" tiene el botón que limpia; cada rol llega a su pestaña y la guardia lo devuelve a la suya |
| Tool or method | npm test (gestion.component.spec.ts, role.guard.spec.ts, login.component.spec.ts) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | web/src/app/features/gestion/gestion.component.spec.ts::"TC-06 avisa cuando hay más de 50 resultados"; web/src/app/features/gestion/gestion.component.spec.ts::"TC-06 al abrir trae las pólizas más recientes y las muestra en la tabla"; web/src/app/core/guards/role.guard.spec.ts::"TC-06 devuelve al asesor al cotizador y al analista a gestión"; web/src/app/features/auth/login.component.spec.ts::"HU-WEB-08: el analista de operaciones entra a Gestión"; prueba de punta a punta local con Chrome el 2026-10-27 (analista de operaciones, analista de Banco Aliado y asesor contra el backend con PostgreSQL): 24 de 24 verificaciones |
| Status | PASS |

### TC-07 · Usuarios de prueba solo en desarrollo

| Field | Value |
|---|---|
| Covers | SR-06 |
| Kind | SECURITY |
| Risk covered | THR-05 |
| Level | UNIT |
| Preconditions | Servicio de autenticación con la base en memoria |
| Procedure | 1. Sembrar en desarrollo. 2. Revisar los roles y el socio de los usuarios sembrados |
| Expected result | Se crean el analista sin socio y el de Banco Aliado con rol analyst; el arranque solo siembra con ENVIRONMENT distinto de production (ya cubierto en main.py) |
| Tool or method | pytest backend/tests/unit/test_policy_search.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_search_api.py::test_tc_07_los_analistas_de_prueba_quedan_con_su_rol_y_su_socio |
| Status | PASS |

### TC-08 · Tiempo de respuesta

| Field | Value |
|---|---|
| Covers | AC-02, QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | PERFORMANCE |
| Preconditions | PostgreSQL local con 10.000 pólizas de dos socios y del portal |
| Procedure | 1. Ejecutar 200 búsquedas mezcladas (documento, número, socio y titular) contra el endpoint. 2. Calcular el p95. 3. Revisar con EXPLAIN que la búsqueda por titular y por documento usa los índices de trigramas |
| Expected result | p95 por debajo de 150 ms y el plan usa los índices |
| Tool or method | pytest backend/tests/integration/test_policy_search_performance.py |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_policy_search_performance.py::test_tc_08_el_p95_de_la_busqueda_queda_por_debajo_de_150_ms; backend/tests/integration/test_policy_search_performance.py::test_tc_08_la_busqueda_por_titular_y_por_documento_usa_los_indices_de_trigramas; 10.000 pólizas, 200 búsquedas con los índices de trigramas: p50 24,3 ms y p95 31,2 ms el 2026-10-27 |
| Status | PASS |

### TC-09 · Accesibilidad de la consulta

| Field | Value |
|---|---|
| Covers | QR-02 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | Portal en el navegador con lector de pantalla |
| Procedure | 1. Llegar al buscador con Tab. 2. Buscar y escuchar el resultado. 3. Buscar sin coincidencias y activar "Limpiar filtro" con teclado |
| Expected result | El campo tiene etiqueta, la tabla tiene encabezados, el número de resultados y el "sin resultados" se anuncian y el botón se usa con teclado |
| Tool or method | Narrador o NVDA, teclado |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | PENDING: auditoría automática con Chrome el 2026-10-27. axe-core 4.12.1 (WCAG 2.2 AA) sin violaciones en la lista y en «sin resultados». Árbol de accesibilidad: landmark de búsqueda, campo «Buscar», filtro «Estado», conteo en role=status con aria-live=polite, tabla «Pólizas encontradas» con 6 encabezados de columna y uno de fila por póliza. Teclado: Enter busca y «Limpiar filtro» devuelve el foco al buscador. A 320 px el encabezado del portal se desbordaba y se corrigió (app.component.css). Falta la pasada con lector de pantalla |
| Status | PENDING |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-04 | — |
| User without permissions | YES | TC-04 | — |
| Access to another user's resources | YES | TC-03 | — |
| Cross-tenant access | YES | TC-03 | — |
| Malicious input | YES | TC-05 | — |
| Expired tokens | NO | — | La validación del token no cambia (HU-WEB-13) |
| Revoked sessions | YES | TC-03, TC-04 | — |
| Tampered data | NO | — | Sin datos firmados nuevos |
| Wrong keys | NO | — | Sin llaves nuevas |
| External service failures | NO | — | Sin servicios externos |
| Race conditions | NO | — | Consulta de solo lectura |
| Information exposure | YES | TC-05 | — |
| Secrets in code | YES | TC-07 | — |
| Insecure configuration | YES | TC-07 | — |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas |

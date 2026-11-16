# Test cases · HOTFIX

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | UC-01, AC-01 | Dadas cotizaciones y pólizas de hoy, de hace 3, 20 y 40 días, cuando el analista de Solventa pide `today`, `7d` y `30d`, entonces recibe 4, 6 y 7 cotizaciones, 2, 3 y 3 pólizas, la conversión de cada periodo, los siniestros en curso y los pendientes del outbox | TC-01 |
| SC-02 | AC-02 | Dado un periodo sin cotizaciones, cuando se piden los indicadores, entonces la conversión es null, las cotizaciones por minuto son 0 y el portal dice «Sin cotizaciones» | TC-01, TC-06 |
| SC-03 | AC-01 | Dada una hora de la noche en Colombia que en UTC ya es el día siguiente, cuando se calcula el periodo, entonces empieza a las 00:00 de Colombia | TC-01 |
| SC-04 | UC-03, AC-05 | Dado el analista de un socio, un asesor o nadie, cuando pide los indicadores, entonces recibe 403, 403 o 401 | TC-02 |
| SC-05 | AC-04 | Dado S3 que falla con un mensaje interno, SQS que no responde y Firebase lento, cuando se piden los indicadores, entonces responde 200 con S3 y SQS `down`, Firebase `degraded` y ningún detalle del error | TC-03 |
| SC-06 | AC-04 | (a) Dado el analista validado con la base, cuando después la base se cuelga o falla, entonces responde 200, la base `down` y los conteos null sin el detalle del error | TC-03 |
| SC-23 | AC-04 | (b) Dada la base caída del todo (falla o se cuelga la lectura del analista), cuando se piden los indicadores, entonces responde 503 genérico antes del límite más un margen y sin el detalle del error | TC-10 |
| SC-24 | AC-04 | Dado un conteo que pasa del límite, cuando se piden los indicadores, entonces los conteos son null, la base sigue operativa y no se cuelga | TC-10 |
| SC-25 | AC-04 | Dada una sonda de la base cortada por el límite durante un `pg_sleep(2)`, cuando se usa la sesión del pedido, entonces responde y se cierra sin «another operation is in progress» | TC-10 |
| SC-26 | AC-10 | Dadas dos consultas seguidas del mismo periodo, cuando se responden, entonces solo la primera comprueba las dependencias; otro periodo comprueba de nuevo y el analista de un socio sigue con 403 | TC-10 |
| SC-07 | AC-04 | Dado Firebase sin credenciales, cuando se piden los indicadores, entonces queda `not_configured` sin latencia | TC-03 |
| SC-08 | AC-03 | Dadas solicitudes a la cotización, cuando se piden los indicadores, entonces la cotización trae sus muestras, p50 ≤ p95 ≤ p99 y la meta de 400 ms; un grupo sin muestras trae null | TC-04 |
| SC-09 | AC-03 | Dadas más de 500 muestras, cuando se agrega una, entonces sale la más vieja | TC-04 |
| SC-10 | AC-03 | Dadas solicitudes a cada ruta crítica que responden 4xx y a rutas con radicado o número de póliza, cuando se lee la ventana, entonces no hay muestras ni identificadores; con una aplicación mínima, solo cuenta la respuesta 200 y no las 400, 401 ni 500 | TC-05 |
| SC-11 | AC-03 | Dada una falla al registrar la latencia, cuando llega una solicitud, entonces responde igual | TC-05 |
| SC-12 | AC-03 | Dados 8 hilos que registran a la vez, cuando terminan, entonces la ventana queda con 500 muestras sin error | TC-04 |
| SC-27 | AC-04 | Dados dos hilos que piden el emisor de Firebase a la vez, cuando terminan, entonces Firebase se inició una sola vez y ambos reciben el mismo emisor | TC-03 |
| SC-13 | UC-02, AC-06 | Dada la pestaña visible, cuando pasan 30 s, entonces se consulta de nuevo sin anunciar el refresco | TC-06 |
| SC-14 | AC-06 | Dada la pestaña oculta, cuando pasan 90 s, entonces no se consulta y no queda temporizador; al volver, se consulta en seguida y se reanuda | TC-06 |
| SC-15 | AC-06 | Dado el tablero abierto, cuando el analista sale de la pantalla, entonces no queda temporizador ni listener activo | TC-06 |
| SC-16 | AC-06 | Dado un refresco en el que una dependencia cambia de estado, cuando llega, entonces la región viva anuncia solo ese cambio | TC-07 |
| SC-17 | AC-07 | Dado el semáforo, cuando se muestra, entonces cada estado tiene icono y texto | TC-07 |
| SC-18 | AC-09 | Dado un refresco que falla, cuando responde 500 o 503, entonces se ve un aviso genérico con «Sin datos desde HH:mm:ss», las tarjetas, la latencia y el semáforo quedan marcados y el semáforo no muestra los estados anteriores; el siguiente refresco que responde quita la marca y lo anuncia | TC-06 |
| SC-28 | AC-06 | Dada una carga pedida por el analista en curso, cuando llega el tick de 30 s, entonces no hay consulta nueva y la del analista no se cancela | TC-06 |
| SC-29 | UC-02, QR-01 | Dado un aviso igual al anterior, cuando el analista actualiza, entonces la región viva se vacía y se vuelve a escribir | TC-07 |
| SC-30 | AC-05 | Dado un analista que no es de Solventa, cuando abre `/operacion`, entonces `solventaGuard` lo devuelve a `/gestion`; el de Solventa pasa | TC-06 |
| SC-19 | AC-08 | Dado un periodo `1y`, cuando se piden los indicadores, entonces responde 422 | TC-01 |
| SC-20 | AC-03, QR-02 | Dadas 10.000 muestras, cuando se clasifican y registran, entonces cada una cuesta menos de 50 µs | TC-08 |
| SC-21 | AC-05 | Dado un analista, cuando inicia sesión, entonces la pestaña «Operación» aparece solo si es de Solventa | TC-06 |
| SC-22 | AC-04 | Dadas varias réplicas de ECS, cuando el analista consulta, entonces ve la latencia de una sola réplica | OUT OF SCOPE: RISK-01; el agregado entre réplicas queda para CloudWatch |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-08, SC-13 | — |
| Alternate flows | YES | SC-02, SC-07, SC-14 | — |
| Invalid input and errors | YES | SC-05, SC-06, SC-23, SC-24, SC-11, SC-18, SC-19 | — |
| Limits and boundaries | YES | SC-03, SC-09, SC-20 | Medianoche de Colombia, ventana de 500 y costo del registro |
| Related flows before and after | YES | SC-10, SC-21 | Las rutas críticas alimentan el tablero; la pestaña depende de `/experience/operator` |
| Several users, sessions, devices or roles | YES | SC-04, SC-12, SC-27, SC-30, SC-22 | Roles, hilos concurrentes y varias réplicas |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-25, SC-26, SC-13, SC-14, SC-15, SC-16, SC-28, SC-29 | Refresco, pestaña oculta, salida de la pantalla, caché y cortes por límite |

## Test cases

### TC-01 · Indicadores por periodo

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-08 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas (`TEST_DATABASE_NAME=solventa_test_b2`) con cotizaciones, pólizas, siniestros y eventos del outbox sembrados; S3, SQS y Firebase simulados |
| Procedure | 1. Pedir `today`, `7d` y `30d`. 2. Pedir sin datos. 3. Pedir `1y`. 4. Calcular el inicio de cada periodo a las 22:30 de Colombia |
| Expected result | 4, 6 y 7 cotizaciones; 2, 3 y 3 pólizas; conversión 0,5, 0,5 y 3/7; 2 siniestros en curso de 5; 2 pendientes del outbox; `since` con `-05:00`; sin datos la conversión es null; `1y` da 422; los periodos empiezan a las 00:00 de Colombia de hoy, de hace 6 y de hace 29 días |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_operations_dashboard_api.py::test_indicadores_por_periodo_en_hora_de_colombia; backend/tests/integration/test_operations_dashboard_api.py::test_sin_cotizaciones_la_conversion_es_nula_y_no_falla; backend/tests/integration/test_operations_dashboard_api.py::test_un_periodo_desconocido_da_422; backend/tests/unit/test_operations_metrics.py::test_los_periodos_empiezan_a_medianoche_de_colombia; backend/tests/unit/test_operations_metrics.py::test_division_segura_sin_cotizaciones |
| Status | PASS |

### TC-02 · Solo el analista de Solventa

| Field | Value |
|---|---|
| Covers | AC-05, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Analista de Solventa, analista de Banco Aliado y asesor |
| Procedure | 1. Pedir sin sesión. 2. Pedir con el analista del socio. 3. Pedir con el asesor |
| Expected result | 401, 403 y 403 |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_operations_dashboard_api.py::test_sin_sesion_401_y_el_analista_de_un_socio_o_el_asesor_403 |
| Status | PASS |

### TC-03 · Dependencias caídas, lentas o sin configurar

| Field | Value |
|---|---|
| Covers | AC-04, SR-03, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-02, THR-04 |
| Level | INTEGRATION |
| Preconditions | Comprobaciones simuladas con `dependency_overrides`; límite y umbral reducidos con `monkeypatch` |
| Procedure | 1. S3 lanza un error con un texto interno, SQS no responde y Firebase tarda. 2. Firebase sin credenciales. 3. Caso (a): el analista se valida con la base real y luego la sonda de la base se cuelga (`pg_sleep(5)`) o falla (tabla inexistente), con 0,2 s de límite. 4. Buscar el texto interno en la respuesta. 5. Dos hilos piden el emisor de Firebase a la vez |
| Expected result | 200 en los casos 1 a 3; S3 y SQS `down` antes del límite más un margen, Firebase `degraded`; Firebase sin credenciales `not_configured` sin latencia; en el caso (a) la base `down`, los conteos null y respuesta en menos de 2 s; cada dependencia solo con `name`, `status` y `latency_ms`; ni el texto interno, ni el nombre de la tabla, ni el tipo de error en la respuesta; Firebase se inicia una sola vez |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_operations_dashboard_api.py::test_una_dependencia_caida_o_lenta_no_tumba_el_tablero_ni_expone_el_error; backend/tests/integration/test_operations_dashboard_api.py::test_firebase_sin_credenciales_queda_como_no_configurado; backend/tests/integration/test_operations_dashboard_api.py::test_si_la_base_falla_despues_de_validar_al_analista_responde_sin_conteos_y_la_marca_caida; backend/tests/unit/test_notifications.py::test_dos_hilos_a_la_vez_inician_firebase_una_sola_vez |
| Status | PASS |

### TC-04 · Percentiles y ventana de latencia

| Field | Value |
|---|---|
| Covers | AC-03 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | `LatencyWindow` en memoria |
| Procedure | 1. Percentiles de 1 a 100, de una muestra y sin muestras. 2. Ventana de 3 con 4 muestras y un grupo desconocido. 3. 8 hilos con 1000 registros cada uno. 4. Meta por p95. 5. Tres muestras de cotización (120, 250 y 380 ms) y el tablero por HTTP |
| Expected result | p50 50, p95 95, p99 99; sin muestras null; quedan las 3 últimas y el grupo desconocido se ignora; 500 muestras sin error; la cotización con p95 de 100 ms cumple y la emisión con 1200 ms no; por HTTP la cotización trae 3 muestras, p50 250, p95 y p99 380, meta 400 y dentro de la meta, y el reporte de siniestro sin muestras en null |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/unit/test_operations_metrics.py::test_percentil_por_rango_mas_cercano; backend/tests/unit/test_operations_metrics.py::test_percentil_sin_muestras_es_nulo; backend/tests/unit/test_operations_metrics.py::test_la_ventana_guarda_solo_las_ultimas_muestras_de_grupos_conocidos; backend/tests/unit/test_operations_metrics.py::test_la_ventana_soporta_escrituras_concurrentes; backend/tests/unit/test_operations_metrics.py::test_la_latencia_se_compara_con_la_meta_por_p95; backend/tests/integration/test_operations_dashboard_api.py::test_la_latencia_por_grupo_se_compara_con_su_meta |
| Status | PASS |

### TC-05 · El middleware no guarda identificadores ni rompe el pedido

| Field | Value |
|---|---|
| Covers | AC-03, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-03, THR-05 |
| Level | INTEGRATION |
| Preconditions | Aplicación completa con su pila de middlewares |
| Procedure | 1. Llamar a las cuatro rutas críticas sin sesión o con el cuerpo inválido, a un expediente `SIN-…`, a una renovación `POL-…` y a `/health`. 2. Leer la ventana y su estado interno. 3. Con una aplicación mínima con el middleware, responder 200, 401, 400 y 500 en rutas críticas. 4. Clasificar plantillas con y sin prefijo y rutas no críticas. 5. Hacer que el registro lance una excepción y llamar a una ruta crítica y a `/health` |
| Expected result | Las rutas críticas responden 4xx y no dejan muestras; ninguna de las otras rutas cuenta; ni el documento, ni el radicado, ni la póliza, ni `/api/v1`, ni el usuario en la ventana; en la aplicación mínima solo cuenta la respuesta 200; la clasificación usa la plantilla y no la URL; con la falla, la cotización responde 401 como siempre y `/health` 200 |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_operations_dashboard_api.py::test_el_middleware_no_guarda_respuestas_de_error_ni_identificadores; backend/tests/unit/test_operations_metrics.py::test_solo_se_registran_las_respuestas_por_debajo_de_400; backend/tests/integration/test_operations_dashboard_api.py::test_una_falla_del_middleware_no_rompe_el_pedido; backend/tests/unit/test_operations_metrics.py::test_la_ruta_se_clasifica_por_su_plantilla_con_o_sin_prefijo; backend/tests/unit/test_operations_metrics.py::test_rutas_no_criticas_o_sin_plantilla_no_se_clasifican |
| Status | PASS |

### TC-06 · Pantalla «Operación» y refresco

| Field | Value |
|---|---|
| Covers | AC-02, AC-05, AC-06, AC-09, SR-01, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-01, THR-04, THR-06 |
| Level | UNIT |
| Preconditions | Servidor simulado con `HttpTestingController`, temporizadores falsos de Vitest y `document.visibilityState` controlado |
| Procedure | 1. Cargar y verificar el periodo `today`. 2. Avanzar 30 s dos veces. 3. Ocultar la pestaña y avanzar 90 s; mostrarla. 4. Destruir el componente y avanzar 90 s. 5. Cambiar a `7d`. 6. Responder 500 a un refresco. 7. Responder 503 a un refresco y luego 200. 8. Dejar sin responder la carga inicial y avanzar 30 s. 9. Sin cotizaciones. 10. Pestañas con y sin Solventa. 11. Correr los guards de `/operacion` con y sin Solventa |
| Expected result | Una consulta a los 30 s y no antes; oculta no consulta y no queda temporizador; al volver consulta en seguida y reanuda; destruido no queda temporizador ni consultas; `7d` en la consulta; aviso genérico sin el detalle del servidor; tras el 503, «Sin datos desde HH:mm:ss» en la hora, en una nota sobre las tarjetas (con borde punteado), en el título del semáforo, en cada estado y en el aviso, y el semáforo sin «Operativa»; con el 200 la marca y el aviso desaparecen y se anuncia «Indicadores actualizados de nuevo.»; el tick no consulta ni cancela la carga del analista; «Sin cotizaciones»; las pestañas «Socios» y «Operación» solo con Solventa; `/operacion` es diferida, usa `solventaGuard` y devuelve a `/gestion` al analista que no es de Solventa |
| Tool or method | Vitest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | web/src/app/features/operacion/operations.component.spec.ts::"se actualiza cada 30 segundos sin anunciar cada refresco"; web/src/app/features/operacion/operations.component.spec.ts::"no consulta mientras la pestaña está oculta y se pone al día al volver"; web/src/app/features/operacion/operations.component.spec.ts::"al destruir el componente detiene el temporizador y deja de escuchar la visibilidad"; web/src/app/features/operacion/operations.component.spec.ts::"cambiar el periodo consulta de nuevo y lo anuncia"; web/src/app/features/operacion/operations.component.spec.ts::"un error muestra un aviso genérico y conserva los últimos datos"; web/src/app/features/operacion/operations.component.spec.ts::"un refresco fallido marca el semáforo y las tarjetas como desactualizados"; web/src/app/features/operacion/operations.component.spec.ts::"el refresco automático no cancela la carga pedida por el analista"; web/src/app/features/operacion/operations.component.spec.ts::"sin cotizaciones lo dice en lugar de mostrar una conversión"; web/src/app/core/operations/operations.service.spec.ts::"pide los indicadores del periodo a experience"; web/src/app/app.component.spec.ts::"FE-WEB-05 y FE-WEB-07 las pestañas Socios y Operación solo aparecen para el analista de Solventa"; web/src/app/app.routes.spec.ts::"usa solventaGuard y carga la pantalla de forma diferida"; web/src/app/app.routes.spec.ts::"devuelve a /gestion al analista que no es de Solventa"; web/src/app/app.routes.spec.ts::"deja pasar al analista de Solventa" |
| Status | PASS |

### TC-07 · Accesibilidad automatizada de «Operación»

| Field | Value |
|---|---|
| Covers | AC-07, QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | Pantalla con indicadores |
| Procedure | 1. Revisar `caption`, `th scope="col"` y `th scope="row"` de las dos tablas. 2. Etiqueta del selector. 3. Icono `aria-hidden` y texto de cada estado. 4. Región `aria-live` tras la carga, tras dos refrescos y tras un cambio de estado. 5. Pedir dos veces la misma carga |
| Expected result | Todo presente; los estados dicen «Operativa», «Degradada», «Caída», «Sin configurar», «Dentro de la meta», «Sobre la meta» o «Sin datos» además del icono; la región viva no cambia con un refresco sin cambios y anuncia «Cambió el estado de: Cola SQS (Operativa).»; con el mismo aviso, la región queda vacía y 100 ms después vuelve a tener el texto |
| Tool or method | Vitest con jsdom |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | web/src/app/features/operacion/operations.component.spec.ts::"muestra las tarjetas, la tabla de latencia y el semáforo con texto e icono"; web/src/app/features/operacion/operations.component.spec.ts::"anuncia solo las dependencias que cambiaron de estado en un refresco"; web/src/app/features/operacion/operations.component.spec.ts::"se actualiza cada 30 segundos sin anunciar cada refresco"; web/src/app/features/operacion/operations.component.spec.ts::"cambiar el periodo consulta de nuevo y lo anuncia"; web/src/app/features/operacion/operations.component.spec.ts::"un aviso igual al anterior vacía la región viva y la vuelve a escribir" |
| Status | PASS |

### TC-08 · Costo del middleware

| Field | Value |
|---|---|
| Covers | QR-02 |
| Kind | QUALITY |
| Risk covered | — |
| Level | PERFORMANCE |
| Preconditions | Proceso local, sin red |
| Procedure | 1. Clasificar y registrar 10.000 muestras de la cotización. 2. Medir el promedio por muestra |
| Expected result | Menos de 50 µs por muestra |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/unit/test_operations_metrics.py::test_clasificar_y_registrar_una_muestra_cuesta_menos_de_50_microsegundos |
| Status | PASS |

### TC-10 · Base caída del todo, límites y caché

| Field | Value |
|---|---|
| Covers | AC-04, AC-10, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-04, RISK-03, RISK-04 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas (`TEST_DATABASE_NAME=solventa_test_b2`); límites reducidos con `monkeypatch` |
| Procedure | 1. Caso (b): sesión cuya lectura del analista falla con un texto interno o se cuelga 30 s, con 0,3 s de límite. 2. Conteos con `statement_timeout` de 100 ms y una sentencia `pg_sleep(2)`. 3. Sonda de la base con `pg_sleep(2)` y 0,2 s de límite; luego usar y cerrar la sesión del pedido y repetir la sonda con `SELECT 1`. 4. Dos consultas seguidas de `today`, una de `7d` y una del analista de un socio. 5. Comprobación en un hilo y con el tope de hilos agotado. 6. Clientes de sonda con el emulador |
| Expected result | (b) 503 con `{"detail": "El tablero no está disponible en este momento. Intente de nuevo en unos segundos."}` en menos de 1,5 s, sin el texto interno ni el tipo de error; conteo lento: 200 con los conteos null, la base operativa, en menos de 1,5 s y sin el texto de PostgreSQL; sonda cortada: `down` en menos de 1 s, la sesión responde `SELECT 1` y se cierra, y la sonda siguiente da `ok`; una sola comprobación para las dos consultas de `today`, otra para `7d`, y 403 para el analista del socio; la comprobación corre en un hilo daemon y, sin hilos libres, falla en seguida; clientes con 1 s de conexión, 1 s de lectura, un intento, el endpoint y la región configurados, cacheados |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_operations_dashboard_api.py::test_con_la_base_caida_del_todo_responde_503_generico_y_rapido; backend/tests/integration/test_operations_dashboard_api.py::test_un_conteo_lento_da_null_sin_colgar; backend/tests/integration/test_operations_dashboard_api.py::test_el_limite_de_la_base_deja_la_sesion_del_pedido_usable_y_cerrable; backend/tests/integration/test_operations_dashboard_api.py::test_dos_llamadas_seguidas_hacen_una_sola_comprobacion; backend/tests/unit/test_operations_metrics.py::test_las_comprobaciones_corren_en_hilos_daemon_y_sin_hilos_libres_fallan_en_seguida; backend/tests/unit/test_operations_metrics.py::test_las_sondas_tienen_clientes_propios_con_un_intento_y_un_segundo |
| Status | PASS |

### TC-09 · Prueba en el portal

| Field | Value |
|---|---|
| Covers | AC-01, AC-05, AC-06, AC-07, QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | E2E |
| Preconditions | Backend, Floci y portal locales con los usuarios semilla |
| Procedure | 1. Entrar como analista de Solventa, cotizar y emitir desde «Cotizador» con un asesor y abrir «Operación». 2. Cambiar el periodo. 3. Detener Floci y esperar el siguiente refresco; detener el backend y esperar el siguiente refresco. 4. Pasar a otra pestaña del navegador y revisar en la red que no hay consultas. 5. Entrar como analista de Banco Aliado y abrir `/operacion`. 6. Recorrer la pantalla con teclado y con NVDA |
| Expected result | Los indicadores y la latencia reflejan lo hecho; S3 y SQS pasan a «Caída» y el lector lo anuncia una vez; con el backend detenido, el semáforo y las tarjetas dicen «Sin datos desde HH:mm:ss» y el lector lo anuncia; sin consultas con la pestaña oculta; el analista del socio no ve la pestaña y vuelve a «Pólizas»; el lector anuncia las tablas, los estados y el selector |
| Tool or method | Prueba manual |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-02 | — |
| User without permissions | YES | TC-02 | Asesor |
| Access to another user's resources | YES | TC-02 | El analista de un socio no ve los indicadores globales |
| Cross-tenant access | YES | TC-02 | — |
| Malicious input | YES | TC-01, TC-05 | `period` fuera de la lista da 422; rutas con identificadores no se guardan |
| Expired tokens | NO | — | No cambia la expiración |
| Revoked sessions | NO | — | No cambia la sesión |
| Tampered data | NO | — | Sin datos firmados nuevos |
| Wrong keys | NO | — | Sin llaves nuevas |
| External service failures | YES | TC-03, TC-06, TC-10 | S3, SQS, Firebase y base caídos o lentos (antes y después de validar al analista); error del servidor en el portal |
| Race conditions | YES | TC-03, TC-04, TC-06 | Escrituras concurrentes en la ventana; inicio de Firebase con dos hilos; el refresco automático no cancela la carga del analista |
| Information exposure | YES | TC-03, TC-05, TC-10 | Sin mensajes de error ni identificadores, también en el 503 |
| Secrets in code | NO | — | Sin secretos; las pruebas no usan credenciales |
| Insecure configuration | NO | — | Sin configuración nueva |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas |

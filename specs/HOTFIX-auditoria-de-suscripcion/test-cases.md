# Test cases · HOTFIX

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | UC-01, AC-01 | Dada una cotización nueva, cuando el analista de Solventa la consulta, entonces ve el documento enmascarado, la fecha, la suma, el riesgo, el puntaje, la versión `2026.11` y los tres tiers con su desglose, con `no-store` | TC-01 |
| SC-02 | AC-02 | Dado un rechazo nuevo, cuando lo consulta, entonces ve solo lo guardado: documento enmascarado, monto solicitado, regla, motivo, monto máximo, versión y fecha, sin puntaje, fecha de nacimiento ni riesgo | TC-01 |
| SC-03 | UC-02, AC-03 | Dadas una cotización activa y una degradada, cuando se recalculan sin cambiar las reglas, entonces coinciden | TC-02 |
| SC-04 | AC-04 | Dado un multiplicador de ocupación o un cupo distintos y otra versión de reglas, cuando se recalcula una cotización, entonces no coincide y lista cada diferencia | TC-03 |
| SC-05 | AC-05 | Dada una cotización y un rechazo guardados antes de la auditoría, cuando se consultan, entonces el detalle dice que no se pueden recalcular y el recálculo de la cotización da 409 | TC-04 |
| SC-06 | AC-08 | Dado el analista de un socio, un asesor o nadie, cuando llama a cualquier endpoint, entonces recibe 403, 403 o 401 y el consentimiento no cambia | TC-05 |
| SC-07 | AC-08 | Dado un id inexistente o mal formado o un documento inválido, cuando se consulta o se revoca, entonces 404 o 422 | TC-05 |
| SC-08 | AC-06 | Dada una cotización guardada, cuando alguien ejecuta UPDATE o DELETE sobre `quotes`, entonces la base lo rechaza y la fila no cambia | TC-06 |
| SC-09 | AC-06 | Dada una base en `0004` sin las columnas ni el disparador, cuando se migra dos veces y se revierte, entonces quedan cuatro columnas (tres en `quotes` y `rules_version` en `underwriting_rejections`), ninguna de puntaje ni fecha en los rechazos, y el disparador, y luego desaparecen | TC-06 |
| SC-10 | UC-03, UC-04, AC-07 | Dado un consentimiento vigente usado en una cotización, cuando el analista registra la revocación, entonces el detalle de la cotización y del rechazo del documento omite el documento (y, en la cotización, la fecha y el puntaje), cotizar con o sin ese consentimiento da 422 y revocar otra vez da 0 | TC-07 |
| SC-11 | AC-07 | Dada otra réplica con la copia en memoria del consentimiento otorgado, cuando se cotiza tras la revocación, entonces da 422 | TC-07 |
| SC-12 | UC-03 | Dadas una consulta, un recálculo y una revocación, cuando se leen los registros, entonces hay tres eventos con `operator_ref` y sin documento, fecha ni correo | TC-08 |
| SC-13 | AC-09 | Dado un analista, cuando inicia sesión, entonces «Auditoría» aparece junto a «Socios» solo si es de Solventa | TC-09 |
| SC-14 | AC-10 | Dada la pantalla, cuando se busca, se recalcula y se registra la revocación, entonces se valida el id, se ven las tablas, el resultado se anuncia y la revocación pide confirmación y devuelve el foco | TC-09, TC-10 |
| SC-15 | AC-11 | Dado el rechazo de una cotización, cuando se registra, entonces guarda la versión de reglas y no guarda el puntaje ni la fecha de nacimiento | TC-12 |
| SC-17 | AC-13 | Dada una cotización cuyo consentimiento se revocó, cuando se consulta y se pide recalcular, entonces llegan solo los planes con el id y el nombre de sus coberturas, sin entradas, primas, desglose ni montos (ni `monthly_premium`, `annual_premium`, `limit`, `insured_amount`, `occupation_risk` ni «años»), `replayable: false`, el recálculo da 409 con su motivo y `no-store`, y el portal muestra una sola nota, los planes y los nombres de las coberturas, sin «Primas por plan», y el motivo en lugar de «Recalcular» | TC-07, TC-09 |
| SC-18 | AC-12 | Dado el `consent_id` vigente de otro documento, cuando el asesor o un socio cotizan, entonces 422 «El consentimiento no corresponde al cliente.» sin consultar el buró ni guardar la cotización; con un `consent_id` revocado el socio también recibe 422 | TC-13 |
| SC-19 | AC-07 | Dada una cotización vieja con el consentimiento vigente de otro titular, cuando se revocan los del documento, entonces el detalle la trata como revocada | TC-13 |
| SC-20 | AC-04 | Dado un tier que ya no se ofrece o uno nuevo, una prima anual o una cobertura distintas, o una diferencia de coma flotante de hasta 0,005, cuando se recalcula, entonces se reporta el tier, la prima o la cobertura y se ignora la diferencia menor | TC-03 |
| SC-21 | AC-14 | Dado un rechazo cuyo consentimiento se revocó, cuando el analista lo consulta, entonces el detalle no trae el documento, la regla, el motivo, el monto solicitado ni el monto máximo, y el portal solo muestra la nota «Se omiten los datos personales y los montos derivados de ellos.» | TC-14 |
| SC-22 | AC-15 | Dado un `consent_id` que no existe, cuando el asesor o un socio cotizan, entonces 422 «El consentimiento no existe.» sin consultar el buró ni guardar la cotización | TC-15 |
| SC-23 | AC-10 | Dada una revocación con una decisión abierta, cuando la recarga falla, entonces el detalle se vacía y el aviso pide buscarla de nuevo | TC-09 |
| SC-16 | AC-11 | Dado un rechazo nuevo o anterior a la auditoría, cuando se pide recalcularlo, entonces 409 «Los rechazos no guardan el puntaje del buró (HU-WEB-07), así que no se pueden recalcular.» sin evento de recálculo, y el portal muestra ese texto en lugar de «Recalcular» | TC-12 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-03, SC-10 | — |
| Alternate flows | YES | SC-02, SC-04, SC-05, SC-16 | — |
| Invalid input and errors | YES | SC-07, SC-14, SC-22, SC-23 | — |
| Limits and boundaries | YES | SC-05, SC-07 | Datos viejos; formatos en el límite |
| Related flows before and after | YES | SC-10, SC-11 | Cotizar después de la revocación |
| Several users, sessions, devices or roles | YES | SC-06, SC-11, SC-13, SC-18 | Otra réplica con la copia en memoria; consentimiento de otro titular |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-03, SC-05, SC-10, SC-21 | Edad a la fecha de la decisión; revocar dos veces |

## Test cases

### TC-01 · Detalle de una cotización y de un rechazo

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas, consentimiento semilla y analista de Solventa |
| Procedure | 1. Cotizar 100 millones con riesgo 2. 2. Cotizar 900 millones. 3. Consultar ambas |
| Expected result | Cotización: documento `******4050`, fecha, suma, riesgo, puntaje y versión `2026.11`; tres tiers con cinco factores; `no-store`. Rechazo: `inputs` solo con `******4050` y 900 millones, regla `UW-01`, motivo, 700 millones, versión `2026.11`, fecha y `replayable: false`. El documento completo no aparece |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_underwriting_audit_api.py::test_el_detalle_de_una_cotizacion_trae_entradas_reglas_y_desglose_con_el_documento_enmascarado; backend/tests/integration/test_underwriting_audit_api.py::test_el_detalle_de_un_rechazo_muestra_solo_lo_guardado_con_el_documento_enmascarado |
| Status | PASS |

### TC-02 · El recálculo coincide

| Field | Value |
|---|---|
| Covers | AC-03 |
| Kind | FUNCTIONAL |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | Cotización activa y cotización degradada (buró lento) |
| Procedure | 1. Recalcular las dos |
| Expected result | `matches: true`, sin diferencias, versión `2026.11` antes y ahora |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_underwriting_audit_api.py::test_el_recalculo_coincide_en_cotizaciones_activas_y_degradadas |
| Status | PASS |

### TC-03 · El recálculo detecta una regla distinta

| Field | Value |
|---|---|
| Covers | AC-04 |
| Kind | FUNCTIONAL |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | Una cotización de 100 millones con riesgo 2 |
| Procedure | 1. Cambiar en la prueba el multiplicador del riesgo 2 y `RULES_VERSION` y recalcular. 2. Bajar el cupo por puntaje a 50 millones y recalcular otra vez. 3. Quitar `premium` de `OFFERED_TIERS`; recalcular una copia sin `basic`. 4. Cambiar en una copia la prima anual y el límite de una cobertura y quitar otra. 5. Mover primas y un multiplicador 0,004 y luego 0,006 |
| Expected result | `matches: false`; la cotización lista el multiplicador de ocupación de 1,1 a 1,2 y la prima de cada tier; versiones `2026.11` y `2027.01`; con el cupo menor la decisión pasa de aprobada a `review_required:UW-01`; `premium.tier` desaparece y `basic.tier` aparece; se reportan `standard.annual_premium`, `standard.coverages.death.limit` y `standard.coverages.disability`; 0,004 no es diferencia y 0,006 sí |
| Tool or method | pytest con monkeypatch |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_underwriting_audit_api.py::test_el_recalculo_detecta_un_multiplicador_o_un_cupo_distintos; backend/tests/integration/test_underwriting_audit_api.py::test_el_recalculo_reporta_los_tiers_que_desaparecen_o_aparecen; backend/tests/integration/test_underwriting_audit_api.py::test_el_recalculo_compara_la_prima_anual_y_las_coberturas; backend/tests/integration/test_underwriting_audit_api.py::test_el_recalculo_tolera_diferencias_de_coma_flotante_de_hasta_medio_centavo |
| Status | PASS |

### TC-04 · Decisiones anteriores a la auditoría

| Field | Value |
|---|---|
| Covers | AC-05 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Una cotización y un rechazo sin versión ni entradas |
| Procedure | 1. Consultar ambos. 2. Recalcular la cotización |
| Expected result | `rules_version` nulo y `replayable: false` en ambos; la cotización da 409 «Decisión anterior a la auditoría: no se puede recalcular.» (el rechazo, en TC-12) |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_underwriting_audit_api.py::test_una_decision_anterior_a_la_auditoria_se_consulta_pero_no_se_recalcula |
| Status | PASS |

### TC-05 · Solo el analista de Solventa y entradas validadas

| Field | Value |
|---|---|
| Covers | AC-08, SR-01, SR-06 |
| Kind | SECURITY |
| Risk covered | THR-01, THR-05 |
| Level | INTEGRATION |
| Preconditions | Analista de Solventa, analista de Banco Aliado y asesor |
| Procedure | 1. Llamar al detalle, al recálculo y a la revocación con el analista del socio, el asesor y sin sesión. 2. Ids inexistentes. 3. Ids y documentos mal formados |
| Expected result | 403, 403 y 401; el consentimiento sigue GRANTED; 404, con `no-store` en el detalle; 422 |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_underwriting_audit_api.py::test_solo_el_analista_de_solventa_audita_y_sin_sesion_responde_401; backend/tests/integration/test_underwriting_audit_api.py::test_un_identificador_inexistente_da_404_y_uno_mal_formado_422; backend/tests/integration/test_underwriting_audit_api.py::test_el_404_del_detalle_responde_sin_cache |
| Status | PASS |

### TC-06 · Cotizaciones inmutables y migración

| Field | Value |
|---|---|
| Covers | AC-06, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas; base nueva para las migraciones |
| Procedure | 1. Ejecutar UPDATE y DELETE sobre `quotes`. 2. Subir una base vacía a la última versión. 3. Llevar una base a `0004`, quitar columnas y disparador, subir dos veces y revertir a `0004` |
| Expected result | La base rechaza el cambio y la fila sigue igual; la base nueva queda en `0005` con `quotes_no_change`; la subida deja cuatro columnas, ninguna `score` ni `birth_date` en `underwriting_rejections`, y el disparador, y la reversión los quita |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_underwriting_audit_api.py::test_la_base_impide_cambiar_o_borrar_una_cotizacion; backend/tests/integration/test_migrations.py::test_una_base_vacia_queda_en_la_ultima_migracion_con_todo_el_esquema; backend/tests/integration/test_migrations.py::test_la_migracion_de_auditoria_agrega_las_entradas_y_hace_inmutables_las_cotizaciones_desde_0004; backend/tests/integration/test_underwriting_api.py::test_tc_05_la_base_impide_cambiar_o_borrar_un_rechazo |
| Status | PASS |

### TC-07 · Revocación del consentimiento

| Field | Value |
|---|---|
| Covers | AC-07, AC-13, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-04, THR-02 |
| Level | INTEGRATION |
| Preconditions | Consentimiento semilla usado en una cotización; un rechazo del mismo documento; un consentimiento otorgado con OTP |
| Procedure | 1. Revocar. 2. Consultar la cotización y el rechazo y recalcular la cotización. 3. Cotizar con y sin `consent_id`. 4. Revocar otra vez. 5. Simular otra réplica con la copia en memoria y cotizar. 6. Recalcular y revocar con un consentimiento vigente y leer las cabeceras |
| Expected result | `revoked: 1`; `consent_revoked: true` sin documento, fecha ni puntaje; la cotización con `replayable: false`, `inputs: {}` y tiers solo con `tier` y `coverages` de `{id, name}` (en el JSON serializado no aparecen `monthly_premium`, `annual_premium`, `limit`, `deductible`, `insured_amount`, `occupation_risk`, `birth_date`, `score`, `document_masked`, «años», `payment_history` ni `breakdown`); el recálculo da 409 «El consentimiento del titular fue revocado: no se recalcula con sus datos personales.» con `no-store` y sin evento `DECISION_REPLAYED`; 422 «El consentimiento informado del cliente fue revocado.» y 422 sin id; `revoked: 0`; la otra réplica también da 422; el recálculo y la revocación responden `no-store` |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_underwriting_audit_api.py::test_la_revocacion_oculta_las_entradas_personales_e_impide_cotizar_con_ese_consentimiento; backend/tests/integration/test_underwriting_audit_api.py::test_con_el_consentimiento_revocado_el_detalle_de_una_cotizacion_solo_trae_planes_y_coberturas_sin_montos_y_no_se_recalcula; backend/tests/integration/test_underwriting_audit_api.py::test_el_recalculo_y_la_revocacion_responden_sin_cache; backend/tests/integration/test_partner_quote_api.py::test_partner_quote_con_consentimiento_revocado_responde_422; backend/tests/integration/test_underwriting_audit_api.py::test_un_consentimiento_recien_otorgado_tampoco_sirve_desde_la_copia_en_memoria_tras_revocarlo; backend/tests/integration/test_consent_flow.py::test_consent_flow_mandatory_otp_and_persistence_before_quote |
| Status | PASS |

### TC-08 · Eventos sin datos personales

| Field | Value |
|---|---|
| Covers | SR-05 |
| Kind | SECURITY |
| Risk covered | THR-01, THR-02 |
| Level | INTEGRATION |
| Preconditions | Una cotización |
| Procedure | 1. Consultar, recalcular y revocar. 2. Leer los registros |
| Expected result | `DECISION_VIEWED`, `DECISION_REPLAYED` y `CONSENT_REVOKED_BY_OPERATOR` con el `operator_ref` del analista; sin documento, documento enmascarado, fecha ni correo |
| Tool or method | pytest con caplog |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_underwriting_audit_api.py::test_la_revocacion_y_la_consulta_dejan_un_evento_sin_datos_personales |
| Status | PASS |

### TC-09 · Pantalla «Auditoría»

| Field | Value |
|---|---|
| Covers | AC-09, AC-10, AC-13, AC-14, SR-01, SR-02, SR-06 |
| Kind | SECURITY |
| Risk covered | THR-01, THR-02, THR-05 |
| Level | UNIT |
| Preconditions | Servidor simulado con `HttpTestingController` |
| Procedure | 1. Buscar un id inválido y uno con espacios al inicio. 2. Buscar una cotización y recalcular dos veces (coincide y no coincide). 3. Un 409 al recalcular. 4. Un rechazo nuevo y uno viejo con el consentimiento revocado. 5. Una cotización con el consentimiento revocado. 6. Revocar: documento inválido, cancelar y confirmar con espacios. 7. Revocar con una decisión abierta y con la recarga fallida. 8. Buscar otro id tras recalcular. 9. Ver la pestaña con y sin Solventa |
| Expected result | Sin llamada con el id inválido; el id con espacios se recorta y se busca (sin `maxlength` de 16); tablas y estado; el resultado y el error se anuncian; el rechazo muestra solo lo guardado y, en lugar de «Recalcular», el texto de por qué no se recalcula; la cotización con el consentimiento revocado muestra una sola nota «Se omiten los datos personales y los montos derivados de ellos.», los planes y los nombres de sus coberturas, sin «Primas por plan», entradas, desglose, primas ni montos, y, en lugar de «Recalcular», el motivo; la confirmación muestra el documento enmascarado y solo al confirmar se llama, con el documento recortado en el cuerpo; la decisión abierta se vuelve a pedir y, si falla, el detalle se vacía y aparece el aviso «La revocación quedó registrada. Busque la decisión de nuevo.»; el resultado del recálculo anterior se limpia al recargar y al buscar otro id; «Auditoría» solo con `solventa: true` |
| Tool or method | Vitest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | web/src/app/core/audit/audit.service.spec.ts::"consulta el detalle y recalcula una decisión desde experience"; web/src/app/core/audit/audit.service.spec.ts::"registra la revocación con el documento en el cuerpo y no en la URL"; web/src/app/core/audit/audit.service.spec.ts::"acepta solo identificadores de cotización o de rechazo"; web/src/app/features/auditoria/audit.component.spec.ts::"valida el identificador antes de consultar al servidor"; web/src/app/features/auditoria/audit.component.spec.ts::"informa el error del servidor al recalcular"; web/src/app/features/auditoria/audit.component.spec.ts::"un rechazo muestra solo lo guardado y explica por qué no se recalcula en lugar del botón"; web/src/app/features/auditoria/audit.component.spec.ts::"un rechazo anterior a la auditoría con el consentimiento revocado omite el documento, la regla, el motivo y los montos"; web/src/app/features/auditoria/audit.component.spec.ts::"tras revocar vuelve a pedir la decisión abierta para omitir sus entradas personales"; web/src/app/features/auditoria/audit.component.spec.ts::"un espacio pegado no corta el identificador: no hay maxlength de 16 y se recorta antes de validar"; web/src/app/features/auditoria/audit.component.spec.ts::"con el consentimiento revocado una cotización solo muestra la nota, los planes y los nombres de las coberturas, sin primas ni montos"; web/src/app/features/auditoria/audit.component.spec.ts::"buscar otro identificador limpia el resultado del recálculo anterior"; web/src/app/features/auditoria/audit.component.spec.ts::"tras revocar limpia el recálculo anterior y, si la recarga falla, vacía el detalle y avisa"; web/src/app/app.component.spec.ts::"FE-WEB-08 la pestaña Auditoría solo aparece para el analista de Solventa y lleva a /auditoria"; web/src/app/core/guards/solventa.guard.spec.ts::"devuelve a gestión al analista de un socio"; web/src/app/app.routes.spec.ts::"la ruta /auditoria exige sesión, rol analista y ser de Solventa y carga la pantalla bajo demanda" |
| Status | PASS |

### TC-10 · Accesibilidad automatizada de «Auditoría»

| Field | Value |
|---|---|
| Covers | QR-01, AC-10 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | Pantalla con una cotización cargada |
| Procedure | 1. Revisar `caption`, `th scope="col"` y `th scope="row"`. 2. Etiquetas, textos de ayuda, `aria-invalid`, `aria-errormessage` y el error también en `aria-describedby`. 3. Región `aria-live` tras recalcular y revocar. 4. Foco en el panel de confirmación y de vuelta al botón al cancelar y al confirmar |
| Expected result | Todo presente; el foco llega al panel y vuelve al botón «Registrar revocación del consentimiento» |
| Tool or method | Vitest con jsdom |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | web/src/app/features/auditoria/audit.component.spec.ts::"valida el identificador antes de consultar al servidor"; web/src/app/features/auditoria/audit.component.spec.ts::"el campo del documento tiene texto de ayuda y su error en aria-describedby"; web/src/app/features/auditoria/audit.component.spec.ts::"muestra las entradas, la versión de reglas, la decisión y el desglose por tier en tablas accesibles"; web/src/app/features/auditoria/audit.component.spec.ts::"Recalcular anuncia en aria-live si coincide o la lista de diferencias"; web/src/app/features/auditoria/audit.component.spec.ts::"la revocación del consentimiento pide confirmación, maneja el foco y solo al confirmar llama al servidor" |
| Status | PASS |

### TC-11 · Prueba en el portal

| Field | Value |
|---|---|
| Covers | AC-01, AC-03, AC-07, AC-09, QR-01 |
| Kind | QUALITY |
| Risk covered | THR-01, THR-02 |
| Level | E2E |
| Preconditions | Backend y portal locales migrados a `0005`, con los usuarios semilla |
| Procedure | 1. Cotizar como asesor, una vez aprobada y otra por encima del cupo. 2. Entrar como analista de Solventa, abrir «Auditoría», buscar la cotización y recalcular; buscar el rechazo. 3. Registrar la revocación del consentimiento y volver a consultar. 4. Intentar cotizar de nuevo como asesor. 5. Entrar como analista de Banco Aliado y abrir `/auditoria`. 6. Recorrer la pantalla solo con teclado y con NVDA |
| Expected result | «Coincide»; el rechazo no tiene «Recalcular» y explica que no guarda el puntaje del buró; tras revocar se omiten documento, fecha y puntaje y la cotización da 422; el analista del socio no ve la pestaña y vuelve a «Pólizas»; el lector anuncia tablas, campos, resultados y el panel de confirmación |
| Tool or method | Prueba manual |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

### TC-12 · El rechazo no guarda el puntaje y no se recalcula

| Field | Value |
|---|---|
| Covers | AC-11, SR-07 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas migrado; un rechazo nuevo y uno anterior a la auditoría |
| Procedure | 1. Cotizar por encima del cupo y leer la fila y las columnas de `underwriting_rejections`. 2. Recalcular el rechazo nuevo y el viejo. 3. En el portal, abrir un rechazo |
| Expected result | La fila guarda `rules_version` `2026.11` y la tabla no tiene `score` ni `birth_date`; HU-WEB-07 TC-03 sigue verificando que el rechazo no guarda el puntaje; 409 «Los rechazos no guardan el puntaje del buró (HU-WEB-07), así que no se pueden recalcular.» sin evento `DECISION_REPLAYED`; el portal muestra ese texto y no el botón «Recalcular» |
| Tool or method | pytest y Vitest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_underwriting_audit_api.py::test_el_rechazo_no_guarda_el_puntaje_ni_la_fecha_de_nacimiento; backend/tests/integration/test_underwriting_audit_api.py::test_el_recalculo_de_un_rechazo_responde_409_porque_no_guarda_el_puntaje; backend/tests/integration/test_underwriting_api.py::test_tc_03_cada_rechazo_queda_registrado_sin_el_puntaje; backend/tests/integration/test_migrations.py::test_la_migracion_de_auditoria_agrega_las_entradas_y_hace_inmutables_las_cotizaciones_desde_0004; web/src/app/features/auditoria/audit.component.spec.ts::"un rechazo muestra solo lo guardado y explica por qué no se recalcula en lugar del botón" |
| Status | PASS |

### TC-13 · El consentimiento queda atado al documento

| Field | Value |
|---|---|
| Covers | AC-12, AC-07, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | INTEGRATION |
| Preconditions | Consentimiento vigente de otro titular ficticio; buró reemplazado por un doble que cuenta las llamadas |
| Procedure | 1. Cotizar como asesor con el `consent_id` del otro titular. 2. Cotizar como socio con ese `consent_id`. 3. Guardar una cotización vieja con ese consentimiento ajeno, consultarla, revocar los consentimientos del documento y consultarla otra vez |
| Expected result | 422 «El consentimiento no corresponde al cliente.» en los dos canales, cero llamadas al buró y ninguna cotización, rechazo ni registro de idempotencia guardados; la cotización vieja pasa de `consent_revoked: false` a `true`, sin fecha ni puntaje, y su recálculo da 409 |
| Tool or method | pytest con un doble del buró |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_underwriting_audit_api.py::test_el_asesor_con_el_consentimiento_de_otro_documento_recibe_422_sin_consultar_el_buro_ni_guardar; backend/tests/integration/test_partner_quote_api.py::test_partner_quote_con_consentimiento_de_otro_documento_responde_422_sin_consultar_el_buro; backend/tests/integration/test_underwriting_audit_api.py::test_una_cotizacion_con_un_consentimiento_ajeno_oculta_los_datos_si_el_documento_solo_tiene_revocados |
| Status | PASS |

### TC-14 · Un rechazo con el consentimiento revocado no revela la franja

| Field | Value |
|---|---|
| Covers | AC-14, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Consentimiento semilla; un rechazo de 900 millones con ese consentimiento |
| Procedure | 1. Revocar los consentimientos del documento. 2. Consultar el rechazo. 3. En el portal, abrir un rechazo con el consentimiento revocado |
| Expected result | `consent_revoked: true`, `replayable: false`, versión y fecha; `inputs` igual a `{document_masked: null}`; `outcome` sin `rule_id`, `reason` ni `max_approvable_amount`; la respuesta no contiene la regla, el motivo, el monto máximo, el monto solicitado ni el documento. El portal muestra solo la nota «Se omiten los datos personales y los montos derivados de ellos.», sin documento, monto solicitado, regla, motivo ni montos, y sin «Recalcular» |
| Tool or method | pytest y Vitest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_underwriting_audit_api.py::test_con_el_consentimiento_revocado_un_rechazo_omite_la_regla_el_motivo_y_los_montos; web/src/app/features/auditoria/audit.component.spec.ts::"un rechazo anterior a la auditoría con el consentimiento revocado omite el documento, la regla, el motivo y los montos" |
| Status | PASS |

### TC-15 · Un consentimiento inexistente no autoriza cotizar

| Field | Value |
|---|---|
| Covers | AC-15, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | INTEGRATION |
| Preconditions | Buró reemplazado por un doble que cuenta las llamadas |
| Procedure | 1. Cotizar como asesor con un `consent_id` que no existe. 2. Cotizar como socio con ese `consent_id` |
| Expected result | 422 «El consentimiento no existe.» en los dos canales, cero llamadas al buró y ninguna cotización, rechazo ni registro de idempotencia guardados |
| Tool or method | pytest con un doble del buró |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_underwriting_audit_api.py::test_el_asesor_con_un_consentimiento_inexistente_recibe_422_sin_consultar_el_buro_ni_guardar; backend/tests/integration/test_partner_quote_api.py::test_partner_quote_con_un_consentimiento_inexistente_responde_422_sin_consultar_el_buro |
| Status | PASS |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-05 | — |
| User without permissions | YES | TC-05 | — |
| Access to another user's resources | YES | TC-05, TC-13, TC-15 | El analista de un socio no audita; el consentimiento de otro titular no autoriza cotizar |
| Cross-tenant access | YES | TC-05 | — |
| Malicious input | YES | TC-05, TC-09 | Ids y documentos mal formados |
| Expired tokens | NO | — | No cambia la sesión |
| Revoked sessions | NO | — | La revocación es del consentimiento, cubierta en TC-07 |
| Tampered data | YES | TC-06, TC-03 | UPDATE y DELETE rechazados; el recálculo detecta cambios de regla |
| Wrong keys | NO | — | Sin llaves nuevas |
| External service failures | YES | TC-02, TC-09 | Buró lento (cotización degradada); error del servidor en la pantalla |
| Race conditions | YES | TC-07 | La revocación bloquea las filas; revocar dos veces es idempotente |
| Information exposure | YES | TC-01, TC-07, TC-08, TC-12, TC-13, TC-14 | El rechazo no guarda el puntaje ni la fecha de nacimiento; sin desglose con el consentimiento revocado; el rechazo revocado sin regla, motivo ni montos |
| Secrets in code | NO | — | Sin secretos |
| Insecure configuration | NO | — | Sin configuración nueva |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas |

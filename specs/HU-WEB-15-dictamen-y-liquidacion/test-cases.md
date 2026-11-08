# Test cases · HU-WEB-15

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | UC-01 | Dado el analista, cuando abre la bandeja, entonces ve los siniestros más recientes con radicado, póliza, fecha y estado | TC-01, TC-10 |
| SC-02 | UC-01 | Dado un siniestro, cuando abre el expediente, entonces ve póliza, amparo, asegurado enmascarado, descripción, fotos con ubicación y hora, e historial | TC-01, TC-10 |
| SC-03 | AC-03 | Dada una foto que no coincide con su huella, cuando se muestra, entonces sale alterada; sin S3 queda sin verificar | TC-02 |
| SC-04 | AC-17 | Dada una foto con ubicación simulada u hora sin confirmar, cuando se muestra, entonces el expediente lo advierte | TC-02, TC-10 |
| SC-05 | AC-07 | Dado el analista, cuando toma el caso con un perito, entonces queda responsable, en evaluación y con el perito | TC-03, TC-10 |
| SC-06 | AC-05 | Dado un valor mayor que el amparo, cuando liquida, entonces responde 422 y el portal no deja registrar | TC-03, TC-10 |
| SC-07 | AC-08 | Dada una liquidación aprobada, cuando se registra, entonces se paga a cada beneficiario por su porcentaje con recibo | TC-04, TC-10 |
| SC-08 | AC-20 | Dado un siniestro ya decidido, cuando se intenta otro dictamen, entonces responde 409 | TC-03 |
| SC-09 | AC-08 | Dado un pago rechazado, cuando se reintenta, entonces no repite el aprobado | TC-04 |
| SC-10 | UC-02 | Dado un sismo de 7,1, cuando llega el siniestro paramétrico, entonces se liquida y paga solo; con 5,0 queda para un analista | TC-05, TC-10 |
| SC-11 | UC-03 | Dada una emisión sin beneficiarios o con porcentajes que no suman 100, cuando se emite, entonces responde 422 y el portal no deja emitir | TC-06, TC-11 |
| SC-12 | AC-10 | Dado un endoso, cuando se registra, entonces sube la versión y la carátula nueva lo refleja | TC-06, TC-10 |
| SC-13 | AC-11 | Dada una cancelación, cuando se registra, entonces devuelve la prima proporcional y la póliza no admite más operaciones; un siniestro de muerte liquidado la termina | TC-04, TC-06, TC-10 |
| SC-14 | UC-05 | Dada una póliza con decisiones, cuando se abre la auditoría, entonces salen en orden con responsable y justificación | TC-06, TC-10 |
| SC-15 | AC-13 | Dado un mes, cuando se pide el reporte, entonces sale en CSV y PDF sin datos personales | TC-06, TC-10 |
| SC-16 | UC-04 | Dado un usuario de un socio, cuando pide un siniestro, una póliza o un reporte ajeno, entonces recibe 404 o 403 | TC-07, TC-10 |
| SC-17 | UC-06 | Dado un usuario del portal, cuando cierra sesión, entonces su refresh ya no renueva; y dos renovaciones a la vez hacen una sola petición | TC-08, TC-11 |
| SC-18 | AC-06 | Dado un dictamen, cuando se intenta cambiarlo en la base, entonces la base lo rechaza | TC-09 |
| SC-19 | AC-01 | Dados 10.000 siniestros, cuando se consultan la bandeja y el expediente, entonces el p95 queda por debajo de 150 ms | TC-12 |
| SC-20 | AC-02 | Dado el expediente con teclado y lector de pantalla, cuando se recorre, entonces las fotos tienen texto alternativo y el dictamen se usa con teclado | TC-13 |
| SC-21 | UC-01 | Dado un asegurado, cuando reporta el siniestro desde la app | OUT OF SCOPE: es HU-MOV-08, HU-MOV-09 y HU-MOV-10 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-02, SC-05, SC-07 | — |
| Alternate flows | YES | SC-10, SC-12, SC-13 | — |
| Invalid input and errors | YES | SC-03, SC-06, SC-11 | — |
| Limits and boundaries | YES | SC-06, SC-19 | — |
| Related flows before and after | YES | SC-15, SC-21 | — |
| Several users, sessions, devices or roles | YES | SC-16, SC-17 | — |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-08, SC-09, SC-17 | — |

## Test cases

### TC-01 · Bandeja y expediente

| Field | Value |
|---|---|
| Covers | AC-01, AC-02 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | PostgreSQL y Floci con cuatro siniestros, uno con dos fotos |
| Procedure | 1. Pedir la bandeja. 2. Filtrar por estado. 3. Abrir el expediente |
| Expected result | Los siniestros en orden con sus campos; el expediente con póliza, amparo, asegurado enmascarado, descripción, fotos con enlace e historial |
| Tool or method | pytest backend/tests/integration/test_claims_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_claims_api.py::test_tc_01_la_bandeja_lista_los_siniestros_mas_recientes_con_sus_campos; backend/tests/integration/test_claims_api.py::test_tc_02_el_expediente_trae_poliza_asegurado_descripcion_fotos_e_historial |
| Status | PASS |

### TC-02 · Huella de las fotos y datos protegidos

| Field | Value |
|---|---|
| Covers | AC-03, AC-17, SR-05, SR-06, SR-09 |
| Kind | SECURITY |
| Risk covered | THR-04, THR-08 |
| Level | INTEGRATION |
| Preconditions | Una foto íntegra y otra cambiada en S3, con ubicación simulada |
| Procedure | 1. Abrir el expediente. 2. Repetir con S3 caído |
| Expected result | Íntegra y alterada; advertencias de ubicación y hora; sin S3 quedan sin verificar y sin enlace; el documento completo no aparece |
| Tool or method | pytest backend/tests/integration/test_claims_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_claims_api.py::test_tc_03_la_foto_que_no_coincide_con_su_huella_sale_alterada; backend/tests/integration/test_claims_api.py::test_tc_03_sin_s3_las_fotos_quedan_sin_verificar; backend/tests/integration/test_claims_api.py::test_tc_02_el_expediente_trae_poliza_asegurado_descripcion_fotos_e_historial |
| Status | PASS |

### TC-03 · Asignación, dictamen y amparo

| Field | Value |
|---|---|
| Covers | AC-04, AC-05, AC-07, AC-20, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Siniestros abiertos de pólizas al día y sin pagar |
| Procedure | 1. Tomar el caso con perito. 2. Rechazar sin justificación suficiente. 3. Rechazar con justificación. 4. Intentar otro dictamen. 5. Liquidar por encima del amparo y sin valor. 6. Liquidar una póliza sin pagar |
| Expected result | Responsable y perito; 422; rechazado; 409; 422; 422 |
| Tool or method | pytest backend/tests/integration/test_claims_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_claims_api.py::test_tc_04_el_analista_toma_el_caso_y_asigna_un_perito; backend/tests/integration/test_claims_api.py::test_tc_05_el_rechazo_exige_justificacion_y_no_se_puede_decidir_dos_veces; backend/tests/integration/test_claims_api.py::test_tc_06_la_liquidacion_no_supera_el_amparo; backend/tests/integration/test_claims_api.py::test_tc_08_no_se_liquida_una_poliza_sin_pagar |
| Status | PASS |

### TC-04 · Pago a los beneficiarios y terminación

| Field | Value |
|---|---|
| Covers | AC-08, AC-11, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Póliza de vida con beneficiarios 60/40; otra con un beneficiario cuyo pago se rechaza |
| Procedure | 1. Liquidar el siniestro de muerte. 2. Liquidar con un pago rechazado y reintentar |
| Expected result | Pagos de 60 % y 40 % con recibo, siniestro pagado y póliza terminada; el reintento no repite el aprobado |
| Tool or method | pytest backend/tests/integration/test_claims_api.py; backend/tests/unit/test_claims_rules.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_claims_api.py::test_tc_07_liquidar_paga_a_los_beneficiarios_y_termina_la_poliza_de_muerte; backend/tests/integration/test_claims_api.py::test_tc_09_el_pago_rechazado_se_reintenta_sin_repetir_los_aprobados; backend/tests/unit/test_claims_rules.py::test_el_pago_se_reparte_por_porcentaje_y_el_ultimo_recibe_el_redondeo |
| Status | PASS |

### TC-05 · Liquidación paramétrica

| Field | Value |
|---|---|
| Covers | AC-12, SR-07 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | INTEGRATION |
| Preconditions | Pólizas al día |
| Procedure | 1. Registrar un sismo de 7,1. 2. Registrar uno de 5,0. 3. Probar la regla con valores límite y datos inválidos |
| Expected result | El primero queda pagado por la regla `SISMO-6.5`; el segundo queda reportado; la regla solo aplica desde 6,5 |
| Tool or method | pytest backend/tests/integration/test_claims_api.py; backend/tests/unit/test_claims_rules.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_claims_api.py::test_tc_10_el_siniestro_parametrico_que_cumple_la_regla_se_liquida_solo; backend/tests/unit/test_claims_rules.py::test_la_regla_parametrica_solo_aplica_desde_el_umbral |
| Status | PASS |

### TC-06 · Beneficiarios, endosos, cancelación, auditoría y reportes

| Field | Value |
|---|---|
| Covers | AC-09, AC-10, AC-11, AC-13, AC-14, SR-06 |
| Kind | SECURITY |
| Risk covered | THR-08 |
| Level | INTEGRATION |
| Preconditions | Póliza al día con beneficiarios y un cobro aprobado |
| Procedure | 1. Ver el detalle. 2. Emitir con beneficiarios inválidos. 3. Endoso de beneficiarios y de suma. 4. Cancelar. 5. Ver la auditoría. 6. Descargar el reporte del mes |
| Expected result | Beneficiarios enmascarados; 422; versión 2 y carátula nueva; suma solo baja; devolución y 409 al repetir; auditoría en orden; CSV y PDF sin datos personales |
| Tool or method | pytest backend/tests/integration/test_policy_operations_api.py; backend/tests/unit/test_claims_rules.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_operations_api.py::test_tc_14_el_detalle_muestra_los_beneficiarios_con_el_documento_enmascarado; backend/tests/integration/test_policy_operations_api.py::test_tc_15_la_emision_exige_beneficiarios_validos; backend/tests/integration/test_policy_operations_api.py::test_tc_16_el_endoso_de_beneficiarios_sube_la_version_y_emite_otra_caratula; backend/tests/integration/test_policy_operations_api.py::test_tc_17_el_endoso_de_suma_solo_la_baja; backend/tests/integration/test_policy_operations_api.py::test_tc_18_cancelar_devuelve_la_prima_proporcional_y_cierra_la_poliza; backend/tests/integration/test_policy_operations_api.py::test_tc_19_la_auditoria_reconstruye_las_decisiones_en_orden; backend/tests/integration/test_policy_operations_api.py::test_tc_22_el_reporte_mensual_sale_en_csv_y_pdf_sin_datos_personales |
| Status | PASS |

### TC-07 · Alcance por socio y roles

| Field | Value |
|---|---|
| Covers | AC-18, AC-19, SR-01, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-01, THR-05 |
| Level | INTEGRATION |
| Preconditions | Siniestros y pólizas de varios socios; usuarios de socio activo y revocado |
| Procedure | 1. Como usuario de Banco Aliado, listar, abrir y decidir siniestros ajenos. 2. Operar una póliza ajena y pedir el reporte. 3. Como asesor, sin sesión y como socio revocado |
| Expected result | Solo lo suyo; 404 igual al inexistente; 403 en el reporte; 403, 401 y 403 |
| Tool or method | pytest backend/tests/integration/test_claims_api.py; backend/tests/integration/test_policy_operations_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_claims_api.py::test_tc_11_el_usuario_de_un_socio_solo_ve_y_decide_los_de_su_socio; backend/tests/integration/test_claims_api.py::test_tc_12_solo_el_rol_analyst_con_socio_habilitado; backend/tests/integration/test_policy_operations_api.py::test_tc_21_el_usuario_de_un_socio_no_opera_polizas_de_otros_ni_descarga_reportes |
| Status | PASS |

### TC-08 · Cierre de sesión con revocación

| Field | Value |
|---|---|
| Covers | AC-15, SR-08 |
| Kind | SECURITY |
| Risk covered | THR-07 |
| Level | INTEGRATION |
| Preconditions | Analista con sesión |
| Procedure | 1. Renovar. 2. Reusar el refresh usado. 3. Cerrar sesión con el nuevo. 4. Renovar con él. 5. Cerrar sesión con un texto que no es token |
| Expected result | 200, 401, 204, 401 y 204 |
| Tool or method | pytest backend/tests/integration/test_policy_operations_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_operations_api.py::test_tc_23_cerrar_sesion_revoca_el_refresh_y_un_refresh_usado_no_sirve |
| Status | PASS |

### TC-09 · Registros de solo inserción

| Field | Value |
|---|---|
| Covers | AC-06, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | Un siniestro con historial y una póliza cancelada |
| Procedure | 1. UPDATE y DELETE sobre `claim_events`. 2. UPDATE y DELETE sobre `policy_events` |
| Expected result | La base rechaza las cuatro sentencias |
| Tool or method | pytest backend/tests/integration/test_claims_api.py; backend/tests/integration/test_policy_operations_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_claims_api.py::test_tc_13_el_historial_no_se_puede_cambiar_ni_borrar_en_la_base; backend/tests/integration/test_policy_operations_api.py::test_tc_20_las_operaciones_de_la_poliza_no_se_cambian_en_la_base |
| Status | PASS |

### TC-10 · Recorrido completo en el portal

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-03, AC-04, AC-05, AC-06, AC-07, AC-08, AC-09, AC-10, AC-11, AC-12, AC-13, AC-14, AC-17, AC-18 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | E2E |
| Preconditions | Portal, backend, PostgreSQL y Floci locales con cuatro siniestros |
| Procedure | Bandeja, expediente, tomar el caso, liquidar por encima y dentro del amparo, rechazar, ver el paramétrico, endoso, auditoría, cancelación, reporte, cierre de sesión, usuario de socio, asesor y celular de 320 px |
| Expected result | Cada paso como dicen los criterios |
| Tool or method | Playwright con Chrome y axe-core 4.12.1 |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | Prueba de punta a punta del 2026-11-06: 33 de 33 verificaciones, incluidas cuatro auditorías WCAG 2.2 AA sin violaciones (bandeja, expediente, detalle con endoso y expediente a 320 px) |
| Status | PASS |

### TC-11 · Portal: pantallas y servicios

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-03, AC-04, AC-05, AC-06, AC-07, AC-08, AC-09, AC-10, AC-11, AC-13, AC-14, AC-15, AC-16 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Componentes con el servidor simulado |
| Procedure | Bandeja, expediente, dictamen, operaciones, reporte, editor de beneficiarios, cotizador, servicios de pólizas y pagos, y renovación única |
| Expected result | Todos los casos pasan |
| Tool or method | npm test |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | web/src/app/features/siniestros/claim-file.component.spec.ts; web/src/app/features/siniestros/claims-list.component.spec.ts; web/src/app/features/gestion/policy-operations.component.spec.ts; web/src/app/features/shared/beneficiaries-editor.component.spec.ts; web/src/app/core/policies/policy.service.spec.ts; web/src/app/core/auth/auth.service.spec.ts::"HU-WEB-15 (AC-15) dos renovaciones a la vez comparten una sola petición" |
| Status | PASS |

### TC-12 · Tiempo de respuesta de la bandeja y el expediente

| Field | Value |
|---|---|
| Covers | QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | PERFORMANCE |
| Preconditions | PostgreSQL local con 10.000 siniestros de tres dueños |
| Procedure | 200 consultas mezcladas de bandeja (analista y socio) y expediente |
| Expected result | p95 por debajo de 150 ms |
| Tool or method | pytest backend/tests/integration/test_policy_search_performance.py |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_policy_search_performance.py::test_tc_hu_web_15_el_p95_de_la_bandeja_y_el_expediente_queda_por_debajo_de_150_ms; p50 10,9 ms y p95 14,6 ms el 2026-11-06 |
| Status | PASS |

### TC-13 · Accesibilidad con lector de pantalla

| Field | Value |
|---|---|
| Covers | QR-02 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | Portal con Narrador o NVDA |
| Procedure | 1. Activar el lector de pantalla. 2. Abrir Siniestros con Tab y Enter. 3. Abrir un expediente y escuchar el título, las fotos y su huella. 4. Llenar el dictamen solo con teclado. 5. Registrar y escuchar el resultado |
| Expected result | Las fotos se leen con fecha y ubicación, la huella y las advertencias se leen como texto, el dictamen se llena con teclado y los resultados se anuncian |
| Tool or method | Narrador o NVDA, teclado |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | PENDING: axe-core 4.12.1 sin violaciones WCAG 2.2 AA en bandeja, expediente, detalle con endoso y 320 px (TC-10); falta la pasada con lector de pantalla |
| Status | PENDING |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-07 | — |
| User without permissions | YES | TC-07 | — |
| Access to another user's resources | YES | TC-07 | — |
| Cross-tenant access | YES | TC-07 | — |
| Malicious input | YES | TC-03, TC-06, TC-11 | — |
| Expired tokens | NO | — | La validación del vencimiento no cambia |
| Revoked sessions | YES | TC-08 | — |
| Tampered data | YES | TC-02, TC-09 | — |
| Wrong keys | NO | — | Sin llaves nuevas |
| External service failures | YES | TC-02, TC-04 | — |
| Race conditions | YES | TC-03, TC-04 | — |
| Information exposure | YES | TC-02, TC-06 | — |
| Secrets in code | NO | — | Sin secretos nuevos |
| Insecure configuration | YES | TC-02 | — |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas |

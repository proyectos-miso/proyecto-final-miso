# Definition of Done and security gate · HU-WEB-15

| Field | Value |
|---|---|
| Security status | SECURITY_VERIFIED |
| Evaluated on | 2026-11-15 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-WEB-15-dictamen-y-liquidacion |

<!-- Status: IN_PROGRESS, IMPLEMENTED, SECURITY_TESTS_PENDING, SECURITY_REVIEW_PENDING, RISK_ACCEPTED,
     SECURITY_VERIFIED or BLOCKED. The validator computes the status the evidence supports and rejects a
     higher declared one. An agent may prepare evidence; it never signs reviews or accepts risk.
     With "requireSignatures": false in sdd/config.json, the merge request approval is the security review
     and risk decisions recorded in the spec count as accepted; the two signature tables are optional. -->

## Definition of Done

- [x] Acceptance criteria implemented, each covered by a passing test case.
- [x] Security requirements mitigated by an applicable control and verified by a security test case.
- [x] No required control MISSING.
- [x] No secret in the code, logs, artifacts or this package.
- [x] New or updated dependencies evaluated.
- [x] Personal data handling reviewed (complete lifecycle, nothing UNKNOWN).
- [x] MEDIUM or higher residual risks mitigated or accepted by an authorized person.
- [x] Security review approved by a person other than the author.
- [x] Closeout: every acceptance criterion MET with evidence.
- [x] `python sdd/tools/sdd.py spec validate` reports no errors.

## Security reviews

| Reviewer | Role | Date | Scope | Result |
|---|---|---|---|---|

## Risk acceptance

| Risk | Decision | Approved by | Role | Date | Expires |
|---|---|---|---|---|---|

## Evidence

| ID | Evidence | Supports | Location | Status |
|---|---|---|---|---|
| EV-01 | Pruebas de siniestros: expediente, huella, dictamen, pago, paramétrico, alcance e inmutabilidad | SR-01, SR-02, SR-03, SR-04, SR-05, SR-07, SR-09, CTL-01, CTL-02, CTL-03, CTL-04, CTL-05, CTL-07, CTL-09, TC-01, TC-02, TC-03, TC-04, TC-05, TC-07, TC-09 | backend/tests/integration/test_claims_api.py | AVAILABLE |
| EV-02 | Pruebas de póliza, reportes y cierre de sesión | SR-06, SR-08, CTL-06, CTL-08, TC-06, TC-08 | backend/tests/integration/test_policy_operations_api.py | AVAILABLE |
| EV-03 | Reglas del pago, la devolución y la regla paramétrica | TC-04, TC-05 | backend/tests/unit/test_claims_rules.py | AVAILABLE |
| EV-04 | Pruebas del portal | TC-11 | web/src/app/features/siniestros/ | AVAILABLE |
| EV-05 | Tiempo de respuesta | QR-01, TC-12 | backend/tests/integration/test_policy_search_performance.py | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | TC-01: backend/tests/integration/test_claims_api.py::test_tc_01_la_bandeja_lista_los_siniestros_mas_recientes_con_sus_campos; TC-11: web/src/app/features/siniestros/claims-list.component.spec.ts; TC-10: prueba de punta a punta con Playwright del 2026-11-06, 33 de 33 verificaciones |
| AC-02 | MET | TC-01: backend/tests/integration/test_claims_api.py::test_tc_02_el_expediente_trae_poliza_asegurado_descripcion_fotos_e_historial; TC-11: web/src/app/features/siniestros/claim-file.component.spec.ts; TC-10: prueba de punta a punta con Playwright del 2026-11-06, 33 de 33 verificaciones |
| AC-03 | MET | TC-02: backend/tests/integration/test_claims_api.py::test_tc_03_la_foto_que_no_coincide_con_su_huella_sale_alterada; backend/tests/integration/test_claims_api.py::test_tc_03_sin_s3_las_fotos_quedan_sin_verificar; TC-11: web/src/app/features/siniestros/claim-file.component.spec.ts |
| AC-04 | MET | TC-03: backend/tests/integration/test_claims_api.py::test_tc_05_el_rechazo_exige_justificacion_y_no_se_puede_decidir_dos_veces; TC-11: web/src/app/features/siniestros/claim-file.component.spec.ts |
| AC-05 | MET | TC-03: backend/tests/integration/test_claims_api.py::test_tc_06_la_liquidacion_no_supera_el_amparo |
| AC-06 | MET | TC-09: backend/tests/integration/test_claims_api.py::test_tc_13_el_historial_no_se_puede_cambiar_ni_borrar_en_la_base; TC-11: web/src/app/features/siniestros/claim-file.component.spec.ts |
| AC-07 | MET | TC-03: backend/tests/integration/test_claims_api.py::test_tc_04_el_analista_toma_el_caso_y_asigna_un_perito |
| AC-08 | MET | TC-04: backend/tests/integration/test_claims_api.py::test_tc_07_liquidar_paga_a_los_beneficiarios_y_termina_la_poliza_de_muerte; backend/tests/integration/test_claims_api.py::test_tc_09_el_pago_rechazado_se_reintenta_sin_repetir_los_aprobados; backend/tests/unit/test_claims_rules.py::test_el_pago_se_reparte_por_porcentaje_y_el_ultimo_recibe_el_redondeo |
| AC-09 | MET | TC-06: backend/tests/integration/test_policy_operations_api.py::test_tc_14_el_detalle_muestra_los_beneficiarios_con_el_documento_enmascarado; backend/tests/integration/test_policy_operations_api.py::test_tc_15_la_emision_exige_beneficiarios_validos; TC-11: web/src/app/features/shared/beneficiaries-editor.component.spec.ts |
| AC-10 | MET | TC-06: backend/tests/integration/test_policy_operations_api.py::test_tc_16_el_endoso_de_beneficiarios_sube_la_version_y_emite_otra_caratula; backend/tests/integration/test_policy_operations_api.py::test_tc_17_el_endoso_de_suma_solo_la_baja; TC-11: web/src/app/features/gestion/policy-operations.component.spec.ts |
| AC-11 | MET | TC-06: backend/tests/integration/test_policy_operations_api.py::test_tc_18_cancelar_devuelve_la_prima_proporcional_y_cierra_la_poliza; TC-04: backend/tests/integration/test_claims_api.py::test_tc_07_liquidar_paga_a_los_beneficiarios_y_termina_la_poliza_de_muerte; TC-11: web/src/app/features/gestion/policy-operations.component.spec.ts |
| AC-12 | MET | TC-05: backend/tests/integration/test_claims_api.py::test_tc_10_el_siniestro_parametrico_que_cumple_la_regla_se_liquida_solo; backend/tests/unit/test_claims_rules.py::test_la_regla_parametrica_solo_aplica_desde_el_umbral |
| AC-13 | MET | TC-06: backend/tests/integration/test_policy_operations_api.py::test_tc_22_el_reporte_mensual_sale_en_csv_y_pdf_sin_datos_personales; TC-11: web/src/app/core/policies/policy.service.spec.ts |
| AC-14 | MET | TC-06: backend/tests/integration/test_policy_operations_api.py::test_tc_19_la_auditoria_reconstruye_las_decisiones_en_orden; TC-11: web/src/app/features/gestion/policy-operations.component.spec.ts |
| AC-15 | MET | TC-08: backend/tests/integration/test_policy_operations_api.py::test_tc_23_cerrar_sesion_revoca_el_refresh_y_un_refresh_usado_no_sirve; TC-11: web/src/app/core/auth/auth.service.spec.ts::"HU-WEB-15 (AC-15) dos renovaciones a la vez comparten una sola petición" |
| AC-16 | MET | TC-11: web/src/app/core/policies/policy.service.spec.ts, que prueba PolicyService y PaymentService |
| AC-17 | MET | TC-02: backend/tests/integration/test_claims_api.py::test_tc_02_el_expediente_trae_poliza_asegurado_descripcion_fotos_e_historial; TC-13: la advertencia de ubicación simulada se lee como texto en el expediente, 2026-11-15 |
| AC-18 | MET | TC-07: backend/tests/integration/test_claims_api.py::test_tc_11_el_usuario_de_un_socio_solo_ve_y_decide_los_de_su_socio; backend/tests/integration/test_policy_operations_api.py::test_tc_21_el_usuario_de_un_socio_no_opera_polizas_de_otros_ni_descarga_reportes |
| AC-19 | MET | TC-07: backend/tests/integration/test_claims_api.py::test_tc_12_solo_el_rol_analyst_con_socio_habilitado |
| AC-20 | MET | TC-03: backend/tests/integration/test_claims_api.py::test_tc_05_el_rechazo_exige_justificacion_y_no_se_puede_decidir_dos_veces |
| QR-01 | MET | TC-12: backend/tests/integration/test_policy_search_performance.py::test_tc_hu_web_15_el_p95_de_la_bandeja_y_el_expediente_queda_por_debajo_de_150_ms |
| QR-02 | MET | TC-13: axe-core sin violaciones WCAG 2.2 AA (TC-10) y pasada con teclado y árbol de accesibilidad con Playwright, 2026-11-15: fotos con texto alternativo con fecha y ubicación, formularios con etiquetas y resultados anunciados; el error de valor mayor que el amparo queda asociado al campo con aria-invalid, sin región viva |

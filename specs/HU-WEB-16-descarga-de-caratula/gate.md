# Definition of Done and security gate · HU-WEB-16

| Field | Value |
|---|---|
| Security status | SECURITY_VERIFIED |
| Evaluated on | 2026-11-05 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-WEB-16-descarga-de-caratula |

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
| EV-01 | Pruebas del backend con PostgreSQL y Floci: 322 de 322; `storage.py` 95 % y `document.py` 100 % de cobertura | SR-01, SR-02, SR-03, SR-04, SR-05, SR-07, CTL-01, CTL-02, CTL-03, CTL-04, CTL-05, CTL-07, TC-01, TC-02, TC-03, TC-04, TC-05, TC-06, TC-07, TC-08, TC-10, TC-12 | backend/tests/ | AVAILABLE |
| EV-02 | Pruebas del portal: 116 de 116 y build de producción | TC-11 | web/src/app/features/gestion/policy-detail.component.spec.ts | AVAILABLE |
| EV-03 | Terraform: `fmt -check`, `validate` y `test` (1 de 1) en verde | SR-06, CTL-06, TC-09 | terraform/ | AVAILABLE |
| EV-04 | Prueba de punta a punta en Chrome con Playwright, 13 de 13 comprobaciones en dos corridas, y auditoría axe-core sin violaciones, 2026-11-01 | TC-13, TC-15 | Resultado en TC-13 y TC-15 | AVAILABLE |
| EV-08 | Teléfono: Galaxy S22 Ultra con Android 16 y Chrome 156, descarga real a Descargas, PDF en el visor, S3 caído en caliente y pasada con TalkBack, 2026-11-01 | TC-13, TC-15 | Resultado en TC-13 y TC-15 | AVAILABLE |
| EV-05 | Mutantes: 13 de 13 en el backend y 3 de 3 en el portal detectados por las pruebas, 2026-11-01 | TC-01, TC-02, TC-04, TC-07, TC-08, TC-10, TC-11, TC-16 | Descripción del pull request | AVAILABLE |
| EV-07 | Imagen de Docker del backend con boto3 y fpdf2, con Floci en red como en docker compose: bucket creado al arrancar y enlace con la dirección pública que descarga, 2026-11-01 | TC-16 | Resultado en TC-16 | AVAILABLE |
| EV-06 | OSV.dev, `sdd.py scan deps` y `scan secrets` sin hallazgos; el registro del backend sin enlaces firmados | SR-08, CTL-08, TC-14 | Resultado en TC-14 | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | TC-01: backend/tests/integration/test_policy_caratula_api.py::test_tc_01_el_analista_recibe_un_enlace_de_15_minutos_que_descarga_el_pdf; backend/tests/unit/test_policy_document.py::test_tc_01_el_enlace_es_https_de_esa_clave_vence_en_15_minutos_y_descarga_el_archivo; backend/tests/unit/test_policy_document.py::test_tc_01_un_enlace_sin_https_contra_aws_se_rechaza; TC-11: web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-11 el botón pide el enlace, anuncia el avance y abre la descarga" |
| AC-02 | MET | TC-02: backend/tests/unit/test_policy_document.py::test_tc_02_la_caratula_trae_numero_titular_plan_coberturas_vigencia_y_prima; backend/tests/unit/test_policy_document.py::test_tc_02_el_nombre_con_html_o_fuera_de_latin1_se_escribe_como_texto; backend/tests/integration/test_policy_caratula_api.py::test_tc_02_el_pdf_descargado_trae_los_datos_con_el_documento_enmascarado |
| AC-03 | MET | TC-03: backend/tests/integration/test_policy_caratula_api.py::test_tc_03_un_enlace_vencido_se_rechaza_y_al_pedir_otro_descarga; TC-11: web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-11 cada clic pide un enlace nuevo y mientras prepara ignora otro clic" |
| AC-04 | MET | TC-08: backend/tests/integration/test_policy_caratula_api.py::test_tc_08_con_s3_caido_informa_que_esta_en_generacion_y_luego_la_entrega; backend/tests/unit/test_policy_document.py::test_tc_08_un_error_de_s3_al_guardar_se_vuelve_almacenamiento_no_disponible; backend/tests/unit/test_policy_document.py::test_tc_08_sin_credenciales_firmar_falla_como_almacenamiento_no_disponible; TC-11: web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-11 si el almacenamiento no responde dice que está en generación como alerta" |
| AC-05 | MET | TC-07: backend/tests/integration/test_policy_caratula_api.py::test_tc_07_el_pdf_se_genera_una_vez_y_luego_solo_se_firma |
| AC-06 | MET | TC-04: backend/tests/integration/test_policy_caratula_api.py::test_tc_04_el_usuario_de_un_socio_solo_descarga_las_de_su_socio; TC-05: backend/tests/integration/test_policy_caratula_api.py::test_tc_05_solo_el_rol_analyst_con_su_socio_habilitado_pide_la_caratula |
| AC-07 | MET | TC-02: backend/tests/unit/test_policy_document.py::test_tc_02_el_documento_va_enmascarado_y_sin_contacto_ni_beneficiarios; backend/tests/integration/test_policy_caratula_api.py::test_tc_02_el_pdf_descargado_trae_los_datos_con_el_documento_enmascarado |
| AC-08 | MET | TC-10: backend/tests/integration/test_policy_caratula_api.py::test_tc_10_una_poliza_cancelada_no_tiene_caratula; TC-11: web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-11 una póliza cancelada no ofrece la carátula" |
| AC-09 | MET | TC-01: backend/tests/integration/test_policy_caratula_api.py::test_tc_01_el_analista_recibe_un_enlace_de_15_minutos_que_descarga_el_pdf; backend/tests/unit/test_policy_document.py::test_tc_01_el_enlace_es_https_de_esa_clave_vence_en_15_minutos_y_descarga_el_archivo |
| AC-10 | MET | TC-02: backend/tests/unit/test_policy_document.py::test_tc_02_sin_titular_registrado_lo_dice_y_enmascara_el_documento; backend/tests/integration/test_policy_caratula_api.py::test_tc_02_una_poliza_b2b_sin_titular_muestra_sin_nombre_y_el_socio |
| AC-11 | MET | TC-16: backend/tests/integration/test_policy_caratula_api.py::test_tc_16_con_el_emulador_el_bucket_se_crea_una_vez_y_el_enlace_usa_la_direccion_publica |
| AC-12 | MET | TC-02: backend/tests/unit/test_policy_document.py::test_tc_02_la_caratula_trae_numero_titular_plan_coberturas_vigencia_y_prima; backend/tests/integration/test_policy_caratula_api.py::test_tc_02_el_pdf_descargado_trae_los_datos_con_el_documento_enmascarado; backend/tests/integration/test_policy_caratula_api.py::test_tc_02_una_poliza_b2b_sin_titular_muestra_sin_nombre_y_el_socio |
| QR-01 | MET | TC-12: backend/tests/integration/test_policy_caratula_api.py::test_tc_12_el_p95_del_enlace_queda_por_debajo_de_150_ms_y_la_primera_generacion_de_1_s |
| QR-02 | MET | TC-13: prueba manual del 2026-11-01: axe-core 4.10.2 sin violaciones, teclado hasta el botón y Enter, 320 px y TalkBack en un Galaxy S22 Ultra (EV-04 y EV-08) |

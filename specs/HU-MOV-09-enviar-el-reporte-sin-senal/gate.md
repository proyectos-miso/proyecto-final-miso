# Definition of Done and security gate · HU-MOV-09

| Field | Value |
|---|---|
| Security status | SECURITY_VERIFIED |
| Evaluated on | 2026-11-13 |
| Command | python sdd/tools/sdd.py spec validate specs/HU-MOV-09-enviar-el-reporte-sin-senal |

<!-- Status: IN_PROGRESS, IMPLEMENTED, SECURITY_TESTS_PENDING, SECURITY_REVIEW_PENDING, RISK_ACCEPTED,
     SECURITY_VERIFIED or BLOCKED. The validator computes the status the evidence supports and rejects a
     higher declared one. An agent may prepare evidence; it never signs reviews or accepts risk.
     With "requireSignatures": false in sdd/config.json, the merge request approval is the security review
     and risk decisions recorded in the spec count as accepted; the two signature tables are optional. -->

## Definition of Done

- [ ] Acceptance criteria implemented, each covered by a passing test case.
- [ ] Security requirements mitigated by an applicable control and verified by a security test case.
- [ ] No required control MISSING.
- [ ] No secret in the code, logs, artifacts or this package.
- [ ] New or updated dependencies evaluated.
- [ ] Personal data handling reviewed (complete lifecycle, nothing UNKNOWN).
- [ ] MEDIUM or higher residual risks mitigated or accepted by an authorized person.
- [ ] Security review approved by a person other than the author.
- [ ] Closeout: every acceptance criterion MET with evidence.
- [ ] `python sdd/tools/sdd.py spec validate` reports no errors.

## Security reviews

| Reviewer | Role | Date | Scope | Result |
|---|---|---|---|---|

## Risk acceptance

| Risk | Decision | Approved by | Role | Date | Expires |
|---|---|---|---|---|---|

## Evidence

| ID | Evidence | Supports | Location | Status |
|---|---|---|---|---|
| EV-01 | Pruebas de la recepción y del token de reporte | SR-01, SR-02, SR-03, SR-04, CTL-01, CTL-02, CTL-03, CTL-04, TC-01, TC-02, TC-09 | backend/tests/integration/test_claim_report_api.py | AVAILABLE |
| EV-02 | Pruebas de la cola, el envío y el token en la app | SR-05, CTL-05, TC-11, TC-12, TC-14 | movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt | AVAILABLE |

## Closeout

<!-- Filled by spec-close: one row per acceptance criterion (AC) and quality requirement (QR).
     Result: MET, NOT MET or PARTIAL, with file:line or path::test evidence.
     The spec cannot move to DONE while one is not MET. -->

| Criterion | Result | Evidence |
|---|---|---|
| AC-01 | MET | TC-11: movil/app/src/test/java/com/solventa/app/data/claims/ClaimOutboxTest.kt::"AC-01 enviar deja el reporte en cola con su hora y ya no se edita"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-11 enviar con todo deja el reporte en cola, agenda el envío y lo muestra"; TC-19: movil/app/src/test/java/com/solventa/app/data/claims/ClaimOutboxTest.kt::"la migración a la versión 2 conserva el reporte listo y lo deja en cola"; TC-20: Prueba manual de Juan Camilo Peña el 2026-11-14 en un celular Android con la APK local por túnel: reporte con señal, reporte en modo avión con la app cerrada que llegó solo al portal, cierre de sesión con un reporte en cola que no llegó |
| AC-02 | MET | TC-09: backend/tests/integration/test_claim_report_api.py::test_con_la_app_cerrada_el_token_de_reporte_envia_el_reporte; backend/tests/integration/test_claim_report_api.py::test_el_token_de_reporte_no_abre_otros_endpoints; backend/tests/integration/test_claim_report_api.py::test_sin_token_o_con_token_del_portal_se_rechaza; backend/tests/integration/test_claim_report_api.py::test_el_ingreso_entrega_un_token_de_reporte_atado_a_su_refresh; TC-10: backend/tests/integration/test_claim_report_api.py::test_cerrar_sesion_revoca_el_token_de_reporte; backend/tests/integration/test_claim_report_api.py::test_cambiar_la_contrasena_revoca_el_token_de_reporte; backend/tests/integration/test_claim_report_api.py::test_cerrar_la_app_sin_cerrar_sesion_deja_enviar_el_reporte; backend/tests/integration/test_claim_report_api.py::test_la_renovacion_conserva_la_familia_y_el_cierre_de_sesion_revoca_todos_sus_tokens; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"HU-MOV-09 al terminar la actividad revoca la sesion conservando el token de reporte"; TC-14: movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-02 sin sesión abierta usa el token de reporte guardado"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-04 un error del servidor reintenta, cuenta el intento y conserva el reporte"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-09 un rechazo definitivo deja de reintentar y guarda el motivo"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-08 recibido borra la copia local y pide sincronizar Mis siniestros"; TC-15: movil/app/src/test/java/com/solventa/app/data/claims/ClaimSendWorkerTest.kt::"QR-01 exige red, reintenta con espera exponencial desde 10 s y no lleva datos de la cuenta"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSendWorkerTest.kt::"AC-02 un solo trabajo que espera la red y al tenerla envía la cola"; TC-20: Prueba manual de Juan Camilo Peña el 2026-11-14 en un celular Android con la APK local por túnel: reporte con señal, reporte en modo avión con la app cerrada que llegó solo al portal, cierre de sesión con un reporte en cola que no llegó |
| AC-03 | MET | TC-02: backend/tests/integration/test_claim_report_api.py::test_el_mismo_reporte_enviado_otra_vez_devuelve_el_mismo_siniestro; backend/tests/integration/test_claim_report_api.py::test_dos_envios_simultaneos_del_mismo_reporte_dejan_un_siniestro; TC-06: backend/tests/integration/test_claim_report_api.py::test_dos_reportes_distintos_reciben_radicados_distintos; TC-16: movil/app/src/test/java/com/solventa/app/data/claims/HttpClaimSubmitApiTest.kt::"envía un multipart con el reporte y cada foto con su posición y el token"; movil/app/src/test/java/com/solventa/app/data/claims/HttpClaimSubmitApiTest.kt::"AC-09 un 422 o un 413 rechazan con el motivo del servidor" |
| AC-04 | MET | TC-05: backend/tests/integration/test_claim_report_api.py::test_s3_caido_responde_503_y_el_reporte_se_puede_reintentar; TC-14: movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-02 sin sesión abierta usa el token de reporte guardado"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-04 un error del servidor reintenta, cuenta el intento y conserva el reporte"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-09 un rechazo definitivo deja de reintentar y guarda el motivo"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-08 recibido borra la copia local y pide sincronizar Mis siniestros"; TC-15: movil/app/src/test/java/com/solventa/app/data/claims/ClaimSendWorkerTest.kt::"QR-01 exige red, reintenta con espera exponencial desde 10 s y no lleva datos de la cuenta"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSendWorkerTest.kt::"AC-02 un solo trabajo que espera la red y al tenerla envía la cola"; TC-16: movil/app/src/test/java/com/solventa/app/data/claims/HttpClaimSubmitApiTest.kt::"envía un multipart con el reporte y cada foto con su posición y el token"; movil/app/src/test/java/com/solventa/app/data/claims/HttpClaimSubmitApiTest.kt::"AC-09 un 422 o un 413 rechazan con el motivo del servidor" |
| AC-05 | MET | TC-17: movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsListScreenTest.kt::"HU-MOV-09 AC-05 el reporte en cola aparece arriba como pendiente de envio"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsViewModelTest.kt::"HU-MOV-09 AC-05 el reporte en cola aparece como pendiente aun sin conexion"; TC-20: Prueba manual de Juan Camilo Peña el 2026-11-14 en un celular Android con la APK local por túnel: reporte con señal, reporte en modo avión con la app cerrada que llegó solo al portal, cierre de sesión con un reporte en cola que no llegó |
| AC-06 | MET | TC-01: backend/tests/integration/test_claim_report_api.py::test_registra_el_reporte_con_radicado_y_guarda_las_fotos_verificadas; TC-03: backend/tests/integration/test_claim_report_api.py::test_una_foto_alterada_se_rechaza_y_no_queda_nada |
| AC-07 | MET | TC-01: backend/tests/integration/test_claim_report_api.py::test_registra_el_reporte_con_radicado_y_guarda_las_fotos_verificadas; TC-04: backend/tests/integration/test_claim_report_api.py::test_fotos_que_no_cumplen_los_limites_se_rechazan; backend/tests/integration/test_claim_report_api.py::test_un_reporte_mal_formado_responde_422; TC-07: backend/tests/integration/test_claim_report_api.py::test_la_poliza_de_otra_persona_o_no_vigente_responde_404 |
| AC-08 | MET | TC-01: backend/tests/integration/test_claim_report_api.py::test_registra_el_reporte_con_radicado_y_guarda_las_fotos_verificadas; TC-12: movil/app/src/test/java/com/solventa/app/data/session/ReportTokenStoreTest.kt::"guarda el último token de cada cuenta cifrado y lo lee por su id"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimOutboxTest.kt::"AC-08 recibido borra la base, las fotos y la frase, pero no un reporte nuevo"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"HU-MOV-09 cerrar sesion, la huella revocada o el cambio de contrasena borran el token de reporte"; TC-14: movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-02 sin sesión abierta usa el token de reporte guardado"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-04 un error del servidor reintenta, cuenta el intento y conserva el reporte"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-09 un rechazo definitivo deja de reintentar y guarda el motivo"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-08 recibido borra la copia local y pide sincronizar Mis siniestros" |
| AC-09 | MET | TC-14: movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-02 sin sesión abierta usa el token de reporte guardado"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-04 un error del servidor reintenta, cuenta el intento y conserva el reporte"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-09 un rechazo definitivo deja de reintentar y guarda el motivo"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-08 recibido borra la copia local y pide sincronizar Mis siniestros"; TC-16: movil/app/src/test/java/com/solventa/app/data/claims/HttpClaimSubmitApiTest.kt::"envía un multipart con el reporte y cada foto con su posición y el token"; movil/app/src/test/java/com/solventa/app/data/claims/HttpClaimSubmitApiTest.kt::"AC-09 un 422 o un 413 rechazan con el motivo del servidor"; TC-18: movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"HU-MOV-09 AC-09 un reporte rechazado muestra el motivo y ofrece descartarlo"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsListScreenTest.kt::"HU-MOV-09 AC-09 un reporte rechazado dice el motivo en la lista" |
| AC-10 | MET | TC-13: .github/workflows/ci.yml; movil/app/build.gradle.kts; terraform/modules/ecs/main.tf; CI de dev, corrida 44377943077: job «Mobile release (APK firmado)» en success |
| QR-01 | MET | TC-15: movil/app/src/test/java/com/solventa/app/data/claims/ClaimSendWorkerTest.kt::"QR-01 exige red, reintenta con espera exponencial desde 10 s y no lleva datos de la cuenta"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSendWorkerTest.kt::"AC-02 un solo trabajo que espera la red y al tenerla envía la cola"; TC-20: Prueba manual de Juan Camilo Peña el 2026-11-14 en un celular Android con la APK local por túnel: reporte con señal, reporte en modo avión con la app cerrada que llegó solo al portal, cierre de sesión con un reporte en cola que no llegó |
| QR-02 | MET | TC-21: Prueba manual de Juan Camilo Peña el 2026-11-14 con TalkBack: anuncia la cola y lee el pendiente con su póliza y fecha |

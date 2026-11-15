# Test cases · HU-MOV-09

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | AC-01 | Dado un asegurado sin señal, cuando pulsa «Enviar», entonces el reporte queda cifrado en cola y la app dice "Reporte en cola de envío" | TC-11 |
| SC-02 | AC-02 | Dado un reporte en cola y la app cerrada, cuando vuelve la red, entonces el trabajo de envío lo manda con el token de reporte | TC-14, TC-15 |
| SC-03 | UC-03 | Dado un reporte válido, cuando llega al servidor, entonces se registra con radicado y las fotos verificadas quedan en S3 | TC-01 |
| SC-04 | AC-03 | Dado un reporte que llega dos veces, una tras otra o al mismo tiempo, cuando se registra, entonces hay un solo siniestro | TC-02 |
| SC-05 | AC-06 | Dada una foto que cambió después de tomarla, cuando llega, entonces se rechaza y no queda nada | TC-03 |
| SC-06 | AC-07 | Dada una foto que no es JPEG, de más de 2 MB, sin fotos o un reporte mal formado, cuando llega, entonces se rechaza | TC-04 |
| SC-07 | AC-04 | Dado S3 o el servidor caído, cuando falla el envío, entonces la app reintenta con espera creciente y conserva el reporte | TC-05, TC-14, TC-15 |
| SC-08 | AC-07 | Dada la póliza de otra persona, cancelada o vencida, cuando llega el reporte, entonces responde 404 | TC-07 |
| SC-09 | UC-03 | Dado el identificador de un reporte ajeno, cuando otra persona lo envía, entonces responde 404 | TC-08 |
| SC-10 | AC-02 | Dado el token de reporte, cuando se usa en otro endpoint, entonces se rechaza; sin token o con token del portal, también | TC-09 |
| SC-11 | AC-02 | Dado que el asegurado cierra la app sin cerrar sesión, cuando vuelve la red, entonces el reporte sale; si cierra sesión o cambia la contraseña, el token deja de servir | TC-10 |
| SC-12 | AC-08 | Dado el reporte recibido, cuando responde el servidor, entonces se borra la copia local y se actualiza «Mis siniestros» | TC-14 |
| SC-13 | AC-09 | Dado un rechazo definitivo o una foto dañada en el teléfono, cuando ocurre, entonces deja de reintentar y explica qué hacer | TC-14, TC-18 |
| SC-14 | UC-02 | Dado un reporte en cola, cuando el asegurado entra a «Mis siniestros», entonces lo ve arriba como pendiente, también sin señal | TC-17 |
| SC-15 | UC-01 | Dado un reporte listo de la versión anterior de la app, cuando se actualiza, entonces queda en cola sin perder datos | TC-19 |
| SC-16 | AC-10 | Dado un push a `dev` con la llave de firma configurada, cuando corre el CI, entonces publica el APK de release firmado | TC-13 |
| SC-17 | UC-03 | Dados dos reportes distintos, cuando se registran, entonces reciben radicados distintos | TC-06 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-02, SC-03 | — |
| Alternate flows | YES | SC-11, SC-14 | — |
| Invalid input and errors | YES | SC-05, SC-06, SC-08, SC-13 | — |
| Limits and boundaries | YES | SC-06 | — |
| Related flows before and after | YES | SC-12, SC-15, SC-16 | — |
| Several users, sessions, devices or roles | YES | SC-08, SC-09, SC-10 | — |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-04, SC-07, SC-11 | — |

## Test cases

### TC-01 · El reporte se registra con radicado y las fotos verificadas

| Field | Value |
|---|---|
| Covers | AC-06, AC-07, AC-08, SR-03 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Póliza vigente de María; S3 emulado |
| Procedure | 1. Enviar el reporte con 2 fotos. 2. Revisar el siniestro, las fotos y el evento |
| Expected result | 201, radicado `SIN-AAAA-NNNNN`, fotos en S3 iguales a las enviadas y evento REPORTED |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_report_api.py::test_registra_el_reporte_con_radicado_y_guarda_las_fotos_verificadas |
| Status | PASS |

### TC-02 · Un reporte, un siniestro

| Field | Value |
|---|---|
| Covers | AC-03, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | Póliza vigente |
| Procedure | 1. Enviar el mismo reporte dos veces seguidas. 2. Enviarlo dos veces al mismo tiempo |
| Expected result | 201 y 200 con el mismo radicado; un solo siniestro |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_report_api.py::test_el_mismo_reporte_enviado_otra_vez_devuelve_el_mismo_siniestro; backend/tests/integration/test_claim_report_api.py::test_dos_envios_simultaneos_del_mismo_reporte_dejan_un_siniestro |
| Status | PASS |

### TC-03 · Foto alterada

| Field | Value |
|---|---|
| Covers | AC-06, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Póliza vigente |
| Procedure | 1. Enviar una foto cuyo contenido no coincide con su huella |
| Expected result | 422 que pide tomar las fotos de nuevo; ningún siniestro |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_report_api.py::test_una_foto_alterada_se_rechaza_y_no_queda_nada |
| Status | PASS |

### TC-04 · Límites y formato

| Field | Value |
|---|---|
| Covers | AC-07, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | INTEGRATION |
| Preconditions | Póliza vigente |
| Procedure | 1. Enviar un PNG, una foto de más de 2 MB y un reporte sin fotos. 2. Enviar JSON inválido, descripción de 501 caracteres, posiciones salteadas y 6 fotos |
| Expected result | 422, 413 y 422; ningún siniestro |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_report_api.py::test_fotos_que_no_cumplen_los_limites_se_rechazan; backend/tests/integration/test_claim_report_api.py::test_un_reporte_mal_formado_responde_422 |
| Status | PASS |

### TC-05 · S3 caído

| Field | Value |
|---|---|
| Covers | AC-04 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | S3 que falla una vez |
| Procedure | 1. Enviar con S3 caído. 2. Reintentar |
| Expected result | 503 con `Retry-After`; luego 201 |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_report_api.py::test_s3_caido_responde_503_y_el_reporte_se_puede_reintentar |
| Status | PASS |

### TC-06 · Radicados distintos

| Field | Value |
|---|---|
| Covers | AC-03 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Póliza vigente |
| Procedure | 1. Enviar dos reportes distintos |
| Expected result | Dos radicados distintos |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_report_api.py::test_dos_reportes_distintos_reciben_radicados_distintos |
| Status | PASS |

### TC-07 · Póliza ajena, cancelada o vencida

| Field | Value |
|---|---|
| Covers | AC-07, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Pólizas de otra persona, cancelada y vencida |
| Procedure | 1. Enviar un reporte sobre cada una |
| Expected result | 404 en las tres; ningún siniestro |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_report_api.py::test_la_poliza_de_otra_persona_o_no_vigente_responde_404 |
| Status | PASS |

### TC-08 · Identificador de un reporte ajeno

| Field | Value |
|---|---|
| Covers | SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Reporte de María registrado; segunda asegurada |
| Procedure | 1. La segunda asegurada envía el mismo identificador sobre su póliza |
| Expected result | 404 y sigue habiendo un siniestro |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_report_api.py::test_el_reporte_de_otra_persona_con_el_mismo_identificador_no_se_entrega |
| Status | PASS |

### TC-09 · Alcance del token de reporte

| Field | Value |
|---|---|
| Covers | AC-02, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | INTEGRATION |
| Preconditions | Token de reporte de María |
| Procedure | 1. Enviar el reporte con el token de reporte. 2. Usarlo en otros endpoints. 3. Enviar sin token y con token del portal. 4. Revisar el token que entrega el ingreso |
| Expected result | 201; 401 en otros endpoints; 401 y 403; el token es de tipo `report`, alcance `claims:submit`, 7 días y misma familia que el refresh |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_report_api.py::test_con_la_app_cerrada_el_token_de_reporte_envia_el_reporte; backend/tests/integration/test_claim_report_api.py::test_el_token_de_reporte_no_abre_otros_endpoints; backend/tests/integration/test_claim_report_api.py::test_sin_token_o_con_token_del_portal_se_rechaza; backend/tests/integration/test_claim_report_api.py::test_el_ingreso_entrega_un_token_de_reporte_atado_a_su_refresh |
| Status | PASS |

### TC-10 · Revocación del token de reporte

| Field | Value |
|---|---|
| Covers | AC-02, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | INTEGRATION |
| Preconditions | Token de reporte de María |
| Procedure | 1. Cerrar sesión. 2. Cambiar la contraseña. 3. Cerrar la app con `keep_reporting`. 4. Renovar y luego cerrar sesión |
| Expected result | 401 tras cerrar sesión o cambiar la contraseña; 201 tras cerrar la app; el cierre de sesión revoca también los tokens renovados |
| Tool or method | pytest y JUnit |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_report_api.py::test_cerrar_sesion_revoca_el_token_de_reporte; backend/tests/integration/test_claim_report_api.py::test_cambiar_la_contrasena_revoca_el_token_de_reporte; backend/tests/integration/test_claim_report_api.py::test_cerrar_la_app_sin_cerrar_sesion_deja_enviar_el_reporte; backend/tests/integration/test_claim_report_api.py::test_la_renovacion_conserva_la_familia_y_el_cierre_de_sesion_revoca_todos_sus_tokens; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"HU-MOV-09 al terminar la actividad revoca la sesion conservando el token de reporte" |
| Status | PASS |

### TC-11 · Reporte en cola

| Field | Value |
|---|---|
| Covers | AC-01 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Reporte completo |
| Procedure | 1. Pulsar «Enviar» sin red |
| Expected result | Queda en cola con su hora, no se edita, se agenda el envío y la pantalla lo dice |
| Tool or method | JUnit y Robolectric |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/data/claims/ClaimOutboxTest.kt::"AC-01 enviar deja el reporte en cola con su hora y ya no se edita"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimReportViewModelTest.kt::"TC-11 enviar con todo deja el reporte en cola, agenda el envío y lo muestra" |
| Status | PASS |

### TC-12 · Cola y token cifrados y borrados

| Field | Value |
|---|---|
| Covers | AC-08, SR-05 |
| Kind | SECURITY |
| Risk covered | THR-05 |
| Level | UNIT |
| Preconditions | Reporte en cola y token guardado |
| Procedure | 1. Guardar y leer el token por cuenta. 2. Recibir el reporte. 3. Cerrar sesión, revocar la huella o cambiar la contraseña |
| Expected result | Token cifrado y por cuenta; la base, las fotos y la frase se borran al recibirse; el token se borra al cerrar sesión |
| Tool or method | JUnit y Robolectric |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/data/session/ReportTokenStoreTest.kt::"guarda el último token de cada cuenta cifrado y lo lee por su id"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimOutboxTest.kt::"AC-08 recibido borra la base, las fotos y la frase, pero no un reporte nuevo"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"HU-MOV-09 cerrar sesion, la huella revocada o el cambio de contrasena borran el token de reporte" |
| Status | PASS |

### TC-13 · APK firmado e IAM

| Field | Value |
|---|---|
| Covers | AC-10, SR-06 |
| Kind | SECURITY |
| Risk covered | THR-07 |
| Level | STATIC |
| Preconditions | Rama de la historia |
| Procedure | 1. Revisar que el job de release solo lee la llave de secretos, la escribe con `umask 077` en `$RUNNER_TEMP` y la borra. 2. Construir `assembleRelease` con una llave de prueba y sin ella. 3. Revisar que el IAM solo da `PutObject` y `GetObject` en `siniestros/*` |
| Expected result | APK firmado con llave y sin firmar sin ella; ningún secreto impreso; IAM acotado |
| Tool or method | Revisión de `.github/workflows/ci.yml`, `movil/app/build.gradle.kts` y `terraform/modules/ecs/main.tf`; `terraform validate` |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | .github/workflows/ci.yml; movil/app/build.gradle.kts; terraform/modules/ecs/main.tf |
| Status | PASS |

### TC-14 · Envío, reintentos y rechazos

| Field | Value |
|---|---|
| Covers | AC-02, AC-04, AC-08, AC-09 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Reporte en cola |
| Procedure | 1. Enviar sin sesión. 2. Con la sesión abierta. 3. Con error del servidor. 4. Con rechazo definitivo. 5. Con una foto dañada. 6. Recibido |
| Expected result | Usa el token de reporte o el de acceso; reintenta y cuenta; rechaza con el motivo; pide tomar las fotos de nuevo; al recibirse borra y sincroniza |
| Tool or method | JUnit |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-02 sin sesión abierta usa el token de reporte guardado"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-04 un error del servidor reintenta, cuenta el intento y conserva el reporte"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-09 un rechazo definitivo deja de reintentar y guarda el motivo"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"AC-08 recibido borra la copia local y pide sincronizar Mis siniestros" |
| Status | PASS |

### TC-15 · Trabajo de envío

| Field | Value |
|---|---|
| Covers | AC-02, AC-04, QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | UNIT |
| Preconditions | WorkManager de pruebas |
| Procedure | 1. Revisar las restricciones y la espera. 2. Ejecutar con cada resultado |
| Expected result | Un solo trabajo, exige red, espera exponencial desde 10 s, sin datos de la cuenta; retry ante fallos |
| Tool or method | JUnit y WorkManager testing |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/data/claims/ClaimSendWorkerTest.kt::"QR-01 exige red, reintenta con espera exponencial desde 10 s y no lleva datos de la cuenta"; movil/app/src/test/java/com/solventa/app/data/claims/ClaimSendWorkerTest.kt::"AC-02 un solo trabajo que espera la red y al tenerla envía la cola" |
| Status | PASS |

### TC-16 · Cliente HTTP del envío

| Field | Value |
|---|---|
| Covers | AC-03, AC-04, AC-09 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Servidor simulado |
| Procedure | 1. Enviar y revisar las partes. 2. Responder 200, 401, 404, 413, 422, 5xx y sin red |
| Expected result | Multiparte con el reporte, las fotos y el token; cada código con su resultado |
| Tool or method | JUnit y MockWebServer |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/data/claims/HttpClaimSubmitApiTest.kt::"envía un multipart con el reporte y cada foto con su posición y el token"; movil/app/src/test/java/com/solventa/app/data/claims/HttpClaimSubmitApiTest.kt::"AC-09 un 422 o un 413 rechazan con el motivo del servidor" |
| Status | PASS |

### TC-17 · Pendientes en «Mis siniestros»

| Field | Value |
|---|---|
| Covers | AC-05 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Reporte en cola |
| Procedure | 1. Abrir «Mis siniestros» con y sin señal |
| Expected result | El reporte aparece arriba como pendiente de envío |
| Tool or method | Robolectric y Compose |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsListScreenTest.kt::"HU-MOV-09 AC-05 el reporte en cola aparece arriba como pendiente de envio"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsViewModelTest.kt::"HU-MOV-09 AC-05 el reporte en cola aparece como pendiente aun sin conexion" |
| Status | PASS |

### TC-18 · Reporte rechazado

| Field | Value |
|---|---|
| Covers | AC-09 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Reporte rechazado |
| Procedure | 1. Abrir el reporte. 2. Abrir «Mis siniestros» |
| Expected result | Se ve el motivo y la opción de descartarlo |
| Tool or method | Robolectric y Compose |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"HU-MOV-09 AC-09 un reporte rechazado muestra el motivo y ofrece descartarlo"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsListScreenTest.kt::"HU-MOV-09 AC-09 un reporte rechazado dice el motivo en la lista" |
| Status | PASS |

### TC-19 · Migración de la base de reportes

| Field | Value |
|---|---|
| Covers | AC-01 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Base de la versión 1 con un reporte listo |
| Procedure | 1. Abrir con la versión 2 |
| Expected result | El reporte queda en cola sin perder datos; el esquema 2 está exportado |
| Tool or method | Robolectric |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/data/claims/ClaimOutboxTest.kt::"la migración a la versión 2 conserva el reporte listo y lo deja en cola" |
| Status | PASS |

### TC-20 · Prueba en el teléfono

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-05, QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | E2E |
| Preconditions | APK local por túnel, backend y S3 locales |
| Procedure | 1. Reportar en modo avión. 2. Cerrar la app. 3. Quitar el modo avión. 4. Revisar en menos de un minuto que el siniestro aparece en el portal y en «Mis siniestros». 5. Recorrer con TalkBack |
| Expected result | El reporte llega solo, una vez, y TalkBack anuncia el estado |
| Tool or method | Prueba manual |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | Prueba manual de Juan Camilo Peña el 2026-11-14 en un celular Android con la APK local por túnel: reporte con señal, reporte en modo avión con la app cerrada que llegó solo al portal, cierre de sesión con un reporte en cola que no llegó |
| Status | PASS |

### TC-21 · Accesibilidad de la cola y los pendientes

| Field | Value |
|---|---|
| Covers | QR-02 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | APK local con un reporte en cola y otro rechazado |
| Procedure | 1. Activar TalkBack. 2. Enviar un reporte sin señal y escuchar el aviso. 3. Recorrer «Mis siniestros» y el reporte rechazado. 4. Medir los objetivos táctiles |
| Expected result | TalkBack anuncia "Reporte en cola de envío" y el motivo del rechazo; objetivos de 48 dp o más |
| Tool or method | Prueba manual con TalkBack |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | Prueba manual de Juan Camilo Peña el 2026-11-14 con TalkBack: anuncia la cola y lee el pendiente con su póliza y fecha |
| Status | PASS |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-09 | — |
| User without permissions | YES | TC-09 | — |
| Access to another user's resources | YES | TC-07, TC-08 | — |
| Cross-tenant access | NO | — | El asegurado no es un socio |
| Malicious input | YES | TC-03, TC-04 | — |
| Expired tokens | YES | TC-09 | El token de reporte vence a los 7 días |
| Revoked sessions | YES | TC-10 | — |
| Tampered data | YES | TC-03 | — |
| Wrong keys | YES | TC-12 | Una llave perdida borra el token de reporte |
| External service failures | YES | TC-05, TC-14 | — |
| Race conditions | YES | TC-02 | — |
| Information exposure | YES | TC-09 | — |
| Secrets in code | YES | TC-13 | — |
| Insecure configuration | YES | TC-13 | — |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas |

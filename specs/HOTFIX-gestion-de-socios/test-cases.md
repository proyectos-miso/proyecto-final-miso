# Test cases · HOTFIX

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | AC-01 | Dado un socio registrado, cuando el analista de Solventa lista, entonces ve entorno, estado, cuota y correo enmascarado, sin secretos ni el correo completo | TC-01 |
| SC-02 | AC-07 | Dado el analista de un socio, un asesor o nadie, cuando llama a cualquier endpoint de gestión, entonces recibe 403 o 401 y nada cambia | TC-02 |
| SC-03 | UC-04 | Dado un socio con token vigente, cuando el analista lo revoca, entonces el token y el secreto dejan de servir; revocar de nuevo da 200 | TC-03 |
| SC-04 | UC-03 | Dado un socio activo, cuando el analista rota sus credenciales, entonces el secreto anterior da 401 y el nuevo obtiene token y cotiza | TC-03 |
| SC-05 | AC-05 | Dado un socio revocado, cuando el analista lo aprueba o lo rota, entonces recibe 409 y el socio sigue revocado | TC-03 |
| SC-06 | UC-05 | Dado un socio en sandbox, cuando cotiza y luego emite, entonces cotiza con su cuota de 1000 y la emisión da 403; aprobado, emite | TC-05 |
| SC-07 | AC-07 | Dado un analista, cuando inicia sesión, entonces la pestaña «Socios» aparece solo si es de Solventa, y la ruta devuelve a «Pólizas» al de un socio | TC-02, TC-04 |
| SC-08 | UC-01 | Dado el alta o la rotación, cuando el servidor responde, entonces el panel muestra el secreto una vez con «Copiar» y el aviso, recibe el foco y se borra al cerrarlo o salir | TC-04 |
| SC-09 | UC-02 | Dada una acción, cuando el analista la pulsa, entonces se pide confirmación y solo al confirmar se llama al servidor; un 409 se muestra | TC-04 |
| SC-10 | UC-01 | Dada la pantalla, cuando se recorre con teclado y lector de pantalla, entonces la tabla, los campos, los avisos y los paneles se anuncian y el foco vuelve al botón que abrió la acción o al encabezado | TC-06, TC-07 |
| SC-11 | AC-06 | Dada la cotización de un socio en sandbox o un asesor de un socio revocado, cuando el asesor emite, entonces recibe 403; dada la póliza de un socio revocado, cuando el asesor cobra, entonces recibe 403 y no queda cobro; sin socio, la emisión y el cobro siguen igual | TC-05 |
| SC-12 | UC-04 | Dado un socio con tildes en el nombre o en un id ya creado, cuando el analista lo rota, aprueba o revoca, entonces responde 200 | TC-03 |
| SC-13 | AC-09 | Dados más socios que el límite, cuando el analista lista, entonces la respuesta trae `truncated: true` y el portal lo avisa | TC-01, TC-04 |
| SC-14 | AC-04 | Dadas dos rotaciones simultáneas, cuando terminan, entonces solo sirve el secreto de la última | TC-03 |
| SC-15 | AC-06 | Dada la póliza vencida de un socio revocado o en sandbox, cuando el analista o el asegurado la renueva, entonces recibe 403 y la póliza sigue vencida, sin evento de renovación | TC-05 |
| SC-16 | AC-06 | Dada una póliza ya emitida, cuando se revoca al socio y se repite la emisión por el asesor o por el socio, entonces se devuelve la misma póliza y no se crea otra | TC-05 |
| SC-17 | AC-06 | Dado un bloqueo por sandbox o por revocación, cuando se rechaza, entonces queda el evento `PARTNER_SANDBOX_ISSUE_BLOCKED` o `PARTNER_ISSUE_BLOCKED` sin secretos ni correos, y no hay póliza ni cobro | TC-05 |
| SC-18 | AC-07 | Dada una sesión con la pestaña «Socios», cuando se renueva el token de la misma persona, entonces la pestaña no desaparece; con otro usuario empieza oculta | TC-04 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-04, SC-08 | — |
| Alternate flows | YES | SC-03, SC-06 | — |
| Invalid input and errors | YES | SC-05, SC-09 | — |
| Limits and boundaries | YES | SC-06, SC-13 | Cuota del sandbox y límite del listado |
| Related flows before and after | YES | SC-03, SC-06 | Token ya emitido; aprobación tras el sandbox |
| Several users, sessions, devices or roles | YES | SC-02, SC-07, SC-11, SC-18 | Respuestas de sesiones anteriores en el portal; renovación del token |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-03, SC-08, SC-15, SC-16 | Revocar dos veces; salir de la pestaña; póliza vencida; repetir la emisión |

## Test cases

### TC-01 · Listado sin secretos

| Field | Value |
|---|---|
| Covers | AC-01, AC-09, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02, THR-04 |
| Level | INTEGRATION |
| Preconditions | Socios semilla y uno registrado en sandbox |
| Procedure | 1. Registrar un socio. 2. Listar con el analista de Solventa. 3. Buscar el secreto, el hash y el correo en la respuesta. 4. Leer un entorno guardado en mayúsculas. 5. Listar con un límite menor que los socios |
| Expected result | Los tres socios con su estado y entorno; correo `s***@dominio`; `no-store`; ni secreto, ni hash, ni correo completo; el entorno en minúsculas; con el límite, `truncated: true` |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_partner_management_api.py::test_el_listado_no_trae_secretos_ni_el_correo_completo; backend/tests/integration/test_partner_management_api.py::test_el_entorno_en_mayusculas_se_lee_en_minusculas; backend/tests/integration/test_partner_management_api.py::test_el_listado_avisa_cuando_queda_truncado |
| Status | PASS |

### TC-02 · Solo el analista de Solventa

| Field | Value |
|---|---|
| Covers | AC-07, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Analista de Solventa, analista de Banco Aliado y asesor |
| Procedure | 1. Llamar a listar, revocar, rotar y aprobar con el analista del socio, el asesor y sin sesión. 2. Consultar `/experience/operator` con cada uno. 3. Pasar el guard con y sin Solventa. 4. Un socio inexistente |
| Expected result | 403, 403 y 401; el socio sigue activo; `solventa` es `true` solo para Solventa; el guard devuelve a `/gestion`; inexistente 404 |
| Tool or method | pytest y Vitest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_partner_management_api.py::test_el_analista_de_un_socio_recibe_403_y_sin_sesion_401; backend/tests/integration/test_partner_management_api.py::test_el_portal_sabe_si_el_analista_es_de_solventa; backend/tests/integration/test_partner_management_api.py::test_un_socio_inexistente_da_404; web/src/app/core/guards/solventa.guard.spec.ts::"devuelve a gestión al analista de un socio"; web/src/app/core/guards/solventa.guard.spec.ts::"un error del servidor cuenta como que no es de Solventa" |
| Status | PASS |

### TC-03 · Revocar, rotar y la aprobación de un revocado

| Field | Value |
|---|---|
| Covers | AC-03, AC-04, AC-05, SR-02, SR-03, SR-05, AB-07 |
| Kind | SECURITY |
| Risk covered | THR-02, THR-03 |
| Level | INTEGRATION |
| Preconditions | Socio registrado con token vigente; socio semilla revocado |
| Procedure | 1. Cotizar con el token. 2. Revocar dos veces. 3. Cotizar y pedir token de nuevo. 4. Rotar un socio activo y probar ambos secretos. 5. Rotar y aprobar al revocado. 6. Registrar, rotar, aprobar y revocar y leer los cuatro eventos. 7. Dos rotaciones simultáneas. 8. Gestionar un socio con tildes en el nombre y uno con tildes en el id |
| Expected result | 200 y `REVOKED` las dos veces; 401 con el token vivo y al autenticarse; el secreto anterior 401 y el nuevo cotiza; el hash empieza con `$2` y no contiene el secreto; 409 «Socio revocado.» sin cambiar estado ni cuota; los cuatro eventos con `operator_ref` del analista, sin secretos ni correos; de dos rotaciones simultáneas solo sirve el secreto que quedó en la base; el id generado es ASCII y ambos socios se gestionan con 200 |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_partner_management_api.py::test_revocar_corta_un_token_ya_emitido_y_es_idempotente; backend/tests/integration/test_partner_management_api.py::test_rotar_invalida_el_secreto_anterior_y_el_nuevo_sirve; backend/tests/integration/test_partner_management_api.py::test_un_socio_revocado_no_se_rota_ni_se_aprueba; backend/tests/integration/test_partner_management_api.py::test_los_eventos_de_gestion_dicen_quien_actuo_sin_secretos_ni_correos; backend/tests/integration/test_partner_management_api.py::test_dos_rotaciones_simultaneas_dejan_valido_solo_el_ultimo_secreto; backend/tests/integration/test_partner_management_api.py::test_un_nombre_con_tildes_da_un_id_ascii_que_se_puede_gestionar; backend/tests/integration/test_partner_management_api.py::test_un_socio_creado_con_tildes_en_el_id_se_puede_revocar |
| Status | PASS |

### TC-04 · Pantalla «Socios»

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-07, AC-08, AC-09, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | UNIT |
| Preconditions | Servidor simulado con `HttpTestingController` y portapapeles falso |
| Procedure | 1. Listar. 2. Registrar y copiar el secreto. 3. Cerrar el panel y salir. 4. Revocar, cancelar y confirmar. 5. Rotar y aprobar con un 409. 6. Ver la pestaña con y sin Solventa. 7. Responder `/experience/operator` en orden invertido entre dos sesiones; renovar el token de la misma persona; cambiar de usuario. 8. `/openapi.json` con 200 y con 404, y sin el token del analista. 9. Listado truncado |
| Expected result | Acciones según estado y entorno; el panel dice «No se volverá a mostrar», recibe el foco y no deja el secreto en storage; al cerrar o salir se borra; sin confirmar no hay llamada; el 409 se anuncia; «Socios» solo con `solventa: true`; la respuesta de la sesión anterior se descarta; renovar el token de la misma persona conserva la pestaña sin consultar y otro usuario empieza sin ella; el enlace OpenAPI solo con 200 y la petición va sin `Authorization` ni intento de renovar la sesión; el aviso de truncado aparece |
| Tool or method | Vitest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | web/src/app/features/socios/partners.component.spec.ts::"registra un socio y muestra las credenciales una sola vez, con Copiar y sin guardarlas en el navegador"; web/src/app/features/socios/partners.component.spec.ts::"revocar pide confirmación y solo entonces llama al servidor"; web/src/app/features/socios/partners.component.spec.ts::"rotar con confirmación muestra el secreto nuevo y aprobar informa el error del servidor"; web/src/app/features/socios/partners.component.spec.ts::"al salir de la pestaña se borran las credenciales mostradas"; web/src/app/app.component.spec.ts::"FE-WEB-05 y FE-WEB-07 las pestañas Socios y Operación solo aparecen para el analista de Solventa"; web/src/app/core/partners/partner.service.spec.ts::"aprueba, rota y revoca con el identificador codificado"; web/src/app/core/partners/partner.service.spec.ts::"sabe si el contrato OpenAPI está publicado"; web/src/app/core/operator/operator.service.spec.ts::"con respuestas en orden invertido se queda con la de la sesión actual"; web/src/app/core/operator/operator.service.spec.ts::"una sesión nueva empieza sin la pestaña «Socios» hasta que el servidor responde"; web/src/app/core/operator/operator.service.spec.ts::"renovar el token de la misma persona no oculta la pestaña ni vuelve a consultar"; web/src/app/core/operator/operator.service.spec.ts::"acepta la respuesta pedida antes de renovar el token de la misma persona"; web/src/app/core/operator/operator.service.spec.ts::"otro usuario empieza sin la pestaña y descarta la respuesta del anterior"; web/src/app/core/interceptors/jwt.interceptor.spec.ts::"no envía el token del analista al pedir el contrato OpenAPI ni renueva la sesión si falla (FE-WEB-05)"; web/src/app/features/socios/partners.component.spec.ts::"muestra el enlace al contrato OpenAPI solo si el servidor lo publica"; web/src/app/features/socios/partners.component.spec.ts::"avisa cuando el listado viene truncado y la tabla referencia el aviso" |
| Status | PASS |

### TC-05 · Ni el sandbox ni un socio revocado emiten o cobran

| Field | Value |
|---|---|
| Covers | AC-06, SR-04, SR-05, AB-06 |
| Kind | SECURITY |
| Risk covered | THR-05 |
| Level | INTEGRATION |
| Preconditions | Socio recién registrado en sandbox; socio semilla revocado; socio aprobado con póliza emitida |
| Procedure | 1. Pedir token y cotizar. 2. Emitir por quote-edge. 3. Emitir esa cotización con un asesor. 4. Aprobar y emitir de nuevo. 5. Emitir con un asesor cuyo socio está revocado. 6. Revocar al socio aprobado y cobrar su póliza con un asesor. 7. Emitir y cobrar sin socio. 8. Leer los eventos de bloqueo y buscar en la base pólizas y cobros. 9. Emitir con un asesor, revocar al socio y repetir la emisión por el asesor y por el servicio del socio. 10. Renovar la póliza vencida de un socio revocado desde el portal y la de un socio en sandbox desde la app. 11. Renovar sin socio |
| Expected result | Cotiza con `X-RateLimit-Limit: 1000`; quote-edge y el asesor reciben 403 «Un socio en sandbox no emite pólizas reales»; aprobado, emite; el asesor del socio revocado y el cobro de la póliza revocada reciben 403 «Socio revocado o inexistente» sin cobro guardado; cada bloqueo deja `PARTNER_SANDBOX_ISSUE_BLOCKED` o `PARTNER_ISSUE_BLOCKED` con el `partner_id`, sin secretos ni correos, y en la base no hay póliza de esa cotización ni cobro; repetir la emisión tras revocar devuelve la misma póliza y hay una sola; la renovación bloqueada da 403 con el mensaje de la regla, la póliza sigue `EXPIRED` con su vigencia y versión y no hay evento `RENEWED`; sin socio, la emisión, el cobro y la renovación siguen funcionando |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_partner_management_api.py::test_un_socio_en_sandbox_cotiza_pero_no_emite_y_aprobado_si; backend/tests/integration/test_partner_registration.py::test_partner_self_registration_sandbox_and_production_approval; backend/tests/integration/test_partner_management_api.py::test_el_asesor_no_emite_la_cotizacion_de_un_socio_en_sandbox; backend/tests/integration/test_partner_management_api.py::test_un_asesor_de_un_socio_revocado_no_emite; backend/tests/integration/test_partner_management_api.py::test_no_se_cobra_la_poliza_de_un_socio_revocado; backend/tests/integration/test_policy_issuance.py::test_emite_la_poliza_del_plan_elegido_con_su_evento; backend/tests/integration/test_payment_charge.py::test_cobro_aprobado_activa_la_poliza_con_su_recibo_y_su_evento; backend/tests/integration/test_payments_partner.py::test_charge_premium_assigns_partner_id_from_authenticated_token; backend/tests/integration/test_partner_management_api.py::test_los_bloqueos_dejan_su_evento_de_seguridad_sin_poliza_ni_cobro; backend/tests/integration/test_partner_management_api.py::test_repetir_la_emision_tras_revocar_al_socio_devuelve_la_misma_poliza; backend/tests/integration/test_policy_renewal_api.py::test_no_se_renueva_la_poliza_vencida_de_un_socio_revocado; backend/tests/integration/test_insured_claims_api.py::test_la_app_no_renueva_la_poliza_vencida_de_un_socio_en_sandbox; backend/tests/integration/test_policy_renewal_api.py::test_el_analista_renueva_una_poliza_por_vencer_y_queda_en_la_auditoria; backend/tests/integration/test_insured_claims_api.py::test_renovacion_solo_dentro_de_los_30_dias |
| Status | PASS |

### TC-06 · Accesibilidad automatizada de «Socios»

| Field | Value |
|---|---|
| Covers | QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | Pantalla con socios |
| Procedure | 1. Revisar `caption`, `th scope="col"` y `th scope="row"`. 2. Etiquetas de los campos. 3. Región `aria-live`. 4. Foco en el panel de confirmación y en el de credenciales. 5. Cancelar, cerrar «Ya las guardé» y confirmar una acción que quita el botón. 6. Listado truncado al cargar y tras una acción. 7. Enlace al contrato OpenAPI |
| Expected result | Todo presente; el foco llega a cada panel y vuelve al botón que abrió la acción o, si ya no existe, al encabezado (`tabindex=-1`); la tabla referencia el aviso de truncado con `aria-describedby` y, si el aviso aparece tras una acción, la región `aria-live` lo anuncia; el enlace dice al lector que abre en una pestaña nueva |
| Tool or method | Vitest con jsdom |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | web/src/app/features/socios/partners.component.spec.ts::"lista los socios con caption, encabezados y el correo enmascarado; las acciones dependen del estado"; web/src/app/features/socios/partners.component.spec.ts::"revocar pide confirmación y solo entonces llama al servidor"; web/src/app/features/socios/partners.component.spec.ts::"devuelve el foco al botón que abrió la acción o, si ya no existe, al encabezado"; web/src/app/features/socios/partners.component.spec.ts::"avisa cuando el listado viene truncado y la tabla referencia el aviso"; web/src/app/features/socios/partners.component.spec.ts::"si el aviso de truncado aparece tras una acción, lo anuncia en la región aria-live"; web/src/app/features/socios/partners.component.spec.ts::"muestra el enlace al contrato OpenAPI solo si el servidor lo publica" |
| Status | PASS |

### TC-07 · Prueba en el portal

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-07, QR-01 |
| Kind | QUALITY |
| Risk covered | THR-01, THR-02 |
| Level | E2E |
| Preconditions | Backend y portal locales con los usuarios semilla |
| Procedure | 1. Entrar como analista de Solventa, registrar un socio, copiar y cerrar el panel. 2. Rotar y revocar. 3. Entrar como analista de Banco Aliado y abrir `/socios`. 4. Recorrer la pantalla solo con teclado y con NVDA |
| Expected result | El panel se ve una vez; el socio revocado no obtiene token; el analista del socio no ve la pestaña y vuelve a «Pólizas»; el lector anuncia la tabla, los campos, los paneles y los mensajes |
| Tool or method | Prueba manual |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-02 | — |
| User without permissions | YES | TC-02 | — |
| Access to another user's resources | YES | TC-02 | El analista de un socio no gestiona socios |
| Cross-tenant access | YES | TC-02 | — |
| Malicious input | YES | TC-02, TC-03 | `partner_id` validado por formato; inexistente 404; ids con tildes |
| Expired tokens | NO | — | No cambia la expiración |
| Revoked sessions | YES | TC-03 | Token vivo de un socio revocado |
| Tampered data | NO | — | Sin datos firmados nuevos |
| Wrong keys | YES | TC-03 | Secreto anterior tras la rotación |
| External service failures | YES | TC-02, TC-04 | Error del servidor en `/experience/operator` y 409 en la pantalla |
| Race conditions | YES | TC-03, TC-05 | Las mutaciones bloquean la fila; revocar dos veces es idempotente; dos rotaciones simultáneas dejan un solo secreto válido (la prueba no detecta si falta el `FOR UPDATE`: RISK-07); repetir la emisión tras revocar devuelve la misma póliza; la ventana entre revocar y una operación en curso queda en RISK-05 |
| Information exposure | YES | TC-01, TC-04 | — |
| Secrets in code | NO | — | Los secretos de prueba se generan en tiempo de ejecución |
| Insecure configuration | NO | — | Sin configuración nueva |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas |

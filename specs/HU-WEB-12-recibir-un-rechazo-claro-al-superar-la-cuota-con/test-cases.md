# Test cases · HU-WEB-12

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | UC-01, AC-04 | Dada una cotización con cuota disponible, cuando el socio la solicita, entonces recibe 200 OK y las cabeceras X-RateLimit-* | TC-01 |
| SC-02 | UC-02, AC-01, AC-02 | Dado un socio con la cuota agotada en su ventana, cuando envía una cotización, entonces recibe HTTP 429 con Retry-After y reset_at | TC-02 |
| SC-03 | UC-03, AC-03 | Dado un socio con cuota agotada y otro socio con cuota disponible cotizando al mismo tiempo, cuando llegan sus peticiones, entonces el segundo socio recibe 200 OK normalmente | TC-03 |
| SC-04 | UC-02, AC-05 | Dado un socio con cuota agotada, cuando reenvía una Idempotency-Key válida de una cotización previa, entonces recibe la cotización previa con 200 OK | TC-04 |
| SC-05 | UC-04, AC-06 | Dado un socio con cuota agotada, cuando transcurre el tiempo superando quota_reset_at, entonces la cuota se reinicia automáticamente y cotiza con 200 OK | TC-05 |
| SC-06 | UC-02, QR-01 | Dado un socio con cuota agotada, cuando el backend evalúa la petición, entonces responde 429 de forma ultra-rápida sin invocar buró ni tarificación | TC-06 |
| SC-07 | UC-02, SR-02, SR-04 | Dado el contrato OpenAPI y los rechazos por cuota, cuando se valida el esquema y los logs, entonces declara 429 y emite el evento PARTNER_QUOTA_EXCEEDED | TC-07 |
| SC-08 | UC-05, AC-10 | Dado un cliente que ingresa al cotizador, cuando solicita consentimiento con OTP y lo valida, entonces se persiste el consentimiento en base de datos y se autoriza a cotizar | TC-08 |
| SC-09 | UC-06, AC-07 | Dado un cliente que acepta una cotización, cuando ingresa su OTP de firma, entonces se genera la póliza con constancia inmutable en base de datos y hash SHA-256 en la carátula | TC-09 |
| SC-10 | UC-07, AC-08 | Dado un fallo en la pasarela de pagos o en la emisión, cuando se retorna un código HTTP (402, 404, 409, 422, 502), entonces el portal muestra un mensaje específico y bloquea reintentos en definitivos | TC-10 |
| SC-11 | UC-01, AC-13 | Dada una petición de cotización en producción, cuando se envía un parámetro de prueba o simulación como simulated_delay, entonces el parámetro es ignorado y no introduce retrasos | TC-11 |
| SC-12 | UC-01, AC-14 | Dado un pago procesado desde el portal o API, cuando se registra el cobro, entonces queda asociado al partner_id del token autenticado y no a 'default' | TC-12 |
| SC-13 | UC-08, AC-15 | Dado un nuevo socio que se registra por autoservicio, cuando completa el formulario, entonces recibe credenciales de sandbox con cuota inicial y permanece en sandbox hasta su aprobación | TC-13 |
| SC-14 | UC-09, AC-16 | Dada una póliza con fecha de vigencia vencida, cuando ejecuta la rutina de mantenimiento diario, entonces la póliza pasa a estado EXPIRED y habilita la acción de renovación | TC-14 |
| SC-15 | UC-10, AC-17 | Dado un proceso de cotización, cuando se evalúa la asegurabilidad, entonces se consulta la fuente de Open Data real datos.gov.co con timeout de 400 ms y degradación ante fallo | TC-15 |
| SC-16 | UC-01, AC-11 | Dada una fecha de nacimiento que corresponda a menor de 18 años o mayor de 75 años, cuando se envía la cotización, entonces el backend responde HTTP 422 | TC-16 |
| SC-17 | UC-01, AC-12 | Dada una cotización de crédito, cuando se capturan la entidad financiera y el plazo, entonces estos datos se guardan persistentemente junto a la cotización | TC-17 |
| SC-18 | UC-05, AC-09, QR-03 | Dado el portal web de asesores en producción, cuando se navega por el cotizador y pago, entonces no contiene datos de prueba por defecto y cumple accesibilidad WCAG 2.2 AA | TC-18 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-08, SC-09, SC-11, SC-12, SC-13, SC-15, SC-17 | Flujo estándar de cotización, consentimiento, firma y autoservicio de socios |
| Alternate flows | YES | SC-04, SC-05, SC-10, SC-14 | Reintentos idempotentes, reinicio de cuota, diagnóstico de pagos y renovación |
| Invalid input and errors | YES | SC-02, SC-10, SC-16 | Cuota agotada, errores diferenciados de cobro y validación de rango de edad |
| Limits and boundaries | YES | SC-02, SC-05, SC-16 | Límites numéricos de quota_limit, frontera de reset_at y edades 18 y 75 años |
| Related flows before and after | YES | SC-01, SC-02, SC-08, SC-09 | Dependencia entre consentimiento previo, cotización y posterior firma de póliza |
| Several users, sessions, devices or roles | YES | SC-03, SC-12, SC-13 | Múltiples socios B2B, roles de asesor, cliente y analista de operaciones |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-02, SC-04, SC-05, SC-14 | Vigencia de cuotas, expiración de pólizas y manejo de idempotencia |

## Test cases

### TC-01 · Emisión exitosa de cotización con cabeceras de cuota (200 OK)

| Field | Value |
|---|---|
| Covers | AC-04, CRY-01 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Socio B2B autenticado con credenciales activas y cuota disponible |
| Procedure | 1. Enviar solicitud POST /api/v1/quote-edge/quotes con Bearer token y Idempotency-Key. 2. Verificar código HTTP de respuesta y presencia de cabeceras de cuota. |
| Expected result | Retorna 200 OK y las cabeceras `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` y `X-RateLimit-Reset-Time`. |
| Tool or method | pytest backend/tests/integration/test_partner_quote_api.py::test_partner_quote_includes_ratelimit_headers |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_partner_quote_api.py::test_partner_quote_includes_ratelimit_headers |
| Status | PASS |

### TC-02 · Rechazo explícito 429 con Retry-After y fecha de renovación al agotar cuota

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, SR-01, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-01, THR-02 |
| Level | INTEGRATION |
| Preconditions | Socio B2B configurado con cuota acotada (ej. quota_limit=2) |
| Procedure | 1. Consumir las 2 solicitudes permitidas en la ventana activa. 2. Enviar una 3.ª solicitud de cotización con una nueva Idempotency-Key. 3. Verificar código HTTP, cabeceras, cuerpo JSON y eventos de log. |
| Expected result | Retorna HTTP 429 Too Many Requests, cabecera `Retry-After > 0`, `X-RateLimit-Remaining: 0`, cuerpo con `error: QUOTA_EXCEEDED`, `retry_after`, `reset_at` en formato ISO, y log estructurado `PARTNER_QUOTA_EXCEEDED`. |
| Tool or method | pytest backend/tests/integration/test_partner_quote_api.py::test_partner_quota_exceeded_returns_429_with_retry_after |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_partner_quote_api.py::test_partner_quota_exceeded_returns_429_with_retry_after |
| Status | PASS |

### TC-03 · Aislamiento multi-tenant entre socios concurrentes ante cuota agotada (HA-07)

| Field | Value |
|---|---|
| Covers | AC-03, SR-01, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-01, THR-03 |
| Level | INTEGRATION |
| Preconditions | Dos socios B2B activos: Socio A (Banco Aliado con cuota en 0) y Socio B (Cooperativa con cuota disponible) |
| Procedure | 1. Socio A envía solicitud a Quote Edge. 2. Socio B envía solicitud simultáneamente. 3. Comparar respuestas y validar estado en base de datos. |
| Expected result | Socio A recibe 429 Too Many Requests. Socio B recibe 200 OK con su cotización calculada y su propia cuota decrementada sin afectación. |
| Tool or method | pytest backend/tests/integration/test_partner_quote_api.py::test_partner_quota_isolation_between_concurrent_partners |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_partner_quote_api.py::test_partner_quota_isolation_between_concurrent_partners |
| Status | PASS |

### TC-04 · Reintento idempotente exitoso sin consumir cuota ni bloquear por 429

| Field | Value |
|---|---|
| Covers | AC-05 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Socio B2B con cotización previa guardada y cuota actual en 0 |
| Procedure | 1. Reenviar solicitud con la misma Idempotency-Key y payload de la cotización previa. 2. Evaluar status y cabeceras. |
| Expected result | Retorna 200 OK con la cotización guardada previamente sin incrementar quota_used ni rechazar con 429. |
| Tool or method | pytest backend/tests/integration/test_partner_quote_api.py::test_partner_idempotent_retry_allowed_when_quota_exhausted |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_partner_quote_api.py::test_partner_idempotent_retry_allowed_when_quota_exhausted |
| Status | PASS |

### TC-05 · Reinicio y renovación automática de cuota al expirar la ventana temporal

| Field | Value |
|---|---|
| Covers | AC-06, SR-03, QR-02 |
| Kind | FUNCTIONAL |
| Risk covered | THR-01 |
| Level | UNIT |
| Preconditions | Socio B2B con quota_reset_at superado por la hora actual |
| Procedure | 1. Invocar consume_partner_quota simulando tiempo posterior a quota_reset_at. 2. Verificar que quota_used se reinicie a 1 y quota_reset_at se extienda una nueva ventana. |
| Expected result | La cuota se reinicia atómicamente y la solicitud se procesa con normalidad. |
| Tool or method | pytest backend/tests/unit/test_partner_quota.py::test_quota_window_auto_reset |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/unit/test_partner_quota.py::test_quota_window_auto_reset |
| Status | PASS |

### TC-06 · Latencia ultra-baja en el rechazo por cuota en capa de borde

| Field | Value |
|---|---|
| Covers | QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | PERFORMANCE |
| Preconditions | Socio con cuota agotada |
| Procedure | 1. Enviar ráfagas de solicitudes de cotización midiendo el tiempo de respuesta hasta el retorno del 429. |
| Expected result | La respuesta 429 se entrega en un tiempo p95 ≤ 25 ms sin invocar buró ni realizar cómputos de primas. |
| Tool or method | pytest backend/tests/integration/test_partner_quote_api.py::test_partner_quota_rejection_latency_performance |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_partner_quote_api.py::test_partner_quota_rejection_latency_performance |
| Status | PASS |

### TC-07 · Documentación de 429 en OpenAPI y auditoría estructurada

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, SR-02, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-02, THR-04 |
| Level | CONTRACT |
| Preconditions | Backend en ejecución o cliente de pruebas montado sobre FastAPI app |
| Procedure | 1. Consultar `/openapi.json`. 2. Validar que la ruta `/api/v1/quote-edge/quotes` declare la respuesta 429 con esquema `PartnerQuotaExceededResponse` y sus cabeceras. |
| Expected result | El esquema OpenAPI documenta formalmente la respuesta 429 y cabeceras Retry-After y X-RateLimit-*. |
| Tool or method | pytest backend/tests/integration/test_partner_quote_api.py::test_partner_quote_openapi_spec_documents_429 |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_partner_quote_api.py::test_partner_quote_openapi_spec_documents_429 |
| Status | PASS |

### TC-08 · Validación y persistencia obligatoria de consentimiento con OTP antes de cotizar

| Field | Value |
|---|---|
| Covers | AC-10, SR-05 |
| Kind | SECURITY |
| Risk covered | THR-05 |
| Level | INTEGRATION |
| Preconditions | Cliente asegurado en cotizador sin consentimiento previo |
| Procedure | 1. Intentar cotizar sin `consent_id`. 2. Verificar respuesta 422. 3. Solicitar OTP, ingresar código válido y persistir consentimiento. 4. Reintentar cotización con `consent_id` válido. |
| Expected result | Rechazo inicial con 422 si falta consentimiento; aprobación 200 OK con consentimiento verificado por OTP en PostgreSQL. |
| Tool or method | pytest backend/tests/integration/test_consent_flow.py |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_consent_flow.py |
| Status | PASS |

### TC-09 · Firma de póliza con OTP, inmutabilidad de firma y hash de PDF

| Field | Value |
|---|---|
| Covers | AC-07, SR-06 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | INTEGRATION |
| Preconditions | Cotización aceptada pendiente de emisión |
| Procedure | 1. Solicitar emisión con código OTP. 2. Verificar creación de fila en `policy_signatures` y hash SHA-256 en carátula. 3. Intentar modificar o eliminar la fila de firma vía SQL. |
| Expected result | Emisión exitosa con hash estampado y bloqueo por trigger de base de datos ante intento de alteración o borrado de firma. |
| Tool or method | pytest backend/tests/integration/test_policy_signature.py |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_policy_signature.py |
| Status | PASS |

### TC-10 · Mensajes específicos de fallo por código HTTP y control de reintento

| Field | Value |
|---|---|
| Covers | AC-08 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Componente de pago y emisión montado en entorno de pruebas web |
| Procedure | 1. Simular respuestas 402, 404, 409, 422 y 502 en cobro. 2. Verificar que se muestre el mensaje correspondiente a cada código. 3. Verificar que el botón de reintento no esté disponible en fallos definitivos. |
| Expected result | Mensajes claros por código y botón de reintento oculto en 402, 404, 409 y 422. |
| Tool or method | npm test web/src/app/features/cotizador/payment-step/payment-step.component.spec.ts |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | web/src/app/features/cotizador/payment-step/payment-step.component.spec.ts |
| Status | PASS |

### TC-11 · Supresión de parámetro de simulación simulated_delay en cotización

| Field | Value |
|---|---|
| Covers | AC-13, SR-07 |
| Kind | SECURITY |
| Risk covered | THR-08 |
| Level | CONTRACT |
| Preconditions | Endpoint de cotización expuesto |
| Procedure | 1. Enviar solicitud de cotización incluyendo parámetro `simulated_delay=5`. 2. Medir tiempo de respuesta y consultar contrato OpenAPI. |
| Expected result | La solicitud procesa en tiempo normal sin demora simulada y OpenAPI no expone el parámetro. |
| Tool or method | pytest backend/tests/integration/test_quote_parameters.py |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_quote_parameters.py |
| Status | PASS |

### TC-12 · Atribución de cobros al partner_id autenticado del token

| Field | Value |
|---|---|
| Covers | AC-14, SR-08 |
| Kind | SECURITY |
| Risk covered | THR-09 |
| Level | INTEGRATION |
| Preconditions | Transacción de cobro iniciada por usuario con token asociado a socio |
| Procedure | 1. Ejecutar cobro de prima autenticado. 2. Consultar registro en tabla `payments`. |
| Expected result | El campo `partner_id` del cobro coincide con el socio del token y no con el valor 'default'. |
| Tool or method | pytest backend/tests/integration/test_payments_partner.py |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_payments_partner.py |
| Status | PASS |

### TC-13 · Registro de socio por autoservicio en sandbox y promoción aprobada

| Field | Value |
|---|---|
| Covers | AC-15, SR-09 |
| Kind | SECURITY |
| Risk covered | THR-10 |
| Level | INTEGRATION |
| Preconditions | Endpoint `POST /partners/register` disponible |
| Procedure | 1. Registrar nuevo socio por autoservicio. 2. Verificar entrega de credenciales con `environment='sandbox'` y cuota inicial. 3. Ejecutar aprobación a producción por analista. |
| Expected result | Socio queda en sandbox con cuota asignada; promoción cambia `environment='production'` solo tras aprobación autorizada. |
| Tool or method | pytest backend/tests/integration/test_partner_registration.py |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_partner_registration.py |
| Status | PASS |

### TC-14 · Expiración de pólizas vencidas y flujo de renovación con vigencia

| Field | Value |
|---|---|
| Covers | AC-16, SR-10 |
| Kind | SECURITY |
| Risk covered | THR-07 |
| Level | INTEGRATION |
| Preconditions | Póliza emitida cuya fecha fin de vigencia es anterior a la fecha actual |
| Procedure | 1. Ejecutar rutina de expiración diaria. 2. Verificar transición a estado `EXPIRED` y evento en auditoría. 3. Invocar endpoint de renovación. |
| Expected result | La póliza pasa a estado `EXPIRED` y se genera una nueva vigencia renovada con tarifa actual. |
| Tool or method | pytest backend/tests/integration/test_policy_expiration.py |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_policy_expiration.py |
| Status | PASS |

### TC-15 · Consulta de Open Data real en perfilamiento con degradación ante fallo

| Field | Value |
|---|---|
| Covers | AC-17 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Servicio de perfilamiento activo |
| Procedure | 1. Invocar perfilamiento con conexión activa a `datos.gov.co`. 2. Simular indisponibilidad o timeout (>400 ms) en la fuente externa. |
| Expected result | En éxito incorpora datos de Open Data; ante falla degrada limpiamente al score base sin interrumpir la cotización. |
| Tool or method | pytest backend/tests/unit/test_quote_validation.py::test_open_data_provider_fallback |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/unit/test_quote_validation.py::test_open_data_provider_fallback |
| Status | PASS |

### TC-16 · Validación de fecha de nacimiento tipo date y rango de 18 a 75 años

| Field | Value |
|---|---|
| Covers | AC-11 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Servicio `RatingService.create_quote` |
| Procedure | 1. Enviar cotización con fecha de nacimiento correspondiente a 17 años. 2. Enviar con 76 años. 3. Enviar con fecha futura. 4. Enviar con 35 años. |
| Expected result | Casos fuera de 18 a 75 años o fechas futuras retornan error 422; edad de 35 años es procesada exitosamente. |
| Tool or method | pytest backend/tests/unit/test_quote_validation.py |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/unit/test_quote_validation.py::test_quote_accepts_valid_age |
| Status | PASS |

### TC-17 · Persistencia de entidad financiera y plazo del crédito en la cotización

| Field | Value |
|---|---|
| Covers | AC-12 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Solicitud de cotización de crédito con `financial_entity="Banco Test"` y `term_months=36` |
| Procedure | 1. Enviar cotización con los datos de crédito. 2. Consultar el registro en la tabla `quotes` de base de datos. |
| Expected result | Los campos `financial_entity` y `term_months` quedan almacenados correctamente en base de datos. |
| Tool or method | pytest backend/tests/unit/test_quote_validation.py |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/unit/test_quote_validation.py::test_quote_persists_credit_fields |
| Status | PASS |

### TC-18 · Eliminación de datos y medios de prueba en producción y verificación de accesibilidad

| Field | Value |
|---|---|
| Covers | AC-09, QR-03 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | Portal web de asesores ejecutándose en entorno de pruebas |
| Procedure | 1. Inspeccionar campos de formulario en consentimiento y cotizador. 2. Verificar opciones de pago. 3. Auditar cumplimiento de accesibilidad en teclado y contrastes. |
| Expected result | No aparecen datos de documento ni tarjetas de prueba por defecto, y los componentes cumplen WCAG 2.2 AA. |
| Tool or method | npm test web/src/app/features/cotizador/cotizador.component.spec.ts |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | web/src/app/features/cotizador/cotizador.component.spec.ts |
| Status | PASS |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-01 | Peticiones sin token Bearer son rechazadas con 401 antes de validar cuota |
| User without permissions | YES | TC-03 | Roles ajenos a 'partner' no pueden acceder a Quote Edge |
| Access to another user's resources | YES | TC-03, TC-12 | El socio solo accede a su propio estado de cuota y no puede desviar cobros |
| Cross-tenant access | YES | TC-03, TC-12 | Cuotas y cobros totalmente aislados en base de datos por partner_id |
| Malicious input | YES | TC-02, TC-16 | Manejo robusto de datos de entrada sin fugas de información y validación de tipos |
| Expired tokens | YES | TC-01 | Tokens JWT expirados son rechazados de inmediato |
| Revoked sessions | YES | TC-01 | Socios inactivos o revocados no pueden cotizar ni consumir cuota |
| Tampered data | YES | TC-01, TC-09 | Tokens manipulados y firmas de pólizas alteradas son detectados y rechazados |
| Wrong keys or algorithms | YES | TC-01 | Firmas no coincidentes son descartadas inmediatamente |
| External service failure | YES | TC-02, TC-15 | Degradación controlada ante indisponibilidad de fuentes externas sin bloquear el flujo |
| Race conditions | YES | TC-03, TC-09 | Actualizaciones transaccionales en PostgreSQL protegidas con locks y triggers |
| Information exposure in errors | YES | TC-02, TC-10 | Respuestas 429 y mensajes web estandarizados sin stack traces ni datos internos |
| Secrets in code or logs | YES | TC-02 | Eventos de auditoría de cuota sin contraseñas ni identificaciones |
| Insecure configuration | YES | TC-05, TC-11 | Supresión de parámetros de depuración y ventanas de tiempo seguras por defecto |
| Vulnerable dependencies | YES | TC-07 | FastAPI y SQLAlchemy evaluadas periódicamente sin vulnerabilidades |

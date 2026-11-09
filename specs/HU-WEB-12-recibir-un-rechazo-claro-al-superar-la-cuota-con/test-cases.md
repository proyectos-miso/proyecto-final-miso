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

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01 | Flujo estándar de cotización exitosa con telemetría de cuota |
| Alternate flows | YES | SC-04, SC-05 | Reintentos idempotentes y reinicio automático de ventana temporal |
| Invalid input and errors | YES | SC-02 | Cuota agotada y rechazo explícito con 429 |
| Limits and boundaries | YES | SC-02, SC-05 | Límites numéricos de quota_limit y frontera temporal de reset_at |
| Related flows before and after | YES | SC-01, SC-02 | Relación entre consumo acumulado de cuota y bloqueo posterior |
| Several users, sessions, devices or roles | YES | SC-03 | Múltiples socios B2B concurrentes (aislamiento multi-tenant) |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-02, SC-04, SC-05 | Cálculo de Retry-After, vigencia de cuota e idempotencia |

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

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-01 | Peticiones sin token Bearer son rechazadas con 401 antes de validar cuota |
| User without permissions | YES | TC-03 | Roles ajenos a 'partner' no pueden acceder a Quote Edge |
| Access to another user's resources | YES | TC-03 | El socio solo accede a su propio estado de cuota y cotizaciones |
| Cross-tenant access | YES | TC-03 | Cuotas totalmente aisladas en base de datos por partner_id |
| Malicious input | YES | TC-02 | Manejo robusto de datos de entrada sin fugas de información |
| Expired tokens | YES | TC-01 | Tokens JWT expirados son rechazados de inmediato |
| Revoked sessions | YES | TC-01 | Socios inactivos o revocados no pueden cotizar ni consumir cuota |
| Tampered data | YES | TC-01 | Tokens manipulados son rechazados por validación criptográfica |
| Wrong keys or algorithms | YES | TC-01 | Firmas no coincidentes son descartadas inmediatamente |
| External service failure | YES | TC-02 | Cuando la cuota está agotada, ni siquiera se intenta contactar al buró |
| Race conditions | YES | TC-03 | Actualizaciones transaccionales en PostgreSQL |
| Information exposure in errors | YES | TC-02 | Respuesta 429 estandarizada sin stack traces ni datos internos |
| Secrets in code or logs | YES | TC-02 | Eventos de auditoría de cuota sin contraseñas ni identificaciones |
| Insecure configuration | YES | TC-05 | Configuración con ventanas de tiempo explícitas y seguras por defecto |
| Vulnerable dependencies | YES | TC-07 | FastAPI y SQLAlchemy evaluadas periódicamente sin vulnerabilidades |

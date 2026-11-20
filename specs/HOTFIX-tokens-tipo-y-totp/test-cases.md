# Test cases · HOTFIX

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | AC-01 | Dado un token firmado sin `type`, cuando llama un endpoint con rol, entonces recibe 401 | TC-01 |
| SC-02 | AC-01 | Dado un token de refresco, cuando llama un endpoint con rol, entonces recibe 401 | TC-01 |
| SC-03 | UC-02 | Dado un token de acceso válido, cuando llama el endpoint, entonces pasa la autenticación | TC-01 |
| SC-04 | AC-02 | Dado un token de socio sin `type`, cuando llama el canal B2B, entonces recibe 401; con el token emitido por el backend, pasa | TC-02 |
| SC-05 | AC-03 | Dado un ingreso exitoso con un código, cuando se repite el mismo código, entonces recibe 401 | TC-03 |
| SC-06 | AC-04 | Dado un ingreso con el código actual, cuando se usa el código anterior, entonces recibe 401; y el siguiente código sí entra | TC-04 |
| SC-07 | UC-01 | Dado el ingreso normal, cuando se entra con un código nuevo, entonces funciona como antes | TC-03, TC-05 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-03, SC-07 | — |
| Alternate flows | YES | SC-06 | — |
| Invalid input and errors | YES | SC-01, SC-02, SC-04 | — |
| Limits and boundaries | YES | SC-06 | — |
| Related flows before and after | YES | SC-07 | — |
| Several users, sessions, devices or roles | YES | SC-04 | — |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-05, SC-06 | — |

## Test cases

### TC-01 · Solo el token de acceso abre la API

| Field | Value |
|---|---|
| Covers | AC-01, AC-05, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Token sin `type`, token de refresco y token de acceso del asesor |
| Procedure | 1. Llamar POST /experience/quotes con cada token |
| Expected result | 401 con el token sin tipo y con el de refresco; 422 (validación del cuerpo) con el de acceso |
| Tool or method | pytest backend/tests/integration/test_tokens_tipo_y_totp.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_tokens_tipo_y_totp.py::test_tc_01_un_token_sin_tipo_no_abre_la_api; backend/tests/integration/test_tokens_tipo_y_totp.py::test_tc_01_un_token_de_refresco_no_abre_la_api; backend/tests/integration/test_tokens_tipo_y_totp.py::test_tc_01_el_token_de_acceso_sigue_funcionando |
| Status | PASS |

### TC-02 · Canal de socios

| Field | Value |
|---|---|
| Covers | AC-02, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Token de socio sin `type` y token emitido por `create_partner_access_token` |
| Procedure | 1. Consultar una cotización del canal B2B con cada token |
| Expected result | 401 con el token sin tipo; el emitido por el backend pasa la autenticación |
| Tool or method | pytest backend/tests/integration/test_tokens_tipo_y_totp.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_tokens_tipo_y_totp.py::test_tc_02_un_token_de_socio_sin_tipo_no_abre_el_canal_b2b |
| Status | PASS |

### TC-03 · Un código TOTP sirve una sola vez

| Field | Value |
|---|---|
| Covers | AC-03, AC-05, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Asesor semilla |
| Procedure | 1. Entrar con el código actual. 2. Repetir el mismo código |
| Expected result | 200 y luego 401 con el mensaje unificado |
| Tool or method | pytest backend/tests/integration/test_tokens_tipo_y_totp.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_tokens_tipo_y_totp.py::test_tc_03_el_mismo_codigo_totp_no_sirve_dos_veces |
| Status | PASS |

### TC-04 · Un código anterior al último aceptado no sirve

| Field | Value |
|---|---|
| Covers | AC-04, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Códigos del paso anterior, el actual y el siguiente |
| Procedure | 1. Entrar con el actual. 2. Probar el anterior. 3. Avanzar 30 s y entrar con el siguiente |
| Expected result | 200, 401 y 200 |
| Tool or method | pytest backend/tests/integration/test_tokens_tipo_y_totp.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_tokens_tipo_y_totp.py::test_tc_04_un_codigo_anterior_al_ultimo_usado_tampoco_sirve |
| Status | PASS |

### TC-05 · Sin regresiones en el ingreso

| Field | Value |
|---|---|
| Covers | AC-05 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Suite del backend |
| Procedure | 1. Correr las pruebas de autenticación y la suite completa |
| Expected result | Todas pasan |
| Tool or method | pytest backend/tests/integration/test_auth_api.py; backend/tests/unit/test_auth_service.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_auth_api.py; backend/tests/unit/test_auth_service.py |
| Status | PASS |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | NO | — | No cambia |
| User without permissions | YES | TC-01 | — |
| Access to another user's resources | NO | — | No cambia |
| Cross-tenant access | YES | TC-02 | — |
| Malicious input | YES | TC-01, TC-02 | — |
| Expired tokens | NO | — | El vencimiento no cambia |
| Revoked sessions | NO | — | Fuera de alcance (RC-20) |
| Tampered data | YES | TC-01, TC-02 | — |
| Wrong keys | NO | — | La validación de firma no cambia |
| External service failures | NO | — | Sin servicios externos |
| Race conditions | NO | — | Riesgo aceptado RISK-01 |
| Information exposure | YES | TC-03 | — |
| Secrets in code | NO | — | Sin secretos nuevos |
| Insecure configuration | NO | — | Sin configuración nueva |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas |

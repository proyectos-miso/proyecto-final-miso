# Test cases · HOTFIX

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | AC-02 | Dada una cotización del documento de prueba, cuando se emite sin código, entonces recibe 422 | TC-01 |
| SC-02 | AC-01 | Dado el código fijo `839201`, cuando se usa para firmar, entonces recibe 422 | TC-02 |
| SC-03 | UC-01 | Dado el código enviado al titular, cuando se emite con él, entonces se crea la póliza con su firma inmutable | TC-03 |
| SC-04 | AC-07 | Dadas diez emisiones simultáneas con el mismo código, cuando terminan, entonces hay una póliza y todas la reciben | TC-04 |
| SC-05 | AC-05 | Dado un pedido de registro sin sesión o de un analista de socio, cuando llega, entonces recibe 401 o 403 y no se crea el socio | TC-05 |
| SC-06 | UC-03 | Dado un analista de Solventa, cuando registra un socio con correo válido, entonces recibe las credenciales de sandbox; con correo inválido, 422 | TC-06 |
| SC-07 | AC-06 | Dado un pedido de código de firma sin teléfono, cuando llega, entonces recibe 422 | TC-07 |
| SC-08 | AC-04 | Dado el portal en local, cuando el asesor pide el código, entonces ve el código de prueba; si el envío falla, ve el aviso | TC-08 |
| SC-09 | AC-03 | Dado el portal, cuando el código no tiene 6 dígitos, entonces no deja emitir; con un código inválido muestra el motivo | TC-09 |
| SC-10 | AC-08 | Dada una póliza vencida, cuando se lista, entonces se ve «Vencida» y se puede filtrar | TC-10 |
| SC-11 | UC-02 | Dado el consentimiento, cuando se registra con el código enviado, entonces queda guardado y se puede cotizar | TC-11 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-03, SC-06, SC-11 | — |
| Alternate flows | YES | SC-08 | — |
| Invalid input and errors | YES | SC-01, SC-02, SC-07, SC-09 | — |
| Limits and boundaries | YES | SC-09 | — |
| Related flows before and after | YES | SC-10, SC-11 | — |
| Several users, sessions, devices or roles | YES | SC-05 | — |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-04 | — |

## Test cases

### TC-01 · Sin código no se emite, ni con el documento de prueba

| Field | Value |
|---|---|
| Covers | AC-02, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Cotización del documento 1020304050 |
| Procedure | 1. Emitir sin `otp_code` |
| Expected result | 422 que pide el código de firma |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_policy_signature.py::test_sin_codigo_de_firma_no_se_emite_ni_con_el_documento_de_prueba |
| Status | PASS |

### TC-02 · El código fijo ya no sirve

| Field | Value |
|---|---|
| Covers | AC-01, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Cotización vigente |
| Procedure | 1. Emitir con `839201`. 2. Emitir con `000000` |
| Expected result | 422 en los dos casos |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_policy_signature.py::test_el_codigo_fijo_de_pruebas_ya_no_sirve; backend/tests/integration/test_policy_signature.py::test_policy_signature_fails_with_invalid_otp |
| Status | PASS |

### TC-03 · La emisión con el código enviado crea la firma inmutable

| Field | Value |
|---|---|
| Covers | AC-01, AC-03 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Cotización vigente |
| Procedure | 1. Pedir el código de firma. 2. Emitir con él. 3. Intentar modificar la firma |
| Expected result | 200, firma guardada y la base rechaza UPDATE y DELETE |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_policy_signature.py::test_policy_signature_requires_otp_and_creates_immutable_record |
| Status | PASS |

### TC-04 · Diez emisiones simultáneas con el mismo código

| Field | Value |
|---|---|
| Covers | AC-07, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Un código de firma |
| Procedure | 1. Lanzar diez emisiones a la vez con el mismo código |
| Expected result | Diez 200, una póliza y un evento |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_policy_issuance.py::test_diez_emisiones_simultaneas_crean_una_sola_poliza |
| Status | PASS |

### TC-05 · Sin sesión o desde un socio no se registran socios

| Field | Value |
|---|---|
| Covers | AC-05, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Analista de Banco Aliado sembrado |
| Procedure | 1. Registrar sin token. 2. Registrar con el analista del socio |
| Expected result | 401 y 403; el socio no se crea |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_partner_registration.py::test_sin_sesion_o_desde_un_socio_no_se_registran_socios |
| Status | PASS |

### TC-06 · El analista de Solventa registra un socio con correo válido

| Field | Value |
|---|---|
| Covers | AC-05, SR-02 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Analista de operaciones sembrado |
| Procedure | 1. Registrar con correo válido. 2. Autenticar con las credenciales. 3. Registrar con correo inválido |
| Expected result | 201 y token del socio; 422 con el correo inválido |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_partner_registration.py::test_partner_self_registration_sandbox_and_production_approval; backend/tests/integration/test_partner_registration.py::test_el_correo_del_socio_debe_ser_valido |
| Status | PASS |

### TC-07 · El código de firma exige teléfono

| Field | Value |
|---|---|
| Covers | AC-06, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | INTEGRATION |
| Preconditions | Asesor |
| Procedure | 1. Pedir el código de firma sin teléfono |
| Expected result | 422 |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_policy_signature.py::test_el_codigo_de_firma_exige_telefono |
| Status | PASS |

### TC-08 · Código de prueba en el portal solo cuando viene

| Field | Value |
|---|---|
| Covers | AC-04, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | UNIT |
| Preconditions | Portal con la respuesta simulada |
| Procedure | 1. Pedir el código de firma con `debug_code`. 2. Pedirlo con un 503. 3. Lo mismo en el consentimiento |
| Expected result | El código de prueba solo aparece cuando la respuesta lo trae; el fallo se avisa |
| Tool or method | Vitest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | web/src/app/features/cotizador/cotizador.component.spec.ts::"RC-02 envía el código de firma al teléfono del titular y muestra el de prueba en local"; web/src/app/features/cotizador/cotizador.component.spec.ts::"RC-02 sin código de prueba no lo muestra y un fallo del envío se avisa"; web/src/app/features/cotizador/consent-step/consent-step.component.spec.ts::"RC-06 pide el código al cliente y en local muestra el código de prueba" |
| Status | PASS |

### TC-09 · El portal exige el código y muestra el motivo del rechazo

| Field | Value |
|---|---|
| Covers | AC-03, SR-04 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Cotización y beneficiarios válidos |
| Procedure | 1. Escribir 2 dígitos. 2. Emitir con un código que el backend rechaza |
| Expected result | Botón deshabilitado; luego el motivo del 422 y se puede reintentar |
| Tool or method | Vitest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | web/src/app/features/cotizador/cotizador.component.spec.ts::"RC-02 no deja emitir sin el código de firma de 6 dígitos"; web/src/app/features/cotizador/cotizador.component.spec.ts::"RC-02 un código de firma inválido muestra el motivo y deja reintentar" |
| Status | PASS |

### TC-10 · Etiqueta «Vencida»

| Field | Value |
|---|---|
| Covers | AC-08 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Ninguna: la etiqueta sale de una tabla fija |
| Procedure | 1. Pedir la etiqueta de `EXPIRED`. 2. Revisar las opciones del filtro |
| Expected result | «Vencida» y la opción en el filtro |
| Tool or method | Vitest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | web/src/app/features/gestion/policy-labels.spec.ts::"una póliza vencida se muestra como «Vencida» y se puede filtrar (HU-MOV-11)" |
| Status | PASS |

### TC-11 · Consentimiento con el código enviado

| Field | Value |
|---|---|
| Covers | AC-01, AC-09 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Cliente sin consentimiento |
| Procedure | 1. Cotizar sin consentimiento. 2. Pedir el código. 3. Registrar con él. 4. Cotizar |
| Expected result | 422, luego el consentimiento queda guardado y la cotización sale |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_consent_flow.py::test_consent_flow_mandatory_otp_and_persistence_before_quote |
| Status | PASS |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-05 | — |
| User without permissions | YES | TC-05 | — |
| Access to another user's resources | NO | — | No cambia |
| Cross-tenant access | YES | TC-05 | — |
| Malicious input | YES | TC-02, TC-06, TC-07 | — |
| Expired tokens | NO | — | No cambia |
| Revoked sessions | NO | — | No cambia |
| Tampered data | YES | TC-02 | — |
| Wrong keys | NO | — | La llave del HMAC no cambia |
| External service failures | YES | TC-08 | — |
| Race conditions | YES | TC-04 | — |
| Information exposure | YES | TC-07, TC-08 | — |
| Secrets in code | YES | TC-02 | — |
| Insecure configuration | NO | — | Sin configuración nueva |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas |

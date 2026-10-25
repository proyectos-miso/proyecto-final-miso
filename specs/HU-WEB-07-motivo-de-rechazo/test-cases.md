# Test cases · HU-WEB-07

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | UC-01 | Dada una suma dentro del cupo, cuando el asesor cotiza, entonces ve los planes sin aviso | TC-02 |
| SC-02 | UC-02 | Dada una suma de 900 millones con puntaje 700, cuando cotiza, entonces ve que requiere revisión, el motivo y la suma máxima de 700 millones | TC-01, TC-02 |
| SC-03 | AC-09 | Dada una suma igual al cupo, cuando cotiza, entonces se aprueba | TC-01 |
| SC-04 | AC-07 | Dado un cliente de 70 años con puntaje 820 y 500 millones, cuando cotiza, entonces el motivo es el de la edad y la suma máxima 300 millones | TC-01 |
| SC-05 | AC-06 | Dado el buró sin respuesta a tiempo, cuando cotiza, entonces se usa el puntaje neutro para el cupo | TC-01 |
| SC-06 | AC-02 | Dado el aviso de rechazo, cuando el asesor pulsa "Recotizar con …", entonces se cotiza con la suma sugerida y aparecen los planes | TC-06 |
| SC-07 | AC-08 | Dado el identificador de un rechazo, cuando se intenta emitir, entonces la emisión responde que la cotización no existe | TC-04 |
| SC-08 | AC-04 | Dado un rechazo, cuando se produce, entonces queda registrado con regla, motivo, sumas y fecha | TC-03 |
| SC-09 | AC-04 | Dado un rechazo registrado, cuando alguien intenta cambiarlo o borrarlo en la base, entonces la base lo impide | TC-05 |
| SC-10 | AC-01 | Dado un rechazo, cuando se muestra, entonces no aparece la regla interna ni el puntaje | TC-06 |
| SC-11 | UC-02 | Dados varios intentos con sumas distintas del mismo documento, cuando se rechazan, entonces cada uno queda registrado | TC-03 |
| SC-12 | UC-01 | Dado un asesor sin sesión, cuando cotiza, entonces recibe 401 | TC-02 |
| SC-15 | AC-10 | Dado el paso 1 sin el plazo del crédito, cuando el asesor pulsa "Validar y continuar", entonces ve que falta el plazo, el campo queda marcado y recibe el foco | TC-08 |
| SC-13 | UC-02 | Dado el rechazo, cuando el asesor pide revisión manual a un suscriptor, entonces se envía la solicitud | OUT OF SCOPE: la revisión manual del mockup no está en los criterios de la historia |
| SC-14 | UC-03 | Dado un auditor, cuando quiere consultar los rechazos desde el portal, entonces los ve en una pantalla | OUT OF SCOPE: la historia pide el registro, no una consulta; hoy se revisa en la base |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-02 | — |
| Alternate flows | YES | SC-04, SC-05, SC-06 | — |
| Invalid input and errors | YES | SC-07, SC-12, SC-15 | — |
| Limits and boundaries | YES | SC-03 | — |
| Related flows before and after | YES | SC-07, SC-13, SC-14 | — |
| Several users, sessions, devices or roles | YES | SC-11, SC-12 | — |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-05, SC-06 | — |

## Test cases

### TC-01 · Reglas de cupo

| Field | Value |
|---|---|
| Covers | AC-05, AC-06, AC-07, AC-09 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Ninguna |
| Procedure | 1. Evaluar cada franja de puntaje justo en el cupo y por encima. 2. Evaluar 70 años con puntaje alto. 3. Evaluar con el puntaje neutro |
| Expected result | En el cupo se aprueba; por encima, rechazo con la regla y la suma máxima correctas; a los 70 años manda la regla de edad |
| Tool or method | pytest backend/tests/unit/test_underwriting_rules.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/unit/test_underwriting_rules.py::test_tc_01_el_cupo_se_aprueba_y_un_peso_mas_requiere_revision; backend/tests/unit/test_underwriting_rules.py::test_tc_01_a_los_70_anos_manda_el_cupo_por_edad_si_es_menor; backend/tests/unit/test_underwriting_rules.py::test_tc_01_el_puntaje_neutro_usa_el_cupo_de_su_franja |
| Status | PASS |

### TC-02 · Cotización con y sin rechazo

| Field | Value |
|---|---|
| Covers | AC-01, AC-03 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas; token de asesor |
| Procedure | 1. Cotizar 200 millones. 2. Cotizar 900 millones. 3. Cotizar sin token |
| Expected result | 1 trae planes y estado active; 2 trae review_required, sin planes, con motivo y suma máxima; 3 responde 401 |
| Tool or method | pytest backend/tests/integration/test_underwriting_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_underwriting_api.py::test_tc_02_dentro_del_cupo_trae_planes_sin_decision; backend/tests/integration/test_underwriting_api.py::test_tc_02_por_encima_del_cupo_requiere_revision_con_motivo_y_suma_maxima; backend/tests/integration/test_underwriting_api.py::test_tc_02_sin_sesion_responde_401 |
| Status | PASS |

### TC-03 · Registro del rechazo

| Field | Value |
|---|---|
| Covers | AC-04, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas |
| Procedure | 1. Cotizar dos sumas distintas por encima del cupo con el mismo documento. 2. Leer la tabla de rechazos |
| Expected result | Dos filas con documento, regla, motivo, sumas y fecha; ninguna columna con el puntaje |
| Tool or method | pytest backend/tests/integration/test_underwriting_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_underwriting_api.py::test_tc_03_cada_rechazo_queda_registrado_sin_el_puntaje |
| Status | PASS |

### TC-04 · Un rechazo no se emite

| Field | Value |
|---|---|
| Covers | AC-08, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Un rechazo recién producido |
| Procedure | 1. Llamar a la emisión con el quote_id del rechazo |
| Expected result | 404, sin póliza creada |
| Tool or method | pytest backend/tests/integration/test_underwriting_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_underwriting_api.py::test_tc_04_el_identificador_de_un_rechazo_no_se_puede_emitir |
| Status | PASS |

### TC-05 · Registro inmutable

| Field | Value |
|---|---|
| Covers | AC-04, SR-02, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Un rechazo registrado en PostgreSQL |
| Procedure | 1. Ejecutar UPDATE sobre la fila. 2. Ejecutar DELETE sobre la fila |
| Expected result | La base rechaza las dos sentencias y la fila queda igual |
| Tool or method | pytest backend/tests/integration/test_underwriting_api.py |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_underwriting_api.py::test_tc_05_la_base_impide_cambiar_o_borrar_un_rechazo |
| Status | PASS |

### TC-06 · Aviso y recotización en el portal

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-03, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | UNIT |
| Preconditions | CotizadorComponent con el servicio de cotización simulado |
| Procedure | 1. Responder review_required. 2. Revisar el aviso. 3. Pulsar "Recotizar con …". 4. Responder active |
| Expected result | El aviso tiene rol de alerta, el motivo y la suma en COP, sin la regla ni el puntaje; el botón cotiza con la suma sugerida; luego aparecen los planes sin aviso |
| Tool or method | npm test (cotizador.component.spec.ts) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | web/src/app/features/cotizador/cotizador.component.spec.ts::"TC-06 muestra el motivo y la suma máxima en COP como alerta, sin planes ni códigos internos"; web/src/app/features/cotizador/cotizador.component.spec.ts::"TC-06 recotiza en un clic con la suma sugerida y luego muestra los planes sin aviso" |
| Status | PASS |

### TC-07 · Accesibilidad del aviso

| Field | Value |
|---|---|
| Covers | QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | Portal en el navegador con lector de pantalla |
| Procedure | 1. Cotizar por encima del cupo. 2. Escuchar el aviso. 3. Llegar al botón con Tab y activarlo con Enter |
| Expected result | El lector anuncia el aviso al aparecer; el botón se alcanza y se activa con teclado y su texto incluye la suma |
| Tool or method | NVDA o el lector del sistema, y teclado |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | Prueba manual de Juan Camilo Peña en el portal, 2026-10-25, y prueba de punta a punta en Chrome contra el portal y el backend locales (23 de 23 verificaciones): el aviso tiene rol de alerta, Tab llega a "Recotizar con…" y Enter recotiza con la suma sugerida |
| Status | PASS |

### TC-08 · Campos que faltan en el paso 1

| Field | Value |
|---|---|
| Covers | AC-10 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | ClientDataStepComponent con datos válidos salvo el plazo y el teléfono |
| Procedure | 1. Comprobar que el botón está habilitado. 2. Enviar el formulario. 3. Revisar el mensaje, los campos marcados y el foco |
| Expected result | El mensaje nombra el plazo y el teléfono; esos campos tienen aria-invalid; el foco queda en el primero; no se llama al backend |
| Tool or method | npm test (client-data-step.component.spec.ts) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | web/src/app/features/cotizador/client-data-step/client-data-step.component.spec.ts::"TC-08 con datos incompletos el botón sigue habilitado y al enviar dice qué falta, marca los campos y enfoca el primero" |
| Status | PASS |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-02 | — |
| User without permissions | NO | — | El endpoint ya exige advisor; no cambia |
| Access to another user's resources | NO | — | Sin recursos por usuario |
| Cross-tenant access | NO | — | Sin socios en esta historia |
| Malicious input | YES | TC-03, TC-04 | — |
| Expired tokens | NO | — | Sin cambios en la sesión |
| Revoked sessions | NO | — | Sin cambios en la sesión |
| Tampered data | YES | TC-05 | — |
| Wrong keys | NO | — | Sin llaves |
| External service failures | YES | TC-01 | — |
| Race conditions | NO | — | Cada rechazo es un insert independiente |
| Information exposure | YES | TC-06 | — |
| Secrets in code | NO | — | Sin secretos |
| Insecure configuration | NO | — | Sin configuración nueva |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas |

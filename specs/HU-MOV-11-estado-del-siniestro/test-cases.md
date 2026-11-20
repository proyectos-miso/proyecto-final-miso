# Test cases · HU-MOV-11

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | AC-01 | Dado un asegurado con siniestros en dos pólizas y otra persona con siniestros, cuando consulta, entonces ve solo los suyos del más reciente al más antiguo | TC-01 |
| SC-02 | AC-02 | Dado un siniestro pagado, cuando lo abre, entonces ve Recibido, En evaluación, Aprobado y Pagado con fecha y explicación | TC-01 |
| SC-03 | AC-07 | Dado un siniestro paramétrico rechazado, cuando lo abre, entonces ve el motivo, el canal de revisión y que se abrió automáticamente | TC-02 |
| SC-04 | AC-06 | Dado un asegurado sin siniestros, cuando entra, entonces ve el estado vacío | TC-03 |
| SC-05 | AC-04 | Dado un asegurado sin señal con copia, cuando entra, entonces ve la copia con la fecha de la última actualización; sin copia, se le pide conexión | TC-04 |
| SC-06 | AC-03 | Dados 10.000 siniestros, cuando consulta, entonces el p95 es menor a 150 ms | TC-05 |
| SC-07 | UC-04 | Dada una póliza propia, cuando pide la carátula, entonces recibe un enlace https; ajena 404, cancelada 409, en generación 503 | TC-06 |
| SC-08 | UC-01 | Dado un token del portal o sin token, cuando consulta, entonces 403 o 401 | TC-07 |
| SC-09 | UC-03 | Dada una póliza a más de 30 días, cuando renueva, entonces 409; a 10 días, se extiende un año; ajena, 404 | TC-08 |
| SC-10 | AC-08 | Dado un teléfono registrado, cuando el siniestro cambia de estado, entonces llega un push con solo el radicado | TC-09 |
| SC-11 | AC-08 | Dado Firebase caído o sin configurar, cuando cambia el estado, entonces el dictamen se guarda igual | TC-10 |
| SC-12 | AC-13 | Dada una póliza vencida sin renovar, cuando corre la tarea diaria, entonces queda vencida y al renovarla vuelve a pendiente de pago | TC-11 |
| SC-13 | AC-11 | Dado un asegurado que cierra sesión, cuando se borra la copia, entonces también se borran los siniestros | TC-12 |
| SC-14 | AC-08 | Dada una sincronización que trae un cambio de estado, cuando termina, entonces se avisa una sola vez y la notificación abre ese siniestro | TC-13 |
| SC-15 | AC-09 | Dada una póliza a 20 días, cuando corre la tarea dos veces, entonces el aviso sale una sola vez | TC-15 |
| SC-16 | AC-12 | Dada una póliza a 10 días en el portal, cuando el analista renueva, entonces ve la versión nueva | TC-17 |
| SC-17 | UC-02 | Dado que el asegurado negó las notificaciones, cuando cambia el estado, entonces lo ve al abrir la app | TC-16 |
| SC-18 | UC-01 | Dado que falla la consulta de siniestros, cuando sincroniza, entonces la billetera se actualiza y conserva la copia anterior de siniestros | TC-13 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-02, SC-10 | — |
| Alternate flows | YES | SC-03, SC-04, SC-05, SC-17 | — |
| Invalid input and errors | YES | SC-07, SC-09, SC-11, SC-18 | — |
| Limits and boundaries | YES | SC-06, SC-09 | — |
| Related flows before and after | YES | SC-12, SC-13, SC-16 | — |
| Several users, sessions, devices or roles | YES | SC-01, SC-08, SC-09 | — |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-05, SC-12, SC-14, SC-15 | — |

## Test cases

### TC-01 · Solo los siniestros propios, en orden y con línea de tiempo

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-05, SR-01, SR-07 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Pólizas de María y de otra persona con siniestros |
| Procedure | 1. Pedir `GET /insured/claims` con el token de María. 2. Revisar el orden, la línea de tiempo y que no aparezcan los ajenos |
| Expected result | Solo los de María, del más reciente al más antiguo, con las etiquetas en lenguaje claro |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_claims_api.py::test_lista_solo_los_siniestros_propios_del_mas_reciente_al_mas_antiguo; movil/app/src/test/java/com/solventa/app/data/claims/HttpClaimsApiTest.kt::"lista los siniestros del mas reciente al mas antiguo con su historial" |
| Status | PASS |

### TC-02 · Rechazo con motivo y canal de revisión

| Field | Value |
|---|---|
| Covers | AC-02, AC-07 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Siniestro paramétrico rechazado de María |
| Procedure | 1. Pedir la lista. 2. Revisar el motivo, el canal y la explicación del primer paso |
| Expected result | Motivo del analista, canal de revisión y texto del evento automático |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_claims_api.py::test_rechazado_trae_motivo_y_canal_de_revision |
| Status | PASS |

### TC-03 · Sin siniestros

| Field | Value |
|---|---|
| Covers | AC-06 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | María sin siniestros |
| Procedure | 1. Pedir la lista. 2. Cargar la pantalla con una lista vacía |
| Expected result | Lista vacía y estado vacío en la app |
| Tool or method | pytest y JUnit |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_claims_api.py::test_sin_siniestros_devuelve_lista_vacia; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsViewModelTest.kt::"AC-06 sin siniestros queda vacio" |
| Status | PASS |

### TC-04 · Sin conexión

| Field | Value |
|---|---|
| Covers | AC-04 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Copia local guardada y servidor inalcanzable |
| Procedure | 1. Abrir siniestros sin conexión. 2. Repetir sin copia |
| Expected result | La copia con la fecha de la última actualización; sin copia, pide conexión |
| Tool or method | JUnit |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsViewModelTest.kt::"AC-04 sin conexion muestra la copia con la fecha de la ultima actualizacion"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsViewModelTest.kt::"sin conexion ni copia pide conexion" |
| Status | PASS |

### TC-05 · p95 con 10.000 siniestros

| Field | Value |
|---|---|
| Covers | AC-03, QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | PERFORMANCE |
| Preconditions | 10.000 siniestros en PostgreSQL local, 3 de María |
| Procedure | 1. Calentar con una consulta. 2. Medir 40 consultas. 3. Calcular el percentil 95 |
| Expected result | p95 menor a 150 ms |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_claims_api.py::test_qr_01_p95_bajo_150_ms_con_10000_siniestros |
| Status | PASS |

### TC-06 · Carátula propia y ajena

| Field | Value |
|---|---|
| Covers | AC-10, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Póliza de otra persona |
| Procedure | 1. Pedir la carátula ajena. 2. En la app, probar 200, 404, 409, 503 y un enlace http |
| Expected result | 404 para la ajena; la app solo abre enlaces https y da un mensaje claro en los demás casos |
| Tool or method | pytest y JUnit |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_claims_api.py::test_caratula_de_otra_persona_responde_404; movil/app/src/test/java/com/solventa/app/data/claims/HttpClaimsApiTest.kt::"AC-10 la caratula devuelve el enlace https o un resultado claro" |
| Status | PASS |

### TC-07 · Sin sesión o con otro rol

| Field | Value |
|---|---|
| Covers | SR-02 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | Token de asesor |
| Procedure | 1. Pedir sin token. 2. Pedir con token de asesor |
| Expected result | 401 y 403 |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_claims_api.py::test_sin_token_o_con_token_del_portal_se_rechaza |
| Status | PASS |

### TC-08 · Renovación en la ventana y solo de lo propio

| Field | Value |
|---|---|
| Covers | AC-12, SR-01, SR-08 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Pólizas de María a 200 y a 10 días; póliza ajena a 10 días |
| Procedure | 1. Renovar la de 200 días. 2. Renovar la de 10 días. 3. Renovar la ajena |
| Expected result | 409, vigencia extendida un año y 404 |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_claims_api.py::test_renovacion_solo_dentro_de_los_30_dias; backend/tests/integration/test_insured_claims_api.py::test_renovar_poliza_de_otra_persona_responde_404 |
| Status | PASS |

### TC-09 · Registro del token y push sin datos sensibles

| Field | Value |
|---|---|
| Covers | AC-08, SR-04, SR-05 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | INTEGRATION |
| Preconditions | Emisor de push falso |
| Procedure | 1. Registrar un token válido. 2. Registrar uno con etiquetas HTML. 3. Enviar el aviso de un siniestro aprobado. 4. En la app, recibir tipos conocidos y desconocidos |
| Expected result | 204 y token ligado a María; 422 al inválido; el push solo trae tipo y radicado; la app ignora los tipos desconocidos |
| Tool or method | pytest y JUnit |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_claims_api.py::test_registra_token_y_envia_push_sin_datos_sensibles; backend/tests/integration/test_insured_claims_api.py::test_token_invalido_se_rechaza; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsViewModelTest.kt::"los avisos push solo aceptan los tipos conocidos" |
| Status | PASS |

### TC-10 · Sin Firebase no falla

| Field | Value |
|---|---|
| Covers | SR-05 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | INTEGRATION |
| Preconditions | Sin credenciales de Firebase |
| Procedure | 1. Pedir el aviso de un siniestro pagado |
| Expected result | No envía y no lanza error |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_claims_api.py::test_sin_firebase_no_falla |
| Status | PASS |

### TC-11 · Vencimiento y renovación de una póliza vencida

| Field | Value |
|---|---|
| Covers | AC-13, SR-08 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Póliza vigente que venció ayer |
| Procedure | 1. Correr `expire_due`. 2. Renovar |
| Expected result | Queda vencida y al renovar pasa a pendiente de pago |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_claims_api.py::test_vencida_pasa_a_expired_y_al_renovar_vuelve_a_emitida |
| Status | PASS |

### TC-12 · Copia cifrada borrada al cerrar sesión

| Field | Value |
|---|---|
| Covers | AC-11, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | UNIT |
| Preconditions | Copia con siniestros |
| Procedure | 1. Guardar siniestros. 2. Cerrar sesión. 3. Leer la copia |
| Expected result | No quedan siniestros |
| Tool or method | JUnit |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt::"AC-11 cerrar sesion borra los siniestros con la copia" |
| Status | PASS |

### TC-13 · Aviso local de cambio de estado

| Field | Value |
|---|---|
| Covers | AC-08 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Copia con un siniestro en evaluación |
| Procedure | 1. Sincronizar con el siniestro aprobado. 2. Sincronizar otra vez. 3. Sincronizar con falla de siniestros |
| Expected result | Un aviso que abre ese siniestro; la falla conserva la copia anterior |
| Tool or method | JUnit |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt::"AC-08 un cambio de estado avisa una vez y abre ese siniestro"; movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt::"si los siniestros fallan la billetera se sincroniza y conserva la copia anterior" |
| Status | PASS |

### TC-14 · La llave de Firebase no está en el repositorio

| Field | Value |
|---|---|
| Covers | SR-06 |
| Kind | SECURITY |
| Risk covered | THR-07 |
| Level | STATIC |
| Preconditions | Rama de la historia |
| Procedure | 1. Revisar que `.gitignore` excluye `movil/app/google-services.json`. 2. Correr el escaneo de secretos del gate. 3. Revisar que la configuración recibe la ruta del archivo o el secreto de CI. 4. Correr las pruebas del inicio de Firebase |
| Expected result | Ningún archivo de Firebase versionado y el gate sin hallazgos |
| Tool or method | python sdd/tools/sdd.py gate |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | .gitignore; backend/tests/unit/test_notifications.py::test_credencial_json_mal_formada_desactiva_el_push_sin_fallar |
| Status | PASS |

### TC-15 · Aviso de vencimiento una sola vez

| Field | Value |
|---|---|
| Covers | AC-09 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Póliza a 20 días |
| Procedure | 1. Correr `remind_renewals` dos veces. 2. En la app, sincronizar dos veces |
| Expected result | Un solo aviso y un solo evento |
| Tool or method | pytest y JUnit |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_claims_api.py::test_aviso_de_vencimiento_se_envia_una_sola_vez; movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt::"AC-09 el aviso de vencimiento sale una sola vez por poliza y vigencia" |
| Status | PASS |

### TC-16 · Accesibilidad y avisos en el teléfono

| Field | Value |
|---|---|
| Covers | QR-02, AC-08 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | Emulador con TalkBack y Firebase configurado |
| Procedure | 1. Recorrer lista y detalle con TalkBack. 2. Medir los objetivos táctiles. 3. Negar las notificaciones y cambiar un estado desde el portal. 4. Permitirlas y repetir |
| Expected result | TalkBack lee estado y fecha; objetivos de 48 dp; sin permiso el cambio se ve al abrir; con permiso llega el push |
| Tool or method | Prueba manual en emulador |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | Prueba manual de Juan Camilo Peña el 2026-11-11 en un celular Android con la APK local por túnel: TalkBack, push de rechazo y de vencimiento, renovación, descarga de la carátula, copia sin conexión y cierre de sesión |
| Status | PASS |

### TC-17 · Renovación desde el portal

| Field | Value |
|---|---|
| Covers | AC-12 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Póliza a 10 días |
| Procedure | 1. Ver que con 2099 no aparece el botón. 2. Cambiar la vigencia a 10 días. 3. Pulsar «Renovar por un año» |
| Expected result | Mensaje con la versión nueva y aviso de cambio |
| Tool or method | Vitest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | web/src/app/features/gestion/policy-operations.component.spec.ts::"HU-MOV-11 renueva por un año una póliza a 30 días o menos de vencer" |
| Status | PASS |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-07 | — |
| User without permissions | YES | TC-07 | — |
| Access to another user's resources | YES | TC-01, TC-06, TC-08 | — |
| Cross-tenant access | NO | — | El asegurado no es un socio |
| Malicious input | YES | TC-09 | — |
| Expired tokens | YES | TC-07 | La dependencia de sesión de HU-MOV-03 rechaza tokens vencidos |
| Revoked sessions | YES | TC-07 | Igual que los tokens vencidos |
| Tampered data | YES | TC-06 | La app rechaza enlaces que no son https |
| Wrong keys | YES | TC-12 | Base por cuenta (ADR 0021) |
| External service failures | YES | TC-10, TC-13 | — |
| Race conditions | YES | TC-15 | El aviso se marca por versión antes del envío |
| Information exposure | YES | TC-01, TC-09 | — |
| Secrets in code | YES | TC-14 | — |
| Insecure configuration | YES | TC-14 | — |
| Vulnerable dependencies | YES | TC-14 | OSV.dev sin hallazgos (DEP-01 a DEP-03) |

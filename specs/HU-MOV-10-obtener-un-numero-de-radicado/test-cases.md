# Test cases · HU-MOV-10

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | AC-03 | Dados varios reportes distintos enviados al mismo tiempo, cuando se registran, entonces cada uno recibe un radicado distinto; un reintento conserva el suyo | TC-01 |
| SC-02 | UC-01 | Dado un reporte en cola, cuando se guarda, entonces la app muestra un radicado provisional `OFF-AAAA-XXXX` siempre igual para ese reporte | TC-02 |
| SC-03 | AC-01 | Dado un reporte recibido, cuando responde el servidor, entonces la app guarda y muestra la constancia oficial que reemplaza al provisional | TC-03 |
| SC-04 | AC-04 | Dado un siniestro radicado, cuando el asegurado vuelve a la app, entonces ve el radicado oficial en la lista y el provisional que reemplazó | TC-04 |
| SC-05 | UC-02 | Dado un sismo firmado de magnitud 6,5 o más, cuando llega, entonces se crea y liquida un siniestro con radicado por cada póliza vigente de la ciudad | TC-05 |
| SC-06 | AC-06 | Dado un evento con firma de otra llave, marca vieja, cuerpo cambiado o sin cabeceras, cuando llega, entonces se rechaza sin crear nada | TC-06 |
| SC-07 | AC-06 | Dado el mismo evento repetido o entregado dos veces a la vez, cuando llega, entonces no crea siniestros nuevos | TC-07 |
| SC-08 | UC-02 | Dado un sismo bajo el umbral, un evento mal formado o demasiado grande, o el canal sin llave, cuando llega, entonces no crea nada | TC-08 |
| SC-09 | UC-03 | Dados mensajes pendientes en el outbox, cuando corre el publicador, entonces llegan a SQS solo con identificadores; si SQS falla quedan pendientes | TC-09 |
| SC-10 | AC-08 | Dado un siniestro registrado, cuando se confirma, entonces su `ClaimFiled` queda en el outbox | TC-10 |
| SC-11 | UC-04 | Dada una base vacía o una creada antes de Alembic con datos, cuando arranca, entonces queda en la última migración sin perder datos | TC-11 |
| SC-12 | AC-01 | Dada la constancia oficial, cuando el asegurado cierra sesión, entonces se borra | TC-12 |
| SC-13 | AC-01 | Dada la constancia en la billetera, cuando el asegurado la cierra, entonces no vuelve a salir | TC-13 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-02, SC-03, SC-05 | — |
| Alternate flows | YES | SC-08, SC-13 | — |
| Invalid input and errors | YES | SC-06, SC-08 | — |
| Limits and boundaries | YES | SC-08 | — |
| Related flows before and after | YES | SC-04, SC-10, SC-11 | — |
| Several users, sessions, devices or roles | YES | SC-05, SC-12 | — |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-01, SC-06, SC-07, SC-09 | — |

## Test cases

### TC-01 · Radicado único

| Field | Value |
|---|---|
| Covers | AC-03, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | Póliza vigente de María |
| Procedure | 1. Enviar 5 reportes distintos a la vez. 2. Enviar el mismo reporte dos veces y a la vez |
| Expected result | 5 radicados distintos; el reintento conserva el suyo |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_report_api.py::test_reportes_distintos_enviados_al_mismo_tiempo_reciben_radicados_distintos; backend/tests/integration/test_claim_report_api.py::test_dos_envios_simultaneos_del_mismo_reporte_dejan_un_siniestro |
| Status | PASS |

### TC-02 · Radicado provisional

| Field | Value |
|---|---|
| Covers | AC-02, QR-02 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Reporte en cola |
| Procedure | 1. Calcular el provisional. 2. Ver la pantalla de la cola. 3. Ver el pendiente en «Mis siniestros» |
| Expected result | `OFF-AAAA-XXXX` siempre igual para el reporte, en la constancia y en el pendiente |
| Tool or method | JUnit y Compose |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/domain/claims/ProvisionalNumberTest.kt::"HU-MOV-10 AC-02 el mismo reporte da siempre el mismo provisional y otro reporte da otro"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsListScreenTest.kt::"HU-MOV-10 AC-02 el pendiente muestra el radicado provisional" |
| Status | PASS |

### TC-03 · Constancia oficial

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, QR-02 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Reporte en cola |
| Procedure | 1. Recibir la respuesta del servidor. 2. Ver la pantalla del reporte y la billetera |
| Expected result | Constancia con el radicado oficial, el provisional que reemplaza, la fecha y la póliza |
| Tool or method | JUnit y Compose |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/data/claims/ClaimSenderTest.kt::"HU-MOV-10 AC-01 recibido guarda la constancia oficial que reemplaza al provisional"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimScreensTest.kt::"HU-MOV-10 AC-02 en cola muestra la constancia provisional y al recibirse la oficial que la reemplaza"; movil/app/src/test/java/com/solventa/app/data/claims/HttpClaimSubmitApiTest.kt::"HU-MOV-10 AC-01 lee la hora oficial del registro con su zona y la tolera ausente" |
| Status | PASS |

### TC-04 · Radicado en la lista

| Field | Value |
|---|---|
| Covers | AC-04 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Siniestro recibido |
| Procedure | 1. Abrir «Mis siniestros» |
| Expected result | El radicado oficial y el provisional que reemplazó |
| Tool or method | Compose |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimsListScreenTest.kt::"HU-MOV-10 AC-04 el siniestro recibido muestra el oficial y el provisional que reemplaza" |
| Status | PASS |

### TC-05 · Siniestro paramétrico desde el oráculo

| Field | Value |
|---|---|
| Covers | AC-05 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Pólizas de Bogotá (con y sin tilde), Medellín, cancelada y sin ciudad |
| Procedure | 1. Enviar un sismo firmado de magnitud 7,1 en Bogotá |
| Expected result | Un siniestro paramétrico liquidado por cada póliza vigente de Bogotá |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_oracle_events_api.py::test_un_sismo_que_cumple_la_regla_crea_y_liquida_un_siniestro_por_poliza_de_la_ciudad |
| Status | PASS |

### TC-06 · Firma del oráculo

| Field | Value |
|---|---|
| Covers | AC-06, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Pólizas de Bogotá |
| Procedure | 1. Firmar con otra llave. 2. Usar una marca de hace 10 minutos o inválida. 3. Cambiar el cuerpo después de firmar. 4. Omitir las cabeceras |
| Expected result | 401 (422 sin cabeceras) y ningún siniestro |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_oracle_events_api.py::test_un_evento_sin_firma_valida_o_viejo_se_rechaza; backend/tests/integration/test_oracle_events_api.py::test_un_cuerpo_cambiado_despues_de_firmar_se_rechaza; backend/tests/integration/test_oracle_events_api.py::test_sin_cabeceras_de_firma_responde_422 |
| Status | PASS |

### TC-07 · Evento repetido

| Field | Value |
|---|---|
| Covers | AC-06, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Pólizas de Bogotá |
| Procedure | 1. Enviar el mismo evento dos veces. 2. Enviarlo dos veces a la vez |
| Expected result | 201 y 200 con los mismos radicados; un solo juego de siniestros |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_oracle_events_api.py::test_el_mismo_evento_no_crea_siniestros_otra_vez; backend/tests/integration/test_oracle_events_api.py::test_dos_entregas_simultaneas_del_mismo_evento_dejan_un_juego_de_siniestros |
| Status | PASS |

### TC-08 · Eventos que no crean siniestros

| Field | Value |
|---|---|
| Covers | AC-05, SR-05 |
| Kind | SECURITY |
| Risk covered | THR-05 |
| Level | INTEGRATION |
| Preconditions | Pólizas de Bogotá |
| Procedure | 1. Sismo de 5,0. 2. Evento de otro tipo. 3. Cuerpo de más de 4 KB. 4. Canal sin llave |
| Expected result | 201 sin siniestros, 422, 413 y 503 |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_oracle_events_api.py::test_un_sismo_bajo_el_umbral_no_crea_siniestros; backend/tests/integration/test_oracle_events_api.py::test_un_evento_mal_formado_responde_422; backend/tests/integration/test_oracle_events_api.py::test_un_evento_demasiado_grande_se_rechaza; backend/tests/integration/test_oracle_events_api.py::test_sin_llave_configurada_el_canal_esta_cerrado |
| Status | PASS |

### TC-09 · Publicador del outbox

| Field | Value |
|---|---|
| Covers | AC-07, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | INTEGRATION |
| Preconditions | Mensajes pendientes; cola del emulador |
| Procedure | 1. Publicar. 2. Publicar con SQS fallando después del primero. 3. Revisar el cuerpo. 4. Leer la cola real |
| Expected result | Publicados y marcados; el que falla queda pendiente con un intento y sale en la siguiente vuelta; solo identificadores; el mensaje llega con su tipo |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_outbox_publisher.py::test_publica_los_pendientes_y_los_marca; backend/tests/integration/test_outbox_publisher.py::test_si_sqs_falla_el_mensaje_queda_pendiente_y_se_reintenta; backend/tests/integration/test_outbox_publisher.py::test_el_mensaje_solo_lleva_identificadores; backend/tests/integration/test_outbox_publisher.py::test_llega_a_la_cola_sqs_del_emulador |
| Status | PASS |

### TC-10 · `ClaimFiled` en el outbox

| Field | Value |
|---|---|
| Covers | AC-08 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Pólizas de Bogotá |
| Procedure | 1. Enviar un sismo que crea dos siniestros |
| Expected result | Dos mensajes `ClaimFiled` en el outbox |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_oracle_events_api.py::test_cada_siniestro_parametrico_deja_su_evento_en_el_outbox |
| Status | PASS |

### TC-11 · Migraciones

| Field | Value |
|---|---|
| Covers | AC-09, AC-10 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | PostgreSQL con permiso de crear bases |
| Procedure | 1. Migrar una base vacía. 2. Migrar dos veces una base creada antes de Alembic con un socio |
| Expected result | Última migración, todo el esquema y el socio intacto |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_migrations.py::test_una_base_vacia_queda_en_la_ultima_migracion_con_todo_el_esquema; backend/tests/integration/test_migrations.py::test_una_base_creada_antes_de_alembic_se_adopta_sin_perder_datos |
| Status | PASS |

### TC-12 · Constancia cifrada y borrada al cerrar sesión

| Field | Value |
|---|---|
| Covers | AC-01, SR-06 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | UNIT |
| Preconditions | Constancia guardada |
| Procedure | 1. Migrar la base de la billetera a la versión 4. 2. Cerrar sesión |
| Expected result | La migración conserva los datos y agrega la constancia; al cerrar sesión se borra |
| Tool or method | JUnit y Robolectric |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/data/policies/WalletMigrationTest.kt::"HU-MOV-10 la migracion de la version 3 a la 4 conserva los datos y agrega la constancia"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"HU-MOV-10 cerrar sesion borra la constancia del reporte recibido" |
| Status | PASS |

### TC-13 · Constancia en la billetera

| Field | Value |
|---|---|
| Covers | AC-01 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Constancia guardada |
| Procedure | 1. Abrir la billetera. 2. Abrir el siniestro. 3. Cerrar la constancia |
| Expected result | Se muestra, abre el siniestro y, al cerrarla, no vuelve |
| Tool or method | JUnit y Compose |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt::"HU-MOV-10 AC-01 la billetera muestra la constancia oficial, abre el siniestro y se cierra"; movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt::"HU-MOV-10 AC-01 muestra la constancia oficial hasta que el asegurado la cierra" |
| Status | PASS |

### TC-15 · Tiempo de publicación

| Field | Value |
|---|---|
| Covers | QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | PERFORMANCE |
| Preconditions | 9 mensajes pendientes |
| Procedure | 1. Publicar un lote. 2. Revisar el ciclo del publicador |
| Expected result | El lote sale en menos de 2 segundos y el ciclo más la publicación queda por debajo de 10 segundos |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_outbox_publisher.py::test_qr_01_un_lote_sale_en_segundos_y_el_ciclo_es_menor_a_10_segundos |
| Status | PASS |

### TC-14 · Constancia en el teléfono con TalkBack

| Field | Value |
|---|---|
| Covers | QR-02 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | APK local por túnel |
| Procedure | 1. Reportar sin señal y ver la constancia provisional. 2. Volver a la red y ver la oficial. 3. Recorrer con TalkBack |
| Expected result | TalkBack lee el radicado, la fecha y la póliza; el oficial reemplaza al provisional |
| Tool or method | Prueba manual |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | Prueba manual de Juan Camilo Peña el 2026-11-14 en un celular Android con la APK local por túnel: constancia provisional sin señal, oficial al recibirse, tarjeta en la billetera, lectura con TalkBack y borrado al cerrar sesión |
| Status | PASS |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-06 | — |
| User without permissions | YES | TC-08 | Sin llave el canal está cerrado |
| Access to another user's resources | YES | TC-01 | El radicado no da acceso (HU-MOV-11) |
| Cross-tenant access | NO | — | No hay socios en esta historia |
| Malicious input | YES | TC-06, TC-08 | — |
| Expired tokens | YES | TC-06 | Marca de tiempo vieja |
| Revoked sessions | NO | — | El oráculo no tiene sesión |
| Tampered data | YES | TC-06 | Cuerpo cambiado después de firmar |
| Wrong keys | YES | TC-06 | Firma de otra llave |
| External service failures | YES | TC-09 | SQS caído |
| Race conditions | YES | TC-01, TC-07 | — |
| Information exposure | YES | TC-09 | Solo identificadores en SQS |
| Secrets in code | YES | TC-08 | La llave solo llega por configuración |
| Insecure configuration | YES | TC-08 | Sin llave el canal responde 503 |
| Vulnerable dependencies | YES | TC-11 | Alembic y Mako sin vulnerabilidades en OSV |

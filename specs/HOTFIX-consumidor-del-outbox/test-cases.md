# Test cases · HOTFIX

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | UC-01, AC-01 | Dado un siniestro reportado, cuando el analista lo rechaza, entonces responde 204, no se llama a Firebase y queda un `ClaimStatusChanged` REJECTED sin el documento | TC-01 |
| SC-02 | AC-01 | Dado un evento del oráculo que liquida, cuando llega dos veces, entonces cada siniestro deja sus SETTLED y PAID una sola vez | TC-01 |
| SC-03 | UC-02, AC-03 | Dado un siniestro reportado y consumido, cuando se liquida y se vuelve a consumir, entonces salen tres push en total y el pago va a payments | TC-02 |
| SC-04 | UC-03, AC-02, AC-03 | Dada una póliza que vence en 20 días, cuando corre el recordatorio y se consume, entonces llega el push de vencimiento con la fecha | TC-06 |
| SC-05 | AC-04 | Dado el mismo mensaje dos veces en la cola, cuando se consume, entonces sale un solo push y ambos se borran | TC-03 |
| SC-06 | AC-05, AC-13 | Dado Firebase caído o una respuesta sin ningún token entregado y con errores pasajeros, cuando se consume, entonces el mensaje no se borra, no queda marcado y al reintentar con Firebase arriba se entrega; con un 429 o con un error de configuración en todos los tokens también se reintenta y se registra la clase del error; con `UNREGISTERED`, `INVALID_ARGUMENT`, `SENDER_ID_MISMATCH` o un éxito parcial no se reintenta y esos tokens se borran junto con la marca del mensaje, aunque el borrado falle | TC-04 |
| SC-07 | AC-06, AC-09 | Dados un cuerpo no JSON, un id no UUID, un id ajeno al outbox, un tipo distinto al guardado, `PolicyIssued`, `PaymentApproved` y un tipo desconocido, cuando se consume, entonces los seis primeros se borran sin push y el desconocido vuelve a la cola aplazado al máximo | TC-05 |
| SC-08 | AC-07 | Dada la categoría claims apagada y otra póliza sin teléfono, cuando se consume, entonces no hay push y los mensajes cuentan como procesados | TC-07 |
| SC-09 | AC-08 | Dado un backend sin `SQS_EVENTS_URL` ni emulador, cuando arranca, entonces no lanza el consumidor; con cola, lo lanza y al apagar espera a que termine; una falla al leer la cola no lo detiene | TC-10 |
| SC-10 | AC-04 | Dada una base en `0005`, cuando se migra, entonces existe `processed_messages`, la subida es idempotente y la reversión la quita | TC-08 |
| SC-11 | AC-10 | Dado el módulo de ECS, cuando se planea, entonces el rol tiene solo las acciones de SQS de publicar y consumir sobre la cola `events` | TC-09 |
| SC-13 | UC-02 | Dado `app/core/outbox.py`, cuando se revisan sus importaciones, entonces no importa de `app.modules` | TC-12 |
| SC-12 | AC-03 | Dado el teléfono real con la app, cuando el analista decide un siniestro en DEV, entonces llega el push a través del consumidor | TC-11 |
| SC-14 | AC-04 | Dadas dos réplicas con el mismo mensaje a la vez y un emisor que se bloquea, cuando ambas lo procesan, entonces sale un solo push | TC-13 |
| SC-15 | AC-05, AC-14, QR-01 | Dado un lote en el que el commit de un mensaje falla, o el borrado de otro, cuando se consume, entonces el resto se procesa, el fallido se aplaza y la espera crece con cada recepción hasta 15 min | TC-14 |
| SC-16 | AC-11 | Dado un siniestro ya decidido o un `ClaimFiled` de hace 25 h, cuando se consume, entonces no sale «Recibimos» | TC-15 |
| SC-17 | AC-12 | Dado un `RenewalDue` de la versión anterior de la póliza, cuando se consume, entonces se borra sin push | TC-16 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-03, SC-04 | — |
| Alternate flows | YES | SC-08 | — |
| Invalid input and errors | YES | SC-06, SC-07, SC-15 | — |
| Limits and boundaries | YES | SC-07 | Id no UUID y tipo fuera del patrón |
| Related flows before and after | YES | SC-02, SC-10, SC-11 | — |
| Several users, sessions, devices or roles | YES | SC-05, SC-08, SC-14 | Dos entregas del mismo mensaje, también a la vez; dos titulares distintos |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-06, SC-09, SC-15, SC-16, SC-17 | — |

## Test cases

### TC-01 · Los cambios de estado quedan en el outbox y no llaman a Firebase

| Field | Value |
|---|---|
| Covers | AC-01, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | INTEGRATION |
| Preconditions | Póliza y siniestro reportado en PostgreSQL |
| Procedure | 1. Rechazar el siniestro por la API con un emisor que registra llamadas. 2. Enviar dos veces un evento del oráculo que liquida |
| Expected result | 204, sin llamadas a Firebase, un `ClaimStatusChanged` REJECTED con solo identificadores; el oráculo deja SETTLED y PAID una vez por siniestro |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_outbox_consumer.py::test_la_decision_del_analista_no_llama_a_firebase_y_solo_escribe_el_evento; backend/tests/integration/test_oracle_events_api.py::test_la_liquidacion_parametrica_deja_sus_cambios_de_estado_en_el_outbox_una_sola_vez |
| Status | PASS |

### TC-02 · El consumidor envía cada push con su categoría

| Field | Value |
|---|---|
| Covers | AC-03, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL, cola en el emulador SQS y teléfono registrado |
| Procedure | 1. Reportar un siniestro, publicar y consumir. 2. Liquidarlo, publicar y consumir |
| Expected result | Tres push al token del titular: recibido y aprobado en claims, pago en payments; la cola queda vacía |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_outbox_consumer.py::test_el_consumidor_envia_cada_push_con_su_categoria_y_el_pago_va_a_pagos |
| Status | PASS |

### TC-03 · Un mensaje repetido no envía dos push

| Field | Value |
|---|---|
| Covers | AC-04, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | El mismo cuerpo dos veces en la cola del emulador |
| Procedure | 1. Consumir hasta vaciar la cola |
| Expected result | Dos mensajes borrados, un push y una fila en `processed_messages` |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_outbox_consumer.py::test_un_mensaje_repetido_no_envia_dos_pushes |
| Status | PASS |

### TC-04 · Una falla de Firebase no borra el mensaje

| Field | Value |
|---|---|
| Covers | AC-05, AC-13, SR-06, QR-01 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | INTEGRATION |
| Preconditions | Emisor que falla; reintento con visibilidad 0; `FirebaseSender` real con `send_each` falso que devuelve un `BatchResponse`; un disparador de PostgreSQL que impide borrar de `push_tokens` |
| Procedure | 1. Consumir con Firebase caído. 2. Consumir con Firebase arriba. 3. Enviar con respuestas todas pasajeras, con un 429, con red caída, con errores de configuración en todos los tokens, con `UNREGISTERED`, `INVALID_ARGUMENT` y `SENDER_ID_MISMATCH`, y con un éxito parcial. 4. Consumir con un token rechazado en la respuesta, sin y con el disparador |
| Expected result | Primera vuelta: nada borrado ni marcado; segunda: un push y la cola vacía. Pasajeras, 429, de red o de configuración en todos los tokens: `PushDeliveryError`; sin ningún éxito, una advertencia con las clases de error y sin tokens; no registrados, mal formados o de otro proyecto: no lanza y los devuelve; parcial: no lanza ni advierte. El token rechazado se borra en la misma transacción que `processed_messages`; con el disparador, el token queda, se registra la clase del error y el mensaje igual se confirma |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_outbox_consumer.py::test_una_falla_de_firebase_no_borra_el_mensaje_y_se_entrega_al_reintentar; backend/tests/integration/test_insured_claims_api.py::test_sin_telefono_registrado_o_con_firebase_caido_no_se_envia; backend/tests/unit/test_notifications.py::test_si_todos_los_tokens_fallan_por_algo_pasajero_el_envio_lanza_para_reintentar; backend/tests/unit/test_notifications.py::test_una_excepcion_de_red_sin_ningun_exito_tambien_se_reintenta; backend/tests/unit/test_notifications.py::test_un_429_de_firebase_es_pasajero_y_se_reintenta; backend/tests/unit/test_notifications.py::test_una_configuracion_rota_que_afecta_a_todos_los_tokens_se_reintenta_y_no_borra_tokens; backend/tests/unit/test_notifications.py::test_si_ningun_token_recibe_el_aviso_se_registra_la_clase_del_error_sin_tokens; backend/tests/unit/test_notifications.py::test_un_envio_con_algun_exito_no_registra_advertencia; backend/tests/unit/test_notifications.py::test_los_tokens_no_registrados_mal_formados_o_de_otro_proyecto_no_se_reintentan_y_se_devuelven; backend/tests/unit/test_notifications.py::test_un_exito_parcial_no_se_reintenta_para_no_duplicar; backend/tests/unit/test_notifications.py::test_los_tokens_no_registrados_se_borran_despues_del_envio; backend/tests/integration/test_outbox_consumer.py::test_el_token_rechazado_se_borra_en_la_misma_transaccion_que_marca_el_mensaje; backend/tests/integration/test_outbox_consumer.py::test_si_el_borrado_del_token_falla_el_mensaje_igual_se_confirma; backend/tests/unit/test_notifications.py::test_el_emisor_de_firebase_manda_el_texto_general_con_prioridad_alta_y_un_tiempo_corto |
| Status | PASS |

### TC-05 · Mensajes mal formados, ajenos al outbox o ignorados se borran; los desconocidos vuelven

| Field | Value |
|---|---|
| Covers | AC-06, AC-09, SR-01, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Cuatro mensajes inválidos, `PolicyIssued`, `PaymentApproved` y un tipo desconocido en la cola del emulador |
| Procedure | 1. Consumir. 2. Consumir con una cola en memoria un tipo ignorado de cada clase y uno desconocido |
| Expected result | Seis borrados, ningún push, `processed_messages` vacía y el desconocido sigue en la cola; con la cola en memoria, el desconocido se aplaza 900 s |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_outbox_consumer.py::test_un_evento_mal_formado_ajeno_al_outbox_o_ignorado_se_borra_y_uno_desconocido_vuelve; backend/tests/unit/test_notifications.py::test_un_tipo_ignorado_se_borra_y_uno_sin_manejador_se_aplaza_al_maximo_hasta_la_dlq |
| Status | PASS |

### TC-06 · El recordatorio de renovación y el siniestro recibido producen su push

| Field | Value |
|---|---|
| Covers | AC-02, AC-03 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Póliza que vence en 20 días; siniestro reportado |
| Procedure | 1. Correr el recordatorio dos veces. 2. Publicar y consumir |
| Expected result | Un solo `RenewalDue`; push de vencimiento con póliza y fecha en renewals y push de recibido en claims |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_outbox_consumer.py::test_el_recordatorio_de_renovacion_y_el_siniestro_recibido_producen_sus_pushes; backend/tests/integration/test_insured_claims_api.py::test_aviso_de_vencimiento_se_envia_una_sola_vez |
| Status | PASS |

### TC-07 · Las preferencias apagadas y la falta de token cuentan como procesado

| Field | Value |
|---|---|
| Covers | AC-07, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | Categoría claims apagada; otra póliza sin teléfono |
| Procedure | 1. Reportar un siniestro en cada póliza. 2. Publicar y consumir |
| Expected result | Ningún push; dos mensajes borrados y marcados |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_outbox_consumer.py::test_una_categoria_apagada_o_un_telefono_sin_token_cuentan_como_procesados; backend/tests/integration/test_push_preferences_api.py::test_una_categoria_apagada_no_se_envia_y_las_demas_si |
| Status | PASS |

### TC-08 · Migración de `processed_messages`

| Field | Value |
|---|---|
| Covers | AC-04, SR-02, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Base vacía en `0005` sin la tabla |
| Procedure | 1. Subir dos veces. 2. Revertir a `0005` |
| Expected result | Tabla con llave primaria; versión `0006`; la reversión la quita y vuelve a `0005` |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_migrations.py::test_la_migracion_de_mensajes_procesados_crea_su_tabla_desde_0005_y_se_revierte; backend/tests/integration/test_migrations.py::test_una_base_vacia_queda_en_la_ultima_migracion_con_todo_el_esquema |
| Status | PASS |

### TC-09 · Permisos de SQS del rol de la tarea

| Field | Value |
|---|---|
| Covers | AC-10, SR-05 |
| Kind | SECURITY |
| Risk covered | THR-05 |
| Level | STATIC |
| Preconditions | Proveedor de AWS simulado |
| Procedure | 1. `terraform test` del módulo de ECS y del módulo de la cola. 2. Agregar `sqs:PurgeQueue` a mano y repetir |
| Expected result | Pasa con las cinco acciones (`SendMessage`, `GetQueueUrl`, `ReceiveMessage`, `DeleteMessage`, `ChangeMessageVisibility`) sobre el ARN de `events` y la llave de almacenamiento, y con la cola en 120 s de visibilidad, 8 recepciones y la DLQ como destino; falla con una acción de más |
| Tool or method | terraform test |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | terraform/tests/consumidor_del_outbox.tftest.hcl |
| Status | PASS |

### TC-10 · El consumidor arranca solo con cola y sobrevive a sus fallas

| Field | Value |
|---|---|
| Covers | AC-08, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | UNIT |
| Preconditions | Configuración sin cola ni emulador |
| Procedure | 1. Evaluar la configuración con cada combinación. 2. Arrancar el lifespan sin cola y con cola. 3. Hacer fallar la lectura de la cola |
| Expected result | Solo con cola o emulador; sin cola el arranque no lanza `consume_outbox`; con cola lo lanza y al apagar todas las tareas quedan terminadas; la falla imprime solo la clase del error y el bucle sigue |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/unit/test_notifications.py::test_el_consumidor_solo_arranca_con_la_cola_o_el_emulador; backend/tests/unit/test_notifications.py::test_sin_cola_configurada_el_arranque_no_lanza_el_consumidor; backend/tests/unit/test_notifications.py::test_con_la_cola_configurada_el_arranque_lanza_el_consumidor_y_al_apagar_espera_las_tareas; backend/tests/unit/test_notifications.py::test_una_falla_al_leer_la_cola_no_detiene_el_consumidor |
| Status | PASS |

### TC-11 · Push real en el teléfono a través del consumidor

| Field | Value |
|---|---|
| Covers | AC-03, QR-01 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | E2E |
| Preconditions | Backend en DEV con la cola `events` y Firebase; app instalada con sesión y avisos encendidos |
| Procedure | 1. Reportar un siniestro desde la app. 2. Decidirlo desde el portal. 3. Esperar los avisos |
| Expected result | Llegan «Recibimos su siniestro …» y el de la decisión en segundos; la petición del analista no espera a Firebase; la cola queda vacía |
| Tool or method | Prueba manual con el teléfono y la consola de SQS |
| Automation | MANUAL |
| Environment | DEV |
| Evidence | PENDING |
| Status | PENDING |

### TC-12 · `core` no depende de los módulos

| Field | Value |
|---|---|
| Covers | QR-02 |
| Kind | QUALITY |
| Risk covered | — |
| Level | STATIC |
| Preconditions | Código fuente del backend |
| Procedure | 1. Analizar las importaciones de `app/core/outbox.py` |
| Expected result | Ninguna empieza por `app.modules` ni `app.bff` |
| Tool or method | pytest con `ast` |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/unit/test_notifications.py::test_el_outbox_de_core_no_importa_modulos |
| Status | PASS |

### TC-13 · Dos réplicas a la vez con el mismo mensaje envían un solo push

| Field | Value |
|---|---|
| Covers | AC-04, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL; un emisor que se bloquea en un `asyncio.Event` hasta que la otra sesión espera |
| Procedure | 1. Procesar el mismo mensaje en dos sesiones con `asyncio.gather`. 2. Liberar el emisor |
| Expected result | Ambas terminan como procesadas; un solo push; una fila en `processed_messages` |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_outbox_consumer.py::test_dos_replicas_con_el_mismo_mensaje_a_la_vez_envian_un_solo_push |
| Status | PASS |

### TC-14 · Una falla con un mensaje no detiene el lote y la espera crece

| Field | Value |
|---|---|
| Covers | AC-05, AC-14, SR-03, SR-06, QR-01 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL y una cola en memoria |
| Procedure | 1. Hacer fallar el commit del primero de dos mensajes. 2. Hacer fallar el borrado del primero de dos. 3. Consumir mensajes que fallan con 1 a 8 recepciones |
| Expected result | El commit fallido se aplaza 30 s y el otro se borra; el borrado fallido no detiene el segundo; esperas de 30, 60, 120, 240, 480 y 900 s, tope de 900 s y 3630 s en ocho recepciones |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_outbox_consumer.py::test_una_falla_de_la_base_al_confirmar_no_detiene_el_lote_y_aplaza_el_mensaje; backend/tests/integration/test_outbox_consumer.py::test_un_borrado_que_falla_no_detiene_el_lote; backend/tests/unit/test_notifications.py::test_la_espera_entre_reintentos_crece_con_cada_recepcion_hasta_15_minutos |
| Status | PASS |

### TC-15 · Un «Recibimos» acumulado o de un siniestro ya decidido no se envía

| Field | Value |
|---|---|
| Covers | AC-11 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | PostgreSQL y la cola del emulador |
| Procedure | 1. Reportar y rechazar un siniestro antes de consumir. 2. Reportar otro y fechar su `ClaimFiled` 25 h atrás |
| Expected result | Solo el push de la decisión; el evento viejo se borra sin push |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_outbox_consumer.py::test_un_siniestro_ya_decidido_no_recibe_el_aviso_de_recibido; backend/tests/integration/test_outbox_consumer.py::test_un_siniestro_recibido_hace_mas_de_un_dia_se_borra_sin_push |
| Status | PASS |

### TC-16 · Un recordatorio de otra versión de la póliza no se envía

| Field | Value |
|---|---|
| Covers | AC-12 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Póliza que vence en 20 días |
| Procedure | 1. Correr el recordatorio. 2. Subir la versión de la póliza. 3. Publicar y consumir |
| Expected result | El mensaje se borra sin push |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_outbox_consumer.py::test_un_recordatorio_de_una_version_anterior_de_la_poliza_no_se_envia |
| Status | PASS |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | NO | — | Sin endpoint nuevo |
| User without permissions | YES | TC-09 | El rol de la tarea no tiene más permisos de SQS |
| Access to another user's resources | YES | TC-02, TC-05 | El push va al titular del evento guardado; uno ajeno se descarta |
| Cross-tenant access | NO | — | El asegurado no es un socio |
| Malicious input | YES | TC-05 | Cuerpos y tipos inválidos en la cola |
| Expired tokens | NO | — | Sin tokens de sesión |
| Revoked sessions | NO | — | Sin sesiones |
| Tampered data | YES | TC-05 | Tipo distinto al guardado en el outbox |
| Wrong keys | NO | — | Sin llaves nuevas |
| External service failures | YES | TC-04, TC-10, TC-14 | Firebase, SQS y la base caídos |
| Race conditions | YES | TC-03, TC-13 | Entrega repetida del mismo mensaje, también a la vez en dos sesiones |
| Information exposure | YES | TC-01, TC-10 | Payload y registros sin datos personales |
| Secrets in code | YES | TC-09 | El gate de secretos y la prueba de Terraform usan valores de prueba |
| Insecure configuration | YES | TC-09, TC-10 | IAM mínimo; el consumidor no arranca sin cola |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas |

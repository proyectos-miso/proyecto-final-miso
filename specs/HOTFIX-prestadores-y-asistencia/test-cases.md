# Test cases · HOTFIX

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | AC-01, AC-03 | Dado el catálogo sembrado, cuando el asegurado busca con coordenadas y con un servicio, entonces recibe hasta 20 activos ordenados por distancia, solo los que ofrecen el servicio y sin los inactivos | TC-01 |
| SC-02 | AC-02 | Dado un asegurado sin ubicación, cuando busca por ciudad, entonces recibe los de la ciudad por nombre y sin distancias; sin ubicación ni ciudad, 422 | TC-01, TC-07 |
| SC-03 | AC-09 | Dada una búsqueda con coordenadas, cuando termina, entonces no hay filas nuevas en solicitudes, outbox ni idempotencia | TC-02 |
| SC-04 | AC-12 | Dada una cuenta en verificación, sin sesión o con rol de analista, cuando usa las rutas, entonces 403, 401 y 403 | TC-03 |
| SC-05 | AC-10 | Dada una póliza ajena, vencida o inexistente, o un servicio sin prestador en la ciudad, cuando pide asistencia, entonces 404 o 409 sin registrar nada | TC-04 |
| SC-06 | AC-07 | Dada una llave usada, cuando llega el mismo cuerpo, otro cuerpo, otro asegurado con la misma llave o dos envíos a la vez, entonces la misma respuesta, 409, otra solicitud y una sola solicitud | TC-05 |
| SC-07 | AC-13 | Dadas 5 solicitudes en la última hora, cuando pide la sexta, entonces 429 con `Retry-After` | TC-06 |
| SC-08 | AC-07, SR-05 | Dada una llave ausente o malformada, coordenadas incompletas, servicio desconocido, descripción larga, campo extra o póliza malformada, cuando pide asistencia, entonces 422 | TC-07 |
| SC-09 | AC-06, AC-14 | Dada una póliza vigente, cuando pide con coordenadas o con ciudad, entonces el más cercano con el tiempo de la fórmula o 45 minutos, y el listado muestra solo las suyas | TC-08 |
| SC-10 | AC-08, AC-09 | Dada una solicitud registrada, cuando se revisa la base, entonces no hay coordenadas, el evento lleva solo identificadores y el consumidor lo ignora; la migración `0009` sube y se revierte | TC-09 |
| SC-11 | AC-01 a AC-05, AC-11 | Dada la app con y sin permiso, sin ubicación, con filtro, sin red con y sin copia, sesión vencida, intents y cada respuesta del servidor, cuando se usa, entonces cada caso deja la lista y el aviso correctos | TC-10 |
| SC-12 | AC-07, AC-11 | Dado el formulario, cuando se reintenta sin cambios, se cambia o se vuelve a abrir, entonces la llave se conserva, se renueva y se renueva | TC-11 |
| SC-13 | AC-09, UC-01 | Dada la app, cuando se revisan el código, la base de la billetera y los intents, entonces la ubicación no se registra ni se guarda, la copia vive cifrada con la billetera y se borra con ella, y no hay mapa embebido ni permiso de llamadas | TC-12 |
| SC-14 | UC-01 | Dado el arranque en producción, cuando se siembran los datos, entonces no hay prestadores ficticios; fuera de producción hay 18 con teléfonos 555 | TC-13 |
| SC-15 | UC-01, UC-02 | Dadas las pantallas, cuando se recorren con TalkBack, entonces cada control dice su nombre y mide 48 dp y los avisos se anuncian | TC-14 |
| SC-16 | UC-01, UC-02 | Dado un teléfono real en DEV, cuando se usa con y sin permiso, en modo avión y se pide asistencia, entonces todo funciona y el marcador y los mapas abren | TC-15 |
| SC-17 | UC-02 | Dada una solicitud asignada, cuando el prestador debe enterarse | OUT OF SCOPE: no hay despacho al prestador (RISK-05) |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-09, SC-16 | — |
| Alternate flows | YES | SC-02, SC-11 | Sin permiso, sin ubicación, sin red |
| Invalid input and errors | YES | SC-05, SC-08 | — |
| Limits and boundaries | YES | SC-01, SC-07, SC-08 | 20 resultados, 5 por hora, 300 caracteres, rangos |
| Related flows before and after | YES | SC-10, SC-14 | Outbox, consumidor, migración y siembra |
| Several users, sessions, devices or roles | YES | SC-04, SC-06 | Otro asegurado, analista y cuenta en verificación |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-05, SC-06, SC-11, SC-12 | Póliza vencida, reintentos, sin red y cerrar |

## Test cases

### TC-01 · Prestadores por distancia, ciudad y servicio

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-03 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas con ciudades y el catálogo ficticio |
| Procedure | 1. Buscar con coordenadas del norte de Bogotá. 2. Buscar por Medellín. 3. Filtrar por cerrajería. 4. Desactivar un prestador |
| Expected result | 18 por distancia creciente con el más cercano primero y `no-store`; 4 de Medellín por nombre sin distancia; los 3 de cerrajería; el inactivo no aparece |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_providers_and_assistance_api.py::test_con_ubicacion_ordena_los_prestadores_por_distancia; backend/tests/integration/test_providers_and_assistance_api.py::test_sin_ubicacion_filtra_por_ciudad_y_no_da_distancias; backend/tests/integration/test_providers_and_assistance_api.py::test_el_filtro_por_servicio_deja_solo_quienes_lo_ofrecen; backend/tests/integration/test_providers_and_assistance_api.py::test_un_prestador_inactivo_no_aparece; backend/tests/integration/test_providers_and_assistance_api.py::test_el_catalogo_de_ciudades_esta_disponible_para_el_asegurado |
| Status | PASS |

### TC-02 · La búsqueda no guarda la ubicación

| Field | Value |
|---|---|
| Covers | AC-09, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas |
| Procedure | Contar solicitudes, outbox e idempotencia antes y después de buscar con coordenadas |
| Expected result | Los conteos no cambian |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_providers_and_assistance_api.py::test_buscar_prestadores_no_guarda_la_ubicacion |
| Status | PASS |

### TC-03 · Sin sesión, otro rol o identidad en verificación

| Field | Value |
|---|---|
| Covers | AC-12, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Cuenta del titular en PENDING; token de analista |
| Procedure | Usar las cuatro rutas sin token y con el del analista; buscar y pedir con la cuenta PENDING |
| Expected result | 401 y 403 en todas; 403 con la cuenta PENDING y nada registrado |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_providers_and_assistance_api.py::test_sin_sesion_responde_401_y_con_otro_rol_403; backend/tests/integration/test_providers_and_assistance_api.py::test_la_cuenta_en_verificacion_no_ve_prestadores_ni_pide_asistencia |
| Status | PASS |

### TC-04 · Póliza ajena, vencida o sin prestador

| Field | Value |
|---|---|
| Covers | AC-10, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Pólizas del titular (vigente y vencida) y de otro asegurado |
| Procedure | 1. Pedir con la póliza ajena, la vencida y una inexistente. 2. Pedir en Cartagena, sin prestadores |
| Expected result | 404 en las tres sin solicitud ni registro de idempotencia; 409 con «línea de Solventa» sin registrar nada |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_providers_and_assistance_api.py::test_una_poliza_ajena_o_vencida_responde_404_sin_registrar_nada; backend/tests/integration/test_providers_and_assistance_api.py::test_sin_prestador_que_ofrezca_el_servicio_responde_409 |
| Status | PASS |

### TC-05 · Idempotencia por asegurado y llave

| Field | Value |
|---|---|
| Covers | AC-07, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas; una segunda cuenta con su póliza |
| Procedure | 1. La misma llave y el mismo cuerpo dos veces. 2. La misma llave con otra descripción. 3. La misma llave desde otra cuenta. 4. Dos envíos iguales a la vez |
| Expected result | 201 dos veces con el mismo cuerpo, una solicitud y un evento; 409; la otra cuenta crea la suya; una sola solicitud y la misma respuesta |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_providers_and_assistance_api.py::test_la_misma_llave_con_el_mismo_cuerpo_devuelve_la_misma_respuesta; backend/tests/integration/test_providers_and_assistance_api.py::test_la_misma_llave_con_otro_cuerpo_responde_409; backend/tests/integration/test_providers_and_assistance_api.py::test_la_llave_es_de_cada_asegurado; backend/tests/integration/test_providers_and_assistance_api.py::test_dos_envios_simultaneos_con_la_misma_llave_crean_una_sola_solicitud |
| Status | PASS |

### TC-06 · Tope por hora

| Field | Value |
|---|---|
| Covers | AC-13, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas |
| Procedure | Pedir 5 veces con llaves distintas y una sexta |
| Expected result | 5 veces 201 y luego 429 con `Retry-After: 600` |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_providers_and_assistance_api.py::test_mas_de_cinco_solicitudes_en_una_hora_responde_429 |
| Status | PASS |

### TC-07 · Entrada inválida

| Field | Value |
|---|---|
| Covers | AC-02, AC-07, SR-05 |
| Kind | SECURITY |
| Risk covered | THR-05 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas |
| Procedure | 1. Buscar sin nada, solo latitud, ciudad en minúsculas, latitud fuera de rango y servicio desconocido. 2. Pedir sin llave, con llave corta o con caracteres no permitidos. 3. Pedir con solo longitud, sin ubicación ni ciudad, servicio desconocido, descripción de 301, campo extra y póliza malformada |
| Expected result | 422 en todos y nada registrado |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_providers_and_assistance_api.py::test_una_busqueda_sin_ubicacion_ni_ciudad_valida_responde_422; backend/tests/integration/test_providers_and_assistance_api.py::test_sin_llave_valida_responde_422; backend/tests/integration/test_providers_and_assistance_api.py::test_un_cuerpo_invalido_responde_422 |
| Status | PASS |

### TC-08 · Asistencia válida, tiempo estimado y listado

| Field | Value |
|---|---|
| Covers | AC-06, AC-14 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas con el catálogo |
| Procedure | 1. Pedir grúa desde el norte de Bogotá. 2. Pedir atención médica en Cali sin coordenadas. 3. Listar las solicitudes |
| Expected result | 201 con PRV-BOG-01 a 1,2 km y 18 minutos (`ceil(1,2 / 30 × 60) + 15`); PRV-CAL-03 con 45 minutos; el listado trae solo la propia con `no-store` |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_providers_and_assistance_api.py::test_pide_asistencia_con_el_prestador_mas_cercano_y_su_tiempo_estimado; backend/tests/integration/test_providers_and_assistance_api.py::test_sin_ubicacion_asigna_en_la_ciudad_con_45_minutos; backend/tests/integration/test_providers_and_assistance_api.py::test_lista_solo_las_solicitudes_del_asegurado |
| Status | PASS |

### TC-09 · Sin ubicación guardada, evento, consumidor y migración

| Field | Value |
|---|---|
| Covers | AC-08, AC-09, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas; base vacía para Alembic |
| Procedure | 1. Pedir asistencia y leer el outbox y el registro de idempotencia. 2. Comprobar que el consumidor ignora `AssistanceRequested`. 3. Migrar desde `0008` sin las tablas, dos veces, y revertir |
| Expected result | Evento `{request_id, provider_id, service}`; respuesta guardada sin coordenadas con el espacio `insured:ins-0001`; el tipo está en los ignorados; dos tablas sin columnas `lat` ni `lng` en las solicitudes, idempotente, y la reversión las quita y deja `0008`; una base vacía queda en `0009` |
| Tool or method | pytest con Alembic |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_providers_and_assistance_api.py::test_la_solicitud_no_guarda_la_ubicacion_y_el_evento_solo_lleva_identificadores; backend/tests/unit/test_notifications.py::test_la_solicitud_de_asistencia_no_tiene_aviso_y_se_ignora; backend/tests/integration/test_migrations.py::test_la_migracion_de_prestadores_y_asistencia_crea_sus_tablas_desde_0008_y_se_revierte; backend/tests/integration/test_migrations.py::test_una_base_vacia_queda_en_la_ultima_migracion_con_todo_el_esquema |
| Status | PASS |

### TC-10 · Flujo de la app

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-03, AC-04, AC-05, AC-11 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Dobles de asistencia, copia, ubicación e intents; cliente HTTP contra MockWebServer |
| Procedure | Abrir con y sin permiso, negar y dar el permiso, sin ubicación a tiempo, filtrar, sin red con y sin copia, sesión vencida, «Llamar» y «Ver en el mapa» con y sin app, pedir con ubicación y con ciudad, cada respuesta del servidor y cerrar; el cliente HTTP con cada código |
| Expected result | Cada caso deja la lista, la copia y el aviso correctos; las coordenadas salen redondeadas y sin ciudad; la llave va en el encabezado; los intents son `ACTION_DIAL` con `tel:` y `ACTION_VIEW` con `geo:` |
| Tool or method | JUnit con Robolectric y MockWebServer |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt::"con permiso busca por la ubicacion aproximada redondeada y guarda la lista en la copia"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt::"sin permiso no pide la ubicacion, carga las ciudades y busca por la elegida"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt::"negar el permiso deja elegir la ciudad y darlo busca alrededor"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt::"sin ubicacion a tiempo avisa y ofrece las ciudades"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt::"el filtro por servicio vuelve a consultar con el servicio"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt::"sin red muestra la ultima lista guardada con su fecha y filtra en el telefono"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt::"sin red y sin copia avisa sin lista, y al volver la red se reemplaza el aviso"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt::"la sesion vencida se avisa al buscar prestadores o ciudades"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt::"Llamar y Ver en el mapa usan el doble de intents y avisan si no hay app"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt::"pedir asistencia envia la poliza, el servicio y la ubicacion y muestra el prestador y el tiempo"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt::"sin ubicacion pide asistencia con la ciudad elegida"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt::"sin ubicacion ni ciudad el formulario no se puede enviar"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt::"cada respuesta del servidor muestra su aviso"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt::"la descripcion se corta en 300 caracteres y sin polizas vigentes no se envia"; movil/app/src/test/java/com/solventa/app/data/assistance/HttpAssistanceApiTest.kt::"los prestadores por ubicacion envian las coordenadas redondeadas y leen la distancia"; movil/app/src/test/java/com/solventa/app/data/assistance/HttpAssistanceApiTest.kt::"los prestadores por ciudad no envian coordenadas y aceptan distancia nula"; movil/app/src/test/java/com/solventa/app/data/assistance/HttpAssistanceApiTest.kt::"sesion vencida, error del servidor o respuesta mal formada en prestadores y ciudades"; movil/app/src/test/java/com/solventa/app/data/assistance/HttpAssistanceApiTest.kt::"pedir asistencia envia la llave y el cuerpo y lee el prestador y el tiempo estimado"; movil/app/src/test/java/com/solventa/app/data/assistance/HttpAssistanceApiTest.kt::"sin ubicacion la solicitud envia la ciudad"; movil/app/src/test/java/com/solventa/app/data/assistance/HttpAssistanceApiTest.kt::"cada codigo de error de la solicitud tiene su resultado"; movil/app/src/test/java/com/solventa/app/data/assistance/HttpAssistanceApiTest.kt::"sin servidor la solicitud queda sin conexion"; movil/app/src/test/java/com/solventa/app/data/assistance/AndroidProviderContactTest.kt::"Llamar abre el marcador con el numero y no llama directamente"; movil/app/src/test/java/com/solventa/app/data/assistance/AndroidProviderContactTest.kt::"Ver en el mapa abre geo con las coordenadas y el nombre del prestador" |
| Status | PASS |

### TC-11 · Llave de idempotencia por formulario

| Field | Value |
|---|---|
| Covers | AC-07, AC-11, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | UNIT |
| Preconditions | Doble de asistencia que responde sin red |
| Procedure | Enviar sin red, reintentar, cambiar la póliza y enviar, cerrar y abrir el formulario y enviar |
| Expected result | Llaves `llave-1`, `llave-1`, `llave-2`, `llave-3` |
| Tool or method | JUnit |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt::"reintentar sin cambios conserva la llave y cambiar el formulario la renueva" |
| Status | PASS |

### TC-12 · Privacidad de la ubicación y copia cifrada en la app

| Field | Value |
|---|---|
| Covers | SR-04, SR-06 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | STATIC |
| Preconditions | Código de la app; SQLite sin cifrar en Robolectric para el flujo del almacén |
| Procedure | 1. Buscar registros, estado guardado y preferencias en el código de asistencia. 2. Revisar que la ubicación se pide una vez y sin la precisa. 3. Revisar el manifiesto y Gradle. 4. Guardar, leer y borrar la copia con la billetera. 5. Migrar de la versión 4 a la 5. 6. Cerrar la pantalla |
| Expected result | Sin coincidencias; `getCurrentLocation` sin `requestLocationUpdates`; sin `CALL_PHONE`, llave de mapas ni SDK de mapas; la copia solo existe con la billetera y se borra con ella; la migración conserva la copia, los siniestros y la constancia; al cerrar no queda nada en memoria |
| Tool or method | JUnit con Robolectric |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/assistance/AssistancePrivacyTest.kt::"la ubicacion no se registra ni se guarda en el estado guardado o en preferencias"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistancePrivacyTest.kt::"la ubicacion se pide una vez, sin escuchar actualizaciones ni pedir la precisa"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistancePrivacyTest.kt::"Llamar abre el marcador sin permiso de llamadas y no hay mapa embebido ni llave de API"; movil/app/src/test/java/com/solventa/app/data/policies/EncryptedPolicyStoreFlowTest.kt::"guarda la lista de prestadores con la copia de la billetera y la borra con ella"; movil/app/src/test/java/com/solventa/app/data/policies/WalletMigrationTest.kt::"FE-MOV-07 la migracion de la version 4 a la 5 conserva la copia y la constancia y agrega los prestadores"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceViewModelTest.kt::"cerrar borra la busqueda y la ubicacion de memoria" |
| Status | PASS |

### TC-13 · Catálogo ficticio solo fuera de producción

| Field | Value |
|---|---|
| Covers | SR-04 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas |
| Procedure | 1. Arrancar el backend en desarrollo y con `ENVIRONMENT=production`. 2. Revisar la línea de Solventa en los recursos de `main` y de depuración |
| Expected result | 18 prestadores con teléfonos 555 en desarrollo; ninguno en producción; la línea vacía en `main` y ficticia solo en depuración |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_lifespan.py::test_arranque_crea_tablas_y_la_asegurada_de_prueba; backend/tests/integration/test_lifespan.py::test_tc_08_con_environment_production_no_siembra_cuentas; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistancePrivacyTest.kt::"la linea ficticia de Solventa solo existe en depuracion" |
| Status | PASS |

### TC-14 · Accesibilidad de las pantallas

| Field | Value |
|---|---|
| Covers | QR-01, AC-04, AC-05, AC-11 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | Pantalla de teléfono en Robolectric |
| Procedure | Revisar el botón «Asistencia» de la billetera, cada prestador, los botones de ubicación, ciudad y línea, los filtros, el aviso sin conexión, las opciones del formulario, el envío deshabilitado y el resultado |
| Expected result | Cada control con nombre (con el del prestador en «Llamar a …» y «Ver … en el mapa») y 48 dp o más; los avisos y el resultado como región en vivo; el recorrido con TalkBack en un teléfono queda en TC-15 |
| Tool or method | Compose UI test con Robolectric |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceScreensTest.kt::"la billetera ofrece Asistencia con un boton de 48 dp"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceScreensTest.kt::"cada prestador tiene Llamar y Ver en el mapa con su nombre para TalkBack y de al menos 48 dp"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceScreensTest.kt::"sin ubicacion ofrece usarla, elegir ciudad y llamar a la linea, todo de 48 dp"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceScreensTest.kt::"el filtro de servicio marca el elegido y cada opcion mide 48 dp"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceScreensTest.kt::"sin conexion el aviso con la fecha de la copia se anuncia por TalkBack"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceScreensTest.kt::"el formulario elige poliza y servicio como radios de 48 dp y envia"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceScreensTest.kt::"sin ubicacion ni ciudad el envio queda deshabilitado y lo explica"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceScreensTest.kt::"sin red el formulario avisa y ofrece llamar a la linea de Solventa"; movil/app/src/test/java/com/solventa/app/ui/assistance/AssistanceScreensTest.kt::"la solicitud recibida muestra el prestador y el tiempo estimado y se anuncia" |
| Status | PASS |

### TC-15 · Prestadores y asistencia de punta a punta en un teléfono

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-03, AC-04, AC-05, AC-06, AC-11, QR-01 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | E2E |
| Preconditions | Backend en DEV con el catálogo ficticio; app de depuración en un teléfono; cuenta y póliza ficticias |
| Procedure | 1. Abrir «Asistencia» sin permiso y elegir ciudad. 2. Dar el permiso aproximado y ver la lista por distancia. 3. Filtrar. 4. «Llamar» y «Ver en el mapa». 5. Modo avión: la lista guardada. 6. «Pedir asistencia» con red y sin red, y reintentar. 7. Un recorrido con TalkBack. 8. Cerrar sesión y comprobar que la copia se borró |
| Expected result | Todo funciona; el marcador muestra el número sin llamar; la app de mapas abre el punto; sin red la lista guardada y la línea; el reintento no duplica |
| Tool or method | Prueba manual con el teléfono y `adb` |
| Automation | MANUAL |
| Environment | DEV |
| Evidence | PENDING |
| Status | PENDING |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-03 | Sin token 401 |
| User without permissions | YES | TC-03 | Cuenta PENDING y analista 403 |
| Access to another user's resources | YES | TC-04, TC-05, TC-08 | Póliza ajena, llave de otro y listado propio |
| Cross-tenant access | NO | — | Rutas del asegurado sin socio |
| Malicious input | YES | TC-07 | Rangos, patrones, longitudes y campos extra |
| Expired tokens | NO | — | Sin cambio en los tokens |
| Revoked sessions | NO | — | Sin cambio en las sesiones |
| Tampered data | YES | TC-05 | La misma llave con otro cuerpo |
| Wrong keys | NO | — | Sin llaves nuevas |
| External service failures | YES | TC-10 | Sin red o error del servidor en la app |
| Race conditions | YES | TC-05 | Dos envíos con la misma llave a la vez |
| Information exposure | YES | TC-02, TC-09, TC-12 | Ubicación en la base, eventos, registros y teléfono |
| Secrets in code | YES | TC-12 | Sin llave de API de mapas |
| Insecure configuration | YES | TC-13 | Catálogo ficticio fuera de producción |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas |

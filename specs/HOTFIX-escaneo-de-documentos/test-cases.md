# Test cases · HOTFIX

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | AC-04, AC-08 | Dado un siniestro abierto del titular, cuando envía dos páginas JPEG, entonces 201, dos filas con la huella de lo guardado, dos imágenes recodificadas bajo `documentos/{upload}/`, el evento del historial y `ClaimDocumentsAdded` solo con identificadores | TC-01, TC-18 |
| SC-02 | AC-05, AC-10 | Dado un envío ya registrado, cuando llega otra vez o dos a la vez, entonces 200 con la misma constancia y una sola copia; con S3 caído 503 y el mismo envío se reintenta | TC-02 |
| SC-03 | AC-06 | Dado un siniestro ajeno, inexistente o decidido, o el `upload_id` de otra persona, cuando se envía, entonces 404, 404, 409 y 404 sin guardar nada | TC-03 |
| SC-04 | AC-07 | Dada una página PNG, ejecutable, PDF, con firma JPEG que no decodifica, con demasiados píxeles o de más de 5 MB, cuando se envía, entonces 422 o 413 sin guardar nada; dado un cuerpo mayor al tope, con o sin `Content-Length`, entonces 413 antes de armar el formulario | TC-04, TC-17 |
| SC-05 | AC-07, AC-13 | Dadas 11 páginas, un manifiesto mal formado, una página declarada que no llega o el tope del siniestro, cuando se envía, entonces 422 | TC-05 |
| SC-06 | AC-07 | Dada una página cambiada después de escanearla, cuando se envía, entonces 422 sin guardar nada | TC-06 |
| SC-07 | AC-12 | Dada una cuenta en verificación, sin sesión, con rol de analista o con el token de solo reporte, cuando sube, entonces 403, 401, 403 y 401 | TC-07 |
| SC-08 | AC-04 | Dada una base en `0007`, cuando se migra y se revierte, entonces la tabla aparece con su índice único y desaparece | TC-08 |
| SC-09 | AC-09 | Dado un expediente con documentos, cuando lo abre el analista de Solventa o el del socio, entonces ve los suyos con enlaces de 900 s y el del socio no ve el de Solventa; sin S3, sin enlaces | TC-09 |
| SC-10 | AC-09 | Dado el expediente en el portal, cuando trae documentos, entonces se listan con enlace, fecha y tamaño, y sin enlace se avisa | TC-10 |
| SC-11 | AC-02, AC-03, AC-10, AC-11 | Dada la app, cuando se escanea, se quita, se envía con y sin red, se cierra o falla el escáner o la cámara, entonces cada caso deja las páginas y el aviso correctos y en cero al terminar | TC-11 |
| SC-12 | AC-11 | Dadas las páginas que ML Kit deja en la caché, cuando se leen, fallan o se cancela, entonces se borran siempre y nada de documentos toca disco, registros ni estado guardado | TC-12 |
| SC-13 | UC-01 | Dados los registros de la aplicación, cuando se suben documentos propios y ajenos, entonces no aparece el documento del asegurado | TC-13 |
| SC-14 | AC-03 | Dada la dependencia nueva, cuando se compila y se revisa, entonces el APK compila, el escáner no usa la galería y no hay permisos nuevos | TC-14 |
| SC-15 | AC-01, AC-02, QR-01 | Dadas las pantallas, cuando se recorren con TalkBack, entonces cada control dice su nombre y mide 48 dp; el detalle abierto ofrece «Agregar documentos» y el decidido no | TC-15 |
| SC-16 | UC-01, UC-02 | Dado un teléfono real en DEV con y sin Google Play services, cuando se agregan documentos y el analista los abre, entonces llegan completos y legibles | TC-16 |
| SC-17 | UC-01 | Dado un reporte sin conexión en la cola cifrada, cuando se agregan documentos | OUT OF SCOPE: los documentos solo se agregan a siniestros ya radicados, que tienen número oficial |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-09, SC-10, SC-16 | — |
| Alternate flows | YES | SC-02, SC-11, SC-14 | Repetición, sin red, sin escáner |
| Invalid input and errors | YES | SC-03, SC-04, SC-05, SC-06 | — |
| Limits and boundaries | YES | SC-04, SC-05 | 5 MB, 10 páginas, 50 por siniestro |
| Related flows before and after | YES | SC-01, SC-09 | Historial, outbox y expediente |
| Several users, sessions, devices or roles | YES | SC-03, SC-07, SC-09 | Otro asegurado, analista y socio |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-02, SC-03, SC-11 | Siniestro decidido, reintentos, sin red y salir |

## Test cases

### TC-01 · Subida válida, historial y outbox

| Field | Value |
|---|---|
| Covers | AC-04, AC-08, SR-05 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas y emulador de S3 |
| Procedure | 1. Enviar dos páginas JPEG al siniestro abierto del titular. 2. Leer filas, S3, historial y outbox |
| Expected result | 201 con 2 páginas; `image/jpeg`; claves `siniestros/{n}/documentos/{upload}/1.jpg` y `2.jpg` con imágenes de las mismas dimensiones; la huella y el tamaño de cada fila son los de lo guardado; `DOCUMENTS_ADDED` en el historial; outbox `{claim_number, upload_id, pages}` |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_documents_api.py::test_agrega_paginas_jpeg_al_siniestro_abierto_del_titular; backend/tests/integration/test_claim_documents_api.py::test_el_evento_del_outbox_lleva_solo_identificadores |
| Status | PASS |

### TC-02 · Idempotencia y reintento

| Field | Value |
|---|---|
| Covers | AC-05, AC-10, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas y emulador de S3 |
| Procedure | 1. Enviar el mismo `upload_id` dos veces seguidas. 2. Dos veces a la vez. 3. Dos a la vez con el mismo `upload_id` y contenido distinto, las dos detenidas hasta llegar al bloqueo. 4. Con S3 caído y luego de nuevo |
| Expected result | 201 y 200 con la misma constancia; una sola copia; con contenido distinto queda solo el primero, S3 se escribe una sola vez y su huella es la guardada; 503 con `Retry-After: 30` y luego 201 |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_documents_api.py::test_el_mismo_envio_repetido_no_duplica_las_paginas; backend/tests/integration/test_claim_documents_api.py::test_dos_envios_simultaneos_iguales_dejan_una_sola_copia; backend/tests/integration/test_claim_documents_api.py::test_s3_caido_responde_503_y_el_mismo_envio_se_puede_reintentar; backend/tests/integration/test_claim_documents_api.py::test_dos_envios_simultaneos_con_el_mismo_id_y_otro_contenido_dejan_solo_el_primero |
| Status | PASS |

### TC-03 · Siniestro ajeno, inexistente o decidido

| Field | Value |
|---|---|
| Covers | AC-06, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Siniestros del titular, de otro asegurado y uno rechazado |
| Procedure | 1. Enviar al siniestro ajeno, a uno inexistente y al decidido. 2. Reutilizar el `upload_id` desde otra cuenta y en otro siniestro propio |
| Expected result | 404, 404 con el mismo cuerpo y 409; otra cuenta 404 y otro siniestro 422; nada guardado |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_documents_api.py::test_un_siniestro_ajeno_o_inexistente_responde_404_y_uno_decidido_409; backend/tests/integration/test_claim_documents_api.py::test_el_envio_de_un_siniestro_no_se_reusa_en_otro_ni_por_otra_persona |
| Status | PASS |

### TC-04 · Formato y tamaño

| Field | Value |
|---|---|
| Covers | AC-07, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas y emulador de S3 |
| Procedure | Enviar un PNG, un ejecutable, un PDF, un archivo con firma JPEG que no decodifica, una imagen sobre el tope de píxeles y un JPEG de más de 5 MB |
| Expected result | 422, 422, 422, 422, 422 y 413; nada guardado |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_documents_api.py::test_paginas_que_no_son_jpeg_o_muy_grandes_se_rechazan; backend/tests/integration/test_claim_documents_api.py::test_una_imagen_con_demasiados_pixeles_se_rechaza |
| Status | PASS |

### TC-05 · Número de páginas, manifiesto y tope

| Field | Value |
|---|---|
| Covers | AC-07, AC-13, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas y emulador de S3 |
| Procedure | 1. 11 páginas en el manifiesto y 11 archivos. 2. Manifiesto con `upload_id` inválido, sin páginas, posiciones salteadas o campo extra. 3. Una página declarada que no llega. 4. Pasar el tope del siniestro |
| Expected result | 422 en todos; el parser corta el undécimo archivo; nada guardado y el tope deja las páginas previas |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_documents_api.py::test_mas_de_10_paginas_se_rechazan; backend/tests/integration/test_claim_documents_api.py::test_un_manifiesto_mal_formado_responde_422; backend/tests/integration/test_claim_documents_api.py::test_una_pagina_declarada_que_no_llega_se_rechaza; backend/tests/integration/test_claim_documents_api.py::test_el_siniestro_tiene_un_tope_de_paginas |
| Status | PASS |

### TC-06 · Huella que no coincide

| Field | Value |
|---|---|
| Covers | AC-07, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas y emulador de S3 |
| Procedure | Enviar una página con bytes distintos a la huella del manifiesto |
| Expected result | 422 «La página 2 cambió después de escanearla…»; nada guardado |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_documents_api.py::test_una_pagina_que_no_coincide_con_su_huella_se_rechaza |
| Status | PASS |

### TC-07 · Cuenta en verificación, sin sesión u otro rol

| Field | Value |
|---|---|
| Covers | AC-12, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Cuenta del titular en PENDING; token de analista |
| Procedure | Subir con la cuenta PENDING, sin token, con el token del analista y con el token de solo reporte |
| Expected result | 403, 401, 403 y 401; nada guardado |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_documents_api.py::test_la_cuenta_en_verificacion_no_sube_documentos; backend/tests/integration/test_claim_documents_api.py::test_sin_sesion_o_con_otro_rol_no_se_suben_documentos; backend/tests/integration/test_claim_documents_api.py::test_el_token_de_solo_reporte_no_sube_documentos |
| Status | PASS |

### TC-08 · Migración 0008

| Field | Value |
|---|---|
| Covers | AC-04 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas |
| Procedure | 1. Migrar hasta `0007` y quitar la tabla. 2. Migrar a la última dos veces. 3. Revertir a `0007` |
| Expected result | `claim_documents` con `uq_claim_documents_upload_position`; idempotente; la reversión la quita y deja `0007`; una base vacía queda en `0008` |
| Tool or method | pytest con Alembic |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_migrations.py::test_la_migracion_de_documentos_del_siniestro_crea_su_tabla_desde_0007_y_se_revierte; backend/tests/integration/test_migrations.py::test_una_base_vacia_queda_en_la_ultima_migracion_con_todo_el_esquema |
| Status | PASS |

### TC-09 · El analista ve los documentos según su socio

| Field | Value |
|---|---|
| Covers | AC-09, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | INTEGRATION |
| Preconditions | Un siniestro de Solventa y uno de una póliza del socio, ambos con documentos |
| Procedure | 1. El analista de Solventa abre el suyo. 2. El del socio abre el de su socio y el de Solventa. 3. Sin S3 |
| Expected result | Documentos en orden con enlaces prefirmados `X-Amz-Expires=900`; el del socio ve el suyo y recibe 404 en el otro; sin S3 se listan sin enlace |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_documents_api.py::test_el_analista_ve_los_documentos_con_enlaces_firmados_y_el_de_otro_socio_no; backend/tests/integration/test_claim_documents_api.py::test_sin_s3_el_expediente_lista_los_documentos_sin_enlace |
| Status | PASS |

### TC-10 · Lista de documentos en el portal

| Field | Value |
|---|---|
| Covers | AC-09, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | UNIT |
| Preconditions | `HttpTestingController` con un expediente |
| Procedure | Abrir el expediente con tres documentos de dos envíos (uno sin enlace) y sin documentos |
| Expected result | Título con el conteo; cada enlace dice «Envío del <fecha>, página N», lleva `noopener noreferrer` y el texto oculto «(se abre en otra pestaña)»; las páginas 1 de dos envíos se distinguen; «… página 2 · enlace no disponible»; tamaño en KB; nada dice PDF; evento «Documentos agregados»; sin documentos, el aviso |
| Tool or method | Vitest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | web/src/app/features/siniestros/claim-file.component.spec.ts::"FE-MOV-05 lista los documentos del asegurado con enlace firmado y avisa el que no tiene enlace"; web/src/app/features/siniestros/claim-file.component.spec.ts::"FE-MOV-05 sin documentos lo dice en lugar de una lista vacía" |
| Status | PASS |

### TC-11 · Flujo de la app

| Field | Value |
|---|---|
| Covers | AC-02, AC-03, AC-10, QR-02 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Doble del escáner, de la cámara y del envío; cliente HTTP contra MockWebServer |
| Procedure | Escanear, quitar, enviar, sin red y reintento, cada error del servidor, más de 10 páginas, envío en curso, sin escáner, cámara fallida o negada, cancelar, cerrar y cambiar de siniestro; el multipart contra MockWebServer |
| Expected result | Páginas en orden con su huella; quitar pone en cero y renueva el `upload_id`; sin red avisa y el reintento usa el mismo id; cada error tiene su aviso; nunca más de 10; sin escáner pasa a la cámara; al cerrar todo queda en cero; el multipart lleva manifiesto, `page_N` y el token |
| Tool or method | JUnit con Robolectric y MockWebServer |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsViewModelTest.kt::"las paginas escaneadas se ven en orden y se envian con su huella"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsViewModelTest.kt::"quitar una pagina la borra de memoria y cambia el id del envio"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsViewModelTest.kt::"sin red avisa, conserva las paginas en memoria y el reintento usa el mismo id"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsViewModelTest.kt::"el siniestro cerrado, inexistente, la sesion vencida y el rechazo se informan"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsViewModelTest.kt::"no acepta mas de 10 paginas y descarta las que sobran"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsViewModelTest.kt::"mientras envia no se puede quitar ni enviar dos veces"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsViewModelTest.kt::"sin escaner de Google se pasa a la camara trasera"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsViewModelTest.kt::"la camara agrega la pagina y una captura fallida avisa"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsViewModelTest.kt::"un escaneo cancelado no cambia nada y uno fallido avisa"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsViewModelTest.kt::"cerrar pone las paginas en cero y olvida el siniestro"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsViewModelTest.kt::"abrir otro siniestro descarta las paginas del anterior"; movil/app/src/test/java/com/solventa/app/data/claims/HttpClaimDocumentsApiTest.kt::"envia un multipart con el manifiesto y cada pagina con su posicion y el token"; movil/app/src/test/java/com/solventa/app/data/claims/HttpClaimDocumentsApiTest.kt::"cada respuesta del servidor tiene su resultado"; movil/app/src/test/java/com/solventa/app/data/claims/HttpClaimDocumentsApiTest.kt::"sin red el envio queda para reintentar"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsScreenTest.kt::"el flujo usa el puerto del escaner y muestra lo escaneado"; movil/app/src/test/java/com/solventa/app/SolventaMobileRootClaimsTest.kt::"salir del siniestro pone en cero las paginas escaneadas que no se enviaron" |
| Status | PASS |

### TC-12 · Páginas solo en memoria

| Field | Value |
|---|---|
| Covers | AC-11, SR-06 |
| Kind | SECURITY |
| Risk covered | THR-05 |
| Level | STATIC |
| Preconditions | Código de la app; archivos de prueba en la caché de Robolectric |
| Procedure | 1. Leer páginas de la caché, con una que falla, una que no existe y un escaneo cancelado. 2. Limpiar la carpeta del escáner y arrancar la app con páginas sobrantes. 3. Cerrar la pantalla de documentos. 4. Mostrar y quitar una miniatura. 5. Buscar en el código de documentos escrituras a disco, galería, registros y estado guardado |
| Expected result | Los archivos de la app se borran siempre y uno fuera de la app no; si una página posterior falla, las ya leídas quedan en cero; la carpeta `mlkit_docscan_ui_client` se borra sin tocar otros archivos, al arrancar y al cerrar la pantalla; la miniatura se decodifica reducida y su bitmap se recicla; ninguna coincidencia; el escáner tiene la galería apagada y salida JPEG |
| Tool or method | JUnit con Robolectric |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/data/claims/ScannedPageReaderTest.kt::"lee las paginas a memoria, las recodifica y borra los archivos"; movil/app/src/test/java/com/solventa/app/data/claims/ScannedPageReaderTest.kt::"si una pagina no se puede recodificar falla y borra todas"; movil/app/src/test/java/com/solventa/app/data/claims/ScannedPageReaderTest.kt::"un archivo que no existe falla sin romper la app"; movil/app/src/test/java/com/solventa/app/data/claims/ScannedPageReaderTest.kt::"al cancelar se borran las paginas y nunca un archivo fuera de la app"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsPrivacyTest.kt::"las paginas nunca se escriben en disco ni se toman de otra app"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsPrivacyTest.kt::"no registra ni usa el estado guardado"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsPrivacyTest.kt::"el escaner de Google no importa de la galeria"; movil/app/src/test/java/com/solventa/app/data/claims/ScannedPageReaderTest.kt::"si una pagina posterior falla las ya leidas quedan en cero"; movil/app/src/test/java/com/solventa/app/data/claims/ScannedPageReaderTest.kt::"si un archivo posterior no se puede abrir las ya leidas quedan en cero"; movil/app/src/test/java/com/solventa/app/data/claims/ScannedPageReaderTest.kt::"limpiar la cache del escaner borra su carpeta y nada mas"; movil/app/src/test/java/com/solventa/app/data/claims/ScannedPageReaderTest.kt::"al arrancar la app borra las paginas que el escaner dejo si el proceso murio"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsScreenTest.kt::"al cerrar la pantalla de documentos se limpia la cache del escaner"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsScreenTest.kt::"la miniatura se reduce y se recicla al quitar la pagina" |
| Status | PASS |

### TC-13 · Nada personal en los registros

| Field | Value |
|---|---|
| Covers | SR-05 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | INTEGRATION |
| Preconditions | Registro de la aplicación capturado en DEBUG |
| Procedure | Subir documentos a un siniestro propio y a uno ajeno y leer el registro |
| Expected result | Ni el documento del titular ni el del otro asegurado aparecen |
| Tool or method | pytest con caplog |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_documents_api.py::test_los_registros_no_llevan_el_documento_del_asegurado |
| Status | PASS |

### TC-14 · Dependencia del escáner

| Field | Value |
|---|---|
| Covers | AC-03, SR-07 |
| Kind | SECURITY |
| Risk covered | THR-07 |
| Level | STATIC |
| Preconditions | Red para Google Maven y OSV.dev |
| Procedure | 1. Consultar OSV.dev con el paquete y sus transitivas directas. 2. Leer el POM. 3. `./gradlew assembleDebug` y revisar el manifiesto combinado |
| Expected result | Sin vulnerabilidades conocidas; licencia ML Kit Terms of Service; compila; componentes de ML Kit con `exported="false"` y sin permisos nuevos |
| Tool or method | OSV.dev API, Gradle |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | Consultas a api.osv.dev del 2026-11-15 con respuesta `{}` para los cinco paquetes; `./gradlew assembleDebug` BUILD SUCCESSFUL con movil/gradle/libs.versions.toml y movil/app/build.gradle.kts; manifiesto combinado de depuración revisado |
| Status | PASS |

### TC-15 · Accesibilidad de las pantallas

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | Pantalla de teléfono en Robolectric |
| Procedure | Revisar el botón del detalle, los títulos, la vista previa y «Quitar la página N», los botones de escanear, fotografiar y enviar, el aviso sin red y el obturador |
| Expected result | Cada control con nombre y 48 dp o más; títulos como encabezados; avisos como región en vivo; el detalle decidido no ofrece agregar; el recorrido con TalkBack en un teléfono queda en TC-16 |
| Tool or method | Compose UI test con Robolectric |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsScreenTest.kt::"el detalle del siniestro abierto ofrece agregar documentos y el decidido no"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsScreenTest.kt::"el siniestro decidido no ofrece agregar documentos"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsScreenTest.kt::"cada pagina se anuncia con su vista previa y su boton de quitar de 48 dp"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsScreenTest.kt::"el boton de enviar dice cuantas paginas y escanear abre el escaner"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsScreenTest.kt::"sin paginas no se puede enviar y con 10 no se puede agregar"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsScreenTest.kt::"sin paginas el boton de enviar esta deshabilitado"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsScreenTest.kt::"sin red el aviso se anuncia y las paginas siguen en pantalla"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsScreenTest.kt::"sin escaner ofrece fotografiar la pagina con la camara"; movil/app/src/test/java/com/solventa/app/ui/claims/ClaimDocumentsScreenTest.kt::"el obturador de la camara tiene nombre y mide al menos 48 dp" |
| Status | PASS |

### TC-17 · Tope del cuerpo antes del parser

| Field | Value |
|---|---|
| Covers | AC-07, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas, emulador de S3 y la aplicación completa con sus middlewares |
| Procedure | 1. Enviar a la ruta de documentos un multipart de `DOCUMENTS_MAX_BODY_BYTES` + 1 bytes con `Content-Length`. 2. El mismo cuerpo en trozos, sin `Content-Length`. 3. En la prueba unitaria, el corte dentro de un grupo de tareas como el de `BaseHTTPMiddleware`. 4. Comprobar el tope |
| Expected result | 413 en los dos casos; con `Content-Length` la ruta no lee el formulario y sin él el formulario nunca termina de armarse; S3 no se escribe; ni filas ni mensajes nuevos en el outbox; el corte envuelto también da 413; el tope está entre 50 000 000 y 50 000 000 + 1 MiB |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_documents_api.py::test_un_cuerpo_mayor_al_tope_responde_413_antes_de_leer_el_formulario; backend/tests/integration/test_claim_documents_api.py::test_el_tope_del_cuerpo_cubre_diez_paginas_de_5_mb_con_poco_margen; backend/tests/unit/test_body_size_limit.py::test_sin_content_length_responde_413_aunque_un_middleware_interno_envuelva_el_corte |
| Status | PASS |

### TC-18 · Recodificación sin EXIF

| Field | Value |
|---|---|
| Covers | AC-04, SR-02, SR-05 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas y emulador de S3 |
| Procedure | Enviar un JPEG con EXIF (fabricante y orientación) y un comentario, y leer lo guardado en S3 |
| Expected result | 201; la imagen guardada no tiene EXIF ni el comentario y la orientación ya está aplicada |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_claim_documents_api.py::test_el_servidor_guarda_la_pagina_sin_exif_ni_comentarios |
| Status | PASS |

### TC-16 · Documentos de punta a punta en un teléfono

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-03, AC-04, AC-09, AC-10, QR-01, QR-02 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | E2E |
| Preconditions | Backend en DEV; app de depuración en un teléfono con Google Play services y en un emulador sin ellos; un siniestro abierto con datos ficticios; documentos ficticios impresos |
| Procedure | 1. Abrir el siniestro y «Agregar documentos». 2. Escanear 3 páginas, quitar una y enviar. 3. Repetir en modo avión y luego con red. 4. En el emulador sin Play services, fotografiar una página. 5. Repetir un recorrido con TalkBack. 6. Abrir el expediente en el portal. 7. Revisar que la caché de la app no conserve páginas |
| Expected result | «Recibimos 2 páginas»; sin red el aviso y el reintento sin duplicar; el respaldo con cámara funciona; el analista ve y abre las páginas; la caché queda sin páginas |
| Tool or method | Prueba manual con el teléfono, el emulador, el portal y `adb shell run-as` |
| Automation | MANUAL |
| Environment | DEV |
| Evidence | PENDING |
| Status | PENDING |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-07 | Sin token 401 |
| User without permissions | YES | TC-07 | Cuenta PENDING y analista 403 |
| Access to another user's resources | YES | TC-03 | Siniestro y envío ajenos 404 |
| Cross-tenant access | YES | TC-09 | El analista del socio no ve el de Solventa |
| Malicious input | YES | TC-04, TC-05 | Firma, tamaño, número de archivos y manifiesto |
| Expired tokens | NO | — | Sin cambio en los tokens |
| Revoked sessions | NO | — | Sin cambio en las sesiones |
| Tampered data | YES | TC-06 | Huella que no coincide |
| Wrong keys | NO | — | Sin llaves nuevas |
| External service failures | YES | TC-02, TC-09 | S3 caído al subir y al firmar |
| Race conditions | YES | TC-02 | Dos envíos iguales a la vez |
| Information exposure | YES | TC-03, TC-12, TC-13 | 404 igual, app y registros |
| Secrets in code | NO | — | Sin secretos nuevos |
| Insecure configuration | YES | TC-14 | Galería apagada y componentes no exportados |
| Vulnerable dependencies | YES | TC-14 | OSV.dev de la dependencia nueva |

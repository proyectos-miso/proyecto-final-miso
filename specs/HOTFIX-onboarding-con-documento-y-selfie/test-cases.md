# Test cases · HOTFIX

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | AC-07 | Dado un documento y un celular válidos, cuando se pide el código, entonces llega; un tipo, documento, celular o campo extra inválidos dan 422 y un segundo pedido en menos de un minuto da 429 | TC-01 |
| SC-02 | AC-02 | Dado un documento que el proveedor aprueba, cuando se registra, entonces 201 con sesión, cuenta APPROVED, ve sus pólizas y nada queda en S3 | TC-02 |
| SC-03 | AC-03, AC-10 | Dado un documento que queda pendiente, cuando se registra, entonces 202 con sesión, imágenes bajo `identidad/` y 403 al pedir pólizas | TC-03 |
| SC-04 | AC-03, QR-02 | Dado un proveedor que tarda más de 400 ms, cuando se registra, entonces queda PENDING sin referencia y la decisión diferida vuelve a enviar las imágenes guardadas | TC-04 |
| SC-05 | AC-04 | Dado un documento que el proveedor rechaza, cuando se registra, entonces 422 sin cuenta ni imágenes | TC-05 |
| SC-06 | AC-05 | Dado un correo (en mayúsculas) o un documento ya registrados, cuando se registra, entonces el mismo 409 genérico | TC-05 |
| SC-07 | AC-06 | Dada una clave débil, un código errado, el código de otro celular, sin autorización, un PDF o una foto de más de 2 MB, cuando se registra, entonces 422 o 413 sin cuenta | TC-06 |
| SC-08 | AC-07 | Dados cinco intentos fallidos con el mismo documento en una hora, cuando hay un sexto, entonces 429 | TC-07 |
| SC-09 | AC-08, AC-13 | Dado un registro creado, cuando se mira `consents`, entonces hay una autorización biométrica con hash que no vale como Open Finance | TC-08 |
| SC-10 | UC-03 | Dada una cuenta pendiente, cuando revoca la autorización, entonces se borran sus imágenes, la verificación queda REVOKED y sin sesión responde 401 | TC-09 |
| SC-11 | UC-02, AC-14 | Dadas verificaciones pendientes, cuando corre la decisión diferida, entonces la aprobada queda APPROVED y la rechazada pierde la sesión y el ingreso; las imágenes se borran | TC-10 |
| SC-12 | AC-10 | Dado el simulado fuera de desarrollo, cuando verifica o decide, entonces nunca aprueba | TC-11 |
| SC-13 | AC-03 | Dado el rol de la tarea, cuando se planea Terraform, entonces solo puede borrar bajo `identidad/` | TC-12 |
| SC-14 | UC-01 | Dado un registro aprobado, uno pendiente y uno rechazado, cuando se leen los registros y la auditoría, entonces no aparecen documento, correo, nombre, celular ni código | TC-13 |
| SC-15 | AC-01, AC-11 | Dada la app, cuando se recorre el registro con éxito, error, sin red o volviendo atrás, entonces cada paso avanza o vuelve al que hay que corregir y la sesión se abre solo con APPROVED o PENDING | TC-14 |
| SC-16 | AC-12 | Dada la app, cuando se toman las fotos, entonces quedan solo en memoria, la selfie usa la cámara frontal y se borran al terminar | TC-15 |
| SC-17 | AC-01 | Dadas las pantallas del registro, cuando se recorren con TalkBack, entonces cada control dice su nombre y estado y mide al menos 48 dp | TC-16 |
| SC-18 | UC-01 | Dado un teléfono real en DEV, cuando una persona se registra de punta a punta, entonces ve el resultado y entra a la app | TC-17 |
| SC-19 | UC-01 | Dado un asegurado con varias cuentas en el teléfono, cuando registra otra, entonces la nueva se suma sin tocar las huellas existentes | OUT OF SCOPE: lo cubre `SecureSessionRepository` (HU-MOV-03) y `completeRegistration` usa el mismo camino del ingreso |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-02, SC-15, SC-18 | — |
| Alternate flows | YES | SC-03, SC-04, SC-05, SC-10, SC-11 | — |
| Invalid input and errors | YES | SC-01, SC-06, SC-07 | — |
| Limits and boundaries | YES | SC-01, SC-04, SC-07, SC-08 | 400 ms, 2 MB, cinco intentos |
| Related flows before and after | YES | SC-09, SC-10, SC-11 | Consentimiento, revocación y decisión diferida |
| Several users, sessions, devices or roles | YES | SC-03, SC-06, SC-19 | Documento ajeno y cuentas existentes |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-04, SC-11, SC-15 | Tiempo máximo, decisión diferida, sin red y atrás |

## Test cases

### TC-01 · Código de registro

| Field | Value |
|---|---|
| Covers | AC-07, SR-03, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas y SMS de consola |
| Procedure | 1. Pedir el código con datos válidos. 2. Con tipo, documento, celular o campos extra inválidos. 3. Pedir otro en el mismo minuto |
| Expected result | 200 con el código de prueba; 422; 429 con `Retry-After` |
| Tool or method | pytest; JUnit con MockWebServer |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_registration_api.py::test_pide_el_codigo_de_registro_con_un_documento_y_un_celular_validos; backend/tests/integration/test_insured_registration_api.py::test_el_codigo_de_registro_tiene_limite_por_documento; movil/app/src/test/java/com/solventa/app/data/auth/HttpRegistrationApiTest.kt::"pide el codigo de registro con el tipo y numero de documento y el celular" |
| Status | PASS |

### TC-02 · Registro aprobado

| Field | Value |
|---|---|
| Covers | AC-02, AC-11 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas, S3 emulado (Floci) y el simulado |
| Procedure | 1. Pedir el código. 2. Registrar con un documento terminado en 2. 3. Pedir las pólizas con la sesión |
| Expected result | 201 con TokenPair y `verification_status` APPROVED, `Cache-Control: no-store`, nada bajo `identidad/`, verificación APPROVED y 200 en pólizas; la app envía el multipart sin token y entiende 201; la migración `0007` agrega las columnas con APPROVED por defecto y se revierte |
| Tool or method | pytest; JUnit con MockWebServer |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_registration_api.py::test_aprobada_crea_la_cuenta_abre_la_sesion_y_no_guarda_imagenes; backend/tests/integration/test_migrations.py::test_la_migracion_de_verificacion_de_identidad_aprueba_las_cuentas_existentes_desde_0006; movil/app/src/test/java/com/solventa/app/data/auth/HttpRegistrationApiTest.kt::"envia un multipart con los datos, la foto del documento y la selfie, sin token" |
| Status | PASS |

### TC-03 · Registro pendiente sin acceso a los datos del documento

| Field | Value |
|---|---|
| Covers | AC-03, AC-10, SR-01, SR-05 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL de pruebas, S3 emulado y el simulado |
| Procedure | 1. Registrar con un documento terminado en 8. 2. Listar `identidad/`. 3. Pedir las pólizas con la sesión |
| Expected result | 202 PENDING; dos imágenes bajo `identidad/{user_id}/`, iguales a `s3_keys`; 403 «Estamos verificando su identidad.»; la app entiende 202 como cuenta en verificación |
| Tool or method | pytest; JUnit con MockWebServer |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_registration_api.py::test_pendiente_crea_la_cuenta_con_202_y_guarda_las_imagenes_bajo_identidad; movil/app/src/test/java/com/solventa/app/data/auth/HttpRegistrationApiTest.kt::"202 es una cuenta creada con la identidad en verificacion" |
| Status | PASS |

### TC-04 · Proveedor lento

| Field | Value |
|---|---|
| Covers | AC-03, QR-02 |
| Kind | QUALITY |
| Risk covered | — |
| Level | PERFORMANCE |
| Preconditions | Proveedor falso que tarda 1 o 2 s |
| Procedure | 1. Registrar con el proveedor lento. 2. Medir la espera de `_verify`. 3. Correr la decisión diferida |
| Expected result | La espera se corta antes de 0,9 s (meta 400 ms); 202 sin referencia; la diferida vuelve a enviar las imágenes guardadas, aprueba y las borra |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_registration_api.py::test_si_el_proveedor_tarda_mas_de_400_ms_queda_pendiente_y_se_decide_despues; backend/tests/unit/test_identity_verifier.py::test_un_proveedor_lento_o_caido_deja_la_verificacion_pendiente |
| Status | PASS |

### TC-05 · Rechazo y duplicados

| Field | Value |
|---|---|
| Covers | AC-04, AC-05, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | María registrada; S3 emulado |
| Procedure | 1. Registrar con un documento terminado en 9. 2. Con el documento de María. 3. Con el correo de María en mayúsculas |
| Expected result | 422 `IDENTITY_REJECTED` sin cuenta ni imágenes; los dos duplicados con el mismo 409 `DUPLICATE`; la app traduce cada código |
| Tool or method | pytest; JUnit con MockWebServer |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_registration_api.py::test_rechazada_responde_422_sin_cuenta_ni_imagenes; backend/tests/integration/test_insured_registration_api.py::test_correo_o_documento_repetidos_responden_409_sin_decir_cual; movil/app/src/test/java/com/solventa/app/data/auth/HttpRegistrationApiTest.kt::"cada rechazo del servidor se traduce por su codigo" |
| Status | PASS |

### TC-06 · Entrada inválida

| Field | Value |
|---|---|
| Covers | AC-06, SR-03, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | INTEGRATION |
| Preconditions | Código pedido para un documento |
| Procedure | Registrar con: clave sin números, un PDF como documento, un código errado, el código pero otro celular, sin autorización biométrica y una selfie de más de 2 MB |
| Expected result | 422 `WEAK_PASSWORD`, `INVALID_PHOTO`, `INVALID_CODE`, `INVALID_CODE`, `INVALID_DATA` y 413; sin cuenta ni imágenes |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_registration_api.py::test_clave_debil_codigo_invalido_y_jpeg_falso_responden_422 |
| Status | PASS |

### TC-07 · Límite de intentos de registro

| Field | Value |
|---|---|
| Covers | AC-07, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | Código pedido |
| Procedure | 1. Seis registros con códigos errados para el mismo documento. 2. Calcular la espera con cuatro y cinco intentos |
| Expected result | Cinco 422 y luego 429; la política admite cinco por hora y calcula la espera del más viejo |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_registration_api.py::test_el_registro_tiene_limite_de_intentos_por_documento; backend/tests/unit/test_identity_verifier.py::test_el_registro_admite_cinco_intentos_por_hora_por_documento |
| Status | PASS |

### TC-08 · Autorización biométrica

| Field | Value |
|---|---|
| Covers | AC-08, AC-13, SR-06 |
| Kind | SECURITY |
| Risk covered | THR-07 |
| Level | INTEGRATION |
| Preconditions | Registro pendiente creado |
| Procedure | 1. Leer `consents` del documento. 2. Consultar el consentimiento Open Finance del documento |
| Expected result | Una fila `biometric_verification`, canal `app`, GRANTED y hash de 64 caracteres; la consulta Open Finance no la devuelve |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_registration_api.py::test_guarda_el_consentimiento_biometrico_que_no_vale_como_open_finance |
| Status | PASS |

### TC-09 · Revocación de la autorización

| Field | Value |
|---|---|
| Covers | AC-08, SR-05, SR-06 |
| Kind | SECURITY |
| Risk covered | THR-05 |
| Level | INTEGRATION |
| Preconditions | Registro pendiente con dos imágenes |
| Procedure | 1. Revocar con la sesión de la cuenta. 2. Revocar sin sesión |
| Expected result | 200, nada bajo `identidad/`, verificación REVOKED sin claves, autorización REVOKED; sin sesión 401 |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_registration_api.py::test_revocar_el_consentimiento_borra_las_imagenes_pendientes |
| Status | PASS |

### TC-10 · Verificación diferida

| Field | Value |
|---|---|
| Covers | AC-09, AC-14, SR-01, SR-05 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Registros pendientes con imágenes |
| Procedure | 1. Decidir con un proveedor que aprueba. 2. Decidir otro con uno que rechaza. 3. Renovar la sesión del rechazado e intentar ingresar |
| Expected result | APPROVED con `verified_at`; REJECTED con `token_version` 1; las imágenes borradas; renovación e ingreso 401 |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_registration_api.py::test_la_verificacion_diferida_aprueba_y_borra_las_imagenes; backend/tests/integration/test_insured_registration_api.py::test_la_verificacion_diferida_rechaza_cierra_la_sesion_y_borra_las_imagenes |
| Status | PASS |

### TC-11 · Adaptador simulado

| Field | Value |
|---|---|
| Covers | SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | UNIT |
| Preconditions | Ninguna: el adaptador no usa base de datos ni red |
| Procedure | 1. Verificar documentos terminados en 9, 8 y otros. 2. Decidir. 3. Repetir con el simulado de fuera de desarrollo |
| Expected result | REJECTED, PENDING y APPROVED con referencias distintas; la diferida aprueba; fuera de desarrollo todo PENDING |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/unit/test_identity_verifier.py::test_el_simulado_decide_por_el_ultimo_digito_del_documento; backend/tests/unit/test_identity_verifier.py::test_cada_verificacion_tiene_su_propia_referencia_y_la_diferida_aprueba; backend/tests/unit/test_identity_verifier.py::test_fuera_de_desarrollo_el_simulado_nunca_aprueba_ni_rechaza |
| Status | PASS |

### TC-12 · Permiso del rol sobre `identidad/`

| Field | Value |
|---|---|
| Covers | SR-05 |
| Kind | SECURITY |
| Risk covered | THR-05 |
| Level | STATIC |
| Preconditions | Proveedor de AWS simulado |
| Procedure | `terraform test` del módulo de ECS |
| Expected result | La sentencia `ImagenesDeIdentidad` se limita a `identidad/*` y es la única con `s3:DeleteObject` |
| Tool or method | terraform test |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | terraform/tests/imagenes_de_identidad.tftest.hcl::el_rol_de_la_tarea_borra_solo_las_imagenes_de_identidad |
| Status | PASS |

### TC-13 · Nada personal en registros ni auditoría

| Field | Value |
|---|---|
| Covers | SR-07 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | INTEGRATION |
| Preconditions | Registro de la aplicación capturado |
| Procedure | 1. Registrar una cuenta pendiente y una rechazada. 2. Leer el registro y `login_attempts` |
| Expected result | Ni el documento, el correo, el nombre, el celular ni el código aparecen; los sujetos son `registro:` más un HMAC; el registro sí dice el resultado |
| Tool or method | pytest con caplog |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_insured_registration_api.py::test_ningun_registro_lleva_datos_personales; backend/tests/unit/test_identity_verifier.py::test_el_seudonimo_del_documento_no_lleva_el_numero_y_es_estable |
| Status | PASS |

### TC-14 · Flujo de la app

| Field | Value |
|---|---|
| Covers | AC-01, AC-11 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | API falsa |
| Procedure | Recorrer los pasos con datos válidos e inválidos, errores del código, foto fallida, sin autorización, aprobada, pendiente, rechazada, cada error del servidor, sin red y atrás; abrir la sesión de la cuenta nueva |
| Expected result | Cada paso avanza o vuelve al que hay que corregir; la sesión se entrega una vez solo con APPROVED o PENDING; la cuenta nueva abre sesión y se le ofrece la huella si hay sensor |
| Tool or method | JUnit |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationViewModelTest.kt::"los datos completos piden el codigo al celular y pasan al paso del codigo"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationViewModelTest.kt::"datos incompletos o una clave debil no piden codigo"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationViewModelTest.kt::"los errores al pedir el codigo se muestran sin avanzar"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationViewModelTest.kt::"el codigo pasa a la foto del documento y luego a la selfie y a la autorizacion"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationViewModelTest.kt::"aprobada envia los datos y las dos fotos y entrega la sesion una sola vez"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationViewModelTest.kt::"pendiente tambien abre la sesion y muestra que se esta verificando"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationViewModelTest.kt::"rechazada no entrega sesion y borra las fotos"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationViewModelTest.kt::"cada error del servidor vuelve al paso que hay que corregir"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationViewModelTest.kt::"sin red se puede reintentar con las fotos que siguen en memoria"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationViewModelTest.kt::"atras recorre los pasos y en el primero pide cerrar"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"la cuenta registrada abre su sesion y ofrece activar la huella"; movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"sin sensor la cuenta registrada entra directo a la billetera" |
| Status | PASS |

### TC-15 · Fotos solo en memoria y cámara frontal

| Field | Value |
|---|---|
| Covers | AC-12, SR-08 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | STATIC |
| Preconditions | Código de la app |
| Procedure | 1. Buscar en el código del registro escrituras a disco, otras apps de cámara, registros y estado guardado. 2. Revisar el selector de cada cámara. 3. Cerrar el registro con fotos tomadas |
| Expected result | Ninguna coincidencia; la selfie usa `LENS_FACING_FRONT` y el documento la trasera; al cerrar no queda ninguna foto ni dato en memoria ni en `toString` |
| Tool or method | JUnit con Robolectric |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationPrivacyTest.kt::"las fotos del registro nunca tocan el disco ni salen de la app"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationPrivacyTest.kt::"el registro no escribe registros ni usa el estado guardado"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationPrivacyTest.kt::"la selfie usa la camara frontal y el documento la trasera"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationViewModelTest.kt::"cerrar el registro borra las fotos, el codigo y los datos"; movil/app/src/test/java/com/solventa/app/data/auth/HttpRegistrationApiTest.kt::"los datos del registro no se imprimen" |
| Status | PASS |

### TC-16 · Accesibilidad de las pantallas

| Field | Value |
|---|---|
| Covers | QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | Pantalla de teléfono en Robolectric |
| Procedure | Revisar etiquetas, roles (radio, casilla, botón), estados, títulos y área de toque de cada pantalla del registro y del enlace «Crear cuenta» |
| Expected result | Todo con nombre y estado; 48 dp o más; el recorrido con TalkBack en un teléfono queda en TC-17 |
| Tool or method | Compose UI test con Robolectric |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationScreensTest.kt::"los datos tienen etiquetas, el paso y el boton solo se activa con todo completo"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationScreensTest.kt::"el tipo de documento es un grupo de opciones de 48 dp que TalkBack lee como radio"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationScreensTest.kt::"los terminos son una casilla de 48 dp con su estado"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationScreensTest.kt::"atras tiene etiqueta y mide 48 dp"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationScreensTest.kt::"la camara tiene el obturador con etiqueta para TalkBack"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationScreensTest.kt::"la autorizacion biometrica explica el uso y exige la casilla"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationScreensTest.kt::"cada resultado muestra su titulo y su accion"; movil/app/src/test/java/com/solventa/app/ui/auth/RegistrationScreensTest.kt::"las pantallas de ingreso ofrecen crear cuenta" |
| Status | PASS |

### TC-17 · Registro de punta a punta en un teléfono

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-03, AC-04, AC-11, QR-01 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | E2E |
| Preconditions | Backend en DEV con el simulado; app de depuración en un teléfono con cámara frontal y trasera; datos ficticios |
| Procedure | 1. «Crear cuenta». 2. Registrar con un documento terminado en 2, otro en 8 y otro en 9. 3. Repetir un recorrido con TalkBack. 4. Revisar en S3 `identidad/` y que la app no dejó archivos |
| Expected result | Aprobada entra y ofrece la huella; pendiente muestra «Estamos verificando su identidad» y entra sin pólizas; rechazada vuelve al ingreso sin cuenta; solo la pendiente deja imágenes |
| Tool or method | Prueba manual con el teléfono y la consola de S3 |
| Automation | MANUAL |
| Environment | DEV |
| Evidence | PENDING |
| Status | PENDING |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-09 | La revocación sin sesión responde 401 |
| User without permissions | YES | TC-03 | La cuenta PENDING no ve pólizas |
| Access to another user's resources | YES | TC-03, TC-05 | Documento ajeno sin verificación o ya registrado |
| Cross-tenant access | NO | — | El asegurado no es un socio |
| Malicious input | YES | TC-01, TC-06 | Esquema, firma JPEG y tamaño |
| Expired tokens | NO | — | Sin cambio en la vigencia de los tokens |
| Revoked sessions | YES | TC-10 | El rechazo diferido revoca la sesión |
| Tampered data | YES | TC-06 | Código usado con otro celular |
| Wrong keys | NO | — | Sin llaves nuevas |
| External service failures | YES | TC-04 | Proveedor lento o caído |
| Race conditions | NO | — | El bloqueo por documento y la unicidad del correo lo cubren; sin prueba concurrente en este cambio |
| Information exposure | YES | TC-05, TC-13, TC-15 | 409 genérico, registros y app |
| Secrets in code | YES | TC-13 | Los códigos y la clave no aparecen en registros |
| Insecure configuration | YES | TC-11, TC-12 | El simulado no aprueba fuera de desarrollo; IAM mínimo |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas |

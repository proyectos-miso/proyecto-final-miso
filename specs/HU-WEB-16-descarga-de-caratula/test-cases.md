# Test cases · HU-WEB-16

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | UC-01 | Dado el detalle de una póliza vigente, cuando el analista pulsa "Descargar carátula (PDF)", entonces el navegador descarga `caratula-POL-….pdf` y la página no cambia | TC-01, TC-11, TC-15 |
| SC-02 | AC-05 | Dada una carátula ya guardada, cuando se pide otra vez, entonces solo se firma un enlace nuevo | TC-07 |
| SC-03 | AC-04 | Dado S3 caído o sin responder, cuando se pide la carátula, entonces el portal dice que está en generación y la póliza no queda marcada | TC-08, TC-15 |
| SC-04 | AC-04 | Dado que S3 vuelve, cuando el analista pulsa otra vez, entonces descarga | TC-08, TC-15 |
| SC-05 | AC-03 | Dado un enlace de más de 15 minutos, cuando se usa, entonces S3 lo rechaza | TC-03 |
| SC-06 | AC-03 | Dado un enlace vencido, cuando el analista pulsa otra vez, entonces recibe uno nuevo que descarga | TC-03, TC-11 |
| SC-07 | AC-08 | Dada una póliza cancelada, cuando se abre su detalle o se pide la carátula, entonces no hay botón y el endpoint responde 409 | TC-10, TC-11 |
| SC-08 | AC-10 | Dada una póliza B2B sin titular, cuando se descarga, entonces dice "Sin nombre registrado" con el documento enmascarado y el socio | TC-02 |
| SC-09 | UC-02 | Dado un usuario de un socio, cuando pide la carátula de una póliza de su socio, entonces la descarga | TC-04 |
| SC-10 | UC-02 | Dado un usuario de un socio, cuando pide la de otro socio, del portal o una inexistente, entonces recibe el mismo 404 y no se genera nada | TC-04 |
| SC-11 | AC-06 | Dado un asesor, alguien sin sesión o un socio revocado, cuando pide la carátula, entonces recibe 403 o 401 | TC-05 |
| SC-12 | UC-01 | Dado un número fuera del patrón o con una ruta, cuando se pide, entonces no llega a S3 | TC-06 |
| SC-13 | AC-07 | Dado un titular con correo, teléfono y beneficiarios, cuando se genera el PDF, entonces trae el documento enmascarado y nada más | TC-02 |
| SC-14 | AC-07 | Dado un nombre con HTML o caracteres fuera de Latin-1, cuando se genera el PDF, entonces se escribe como texto y no falla | TC-02 |
| SC-15 | AC-05 | Dada una carátula generada antes del pago, cuando la póliza pasa a vigente, entonces el PDF sigue siendo correcto porque no trae el estado | TC-02 |
| SC-16 | UC-01 | Dado el detalle abierto, cuando el analista usa el teclado o el lector de pantalla, entonces llega al botón, oye el avance y el error | TC-11, TC-13 |
| SC-17 | UC-01 | Dado un analista que pulsa varias veces, cuando ya hay un pedido en curso, entonces el portal no manda otro | TC-11 |
| SC-18 | UC-01 | Dado el rol del backend, cuando guarda o firma, entonces solo puede tocar `caratulas/` con la llave del bucket | TC-09 |
| SC-19 | UC-01 | Dados dos primeros pedidos a la vez, cuando ambos generan, entonces guardan el mismo PDF en la misma clave | OUT OF SCOPE: comportamiento aceptado en RISK-04; no cambia lo que recibe el analista |
| SC-20 | UC-01 | Dado un enlace vigente reenviado a otra persona, cuando lo abre, entonces descarga hasta que venza | OUT OF SCOPE: propiedad del enlace prefirmado, acotada a 15 minutos (RISK-01) |
| SC-22 | UC-01 | Dado el backend local recién levantado con Floci, con Poetry o con docker compose, cuando se pide la carátula, entonces el bucket existe y el enlace abre en el navegador | TC-16 |
| SC-21 | UC-01 | Dado el asegurado en la app o el asesor al emitir, cuando quiere la carátula, entonces la descarga | OUT OF SCOPE: las tareas y el mockup ubican el botón en Gestión; queda para otra historia |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-02 | — |
| Alternate flows | YES | SC-07, SC-08, SC-09 | — |
| Invalid input and errors | YES | SC-03, SC-12, SC-14 | — |
| Limits and boundaries | YES | SC-05 | — |
| Related flows before and after | YES | SC-15, SC-21, SC-22 | — |
| Several users, sessions, devices or roles | YES | SC-10, SC-11, SC-19, SC-20 | — |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-04, SC-05, SC-06, SC-17 | — |

## Test cases

### TC-01 · Enlace HTTPS de 15 minutos que descarga el PDF

| Field | Value |
|---|---|
| Covers | AC-01, AC-09, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | PostgreSQL y Floci locales; analista de operaciones; póliza vigente del portal |
| Procedure | 1. Pedir la carátula. 2. Revisar la respuesta, las cabeceras y el vencimiento. 3. Descargar con el enlace. 4. Firmar contra AWS con credenciales ficticias y revisar la URL |
| Expected result | 200 con `url` y `expires_at` a 15 minutos y `Cache-Control: no-store`; el enlace descarga un PDF `application/pdf` como `caratula-POL-2026-00001.pdf`; contra AWS el enlace es `https://`, de esa clave, con `X-Amz-Expires=900`; un enlace sin HTTPS se rechaza |
| Tool or method | pytest con Floci |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_caratula_api.py::test_tc_01_el_analista_recibe_un_enlace_de_15_minutos_que_descarga_el_pdf; backend/tests/unit/test_policy_document.py::test_tc_01_el_enlace_es_https_de_esa_clave_vence_en_15_minutos_y_descarga_el_archivo; backend/tests/unit/test_policy_document.py::test_tc_01_un_enlace_sin_https_contra_aws_se_rechaza; 322 de 322 del backend, 2026-11-01 |
| Status | PASS |

### TC-02 · Contenido del PDF con el documento enmascarado

| Field | Value |
|---|---|
| Covers | AC-02, AC-07, AC-10, AC-12, SR-04 |
| Kind | SECURITY |
| Risk covered | THR-04 |
| Level | INTEGRATION |
| Preconditions | Póliza con titular, correo, teléfono y beneficiarios; póliza B2B sin titular |
| Procedure | 1. Generar la carátula. 2. Extraer el texto del PDF. 3. Repetir con la póliza sin titular, con un nombre con HTML y caracteres fuera de Latin-1 y con una póliza pendiente de pago |
| Expected result | Las secciones y etiquetas del detalle de Gestión (Titular, Plan y vigencia, Coberturas contratadas), "Portal Solventa" sin socio y montos como "$ 200.000.000 COP"; número, titular, plan, vigencia, coberturas incluidas con suma y deducible y primas; documento `******4050`; sin documento completo, correo, teléfono, beneficiarios, coberturas no incluidas ni estado; "Sin nombre registrado" y el socio en la B2B; el HTML como texto y "?" en lo que no cabe |
| Tool or method | pytest; texto del PDF con `backend/tests/pdf_text.py` |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/unit/test_policy_document.py::test_tc_02_la_caratula_trae_numero_titular_plan_coberturas_vigencia_y_prima; backend/tests/unit/test_policy_document.py::test_tc_02_el_documento_va_enmascarado_y_sin_contacto_ni_beneficiarios; backend/tests/unit/test_policy_document.py::test_tc_02_sin_titular_registrado_lo_dice_y_enmascara_el_documento; backend/tests/unit/test_policy_document.py::test_tc_02_el_nombre_con_html_o_fuera_de_latin1_se_escribe_como_texto; backend/tests/unit/test_policy_document.py::test_tc_02_la_caratula_no_trae_el_estado_porque_se_genera_una_sola_vez; backend/tests/integration/test_policy_caratula_api.py::test_tc_02_el_pdf_descargado_trae_los_datos_con_el_documento_enmascarado; backend/tests/integration/test_policy_caratula_api.py::test_tc_02_una_poliza_b2b_sin_titular_muestra_sin_nombre_y_el_socio |
| Status | PASS |

### TC-03 · Enlace vencido rechazado y enlace nuevo

| Field | Value |
|---|---|
| Covers | AC-03, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-03 |
| Level | INTEGRATION |
| Preconditions | Floci, que comprueba el vencimiento de los enlaces |
| Procedure | 1. Pedir un enlace con vencimiento de 1 s. 2. Esperar 2,5 s y usarlo. 3. Pedir otro y usarlo |
| Expected result | El vencido responde 403 "Request has expired"; el nuevo es distinto y descarga |
| Tool or method | pytest con Floci |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_caratula_api.py::test_tc_03_un_enlace_vencido_se_rechaza_y_al_pedir_otro_descarga |
| Status | PASS |

### TC-04 · Alcance por socio

| Field | Value |
|---|---|
| Covers | AC-06, SR-01 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | Usuario del Banco Aliado; pólizas del aliado, de otro socio, del portal y un número inexistente |
| Procedure | 1. Pedir la del aliado. 2. Pedir las otras tres |
| Expected result | 200 para la del aliado; el mismo 404 "No encontramos la póliza." para las demás; un solo PDF guardado y ninguna clave anotada fuera del alcance |
| Tool or method | pytest con Floci |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_caratula_api.py::test_tc_04_el_usuario_de_un_socio_solo_descarga_las_de_su_socio |
| Status | PASS |

### TC-05 · Rol, sesión y socio revocado

| Field | Value |
|---|---|
| Covers | AC-06, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Asesor, pedido sin token y usuario de un socio revocado |
| Procedure | Pedir la carátula con cada uno |
| Expected result | 403, 401 y 403; nada se guarda en S3 |
| Tool or method | pytest con Floci |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_caratula_api.py::test_tc_05_solo_el_rol_analyst_con_su_socio_habilitado_pide_la_caratula |
| Status | PASS |

### TC-06 · Número fuera del patrón y clave del servidor

| Field | Value |
|---|---|
| Covers | SR-05 |
| Kind | SECURITY |
| Risk covered | THR-05 |
| Level | INTEGRATION |
| Preconditions | Analista de operaciones |
| Procedure | 1. Pedir `POL-2026-1`, `POL-2026-00001.pdf`, `..%2Fcaratulas%2FPOL-2026-00001` y `pol-2026-00001`. 2. Revisar la clave que arma el servidor |
| Expected result | 422 o 404 sin tocar S3; la clave es `caratulas/{número}.pdf` |
| Tool or method | pytest con Floci |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_caratula_api.py::test_tc_06_un_numero_fuera_del_patron_no_llega_a_s3; backend/tests/unit/test_policy_document.py::test_tc_06_la_clave_sale_del_numero_de_poliza |
| Status | PASS |

### TC-07 · El PDF se genera una vez

| Field | Value |
|---|---|
| Covers | AC-05, SR-07 |
| Kind | SECURITY |
| Risk covered | THR-07 |
| Level | INTEGRATION |
| Preconditions | Floci con un contador de guardados |
| Procedure | Pedir la misma carátula dos veces y descargar con el segundo enlace |
| Expected result | Un solo guardado en S3; el segundo enlace descarga |
| Tool or method | pytest con Floci |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_caratula_api.py::test_tc_07_el_pdf_se_genera_una_vez_y_luego_solo_se_firma |
| Status | PASS |

### TC-08 · S3 sin responder: la carátula está en generación

| Field | Value |
|---|---|
| Covers | AC-04, SR-07 |
| Kind | SECURITY |
| Risk covered | THR-07 |
| Level | INTEGRATION |
| Preconditions | Almacenamiento apuntando a un puerto cerrado; luego a Floci |
| Procedure | 1. Pedir la carátula con S3 caído. 2. Revisar la respuesta y la póliza. 3. Volver a Floci y pedirla. 4. Errores de S3 y falta de credenciales en el adaptador |
| Expected result | 503 "La carátula está en generación. Intente de nuevo en unos segundos." con `Retry-After: 5`, sin clave anotada; al volver S3, 200 y descarga; los errores de boto3 se vuelven `StorageUnavailableError` |
| Tool or method | pytest con Floci; `botocore.stub.Stubber` |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_caratula_api.py::test_tc_08_con_s3_caido_informa_que_esta_en_generacion_y_luego_la_entrega; backend/tests/unit/test_policy_document.py::test_tc_08_un_error_de_s3_al_guardar_se_vuelve_almacenamiento_no_disponible; backend/tests/unit/test_policy_document.py::test_tc_08_sin_credenciales_firmar_falla_como_almacenamiento_no_disponible |
| Status | PASS |

### TC-09 · Bucket privado, cifrado con KMS y permisos mínimos del rol

| Field | Value |
|---|---|
| Covers | SR-06 |
| Kind | SECURITY |
| Risk covered | THR-06 |
| Level | STATIC |
| Preconditions | Terraform 1.10.5 con el proveedor simulado de la prueba de plan |
| Procedure | 1. `terraform fmt -check`, `validate` y `test`. 2. Revisar la política `task_documents`, el bloqueo de acceso público y el cifrado por defecto del bucket. 3. Revisar que el backend no mande cabeceras de cifrado |
| Expected result | Formato, validación y plan sin errores; la política solo permite `s3:PutObject` y `s3:GetObject` en `caratulas/*` y `kms:GenerateDataKey` y `kms:Decrypt` en la llave del bucket; el guardado no cambia el cifrado |
| Tool or method | Terraform CLI; pytest con `Stubber` |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | terraform/modules/ecs/main.tf (`aws_iam_role_policy.task_documents`); terraform/modules/storage_events/main.tf (bloqueo de acceso público y SSE-KMS); `terraform validate` y `terraform test` (1 de 1) en verde, 2026-11-01; backend/tests/unit/test_policy_document.py::test_tc_09_guardar_deja_el_cifrado_a_la_llave_kms_del_bucket |
| Status | PASS |

### TC-10 · Póliza cancelada

| Field | Value |
|---|---|
| Covers | AC-08 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Póliza cancelada |
| Procedure | Pedir su carátula |
| Expected result | 409 "La póliza está cancelada y no tiene carátula vigente." y nada se guarda |
| Tool or method | pytest con Floci |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_caratula_api.py::test_tc_10_una_poliza_cancelada_no_tiene_caratula |
| Status | PASS |

### TC-11 · Botón del portal: avance, enlace nuevo y errores

| Field | Value |
|---|---|
| Covers | AC-01, AC-03, AC-04 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Panel de detalle con el backend simulado |
| Procedure | 1. Pulsar el botón y responder con un enlace. 2. Pulsar dos veces seguidas y luego otra. 3. Responder 503. 4. Abrir una póliza cancelada |
| Expected result | "Preparando la carátula…" en la región de estado y `aria-disabled`; se abre el enlace y dice "La descarga de la carátula comenzó."; un pedido por clic y ninguno mientras prepara; el 503 como alerta con el mensaje del backend, sin códigos; sin botón en la cancelada |
| Tool or method | Vitest (`ng test`) |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-11 el botón pide el enlace, anuncia el avance y abre la descarga"; web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-11 cada clic pide un enlace nuevo y mientras prepara ignora otro clic"; web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-11 si el almacenamiento no responde dice que está en generación como alerta"; web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-11 una póliza cancelada no ofrece la carátula"; 116 de 116 del portal, 2026-11-01 |
| Status | PASS |

### TC-12 · Tiempo de respuesta del enlace

| Field | Value |
|---|---|
| Covers | QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | PERFORMANCE |
| Preconditions | PostgreSQL y Floci locales |
| Procedure | 1. Medir la primera generación. 2. Medir 50 pedidos de la carátula ya guardada y sacar el p95 |
| Expected result | Primera generación por debajo de 1 s; p95 por debajo de 150 ms |
| Tool or method | pytest con Floci |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_caratula_api.py::test_tc_12_el_p95_del_enlace_queda_por_debajo_de_150_ms_y_la_primera_generacion_de_1_s; en local, 2026-11-01: primera generación de 20 ms y p95 de 3,6 ms |
| Status | PASS |

### TC-13 · Accesibilidad del botón

| Field | Value |
|---|---|
| Covers | QR-02 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | Portal y backend locales con Floci; detalle abierto en Chrome |
| Procedure | 1. Auditoría axe-core (WCAG 2.2 AA) con el botón visible. 2. Teclado: Tab desde el título hasta el botón y Enter. 3. Árbol de accesibilidad. 4. 320 px. 5. Pasada con lector de pantalla |
| Expected result | Sin violaciones; el foco llega al botón y Enter descarga; botón "Descargar carátula (PDF)", avance en `role="status"` y error en `role="alert"`; sin desplazamiento horizontal y botón de 44 px; el lector anuncia el botón, el avance y el error |
| Tool or method | Playwright 1.64 con Chrome; axe-core 4.10.2; TalkBack en Chrome 156 de un Galaxy S22 Ultra con Android 16 |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | 2026-11-01, en el PC: axe-core 4.10.2 sin violaciones; 2 Tab desde el título llegan al botón y Enter descarga el PDF; árbol: button "Descargar carátula (PDF)", estado y alerta con sus roles; a 320 px sin desplazamiento horizontal y botón de 44 px. En el teléfono con TalkBack, David confirmó la lectura del botón, el anuncio de "Preparando la carátula…" y, a los 6 s con S3 congelado, la alerta "No se pudo descargar la carátula. La carátula está en generación…"; el registro del portal muestra el botón con `aria-disabled` mientras prepara. Android expone `android.widget.Button` "Descargar carátula (PDF)" enfocable y activable |
| Status | PASS |

### TC-14 · Dependencias, secretos y registros

| Field | Value |
|---|---|
| Covers | SR-08, SR-03, SR-09 |
| Kind | SECURITY |
| Risk covered | THR-08 |
| Level | STATIC |
| Preconditions | Rama de la historia frente a `dev` |
| Procedure | 1. OSV.dev de los 10 paquetes nuevos del lock. 2. `sdd.py scan deps --require-evaluation` y `scan secrets --diff`. 3. Buscar el enlace firmado y la clave del objeto en el registro del backend tras descargar |
| Expected result | Sin vulnerabilidades conocidas; dependencias evaluadas; 0 secretos; el registro solo trae la ruta de la API |
| Tool or method | OSV.dev; sdd.py; grep del registro |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | 2026-11-01: OSV.dev sin vulnerabilidades para boto3 1.43.123, botocore 1.43.123, s3transfer 0.19.3, jmespath 1.1.0, python-dateutil 2.9.0.post0, six 1.17.0, urllib3 2.8.0, fpdf2 2.9.0, pillow 13.0.0 y fonttools 4.68.0; `sdd.py scan deps` con DEP-01 y DEP-02 evaluadas; `sdd.py scan secrets --diff origin/dev` sin hallazgos; el registro del backend de la prueba de punta a punta tiene 0 apariciones de `X-Amz` y de `caratulas/` |
| Status | PASS |

### TC-15 · Descarga de punta a punta en el portal

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, AC-04, AC-08 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | E2E |
| Preconditions | Backend, portal, PostgreSQL y Floci locales, con una base aparte y datos ficticios |
| Procedure | 1. Abrir el detalle de una póliza y descargar con el teclado. 2. Pulsar otra vez. 3. Congelar Floci y pedir otra carátula. 4. Descongelarlo y pulsar otra vez. 5. Abrir una póliza cancelada |
| Expected result | Se descarga `caratula-POL-….pdf` sin cambiar de página; cada clic pide un enlace; con S3 sin responder aparece la alerta de "en generación"; al volver descarga y se quita la alerta; la cancelada no tiene botón |
| Tool or method | Playwright 1.64 con Chrome |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | 2026-11-01, 13 de 13 comprobaciones en dos corridas; la segunda con un Floci nuevo, sin credenciales de AWS y con el bucket creado por el backend al arrancar: PDF descargado en 30 ms con su nombre y `X-Amz-Expires=900`; la página siguió en `/gestion?poliza=POL-2026-00001`; un pedido nuevo por clic; con Floci congelado la alerta llegó a los 6,9 s (antes del ajuste de tiempos de espera tardaba 19,4 s); al descongelarlo descargó `caratula-POL-2026-00002.pdf` y se quitó la alerta; la cancelada sin botón. En el teléfono (Galaxy S22 Ultra con Android 16 y Chrome 156, a 384 px): Gestión sin desplazamiento horizontal; con un toque real el archivo llegó a Descargas en 2 s y abrió en el visor de PDF de Chrome; la póliza pendiente de pago y un titular de nombre largo con tildes y Ñ descargaron bien; la cancelada sin botón; con S3 congelado, "Preparando la carátula…" y la alerta a los 7,7 s sin archivo, y al volver descargó; con el PDF ya guardado y S3 congelado, el portal entregó el enlace y Chrome esperó y terminó la descarga al volver S3 |
| Status | PASS |

### TC-16 · Entorno local con Floci

| Field | Value |
|---|---|
| Covers | AC-11 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | INTEGRATION |
| Preconditions | Floci sin el bucket; backend nativo con `AWS_ENDPOINT_URL` y backend en la imagen de Docker con Floci como `floci` en la red y `S3_PUBLIC_ENDPOINT_URL` |
| Procedure | 1. Crear el bucket dos veces con el adaptador, guardar y firmar con una dirección pública. 2. Levantar el backend nativo y el de la imagen de Docker sin crear el bucket a mano y pedir una carátula |
| Expected result | El bucket se crea una vez sin error; el enlace usa la dirección pública y descarga; los dos backends crean el bucket al arrancar sin credenciales de AWS y el enlace abre desde el navegador |
| Tool or method | pytest con Floci; imagen de Docker del backend |
| Automation | AUTOMATED |
| Environment | CI |
| Evidence | backend/tests/integration/test_policy_caratula_api.py::test_tc_16_con_el_emulador_el_bucket_se_crea_una_vez_y_el_enlace_usa_la_direccion_publica; 2026-11-01: la imagen trae boto3 1.43.123 y fpdf2 2.9.0, el backend en contenedor creó el bucket, firmó con `http://localhost:4567` y el PDF se descargó desde fuera; el backend nativo creó el bucket y pasó la prueba en Chrome (TC-15) |
| Status | PASS |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-05 | — |
| User without permissions | YES | TC-05 | — |
| Access to another user's resources | YES | TC-04 | — |
| Cross-tenant access | YES | TC-04 | — |
| Malicious input | YES | TC-02, TC-06 | — |
| Expired tokens | YES | TC-03 | El enlace prefirmado vence a los 15 minutos |
| Revoked sessions | YES | TC-05 | — |
| Tampered data | NO | — | Floci no comprueba firmas; la firma la comprueba S3 en AWS (RISK-02) |
| Wrong keys | YES | TC-08 | Sin credenciales no se firma y responde 503 |
| External service failures | YES | TC-08, TC-15 | — |
| Race conditions | YES | TC-11 | El portal no manda un pedido mientras otro está en curso; dos generaciones a la vez quedan en RISK-04 |
| Information exposure | YES | TC-01, TC-02, TC-04, TC-14 | — |
| Secrets in code | YES | TC-14 | — |
| Insecure configuration | YES | TC-01, TC-09 | — |
| Vulnerable dependencies | YES | TC-14 | — |

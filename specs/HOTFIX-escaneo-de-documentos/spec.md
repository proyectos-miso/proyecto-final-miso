# HOTFIX · Escaneo de documentos

| Field | Value |
|---|---|
| ID | HOTFIX |
| Title | Escaneo de documentos |
| User story | none: mejora del feature FE-MOV-05 sin historia en el tablero, acordada el 2026-11-14 |
| Owner | Juan Camilo Peña |
| Status | IN_PROGRESS |
| Created | 2026-11-15 |
| Security profile | HIGH |

## 1. Requirements

### User story

Mejora (no es una historia del tablero). FE-MOV-05 pide que el asegurado escanee documentos con la app y los
asocie a su trámite; hasta ahora un siniestro radicado solo tenía las fotos del reporte (HU-MOV-08 y HU-MOV-09) y
el asegurado no tenía cómo enviar facturas, fórmulas o denuncias después.

**Como** asegurado con un siniestro en curso **quiero** escanear documentos con la app y agregarlos al siniestro
**para** que el analista tenga los soportes sin pedírmelos por otro canal.

### Problem and outcome

Problema: los soportes del siniestro llegan por correo o en papel y el analista los pide de nuevo. Resultado: en el
detalle de un siniestro abierto la app ofrece «Agregar documentos»; el asegurado escanea, ve y quita páginas y las
envía; el analista las ve en el expediente del portal con enlaces de corta duración.

### Goals and non-goals

Objetivos: `POST /insured/claims/{claim_number}/documents` (multipart, de 1 a 10 páginas JPEG de hasta 5 MB que el
servidor decodifica y vuelve a codificar sin EXIF, idempotente por `client_upload_id`, aquí `upload_id`); tabla `claim_documents` (migración `0008`); evento
`ClaimDocumentsAdded` en el outbox; `documents` en `GET /experience/claims/{n}`; lista con enlaces en el expediente
del portal; en la app, el puerto `DocumentScanner` con ML Kit y la cámara trasera como respaldo, ViewModel y
pantallas.

Fuera de alcance: cola cifrada sin conexión para los documentos (DEC-02); documentos al radicar el reporte; PDF (el
backend solo acepta JPEG y el portal no los ofrece, RISK-05); borrar documentos ya enviados; aviso push al analista;
manejador del evento `ClaimDocumentsAdded` (es un evento ignorado: el consumidor lo marca como procesado y lo borra
de la cola porque está en `IGNORED_EVENT_TYPES`, ADR 0030);
verificación de la huella de cada documento al abrir el expediente; límite de envíos por usuario e IP.

### Affected paths

| Path | Change |
|---|---|
| backend/app/migrations/versions/0008_documentos_del_siniestro.py | Tabla `claim_documents` con índice por siniestro y único `(upload_id, position)` |
| backend/app/modules/claims/models.py | `ClaimDocumentDB`, `ClaimDocument` y `documents` en `ClaimDetail` |
| backend/app/modules/claims/documents.py | Validación del manifiesto y de las páginas, recepción idempotente, evento y outbox |
| backend/app/modules/claims/service.py | El expediente agrega los documentos con enlaces firmados |
| backend/app/bff/insured/router.py | `POST /insured/claims/{claim_number}/documents` |
| backend/app/main.py | Tope del cuerpo de la ruta de documentos en `BODY_LIMITS` (`DOCUMENTS_MAX_BODY_BYTES`) |
| backend/app/core/middleware.py | El tope sin `Content-Length` responde 413 aunque un middleware interno envuelva el corte |
| backend/pyproject.toml | `pillow` declarado como dependencia directa (ya llegaba con fpdf2) |
| backend/poetry.lock | Solo cambia la huella del archivo de dependencias |
| backend/tests/unit/test_body_size_limit.py | Prueba del corte envuelto en un grupo de tareas |
| backend/tests/integration/test_claim_documents_api.py | Pruebas de integración |
| backend/tests/integration/test_migrations.py | Migración `0008` desde `0007` y su reversión |
| web/src/app/core/models/claim.model.ts | `ClaimDocument` (solo `image/jpeg`) y `documents` |
| web/src/app/features/siniestros/claim-file.component.ts | Lista de documentos con enlaces que nombran el envío y avisan la pestaña nueva |
| web/src/app/features/siniestros/claim-labels.ts | Etiqueta `DOCUMENTS_ADDED` del historial |
| web/src/app/features/siniestros/claim-file.component.spec.ts | Pruebas del expediente |
| movil/gradle/libs.versions.toml | `play-services-mlkit-document-scanner` 16.0.0 |
| movil/app/build.gradle.kts | Dependencia del escáner |
| movil/app/src/main/java/com/solventa/app/domain/claims/ClaimDocuments.kt | Página, resultados, límites y contratos |
| movil/app/src/main/java/com/solventa/app/ui/claims/DocumentScanner.kt | Puerto `DocumentScanner` y adaptador de ML Kit |
| movil/app/src/main/java/com/solventa/app/data/claims/ScannedPageReader.kt | Lee a memoria y borra las páginas que ML Kit deja en la caché |
| movil/app/src/main/java/com/solventa/app/data/claims/HttpClaimDocumentsApi.kt | Multipart en memoria |
| movil/app/src/main/java/com/solventa/app/data/claims/SessionClaimDocuments.kt | Envío con la sesión abierta |
| movil/app/src/main/java/com/solventa/app/ui/claims/ClaimDocumentsViewModel.kt | Escanear, ver, quitar, enviar y errores |
| movil/app/src/main/java/com/solventa/app/ui/claims/ClaimDocumentsScreens.kt | Pantalla de documentos y cámara de respaldo |
| movil/app/src/main/java/com/solventa/app/ui/claims/ClaimsScreens.kt | «Agregar documentos» en el detalle del siniestro abierto |
| movil/app/src/main/java/com/solventa/app/MainActivity.kt | Navegación y ventana segura |
| movil/app/src/main/java/com/solventa/app/SolventaApplication.kt | Cliente del envío y limpieza de la carpeta del escáner al arrancar |
| movil/app/src/main/res/values/strings.xml | Textos |
| movil/app/src/test/** | Pruebas de la app |
| docs/adr/0032-mobile-document-scanner-port.md | ADR Proposed del puerto del escáner |
| CHANGELOG.md | Entrada de la mejora |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Asegurado | Agregar documentos a su siniestro | 1. Abre un siniestro recibido o en evaluación. 2. Toca «Agregar documentos». 3. Escanea con el escáner de Google. 4. Ve las páginas y quita las que sobran. 5. Envía. 6. Ve «Recibimos N páginas» | Sin escáner de Google: fotografía cada página con la cámara trasera. Sin red: aviso «Sin conexión, intente de nuevo» y las páginas siguen en memoria mientras la pantalla esté abierta. Siniestro decidido mientras tanto: aviso sin envío. Más de 10 páginas: el escáner no deja pasar del tope |
| UC-02 | Analista | Ver los documentos del asegurado | 1. Abre el expediente en el portal. 2. Ve la lista con fecha y tamaño. 3. Abre cada página con su enlace | Enlace vencido: recarga el expediente. S3 caído: la lista sale sin enlaces |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Agregar documentos al siniestro de otra persona o reutilizar el envío de otra | Asegurado malicioso | THR-01 | 404 igual que un siniestro inexistente | TC-03 |
| AB-02 | Agregar documentos a un siniestro ya decidido para cambiar el expediente | Asegurado | THR-01 | 409 sin guardar nada | TC-03 |
| AB-03 | Subir un ejecutable, un PDF, un archivo enorme, una imagen que agota la memoria o cientos de páginas | Persona maliciosa | THR-02 | 413 antes de leer el formulario si el cuerpo pasa de 10 × 5 MB + 512 KiB, con o sin `Content-Length`; 422 sin firma JPEG, si no se decodifica o pasa de 25 megapíxeles; 413 sobre 5 MB por página; 422 con más de 10 páginas o pasado el tope del siniestro; el parser no acepta más de 10 archivos | TC-04, TC-05, TC-17 |
| AB-04 | Cambiar una página en tránsito | Atacante en la red o malware | THR-03 | 422 si la huella no coincide | TC-06 |
| AB-05 | Ver documentos de siniestros de otro socio desde el portal | Analista de un socio | THR-04 | 404 en el expediente ajeno | TC-09 |
| AB-06 | Recuperar las páginas del teléfono perdido o de una copia de respaldo | Ladrón o malware | THR-05 | Las páginas solo existen en memoria; los archivos de ML Kit se borran al leerlos | TC-11, TC-12 |
| AB-07 | Una cuenta en verificación de identidad agrega documentos a siniestros del documento | Persona maliciosa | THR-01 | 403 | TC-07 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN el asegurado abre un siniestro en REPORTED o IN_REVIEW, THE app SHALL ofrecer «Agregar documentos»; en otros estados no | STATED |
| AC-02 | WHEN el asegurado escanea, THE app SHALL mostrar las páginas en orden, dejar quitar cualquiera y no pasar de 10 | STATED |
| AC-03 | WHERE no hay Google Play services o el escáner no arranca, THE app SHALL fotografiar las páginas con la cámara trasera sin recorte | STATED |
| AC-04 | WHEN el asegurado envía, THE backend SHALL guardar de 1 a 10 páginas JPEG de hasta 5 MB, decodificadas y codificadas de nuevo sin EXIF ni comentarios, bajo `siniestros/{claim}/documentos/{upload}/{page}.jpg` y responder 201 con el número de páginas | DERIVED: el pedido dice JPEG o PDF; solo JPEG por decisión del orquestador del 2026-11-16 para cerrar RISK-05 |
| AC-05 | WHEN llega otra vez el mismo `upload_id`, THE backend SHALL responder 200 con la misma constancia sin duplicar páginas | STATED |
| AC-06 | IF el siniestro no es del titular o no existe, THE backend SHALL responder 404; IF ya fue decidido, 409 | STATED |
| AC-07 | IF una página no es JPEG por su firma o no se decodifica, pesa más de 5 MB, no coincide con su sha256, hay más de 10 o faltan, o el cuerpo pasa del tope, THE backend SHALL rechazar el envío sin guardar nada | STATED |
| AC-08 | WHEN se registran las páginas, THE backend SHALL escribir `ClaimDocumentsAdded` en el outbox con solo identificadores, en la misma transacción | STATED |
| AC-09 | WHEN el analista abre el expediente, THE portal SHALL listar los documentos con enlaces firmados de 15 minutos, con la misma autorización del expediente | STATED |
| AC-10 | IF no hay red, THE app SHALL avisar «Sin conexión, intente de nuevo» sin guardar las páginas en disco, y el reintento SHALL usar el mismo `upload_id` | STATED |
| AC-11 | THE app SHALL tener las páginas solo en memoria y borrar enseguida los archivos que deje el escáner | STATED |
| AC-12 | IF la identidad del asegurado no está aprobada, THE backend SHALL negar la subida con 403 | DERIVED: la regla de FE-MOV-01 para las rutas del asegurado |
| AC-13 | THE backend SHALL limitar a 50 las páginas de un siniestro | DERIVED: sin tope, una cuenta podría llenar el bucket con envíos distintos |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | ACCESSIBILITY | Pantallas de documentos | TalkBack lee el título, cada página con su vista previa, «Quitar la página N», el obturador y los avisos; objetivos de 48 dp |
| QR-02 | COMPATIBILITY | Teléfonos sin Google Play services | El flujo completo funciona con la cámara trasera de la app cuando el escáner no está o no arranca |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | Los documentos del siniestro identifican al asegurado y a terceros |
| Sensitive data (special categories, biometrics, health, financial, minors) | YES | Fórmulas, historias clínicas y facturas pueden traer datos de salud y financieros |
| Authentication, sessions, credentials or one-time codes | NO | Usa la sesión existente sin cambiarla |
| Authorization or access to other users' resources | YES | Solo el titular sube y solo el analista autorizado ve |
| Payments or money movement | NO | No aplica |
| Third-party services or integrations | YES | ML Kit Document Scanner de Google en el teléfono |
| Infrastructure, network, IAM, CI/CD or secrets | NO | El rol ya escribe y lee `siniestros/*`; sin cambios en Terraform |
| Multiple tenants, partners or organizations | YES | El analista de un socio solo ve los documentos de sus pólizas |
| Storage or processing on a mobile device | YES | Páginas en memoria y archivos temporales del escáner |
| Web UI or data exposed in the browser | YES | Enlaces firmados en el expediente del portal |
| New or updated dependencies | YES | `play-services-mlkit-document-scanner` 16.0.0 |
| Critical paths (sdd/security/critical-paths.md) | YES | `**/migrations/**`, `**/data/claims/**`, `MainActivity.kt`, `SolventaApplication.kt`, `**/*.gradle*` y `libs.versions.toml` |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | Solo el titular con identidad aprobada agrega documentos, y solo a un siniestro abierto; lo ajeno responde 404 | THR-01 | CTL-01 | HIGH |
| SR-02 | Entrada acotada: cuerpo cortado antes del parser, manifiesto estricto, de 1 a 10 páginas, firma JPEG y decodificación con tope de píxeles, 5 MB por página, 50 por siniestro, parser con tope de archivos | THR-02 | CTL-02 | HIGH |
| SR-03 | La huella SHA-256 de cada página coincide con la del teléfono; el envío es idempotente y sus metadatos solo los ve quien ve el expediente | THR-03, DATA-02 | CTL-03, CTL-04 | MEDIUM |
| SR-04 | El analista ve los documentos con enlaces firmados de 15 minutos y la misma autorización del expediente, por socio; el portal los muestra sin interpretar contenido | THR-04, DATA-01 | CTL-04, CTL-08 | HIGH |
| SR-05 | Ni los registros, ni el outbox, ni el historial guardan contenido ni datos personales de los documentos | THR-06 | CTL-05 | MEDIUM |
| SR-06 | La app no deja páginas en claro en disco, registros ni estado guardado; la ventana es segura; el escáner no importa de la galería | DATA-01, THR-05 | CTL-06 | HIGH |
| SR-07 | La dependencia nueva se evalúa y queda detrás de un puerto con respaldo | DEP-01, RISK-01 | CTL-07 | MEDIUM |

## 2. Design

### Components

- **Backend:** `BodySizeLimitMiddleware` corta con 413 el cuerpo de la ruta que pase de `DOCUMENTS_MAX_BODY_BYTES`
  (10 × 5 MB + 512 KiB) antes de leer el formulario: por `Content-Length` o contando los bytes si no viene.
  `claims/documents.py` (`DocumentsUploadRequest` con `extra="forbid"`, `checked_pages`, `clean_jpeg`,
  `ClaimDocuments.receive`: idempotencia por `upload_id` antes de nada, siniestro del titular bloqueado `FOR UPDATE`,
  idempotencia otra vez ya con el bloqueo (si otro envío igual terminó mientras tanto, devuelve ese sin tocar S3),
  estado abierto, tope de 50, páginas validadas y recodificadas con Pillow en un hilo aparte, escritura en S3, filas
  con la huella de lo guardado, evento `DOCUMENTS_ADDED` del historial y outbox en un commit; ante la carrera del
  índice único devuelve el envío ganador). El router lee el multipart con `request.form(max_files=10, max_fields=1)`
  y cada página con tope de 5 MB + 1 byte; usa `insured_account` (sesión de acceso con identidad aprobada; el
  token de solo reporte no sirve). `ClaimService.detail` agrega `documents` con `storage.view_url(..., 900)`.
- **Portal:** `ClaimDocument` y la sección «Documentos del asegurado» del expediente: enlace «Envío del <fecha>,
  página N» con `rel="noopener noreferrer"` y el texto oculto «(se abre en otra pestaña)», y el tamaño; sin enlace si
  S3 no firmó.
- **App:** puerto `DocumentScanner` con `MlKitDocumentScanner` (sin galería, JPEG, `SCANNER_MODE_BASE_WITH_FILTER`,
  tope de páginas restante) y `ScannedPageReader` (lee, recodifica con `EvidenceEncoder`, borra los archivos de la
  caché siempre y pone en cero las páginas ya leídas si una posterior falla); la carpeta `mlkit_docscan_ui_client`
  de la caché se borra al arrancar la app y al cerrar la pantalla de documentos; sin Play services,
  `CameraXEvidenceCamera` trasera. `ClaimDocumentsViewModel` guarda las páginas en memoria, regenera el `upload_id`
  al cambiar las páginas y lo conserva al reintentar, y pone los bytes en cero al quitar, enviar, salir o cambiar de
  siniestro; la miniatura se decodifica reducida y su bitmap se recicla al quitar la página. `SessionClaimDocuments` + `HttpClaimDocumentsApi` envían con la sesión abierta.

### Data flows

App (memoria) → `POST /insured/claims/{n}/documents` (multipart, HTTPS) → tope del cuerpo → validación y
recodificación → S3 `siniestros/{n}/documentos/{upload}/{page}.jpg` (SSE-KMS del bucket) → `claim_documents`,
`claim_events`, `outbox` → publicador → SQS `events` (evento ignorado: el consumidor lo marca procesado y lo borra).
Portal → `GET /experience/claims/{n}` → enlaces prefirmados de 15 minutos → navegador → S3.

### Data contract

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| POST /insured/claims/{claim_number}/documents | multipart: `manifest` (JSON `{upload_id: uuid, pages: [{position, sha256}]}` sin campos extra) y `page_1`…`page_10` | 201 `{claim_number, upload_id, pages, uploaded_at}`; 200 igual si el envío ya existía; 401; 403 identidad en verificación o rol; 404 siniestro ajeno o inexistente; 409 siniestro decidido; 413 cuerpo de más de 10 × 5 MB + 512 KiB o página de más de 5 MB; 422 manifiesto, formato (no JPEG o no decodifica), huella, páginas o tope; 503 con `Retry-After: 30` si S3 falla |
| GET /experience/claims/{claim_number} | — | `ClaimDetail` con `documents: [{document_id, upload_id, position, content_type, size_bytes, uploaded_at, url}]`; `url` nulo si S3 no firmó |
| Evento `ClaimDocumentsAdded` | — | `{claim_number, upload_id, pages}` |

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| Tipo por firma del archivo (`\xff\xd8\xff`), no por el `Content-Type` del cliente | CTL-02 | Confiar en el tipo declarado: se puede falsear |
| Solo JPEG, decodificado y codificado de nuevo con Pillow (orientación aplicada, sin EXIF, ICC ni comentarios, tope de 25 megapíxeles, a lo sumo dos envíos recodificándose a la vez por proceso; si la página limpia pasa de 5 MB se reintenta a calidad 80 y 70 y, si sigue pasando, 413) | CTL-02, CTL-08 | Aceptar PDF: puede traer contenido activo que llega al navegador del analista. Validar con `Image.verify()` y guardar el original: deja EXIF (ubicación, equipo) y cualquier dato escondido en el archivo |
| Tope del cuerpo en el middleware antes del parser | CTL-02 | Medir en la ruta: Starlette ya habría leído todo el formulario, con partes en archivos temporales |
| `request.form(max_files=10, max_fields=1)` en lugar de diez parámetros `File` | CTL-02 | Parámetros de FastAPI: el parser acepta hasta 1000 archivos antes de que el código los cuente |
| `insured_account` y no `reporting_account` | CTL-01 | Aceptar el token de solo reporte: amplía su uso, y sin cola en segundo plano no hace falta |
| La idempotencia mira el dueño del envío y responde 404 si es de otra persona | CTL-01 | Responder la constancia ajena: revelaría el siniestro de otro |
| Sin cola cifrada para los documentos; aviso sin conexión y páginas en memoria (DEC-02) | CTL-06 | Reutilizar `EncryptedClaimStore`: otra tabla, otro trabajo en segundo plano y el token de reporte para un caso menos urgente |
| Recodificar las páginas del escáner con `EvidenceEncoder` | CTL-06 | Enviar el JPEG de ML Kit tal cual: tamaño variable y metadatos |

### Architecture decisions

| ADR | Title | Status |
|---|---|---|
| docs/adr/0032-mobile-document-scanner-port.md | Document scanning behind a port, with ML Kit and a camera fallback | Proposed |

### Configuration and deployment

- Migración `0008`; sin variables nuevas; sin cambios en Terraform: la sentencia `FotosDeSiniestros` ya permite
  `s3:PutObject` y `s3:GetObject` sobre `siniestros/*`.
- La app necesita Google Play services para el escáner; sin ellos usa la cámara (permiso `CAMERA` ya declarado).

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Migración, modelo, recepción idempotente, outbox y endpoint | AC-04 a AC-08, AC-12, AC-13, SR-01, SR-02, SR-03, SR-05 | CTL-01, CTL-02, CTL-03, CTL-05 | DONE | backend/app/modules/claims/documents.py; backend/tests/integration/test_claim_documents_api.py |
| T-02 | Documentos en el expediente del analista y en el portal | AC-09, SR-04 | CTL-04, CTL-08 | DONE | backend/app/modules/claims/service.py; web/src/app/features/siniestros/claim-file.component.ts |
| T-03 | Evaluar y agregar ML Kit detrás del puerto con respaldo de cámara | AC-03, QR-02, SR-07 | CTL-07 | DONE | movil/app/src/main/java/com/solventa/app/ui/claims/DocumentScanner.kt |
| T-04 | ViewModel, pantallas, cliente HTTP y navegación de la app | AC-01, AC-02, AC-10, AC-11, QR-01, SR-06 | CTL-06 | DONE | movil/app/src/main/java/com/solventa/app/ui/claims/ClaimDocumentsViewModel.kt |
| T-05 | Prueba manual de punta a punta en DEV con un teléfono con y sin Play services | AC-01 a AC-04, AC-09, AC-10, QR-01, QR-02 | — | TODO | — |

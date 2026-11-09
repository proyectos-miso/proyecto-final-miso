# HU-WEB-16 · Descarga de caratula

| Field | Value |
|---|---|
| ID | HU-WEB-16 |
| Title | Descarga de caratula |
| User story | https://github.com/proyectos-miso/proyecto-final-miso/issues/114 |
| Owner | David Gutiérrez |
| Status | DONE |
| Created | 2026-10-31 |
| Security profile | HIGH |

## 1. Requirements

### User story

**HU-WEB-16 · Descarga de carátula / certificado PDF** (Sprint 2, Must have, 2 puntos, FE-WEB-02, ESC-LAT-04, ESC-SEG-02; depende de HU-WEB-05).

**Como** cliente o asesor comercial **quiero** descargar el PDF firmado de la póliza almacenado en S3 **para** disponer del soporte legal de amparo y las condiciones contratadas.

Criterios de aceptación de la historia:

1. **Descarga segura.** *Dada* una póliza emitida, *cuando* el usuario pide la carátula, *entonces* obtiene un enlace HTTPS prefirmado que vence en 15 minutos.
2. **Contenido correcto.** *Dado* el PDF descargado, *cuando* se abre, *entonces* contiene número de póliza, titular, plan, coberturas, vigencia y prima.
3. **Enlace vencido.** *Dado* un enlace con más de 15 minutos, *cuando* se usa, *entonces* la descarga se rechaza y se puede generar uno nuevo.
4. **Póliza sin documento.** *Dada* una póliza cuyo PDF aún no está disponible, *cuando* se solicita, *entonces* se informa que el documento está en generación.

Requisitos no funcionales: HA-04 (servir la consulta desde una vista de lectura, #34), HA-15 (proteger los datos personales en transmisión, #45), HA-16 (proteger los datos personales almacenados, #46). Mockup: https://solventa-web.vercel.app/?pestana=gestion, detalle de una póliza, donde va el botón de descarga de la carátula. Tareas: #184 generación del PDF y almacenamiento cifrado en S3; #185 endpoint de URL prefirmada con vencimiento; #186 botón de descarga en el detalle de la póliza; #187 pruebas unitarias y de integración (S3 emulado con Floci).

### Problem and outcome

La póliza emitida solo existe en la base: nadie puede entregar al cliente un documento con lo que contrató. El bucket cifrado de documentos ya existe en Terraform, pero el backend no escribe en él. Resultado: desde el detalle de una póliza en Gestión, el analista pulsa "Descargar carátula (PDF)" y el navegador descarga el PDF con un enlace prefirmado de 15 minutos; el backend genera el PDF al primer pedido, lo guarda cifrado en S3 y lo reutiliza después.

### Goals and non-goals

Objetivos: PDF de la carátula con los datos de la emisión; almacenamiento cifrado en S3 (Floci en local y en el CI); endpoint que entrega un enlace prefirmado de 15 minutos con el mismo alcance por socio del detalle; botón de descarga en el detalle de Gestión; permisos mínimos del backend sobre el bucket.

Fuera de alcance:

- **Firma digital del PDF con certificado:** exige un certificado de una entidad de certificación. La integridad del documento la dan el bucket privado, el cifrado con KMS y el versionado.
- **Descarga desde la app del asegurado y desde la confirmación de emisión del asesor:** las tareas y el mockup ubican el botón en el detalle de Gestión. El endpoint queda listo para que otra historia lo use con su propio alcance.
- **Regenerar la carátula cuando cambia la póliza:** su contenido sale de las condiciones copiadas al emitir, que no cambian.
- **Documento completo y contacto del titular en el PDF.**

### Affected paths

| Path | Change |
|---|---|
| backend/pyproject.toml | Dependencias boto3 y fpdf2 |
| backend/poetry.lock | Versiones fijas de boto3, fpdf2 y sus transitivas |
| backend/requirements.txt | boto3 y fpdf2 para la instalación sin Poetry |
| backend/app/core/storage.py | Puerto de almacenamiento de documentos y adaptador de S3 |
| backend/app/core/config.py | `S3_PUBLIC_ENDPOINT_URL`, opcional, solo para Floci con docker compose |
| backend/app/modules/policy/document.py | PDF de la carátula y caso de uso del enlace |
| backend/app/modules/policy/models.py | Columna `pdf_s3_key` y respuesta del enlace |
| backend/app/bff/experience/router.py | Endpoint del enlace de la carátula |
| backend/app/main.py | Columna `pdf_s3_key` en bases existentes y bucket del emulador al arrancar |
| docker-compose.yml | `S3_PUBLIC_ENDPOINT_URL` del backend |
| backend/.env.example | `AWS_ENDPOINT_URL` de Floci para el backend local |
| README.md | Las pruebas también usan Floci |
| backend/README.md | Las pruebas también usan Floci |
| backend/tests/** | Pruebas unitarias, de integración con Floci y de tiempo de respuesta |
| web/src/app/core/models/policy.model.ts | Contrato del enlace |
| web/src/app/core/policies/policy.service.ts | Pedido del enlace |
| web/src/app/features/gestion/policy-detail.component.ts | Botón de descarga y avisos |
| terraform/modules/ecs/main.tf | Política del rol de la tarea sobre `caratulas/` y la llave KMS |
| terraform/modules/ecs/variables.tf | ARN del bucket y de la llave KMS |
| terraform/modules/storage_events/outputs.tf | Salida con el ARN de la llave KMS |
| terraform/main.tf | Pasa el ARN del bucket y de la llave al módulo de ECS |
| .github/workflows/ci.yml | Floci como servicio de las pruebas del backend |
| docs/adr/0023-backend-document-storage.md | Decisión del almacenamiento de documentos |
| docs/adr/README.md | Índice con la ADR 0023 |
| CHANGELOG.md | Entrada de la historia |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Analista de operaciones | Descargar la carátula | 1. Abre el detalle de una póliza en Gestión. 2. Pulsa "Descargar carátula (PDF)". 3. El backend genera el PDF si no existe, lo guarda cifrado y entrega un enlace de 15 minutos. 4. El navegador descarga `caratula-POL-….pdf` | Sin almacenamiento disponible: "La carátula está en generación. Intente de nuevo en unos segundos." Póliza cancelada: "La póliza está cancelada y no tiene carátula vigente." Enlace vencido: S3 lo rechaza y al pulsar otra vez se obtiene uno nuevo |
| UC-02 | Usuario de un socio | Descargar la carátula de una póliza de su socio | Igual a UC-01 | Póliza de otro socio o del portal: la misma respuesta que si no existiera. Socio revocado: no descarga |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Un usuario de un socio pide la carátula de una póliza de otro socio o del portal | Usuario de un socio | THR-01 | 404 igual al de una póliza inexistente | TC-04 |
| AB-02 | Un asesor o alguien sin sesión pide la carátula | Asesor, atacante externo | THR-02 | 403 o 401 | TC-05 |
| AB-03 | Reusar o reenviar el enlace después de 15 minutos | Cualquiera con el enlace | THR-03 | S3 rechaza el enlace vencido | TC-03 |
| AB-04 | Usar el PDF para obtener el documento completo, el contacto o los beneficiarios del titular | Analista | THR-04 | El PDF trae el documento enmascarado y nada de contacto ni beneficiarios | TC-02 |
| AB-05 | Mandar en la ruta un número que lleve a otra clave del bucket | Analista | THR-05 | El número se valida con un patrón y la clave la arma el servidor | TC-06 |
| AB-06 | Leer el objeto sin enlace o acceder al bucket desde fuera | Atacante externo | THR-06 | Bucket privado y cifrado; el rol del backend solo toca `caratulas/` | TC-09 |
| AB-07 | Pedir la carátula muchas veces para cargar el backend | Analista | THR-07 | El PDF se genera una vez; los pedidos siguientes solo firman el enlace | TC-07 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN el analista pide la carátula de una póliza emitida, THE backend SHALL entregar un enlace HTTPS prefirmado que vence a los 15 minutos | STATED |
| AC-02 | THE PDF SHALL contener número de póliza, titular, plan, coberturas, vigencia y prima | STATED |
| AC-03 | IF el enlace tiene más de 15 minutos, THE descarga SHALL rechazarse, y WHEN el analista pulsa otra vez, THE portal SHALL obtener un enlace nuevo | STATED |
| AC-04 | IF el PDF no se puede guardar o leer en el almacenamiento, THE endpoint SHALL responder 503 con `Retry-After` y THE portal SHALL informar que la carátula está en generación | STATED |
| AC-05 | THE backend SHALL generar el PDF al primer pedido, guardarlo cifrado en `caratulas/{número}.pdf` y reutilizarlo en los pedidos siguientes | DERIVED: la historia pide el PDF almacenado en S3; generarlo al emitir alargaría la emisión de HU-WEB-05 y no hay un procesador de eventos |
| AC-06 | WHEN un usuario ligado a un socio pide la carátula de una póliza de otro socio o del portal, THE endpoint SHALL responder 404 igual que si no existiera; sin sesión 401; con otro rol o con el socio revocado 403 | DERIVED: mismo alcance que el detalle de HU-WEB-09 (HA-14) |
| AC-07 | THE PDF SHALL mostrar el documento del titular enmascarado (últimos 4 dígitos) y SHALL no incluir correo, teléfono ni beneficiarios | DERIVED: quien descarga desde operaciones no debe ver más que en el detalle (HA-16) |
| AC-08 | IF la póliza está cancelada, THE endpoint SHALL responder 409 con "La póliza está cancelada y no tiene carátula vigente." | DERIVED: una carátula de una póliza cancelada haría creer que hay amparo |
| AC-09 | THE enlace SHALL descargar el archivo como `caratula-{número}.pdf`, y THE respuesta del backend SHALL llevar `Cache-Control: no-store` | DERIVED: el enlace da acceso al documento por 15 minutos y no debe quedar en cachés |
| AC-10 | WHEN la póliza no tiene titular registrado, THE PDF SHALL mostrar "Sin nombre registrado" y el documento enmascarado | DERIVED: las pólizas B2B y las anteriores a HU-WEB-08 no traen titular |
| AC-11 | WHERE el backend usa el emulador (`AWS_ENDPOINT_URL`), THE backend SHALL crear el bucket al arrancar si falta, usar credenciales de prueba y firmar los enlaces con `S3_PUBLIC_ENDPOINT_URL` cuando esté definida | DERIVED: el README documenta el backend local con Floci, con Poetry y con docker compose; sin esto la descarga no funcionaría en local |
| AC-12 | THE PDF SHALL usar las secciones, etiquetas y formatos del detalle de Gestión: Titular, Plan y vigencia y Coberturas contratadas, "Portal Solventa" sin socio y montos como "$ 250.000.000 COP" | DERIVED: el analista compara el PDF con el detalle de HU-WEB-09 |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | PERFORMANCE | Tiempo de respuesta del enlace | p95 por debajo de 150 ms en 50 pedidos de una carátula ya guardada, y la primera generación por debajo de 1 s; PostgreSQL y Floci locales |
| QR-02 | ACCESSIBILITY | El botón se usa con teclado y lector de pantalla | WCAG 2.2 AA: botón con nombre accesible, foco visible, avance anunciado y error como alerta |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | Nombre y documento enmascarado del titular en el PDF guardado en S3 |
| Sensitive data (special categories, biometrics, health, financial, minors) | NO | El PDF trae condiciones del plan y primas, sin datos de salud ni del medio de pago |
| Authentication, sessions, credentials or one-time codes | NO | Usa el ingreso y la sesión existentes |
| Authorization or access to other users' resources | YES | Rol analyst y acceso a una póliza por su número |
| Payments or money movement | NO | No cobra ni mueve dinero |
| Third-party services or integrations | YES | S3 y KMS de AWS (Floci en local y en el CI) |
| Infrastructure, network, IAM, CI/CD or secrets | YES | Política del rol de la tarea de ECS y servicio de Floci en el CI |
| Multiple tenants, partners or organizations | YES | Cada socio descarga solo las carátulas de sus pólizas |
| Storage or processing on a mobile device | NO | No aplica |
| Web UI or data exposed in the browser | YES | Botón de descarga y enlace prefirmado en el navegador |
| New or updated dependencies | YES | boto3 y fpdf2 |
| Critical paths (sdd/security/critical-paths.md) | YES | Terraform, el workflow del CI, `pyproject.toml`, `requirements.txt`, `docker-compose.yml`, `.env.example`, `backend/app/core/config.py` (una variable opcional del emulador) y `backend/app/main.py` (la columna nueva y el bucket del emulador) |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | El alcance por socio sale del usuario en la base en cada pedido; una póliza fuera del alcance responde igual que una inexistente | DATA-01, THR-01 | CTL-01 | HIGH |
| SR-02 | Solo el rol analyst pide la carátula; sin sesión 401, con otro rol o con el socio revocado 403 | THR-02 | CTL-02 | HIGH |
| SR-03 | El enlace es HTTPS, vence a los 15 minutos, solo permite leer el objeto de esa póliza y no queda en cachés, en los registros ni en el almacenamiento del navegador | DATA-02, THR-03 | CTL-03, CTL-09 | HIGH |
| SR-04 | El PDF trae el documento enmascarado y no trae correo, teléfono ni beneficiarios | DATA-01, THR-04 | CTL-04 | HIGH |
| SR-05 | El número de póliza se valida con un patrón y la clave del objeto la arma el servidor | THR-05 | CTL-05 | MEDIUM |
| SR-06 | El bucket es privado y cifrado con KMS, y el rol del backend solo lee y escribe en `caratulas/` | DATA-01, THR-06 | CTL-06 | HIGH |
| SR-07 | El PDF se genera una vez por póliza y el cliente de S3 tiene tiempos de espera acotados | THR-07 | CTL-07 | MEDIUM |
| SR-08 | boto3 y fpdf2 se evalúan antes de agregarlos, en versiones sin vulnerabilidades conocidas | THR-08 | CTL-08 | MEDIUM |
| SR-09 | El backend usa en AWS solo las credenciales temporales del rol de la tarea; ninguna llave de AWS va en código, configuración, imágenes ni registros | DATA-03 | CTL-10 | HIGH |

## 2. Design

### Components

Backend (ADR 0004 y 0005, y la nueva ADR 0023):

- `core/storage.py` (nuevo): puerto `DocumentStorage` con `put_pdf(key, data)` y `download_url(key, filename, expires_in)`, y el adaptador `S3DocumentStorage` con boto3:
  - `put_object` sin cabeceras de cifrado, para que aplique la llave KMS por defecto del bucket (mandar `aws:kms` sin llave usaría la administrada por AWS); el bucket y el endpoint salen de `S3_BUCKET_POLICIES` y `AWS_ENDPOINT_URL`, que ya existen;
  - `generate_presigned_url` de `get_object` con `ResponseContentDisposition` de descarga y `ResponseContentType: application/pdf`;
  - tiempos de espera de 2 s para conectar y 2 s para leer, con 2 intentos en total: con S3 sin responder el aviso llega en unos 7 s; los errores de boto3 se vuelven `StorageUnavailableError`;
  - sesión de boto3 propia, porque la global guarda en caché las primeras credenciales que encuentra;
  - boto3 es síncrono: las llamadas corren en un hilo para no bloquear el servidor;
  - con el emulador (`AWS_ENDPOINT_URL`): credenciales de prueba, `ensure_bucket` al arrancar y, si está `S3_PUBLIC_ENDPOINT_URL`, un segundo cliente que solo firma con la dirección que alcanza el navegador.
- `modules/policy/document.py` (nuevo):
  - `render_caratula(policy, partner_name, generated_at) -> bytes` con fpdf2: número, plan, vigencia, socio, titular con documento enmascarado, coberturas incluidas con suma y deducible, suma asegurada, prima mensual y anual, y la fecha de generación; sin el estado, que cambia después de generar; solo texto, sin imágenes ni HTML, y lo que no cabe en Latin-1 se escribe como "?";
  - `get_caratula_link(session, storage, policy_number, partner_id)`: busca la póliza dentro del alcance; `None` si no existe o está fuera; `PolicyCancelledError` si está cancelada; si no tiene `pdf_s3_key`, genera, guarda y lo anota; devuelve el enlace de 15 minutos y su vencimiento.
- `modules/policy/models.py`: columna `pdf_s3_key` en `PolicyDB` (la que ya anunciaban `PolicyRecord` y `PartnerPolicyResponse`) y `CaratulaLink(url, expires_at)`.
- `bff/experience/router.py`: `GET /api/v1/experience/policies/{policy_number}/caratula` con la dependencia de alcance del detalle; `Cache-Control: no-store`.
- `main.py`: `ALTER TABLE policies ADD COLUMN IF NOT EXISTS pdf_s3_key`, como las columnas de HU-WEB-08, y el bucket del emulador al arrancar, con un aviso si falla.
- `core/config.py`: `S3_PUBLIC_ENDPOINT_URL`, opcional.

Portal:

- `features/gestion/policy-detail.component.ts`: botón "Descargar carátula (PDF)" bajo el estado de la póliza, salvo en las canceladas; mientras pide el enlace muestra "Preparando la carátula…" en una región de estado y marca el botón con `aria-disabled` sin quitarle el foco; el 503 y el 409 se muestran como alerta; con el enlace, el navegador lo abre (`OPEN_DOWNLOAD`) y S3 lo entrega como descarga, así que la página no cambia.
- `core/policies/policy.service.ts`: `getCaratulaLink(number)`.

Infraestructura:

- `modules/storage_events`: salida `kms_key_arn`.
- `modules/ecs`: política del rol de la tarea con `s3:PutObject` y `s3:GetObject` sobre `caratulas/*` del bucket, y `kms:GenerateDataKey` y `kms:Decrypt` sobre la llave del almacenamiento.
- CI: Floci 2.3.0 como servicio del trabajo del backend.

### Data flows

Analista → portal (detalle en `/gestion?poliza=…`) → `GET /api/v1/experience/policies/{número}/caratula` (token del analista) → backend (rol, usuario y socio en la base) → PostgreSQL (`policies` con `partners`) → si falta el PDF: fpdf2 → `PutObject` a S3 cifrado con KMS → `pdf_s3_key` en la póliza → enlace prefirmado firmado con las credenciales del rol de la tarea → portal → navegador → S3 por HTTPS (fuera del backend) → archivo `caratula-POL-….pdf`.

### Data contract

`GET /api/v1/experience/policies/{policy_number}/caratula`, con rol `analyst`. `policy_number` cumple `^POL-\d{4}-\d{5}$`.

Respuesta `200`, con `Cache-Control: no-store`:

| Campo | Tipo | Notas |
|---|---|---|
| url | string | Enlace HTTPS prefirmado de `GetObject`; vence a los 900 s |
| expires_at | fecha y hora ISO 8601 | Cuándo vence el enlace |

Errores: `401` sin sesión; `403` con otro rol o con el socio revocado; `404` con `"No encontramos la póliza."` si no existe o está fuera del alcance; `409` con `"La póliza está cancelada y no tiene carátula vigente."`; `422` si el número no cumple el patrón; `503` con `Retry-After: 5` y `"La carátula está en generación. Intente de nuevo en unos segundos."` si el almacenamiento no responde. Clasificación: CONFIDENTIAL (DATA-01, DATA-02).

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| El socio sale del usuario en la base con la dependencia del detalle; fuera del alcance responde 404 | CTL-01, CTL-02 | Una validación propia: se desalinea de la búsqueda y el detalle |
| Enlace prefirmado de 15 minutos, solo `GetObject` de esa clave; `no-store` y sin registrar la URL | CTL-03 | Pasar el PDF por el backend: más carga y el archivo quedaría en la memoria del servidor; un bucket público: cualquiera leería las carátulas |
| PDF con el documento enmascarado, sin contacto ni beneficiarios | CTL-04 | El documento completo: el analista vería en el PDF lo que el detalle le oculta |
| Clave `caratulas/{número}.pdf` armada en el servidor con el número validado | CTL-05 | Recibir la clave del cliente: daría acceso a otros objetos del bucket |
| Cifrado con la llave KMS del bucket y política del rol limitada a `caratulas/*` | CTL-06 | Dar `s3:*` al rol: cualquier fallo del backend expondría todo el bucket |
| Generar al primer pedido y reutilizar; tiempos de espera acotados | CTL-07 | Generar en cada pedido: más carga sin beneficio, porque las condiciones no cambian |

### Architecture decisions

| ADR | Title | Status |
|---|---|---|
| 0023 | Backend: almacenamiento de documentos en S3 con enlaces prefirmados | Proposed |

### Configuration and deployment

- **Variables:** el backend usa `S3_DOCUMENTS_BUCKET` (Terraform) o `S3_BUCKET_POLICIES`, y `AWS_ENDPOINT_URL` solo en local (Floci; ahora en `.env.example`). Nueva y opcional: `S3_PUBLIC_ENDPOINT_URL`, solo con docker compose (`http://localhost:4566`). En AWS las credenciales salen del rol de la tarea; con el emulador el adaptador usa credenciales de prueba.
- **Instalación sin Poetry:** `requirements.txt` incluye boto3 y fpdf2; la imagen de Docker exporta el lock.
- **Columna nueva** `pdf_s3_key` en `policies`: el arranque la agrega si no existe; las pólizas existentes la tienen vacía y generan su carátula al primer pedido.
- **Terraform:** la política nueva del rol de la tarea se aplica con el despliegue a AWS, que pide aprobación.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Evaluar boto3 y fpdf2 y agregarlos | SR-08 | CTL-08 | DONE | backend/pyproject.toml; backend/poetry.lock; security.md DEP-01 a DEP-04 |
| T-02 | Puerto y adaptador de S3 con cifrado, tiempos de espera y enlace prefirmado (#184, #185) | AC-01, AC-03, AC-04, AC-09, SR-03, SR-06, SR-07, SR-09 | CTL-03, CTL-06, CTL-07, CTL-10 | DONE | backend/app/core/storage.py; backend/tests/unit/test_policy_document.py::test_tc_01_el_enlace_es_https_de_esa_clave_vence_en_15_minutos_y_descarga_el_archivo |
| T-03 | PDF de la carátula (#184) | AC-02, AC-07, AC-10, SR-04 | CTL-04 | DONE | backend/app/modules/policy/document.py; backend/tests/unit/test_policy_document.py::test_tc_02_el_documento_va_enmascarado_y_sin_contacto_ni_beneficiarios |
| T-04 | Endpoint del enlace con alcance, cancelación y columna `pdf_s3_key` (#185) | AC-01, AC-04, AC-05, AC-06, AC-08, AC-09, SR-01, SR-02, SR-05 | CTL-01, CTL-02, CTL-05 | DONE | backend/app/bff/experience/router.py; backend/app/main.py; backend/tests/integration/test_policy_caratula_api.py::test_tc_04_el_usuario_de_un_socio_solo_descarga_las_de_su_socio |
| T-05 | Botón de descarga en el detalle (#186) | AC-01, AC-03, AC-04, QR-02, SR-03 | CTL-09 | DONE | web/src/app/features/gestion/policy-detail.component.ts; web/src/app/features/gestion/policy-detail.component.spec.ts::"TC-11 el botón pide el enlace, anuncia el avance y abre la descarga" |
| T-06 | Política del rol de la tarea y salida de la llave KMS | SR-06 | CTL-06 | DONE | terraform/modules/ecs/main.tf; terraform/modules/storage_events/outputs.tf; terraform/main.tf |
| T-07 | Floci en el CI y pruebas unitarias, de integración y de tiempo de respuesta (#187) | AC-01, AC-02, AC-03, AC-04, AC-05, AC-06, AC-07, AC-08, AC-09, AC-10, QR-01 | CTL-01, CTL-02, CTL-03, CTL-04, CTL-05, CTL-07 | DONE | .github/workflows/ci.yml; backend/tests/integration/test_policy_caratula_api.py; backend/tests/unit/test_policy_document.py |
| T-08 | ADR 0023 | AC-05, SR-03, SR-06 | CTL-03, CTL-06 | DONE | docs/adr/0023-backend-document-storage.md |
| T-10 | Entorno local: bucket del emulador, credenciales de prueba, dirección pública, `requirements.txt` y README | AC-11 | — | DONE | backend/tests/integration/test_policy_caratula_api.py::test_tc_16_con_el_emulador_el_bucket_se_crea_una_vez_y_el_enlace_usa_la_direccion_publica |
| T-11 | PDF con las secciones y etiquetas del detalle de Gestión | AC-12 | — | DONE | backend/tests/unit/test_policy_document.py::test_tc_02_la_caratula_trae_numero_titular_plan_coberturas_vigencia_y_prima |
| T-09 | Pruebas en el portal, revisión y evidencia | QR-02, SR-03 | CTL-03 | DONE | test-cases.md (TC-13, TC-14 y TC-15 en PASS, con la pasada de TalkBack en el teléfono) |

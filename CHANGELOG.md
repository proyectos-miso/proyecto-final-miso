# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Hotfix · Configuración de producción (`HOTFIX-configuracion-de-produccion`, RC-30, RC-33):**
  - Inyección explícita de `ENVIRONMENT=production` en la tarea ECS Fargate (`terraform/modules/ecs/main.tf`), garantizando que el backend desactive `/docs`, `/redoc`, `/openapi.json`, impida la siembra de usuarios ficticios y no devuelva códigos OTP en respuestas HTTP (RC-30).
  - Almacenamiento seguro de `DB_PASSWORD` en AWS Secrets Manager cifrado con KMS (`terraform/modules/rds/main.tf`) con generación automática (`random_password`) e inyección en ECS mediante bloque `secrets` con permisos `secretsmanager:GetSecretValue` y `kms:Decrypt` en `execution_role`, eliminando credenciales en texto plano (RC-33).
- **Hotfix · Transporte y borde (`HOTFIX-transporte-y-borde`, RC-31):**
  - Certificado ACM (`aws_acm_certificate`) y listener HTTPS en puerto 443 con política `ELBSecurityPolicy-TLS13-1-2-2021-06` (TLS 1.2+) en el ALB interno Multi-AZ (`terraform/modules/alb/main.tf`).
  - Redirección automática HTTP a HTTPS (301) en el ALB e ingreso de CloudFront VPC origin exclusivamente sobre HTTPS mediante `origin_protocol_policy = "https-only"` (`terraform/modules/web_hosting/main.tf`), eliminando el tránsito HTTP no cifrado entre CloudFront y el balanceador (RC-31).
- **Hotfix · CD y resiliencia de infraestructura (`HOTFIX-cd-y-resiliencia-de-infraestructura`, RC-36):**
  - Inmutabilidad de versiones en ECR mediante etiquetado doble con SHA del commit (`${{ github.sha }}`) y `:latest` en `.github/workflows/deploy.yml` (RC-36).
  - Configuración de `deployment_circuit_breaker { enable = true, rollback = true }` en `aws_ecs_service.backend` para reversión automática ante fallos en producción (`terraform/modules/ecs/main.tf`).
  - Resiliencia en RDS PostgreSQL: retención de backups de 7 días, snapshot final obligatorio y protección contra eliminación accidental (`deletion_protection = var.db_deletion_protection`) con valor por defecto `true` (`terraform/modules/rds/main.tf`).
  - Bloqueo distribuido de estado de Terraform (`tfstate`) mediante DynamoDB en `.github/workflows/deploy.yml` y `destroy.yml`.
  - Alarmas de CloudWatch para métricas de errores 5XX (`HTTPCode_Target_5XX_Count`) y contenedores no sanos (`UnHealthyHostCount`) en `terraform/modules/alb/main.tf`.
  - Exigencia de cobertura mínima de 85% (`--cov-fail-under=85`) y actualización de SonarCloud a `sonarqube-scan-action@v6` en `.github/workflows/ci.yml`.
- **Hotfix · Evidencias CI/CD y rulesets (`HOTFIX-evidencias-ci-cd`, RC-35):**
  - Cierre y validación de spec `HOTFIX-evidencias-ci-cd` con documentación de reglas de protección de ramas y los 6 status checks obligatorios para `dev` y `main` (Backend CI, Web CI, Mobile CI, Terraform CI, SonarCloud y SDD gate) en `docs/ci-cd.md` (RC-35).

### Removed
- Eliminación de `.github/workflows/deploy-local.yml` por redundancia con el pipeline unificado de despliegue (RC-36).
- Eliminación de `specs/pendientes/HOTFIX-produccion.md` tras completar y validar todas sus brechas funcionales (RC-30, RC-31, RC-33, RC-35, RC-36).

### Security
- Restricción estricta de edad asegurable (`RC-07`): validación de tipo `birth_date: date` en el motor de tarificación (`RatingService.create_quote`) que rechaza con HTTP 422 a solicitantes fuera del rango de 18 a 75 años o con fechas futuras.
- Inmutabilidad de firmas electrónicas de póliza (`RC-02`): tabla relacional `policy_signatures` en PostgreSQL con trigger `make_immutable` que impide la modificación o eliminación de firmas registradas con OTP.
- Control de cuota y rate-limiting RFC 6585 (`HU-WEB-12`): bloqueo de peticiones excedentes con código HTTP 429, cabecera `Retry-After` y registro de eventos de auditoría `PARTNER_QUOTA_EXCEEDED`.

### Fixed
- Corrección de enrutamiento en CloudFront (`terraform/modules/web_hosting`): configuración del Application Load Balancer (ALB) como origen dinámico y definición de comportamiento de caché prioritario para `/api/*` (con todos los métodos HTTP, reenvío de cabeceras `Authorization`, `Content-Type`, `Idempotency-Key` y bypass de caché) y `/health`, permitiendo que el portal web en producción se comunique exitosamente con el backend BFF.
- Corrección de conexión a la base de datos PostgreSQL en ECS Fargate: inyección explícita de `DATABASE_URL` hacia la instancia Multi-AZ de RDS en la definición de la tarea Terraform (`terraform/modules/ecs`) y soporte de ensamblado dinámico en `Settings` (`app/core/config.py`) ante la presencia de variables `DB_HOST`, `DB_USER`, `DB_PASSWORD` y `DB_NAME`, corrigiendo el intento de conexión fallido a `localhost:5432` en producción.
- Corrección de la organización y clave de proyecto en SonarCloud (`sonar-project.properties`) para asegurar la vinculación del análisis de calidad y cobertura de código.
- Corrección en el asistente del cotizador web (`CotizadorComponent`) para iniciar en el Paso 1 (Identificación) con estado activo.
- Corrección en `.github/workflows/destroy.yml` asignando `working-directory: terraform` exclusivamente a los pasos que invocan Terraform, evitando errores de inicialización de shell previos al paso de `checkout` del repositorio.
- `get_password_hash` y `verify_password` usan `bcrypt` directamente: `passlib` 1.7.4 fallaba con `bcrypt` 5.0.0 (versión del lock).
- Corrección en `backend/Dockerfile` aislando las dependencias en un entorno virtual (`/opt/venv`), configurando el directorio de inicio del usuario `appuser` y adaptando el sondeo de salud (`healthcheck`) para resolver el error `ModuleNotFoundError: No module named 'uvicorn'` al iniciar el backend mediante Docker Compose.


### Changed
- Diagnóstico y manejo granular de errores de pago en portal web (`RC-03`): el componente `payment-step` maneja respuestas diferenciadas según código HTTP (402, 404, 409, 422, 502) y deshabilita la opción de reintento ante fallos definitivos.
- Atribución de pagos multi-tenant al socio autenticado (`RC-10`): extracción estricta del `partner_id` desde los claims verificados del token JWT en el servicio de pagos y BFF en lugar de asignar el valor genérico 'default'.
- Montos del portal y de la app en pesos con el código de la moneda (`$ 250.000.000 COP`) para que el símbolo $ no se lea como dólares (HU-MOV-04).
- App móvil con el diseño del mockup: barra de la marca, ingreso con huella y acceso alterno, tema claro u oscuro que se cambia desde la barra e ícono con el escudo de Solventa (HU-MOV-04).
- Validación y restricciones en el formulario de captura de datos (`ClientDataStepComponent`): restricción del campo de número de documento a valores numéricos y restricción de la fecha de nacimiento para impedir fechas futuras.
- Ajuste de maquetación en el formulario de consentimiento (`ConsentStepComponent`). Reorganización de campos en filas de dos columnas.
- Habilitación del disparador manual `workflow_dispatch` en el workflow de despliegue continuo (`.github/workflows/deploy.yml`) permitiendo aprovisionar y desplegar la plataforma bajo demanda sin requerir commits vacíos en `main`.
- Adición de `force_destroy = true` a los buckets S3 (`aws_s3_bucket.documents` y `aws_s3_bucket.web`) y `force_delete = true` al repositorio ECR (`aws_ecr_repository.backend`) en Terraform para garantizar un desaprovisionamiento limpio y sin bloqueos ante objetos o imágenes existentes.
- Actualización de la documentación en `README.md` incorporando la guía de uso del workflow de apagado/destrucción y reestructurando la sección de CI/CD.
- `README.md` y `backend/README.md`: ejecución del backend sin Poetry, endpoints de autenticación del asegurado, conexión de la app con el backend local, emulador con huella y nueva guía de pruebas manuales en `docs/GUIA-PRUEBAS-SPRINT1.md`.
- Actualización de apariencia del Portal Web (`web`) con el sistema de diseño del prototipo (`prototipos/web`).
- Configuración de persistencia de estado para Terraform mediante Remote Backend en Amazon S3 en el workflow de despliegue (`.github/workflows/deploy.yml`) y `terraform/versions.tf`, garantizando que `terraform.tfstate` persista entre runners efímeros de GitHub Actions.
- Actualización de archivo `README.md` con permisos adicionales requeridos para el despliegue con el workflow de CD desde Github (`application-autoscaling`, `cloudwatch`, `kms`).
- Actualización de la documentación en `README.md` y `backend/README.md`, sustituyendo la referencia residual de LocalStack por Floci e incorporando instrucciones explícitas para la ejecución del servidor backend mediante Poetry (`uvicorn`) y Docker Compose.

### Removed
- Supresión de parámetros de simulación en cotización (`RC-09`): eliminación total del parámetro público `simulated_delay` en los contratos y endpoints de cotización de producción (`POST /api/v1/quote-edge/quotes` y `POST /api/v1/experience/quotes`).
- Limpieza de datos de prueba en portal web de producción (`RC-04`): remoción de cédulas precargadas y opciones de medios de pago simulados sin fondos en los formularios de consentimiento, cotizador y pago del portal web.

### Added
- **Brechas funcionales de HU-WEB-12 (RC-02 a RC-25):**
  - **Firma digital e inmutabilidad de pólizas (RC-02):** Implementación de firma electrónica con OTP para emisión de pólizas, tabla relacional inmutable `policy_signatures` en PostgreSQL con trigger `make_immutable` y estampado de hash criptográfico SHA-256 en la carátula PDF (`FE-WEB-02`, criterio 4).
  - **Consentimiento informado con OTP y persistencia relacional (RC-06):** Tabla relacional `consents` en PostgreSQL para autorización de tratamiento de datos personales, endpoints `POST /api/v1/experience/consents/otp-request` y `POST /api/v1/experience/consents`, y validación obligatoria de `consent_id` verificado previo a cotizar.
  - **Captura y persistencia de crédito hipotecario (RC-08):** Incorporación de campos `financial_entity` y `term_months` en la solicitud de cotización `QuoteRequest` y persistencia en la tabla `quotes`.
  - **Alta de socios B2B por autoservicio en sandbox (RC-23):** Endpoint público `POST /api/v1/quote-edge/partners/register` para registro de socios con credenciales de sandbox (`client_id`, API key, cuota inicial de 100 cotizaciones) y endpoint protegido `POST /api/v1/experience/operations/partners/{id}/approve` para aprobación formal a entorno de producción (`FE-WEB-05`, `HA-23`).
  - **Adaptador a Open Data real y simulación Open Finance (RC-25):** Integración con API de datos abiertos de Colombia (`datos.gov.co`) y simulación Open Finance detrás de interfaz unificada de perfilamiento, con timeout de 400 ms y degradación transparente a score crediticio base ante fallos de conexión (`HA-24`).
- **HU-MOV-11 · Consultar el estado de mi siniestro (RC-15, RC-16, RC-24, RC-27, RC-40):**
  - El asegurado consulta sus siniestros con línea de tiempo en lenguaje claro y copia cifrada sin conexión (`GET /insured/claims`), recibe push por Firebase Cloud Messaging al cambiar el estado y antes del vencimiento, descarga su carátula y renueva su póliza por un año desde la app o el portal; tarea diaria de vencimiento `EXPIRED` (`daily_policy_lifecycle`).
- **HU-WEB-12 · Recibir un rechazo claro al superar la cuota contratada (#66, #227, #228, #229, #230):**
  - Backend y Control de Cuota por Ventana (#227): Implementación de límites de solicitudes por socio con ventana de tiempo configurable (`PARTNER_QUOTA_WINDOW_SECONDS`, por defecto 3600s / 1 hora) y reinicio automático de consumo en `PartnerDB` (`quota_window_seconds`, `quota_reset_at`). Consumo atómico compartido entre réplicas mediante transacciones en PostgreSQL.
  - Respuesta HTTP 429 y Cabeceras Estándar (#228): Emisión de error HTTP `429 Too Many Requests` cuando `quota_used >= quota_limit`, incorporando cabecera estándar `Retry-After` con los segundos exactos restantes hasta la renovación, y cabeceras de telemetría `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` y `X-RateLimit-Reset-Time` tanto en respuestas 200 OK como 429. Cuerpo de respuesta estructurado en JSON (`PartnerQuotaExceededResponse`) indicando motivo legible y timestamp ISO-8601 de renovación.
  - Idempotencia y Auditoría de Seguridad (#228, #230): Los reintentos idempotentes exitosos con clave previamente registrada dentro de las 24 horas no consumen cuota adicional y se atienden con HTTP 200 aun con la cuota agotada. Registro de eventos de seguridad estructurados `PARTNER_QUOTA_EXCEEDED` en logs de auditoría ante rechazos por cuota superada.
  - Contrato OpenAPI (#229): Documentación completa de la respuesta HTTP 429 y de las cabeceras `Retry-After` y `X-RateLimit-*` en el endpoint `POST /api/v1/quote-edge/quotes` del backend BFF.
  - Pruebas Automatizadas (#230): Pruebas unitarias de consumo y reinicio de ventana (`backend/tests/unit/test_partner_quota.py`) y suite de integración (`backend/tests/integration/test_partner_quote_api.py`) validando cabeceras de cuota, rechazo 429 con `Retry-After`, aislamiento estricto multi-tenant entre socios concurrentes, reintentos idempotentes con cuota agotada, latencia de rechazo (< 500 ms) y esquema OpenAPI.
- **Mockups HU-MOV-11 (Issue #77):** Prototipos visuales SVG en `docs/mockups/hu-mov-11/` para la consulta y seguimiento del estado de siniestros desde la app móvil (`FE-MOV-06`, `ESC-LAT-04`). Cubre la lista cronológica de reclamaciones («Mis siniestros») con radicados y estados, el detalle con línea de tiempo trazable de 5 etapas (recibido, asignado, evaluación, aprobado, pagado), el detalle de rechazo con motivo comprensible en lenguaje claro y sin jerga legal, la consulta sin conexión servida desde la base de datos local cifrada con indicador de reportes «No Enviado», y la pantalla de estado vacío («No registra siniestros en este momento») con acción directa para reportar.
- **Mockups HU-MOV-10 (Issue #76):** Prototipos visuales SVG en `docs/mockups/hu-mov-10/` para la emisión y consulta de números de radicado por siniestro en la app móvil (`FE-MOV-06`, `ESC-DIS-04`). Cubre la constancia oficial inmediata (`SIN-2026-00871`), la constancia provisional en cola sin conexión (`OFF-2026-9901`), la constancia actualizada tras la transmisión en segundo plano (`SIN-2026-00894`) y la disponibilidad permanente de los radicados en el historial de reclamaciones.
- **Mockups HU-MOV-09 (Issue #75):** Prototipos visuales SVG en `docs/mockups/hu-mov-09/` para el envío resiliente de reportes de siniestro sin conexión desde la app móvil (`FE-MOV-06`, `ESC-DIS-01`). Cubre el flujo completo: advertencia no bloqueante de pérdida de red, confirmación de encolado local cifrado con radicado provisional (`OFF-2026-9901`), visualización prioritaria de pendientes en la pestaña de Siniestros, transmisión automática en segundo plano al recuperar la señal con asignación de radicado oficial (`SIN-2026-00894`) e idempotencia, y resiliencia ante errores del servidor mediante espera creciente (backoff exponencial).
- **Mockups HU-MOV-08 (Issue #74):** Prototipos visuales SVG en `docs/mockups/hu-mov-08/` para el reporte de siniestros desde la app móvil con captura de fotografías, ubicación y hora incorporadas (`FE-MOV-06`, `ESC-SEG-03`). Cubre el flujo completo: acceso desde la billetera digital, visor de cámara con geolocalización y hora certificada sin opción de galería, manejo no bloqueante de permiso de ubicación negado, gestor de 1 a 5 evidencias con cifrado local, formulario de circunstancias con validaciones activas y confirmación de radicación con hash SHA-256 e idempotencia.
- **Dictamen y liquidación de siniestros (HU-WEB-15):** pestaña Siniestros para el analista, con bandeja y expediente (fotos con su ubicación, hora y verificación de la huella SHA-256, y advertencias de ubicación simulada). El analista toma el caso, asigna un perito, dictamina con justificación y liquida dentro del amparo; el sistema paga a los beneficiarios por la pasarela simulada y los siniestros paramétricos que cumplen su regla se liquidan solos. Las pólizas de vida se emiten con beneficiarios y admiten endosos, cancelación con devolución proporcional y una auditoría por póliza; los dictámenes y las operaciones quedan en registros que la base no deja cambiar. Reporte mensual al regulador y al reasegurador en CSV y PDF, y cierre de sesión del portal que revoca el refresh. Cubre las brechas RC-01, 05, 17 a 22, 28 y 29 de rutas críticas.
- **Carátula en PDF (HU-WEB-16):** el detalle de Gestión tiene el botón «Descargar carátula (PDF)». El backend genera el PDF al primer pedido (número, titular con el documento enmascarado, plan, vigencia, coberturas y primas), lo guarda en el bucket de documentos cifrado con KMS y entrega un enlace prefirmado de 15 minutos (`GET /api/v1/experience/policies/{número}/caratula`, con el alcance por socio del detalle). Con S3 sin responder avisa que la carátula está en generación. El PDF usa las secciones y etiquetas del detalle. Dependencias boto3 y fpdf2 (también en `requirements.txt`), política mínima del rol de la tarea sobre `caratulas/`, Floci en el CI y ADR 0023. En local, el backend crea el bucket de Floci al arrancar y `S3_PUBLIC_ENDPOINT_URL` deja abrir el enlace con docker compose.
- **Búsqueda de pólizas (HU-WEB-08):** pestaña Gestión en el portal para el analista de operaciones, que busca por documento, número, socio o titular y filtra por estado; un usuario ligado a un socio solo ve las pólizas de ese socio. La emisión guarda los datos del titular y la lista muestra el documento enmascarado (p95 de 22,7 ms con 10.000 pólizas).
- **Detalle de póliza (HU-WEB-09):** desde la lista de Gestión el analista abre un panel con titular (documento, correo y teléfono enmascarados), plan, vigencia, suma asegurada, primas, coberturas, historial de pagos y siniestros; el enlace `?poliza=` abre una póliza directo y una de otro socio responde como inexistente. Nueva tabla mínima `claims` que HU-MOV-10 amplía (p95 de 27,5 ms con 10.000 pólizas).
- **Última sincronización (HU-MOV-06):** la billetera muestra la fecha y hora local de la última sincronización, guardada por cuenta en la copia cifrada, y avisa cuando tiene más de 24 horas o cuando el reloj del teléfono quedó atrás.
- **Sincronización automática (HU-MOV-07):** sin red, la billetera pide un trabajo de WorkManager que actualiza la copia al volver la red, en primer o segundo plano, con reintentos desde 10 s que se duplican hasta 5 horas, una sincronización a la vez y sin pólizas repetidas; al volver a la app sin conexión sincroniza enseguida. Renueva el access token con el refresh token ante un 401, y `auth/refresh` responde 423 con el ingreso suspendido sin rotar el token. La release solo usa HTTPS. ADR 0022 nueva y ADR 0016 y 0017 actualizadas.
- **Evidencia fotográfica del siniestro (HU-MOV-08):** con una póliza vigente, la billetera ofrece reportar un siniestro con hasta 5 fotos tomadas con la cámara de la app, sin galería, cada una con la ubicación precisa, la hora del teléfono y, si la hay, la hora del GPS o de la red; sin ubicación precisa no se toma la foto y la app explica cómo activarla. El asegurado elige la póliza y describe lo ocurrido en hasta 500 caracteres, y el reporte queda guardado en el teléfono, listo para enviar, aun sin conexión. Las fotos se cifran con AES-256-GCM y una llave por foto, con su huella SHA-256, en una base SQLCipher por cuenta; no aparecen en la galería y la release bloquea las capturas de pantalla. El reporte se borra al cerrar sesión, al cambiar la contraseña o cuando el servidor desvincula el teléfono. CameraX 1.5.3. ADR 0024 nueva y ADR 0015 enmendada.
- **Copia cifrada (HU-MOV-05):** solo una falla definitiva (llave invalidada, frase alterada o base dañada) borra la copia de la cuenta, que se regenera con la siguiente descarga; con el teléfono bloqueado o ante una falla pasajera queda «no disponible» sin borrarse. La billetera avisa cuando se descartó la copia sin red y ofrece reintentar cuando no está disponible; la app ya no se cierra con una base dañada. `data_extraction_rules.xml` deja los datos de la app fuera de las copias de seguridad y las transferencias. ADR 0021 y 0017 actualizadas.
- **Pólizas sin conexión (HU-MOV-04):** `GET /api/v1/insured/policies` con las pólizas vigentes del asegurado del token y su suma asegurada; billetera en la app con carné que se voltea a las coberturas, copia cifrada por cuenta con Room y SQLCipher (llave en el Android Keystore), entrada sin red con huella y borrado de la copia al cerrar sesión o con 401. ADR 0021 y ajuste de la ADR 0015.
- **Motivo del rechazo de suscripción (HU-WEB-07):** la cotización aplica cupos de aprobación directa por perfil crediticio y por edad; si se superan, el portal muestra el motivo en lenguaje de negocio y permite recotizar con la suma máxima aprobable en un clic. Los rechazos quedan en `underwriting_rejections`, que la base no deja modificar ni borrar.
- **HU-WEB-14 · Emisión de póliza embebida B2B (#113, #181, #182, #183):**
  - Backend: Extensión del modelo relacional `PolicyDB` (tabla `policies`) con clave foránea indexada `partner_id`, y definición de esquemas Pydantic `PartnerPolicyIssueRequest` y `PartnerPolicyResponse` para emisión y consultas CQRS de socios B2B.
  - Servicio de Políticas (`PolicyService`): Incorporación de `issue_partner_policy` con validación estricta de pertenencia y vigencia de cotización, emisión atómica e idempotente (`insert ... on_conflict_do_nothing`) y encolado de evento `PolicyIssued` en outbox; adición de `get_partner_policy` con aislamiento multi-tenant y `list_partner_policies` con proyección de lectura CQRS.
  - Endpoints Quote Edge B2B: Rutas `POST /api/v1/quote-edge/policies`, `GET /api/v1/quote-edge/policies/{policy_number}` y `GET /api/v1/quote-edge/policies` gobernadas por autenticación OAuth2 Bearer (`role: partner`), retornando `404 Not Found` ante recursos ajenos para evitar enumeración y `409 Conflict` ante cotizaciones expiradas.
  - Pruebas Automatizadas: Cobertura unitaria en `tests/unit/test_partner_policy.py` e integración en `tests/integration/test_partner_policy_api.py` verificando emisión atómica, rechazo cross-tenant, idempotencia, consulta CQRS y rendimiento.
- **HU-WEB-11 · Reintentar una cotización sin crear registros duplicados (#179, #180):**
  - Backend e Idempotencia B2B: Validación del encabezado `Idempotency-Key` en `POST /api/v1/quote-edge/quotes` con rechazo inmediato `400 Bad Request` ante valor vacío.
  - Persistencia y Aislamiento por Socio: Persistencia atómica de cotizaciones B2B calculadas en la tabla `idempotency_records` bajo clave primaria compuesta `(partner_id, idempotency_key)`, garantizando que dos socios no colisionen aunque utilicen la misma clave.
  - Reintentos Idempotentes: Devolución directa de la respuesta previa con status `200 OK` ante reenvíos con misma clave y payload idéntico, sin recalcular primas, sin generar registros duplicados en `quotes` y sin incrementar la cuota mensual de consumo (`quota_used`).
  - Detección de Conflictos: Cálculo de hash canónico SHA-256 del cuerpo JSON de la petición y rechazo con `409 Conflict` ante discrepancias de contenido para una clave activa.
  - Expiración Temporal de 24 Horas: Lógica de vigencia en `app.core.idempotency` que expira registros con más de 24 horas de antigüedad y los trata automáticamente como nuevas cotizaciones.
  - Pruebas Automatizadas: Suite integral en `backend/tests/unit/test_quote_edge_idempotency.py` con 100% de cobertura en los módulos de idempotencia y router de Quote Edge, validando clave requerida, reintentos exitosos, detección de conflictos 409, expiración a 24 horas, aislamiento multi-tenant y concurrencia sin duplicados.
- **HU-WEB-10 · Cotización desde el canal del socio con credenciales propias (#175, #176, #177, #178):**
  - Backend: Modelo relacional `PartnerDB` (tabla `partners`) con hash `bcrypt`, control de estado (`ACTIVE` / `REVOKED`), `quota_limit` y `quota_used`, y extensión de `QuoteDB` con columna indexada `partner_id`.
  - Seguridad y Auditoría: Flujo OAuth2 *Client Credentials* en `POST /api/v1/quote-edge/auth/token`, dependencia `get_current_partner` con sincronización de `tenant_context`, y emisión de logs JSON estructurados con severidad `WARNING` hacia `stdout`/CloudWatch ante rechazos HTTP 401 (`PARTNER_AUTH_FAILED`, `PARTNER_REVOKED`, `PARTNER_TOKEN_MISSING`, `PARTNER_TOKEN_EXPIRED`, `PARTNER_TOKEN_INVALID`, `PARTNER_ROLE_MISMATCH`).
  - Endpoints B2B y Aislamiento Multi-Tenant: Refactorización de `POST /api/v1/quote-edge/quotes` con incremento transaccional atómico de `quota_used`, e incorporación de `GET /api/v1/quote-edge/quotes/{quote_id}` (`404 Not Found` ante cotizaciones de otros socios o del canal asesor) y `GET /api/v1/quote-edge/quotes` confinado al socio autenticado.
  - Siembra y Documentación: Inicialización idempotente en `lifespan` de socios semilla (`banco_aliado` activo y `socio_inactivo` revocado), enriquecimiento de esquemas OpenAPI y actualización de `README.md` y `backend/README.md`.
  - Pruebas: Suites unitarias (`tests/unit/test_partner_auth.py`) y de integración (`tests/integration/test_partner_quote_api.py`) validando emisión de tokens, auditoría de rechazos, cuota atómica y aislamiento estricto entre socios.
- **Framework SDD con seguridad obligatoria (`AGENTS.md`, `sdd/`):**
  - Paquetes de especificación en `specs/` (Requirements → Design → Tasks, evaluación de seguridad, casos de prueba y gate) con plantillas en `sdd/templates/`.
  - Roles, skills y reglas en Markdown, independientes de la herramienta de IA.
  - Seguridad orientada al desarrollo: evaluación proporcional, ciclo de vida de datos, STRIDE, criptografía, catálogo de controles, rutas críticas y referencias verificadas.
  - Skills por lenguaje (Python, TypeScript/Angular, Kotlin/Android, Terraform) con convenciones y revisión de código, y con patrones de código seguro; skills de UI/UX y accesibilidad (WCAG 2.2 AA).
  - Requisitos de calidad medibles en el spec y niveles de prueba validados: unitarias, integración, contrato, E2E, rendimiento (carga, estrés, picos, resistencia), accesibilidad y estáticas.
  - Validador que calcula el gate de seguridad desde la evidencia, escáneres de secretos, dependencias y rutas críticas, y gates como hooks de git; probados con escenarios positivos y negativos.
  - Trabajo por historia de usuario: rama `feature/HU-…-slug`, paquete `specs/HU-…-slug/` y la HU en cada commit; `hotfix/` como excepción.
  - La HU se lee del tablero y se copia en el spec; los criterios se marcan como `STATED` (los dice la HU) o `DERIVED` (los necesita la funcionalidad aunque la HU no los diga).
  - Escenarios derivados de los elementos de cada HU (actores, datos, reglas, estados, pasos, dependencias, tiempo) con su disposición y cobertura validadas.
  - ADR 0001–0019 (propuestas) por capa en `docs/adr/`: sistema, contrato de API, backend hexagonal, web, móvil e infraestructura.
  - Job `sdd-gate` en el CI.
- **HU-WEB-02.1 · Catálogos de bancos y ciudades en base de datos y consumo en cotizador (Issue #163):**
  - Backend: Modelos SQLAlchemy `FinancialEntityDB` (tabla `financial_entities`) y `CityDB` (tabla `cities`) en `app/modules/profile/models.py`. Siembra automática e idempotente en el ciclo de vida de la aplicación (`lifespan`) con el listado completo de 32 entidades bancarias vigiladas por la SFC y las 100 ciudades principales de Colombia según el DANE. Endpoints REST `GET /api/v1/experience/catalogs/financial-entities` y `GET /api/v1/experience/catalogs/cities` en el BFF de experiencia.
  - Web: Interfaces `FinancialEntity` y `City` en `catalog.model.ts`, servicio `CatalogService` para consumo de catálogos, e integración reactiva mediante señales (`financialEntities`, `cities`, `loadingEntities`, `loadingCities`) en `ClientDataStepComponent` con estados de carga y población dinámica de los `<select>` de entidad financiera y ciudad de residencia.
  - Pruebas: Pruebas unitarias y de endpoints para catálogos y siembra en backend (`test_profile_and_consent.py`), pruebas unitarias para `CatalogService` (`catalog.service.spec.ts`) y cobertura de carga e interacción con selectores en `ClientDataStepComponent` (`client-data-step.component.spec.ts`).
- **HU-WEB-02 · Capturar los datos del cliente y solicitar la cotización:**
  - Backend: `POST /api/v1/experience/clients/validate` para validación de asegurabilidad por edad (`TC-S1-03`) en el rango de 18 a 75 años con `ProfileService.validate_insurable_age()`, cálculo determinístico y excepción `IneligibleAgeError` (HTTP 400 Bad Request).
  - Web: componente `ClientDataStepComponent` (Paso 1) en el cotizador con captura de datos personales del titular y condiciones del crédito hipotecario, cálculo dinámico y validación interactiva de edad en el formulario, formateo en pesos colombianos COP y validación con el BFF.
  - Servicio `ClientService` para validación sociodemográfica de asegurabilidad.
  - Integración comercial guiada en `CotizadorComponent`: transferencia fluida hacia consentimiento (`ConsentStepComponent`), resumen visible del titular y monto asegurado, solicitud automática de cotización con `QuoteService.createQuote()` tras autorizar consentimiento, despliegue del desglose explicativo de la prima (`app-premium-breakdown`) y soporte de recotización sin recargar la página.
  - Pruebas: suite `TC-S1-03` en backend con pruebas parametrizadas de límites de edad (17, 18, 40, 75 y 76 años), pruebas de integración de endpoint protegido con rol `advisor` y validación de esquemas (116 pruebas aprobadas con 97 % de cobertura), y 73 pruebas unitarias en frontend con Angular y Vitest.
- **HU-WEB-01 · Registrar el consentimiento de tratamiento de datos:**
  - Backend: `POST /api/v1/experience/consent` y `GET /api/v1/experience/consent/{document_number}` con generación de hash SHA-256 canónico inalterable para auditoría (RC-01 / RC-04), validación estricta de OTP numérico (`^\d{6}$`), vigencia configurable con reloj inyectable para expiración determinística y aislamiento de estado en `ProfileService`.
  - Web: componente `ConsentStepComponent` en el cotizador con indicación de finalidad, alcance y vigencia por el asesor, sincronización reactiva de documento con `linkedSignal`, validación numérica y atributos modernos de formulario (`sms-otp-form`), detalle de fuentes consultadas (Open Finance, Burós, KYC), verificación OTP simulada y visualización del hash SHA-256 de auditoría.
  - Servicio `ConsentService` para comunicación con el BFF de experiencia.
  - Pruebas: suite `TC-S1-04` en backend con pruebas unitarias de expiración determinística y rechazo de campos inválidos, prueba de integración dedicada en `test_health_and_api.py`, y pruebas unitarias con Vitest (62 pruebas pasando).
- Reportes de cobertura en el CI para backend (`coverage.xml`), web (`lcov`, Vitest) y móvil (Kover), publicados como artefactos, y análisis en SonarCloud (`sonar-project.properties`) cuando existe el secreto `SONAR_TOKEN`.
- **HU-WEB-06 · Cobrar la prima sin duplicar el cobro:**
  - Backend: `POST /payments/charge` exige `Idempotency-Key` y cobra la primera prima mensual con una pasarela simulada, con la póliza bloqueada y en una sola transacción: si se aprueba, la póliza pasa a `ACTIVE` con recibo `REC-YYYY-XXXXX` y evento `PaymentApproved` en el outbox; la misma clave repite la respuesta sin otro cargo, un rechazo responde 402 con el motivo y una póliza ya pagada, 409.
  - Web: paso de pago debajo de la póliza emitida, con PSE o tarjeta y una clave por intento que se conserva en los reintentos; muestra el recibo o el motivo del rechazo.
  - Pruebas TC-S1-08 contra PostgreSQL (reintento, corte después de cobrar, rechazo, 10 cobros simultáneos y atomicidad) y Vitest del paso de pago.
- Workflow de Destrucción y Apagado de Infraestructura AWS (`.github/workflows/destroy.yml`) ejecutable bajo demanda con `workflow_dispatch` y confirmación de seguridad para desaprovisionar todos los recursos gestionados por Terraform y reducir a \$0 los costos fijos por hora (NAT Gateway, ALB, RDS Multi-AZ y ECS Fargate).
- **HU-WEB-05 · Aceptar la cotización y emitir la póliza:**
  - Backend: guarda cada cotización y emite la póliza `POL-YYYY-XXXXX` con un año de vigencia y su evento `PolicyIssued` en el outbox, en una sola transacción; repetir la emisión devuelve la misma póliza y una cotización vencida responde 409.
  - Web: botón «Emitir póliza» con la confirmación, y aviso de cotización vencida con «Recotizar con datos actuales».
  - Pruebas TC-S1-07 contra PostgreSQL, ahora también en el CI, y Vitest de la emisión.
- **HU-WEB-04 · Cotización con perfil parcial:**
  - Backend: si el buró supera 400 ms, cotiza con puntaje neutro y recargo del 4 %, y responde `status="degraded"` con `degradation_reason="TIMEOUT_EXCEEDED"`.
  - Web: banner ámbar con `role="alert"` para cotizaciones degradadas.
  - Pruebas TC-S1-06 y Vitest del banner.
- **HU-MOV-12 · Cierre de sesión seguro y revocación móvil:**
  - Backend: `POST /api/v1/auth/logout` revoca el refresh token (lista negra por `jti`) y `POST /api/v1/auth/refresh` rechaza los revocados y rota el token en cada uso.
  - App: confirmación modal, purga de tokens y llave del Keystore, regreso a la bienvenida y revocación en el servidor (de mejor esfuerzo sin red).
  - Pruebas TC-S1-12 en backend y en `AuthViewModel`.
- **HU-MOV-02 · Acceso alternativo con contraseña y segundo factor:**
  - Backend: `POST /api/v1/auth/otp` y `POST /api/v1/auth/login` para el asegurado, con código de 6 dígitos de un solo uso (5 min), bloqueo de 15 minutos al tercer intento fallido (HTTP 423) y emisión de access y refresh token. En desarrollo el código se devuelve como `debug_code` para simular el SMS.
  - App: pantalla de acceso alternativo, cliente HTTP del BFF y oferta de activar la biometría tras ingresar, que guarda la sesión cifrada (HU-MOV-03).
  - Pruebas TC-S1-10 en backend (unitarias e integración) y en `AuthViewModel`.
- **HU-MOV-03 · Credenciales en el almacén protegido del dispositivo:**
  - Llave AES-256-GCM en Android Keystore que exige biometría fuerte en cada uso y se invalida al registrar nuevas huellas o rostros.
  - `SecureSessionRepository`: guarda los tokens cifrados, los desbloquea con el `CryptoObject` de `BiometricPrompt` y sobrescribe en memoria el texto plano.
  - Si la llave se invalida o la sesión guardada se altera, la sesión se descarta y se pide ingresar con contraseña y código.
  - Pruebas unitarias TC-S1-11 sobre el repositorio y el flujo de desbloqueo en `AuthViewModel`.
- **HU-MOV-01 · Acceso biométrico en la app móvil:**
  - Pantalla M1 en Jetpack Compose con tema de marca e íconos del prototipo; el diálogo biométrico se lanza al abrir la app.
  - Puerto `BiometricAuthenticator` e implementación con `BiometricPrompt` restringida a biometría fuerte (`BIOMETRIC_STRONG`).
  - Manejo de sensor ausente, sin huella o rostro registrados, cancelación y bloqueo por intentos, orientando al acceso alternativo (HU-MOV-02).
  - Pruebas unitarias TC-S1-09 sobre `AuthViewModel` y el mapeo de códigos de `BiometricPrompt`.
- **HU-WEB-03 · Desglose explicativo de la prima:**
  - Backend: `calculate_quote` calcula en memoria los planes básico, estándar y premium con el aporte en pesos de la tarifa base y de los ajustes por edad, ocupación, historial de pago y plan; la suma del desglose es exactamente la prima mensual. El buró usa un puntaje neutro hasta HU-WEB-04.
  - Web: `QuoteService` contra `POST /api/v1/experience/quotes`, componente `app-premium-breakdown` con recargos, descuentos y multiplicadores, y selector de plan en el cotizador.
  - Pruebas TC-S1-05 en el motor de tarifa y pruebas Vitest del desglose, el servicio y el cotizador.
- Implementación integral de la historia de usuario **HU-WEB-13: Login y Sesión del Asesor Comercial en el Portal Web**:
  - Modelo relacional `UserDB` en SQLAlchemy con hash seguro bcrypt (`HA-06`) y esquemas Pydantic para endpoints de autenticación y renovación de sesión.
  - Implementación de segundo factor TOTP conforme a RFC 6238 en Python nativo con tolerancia temporal de $\pm 30$ segundos (`HA-06`).
  - Mitigación de fuerza bruta y enumeración de cuentas mediante bloqueo temporal de 15 minutos (HTTP 423 Locked) tras 5 intentos fallidos consecutivos, y mensaje de error genérico.
  - Endpoints BFF `/api/v1/experience/auth/login` y `/api/v1/experience/auth/refresh` con generación de tokens JWT (`access_token` con expiración de 15 min y `refresh_token` de 7 días).
  - Control de acceso por roles RBAC (`HA-13`) mediante la dependencia `require_roles(["advisor"])` asegurando los endpoints comerciales del portal.
  - Inicialización de tablas y siembra del usuario asesor comercial semilla (`asesor@solventa.com` / `SuperSecreta2026*`) mediante gestor de ciclo de vida `lifespan` en FastAPI.
  - Servicio Angular `AuthService` con señales reactivas (`signal`), almacenamiento seguro en `sessionStorage` y métodos `login`, `refreshToken` e `isAuthenticated`.
  - Interceptor HTTP `jwtInterceptor` con inyección de cabecera Bearer y renovación silenciosa transparente ante errores 401.
  - Guard de rutas `authGuard` protegiendo `/cotizador` con redirección a `/login` preservando `returnUrl`.
  - Formulario reactivo `LoginComponent` con manejo visual de estados de carga, error 401 y bloqueo 423.
  - Barra de navegación en `AppComponent` con visualización del nombre del asesor autenticado y acción de cierre de sesión (`logout`).
  - Configuración de proxy inverso de desarrollo (`web/proxy.conf.json`) para reenvío automático de `/api` hacia `localhost:8000`.
- Script de automatización [`scripts/setup-aws-secrets.sh`](scripts/setup-aws-secrets.sh) para ingreso interactivo o lectura de perfil de AWS CLI, verificación de identidad con AWS STS y sincronización desatendida de credenciales IAM de AWS hacia GitHub Actions usando GitHub CLI (`gh`).
- Workflow de Integración Continua en GitHub Actions (`.github/workflows/ci.yml`) ejecutando pruebas automatizadas en cada commit y branch:
  - `backend-ci`: Python 3.12, Poetry y Pytest con validación de cobertura mínima.
  - `web-ci`: Node.js 22, npm test con Vitest y build de producción de Angular 22.
  - `mobile-ci`: Java 17 LTS y Gradle ejecutando `testDebugUnitTest` para Android nativo.
  - `terraform-ci`: `terraform fmt -check` y `terraform validate`.
- Workflow de Despliegue Continuo a AWS en GitHub Actions (`.github/workflows/deploy.yml`) ejecutado exclusivamente ante cambios en la rama `main`:
  - `deploy-infra`: Sincronización de infraestructura Multi-AZ con Terraform.
  - `deploy-backend`: Compilación y subida de imagen Docker a ECR con despliegue progresivo en ECS Fargate.
  - `deploy-web`: Despliegue de estáticos de Angular en S3 con invalidación de caché en CloudFront.
- Workflow de Despliegue Local Simulado (`.github/workflows/deploy-local.yml`) y script de automatización (`scripts/deploy-local.sh`) para ejecutar pruebas de despliegue integrales contra Floci emulando el entorno de producción de AWS sin costos de nube.
- Infraestructura como Código (IaC) modular en `/terraform` para AWS Multi-AZ (`us-east-1`):
  - Módulo VPC (`10.0.0.0/16`) con subredes públicas y privadas en `us-east-1a` y `us-east-1b`, Internet Gateway y NAT Gateway.
  - Módulo ALB con sondeo activo en `/health` cada 5s y tolerancia a fallos (`HA-09`).
  - Módulo ECS Fargate para `solventa-backend` con auto-escalado horizontal de 2 a 6 instancias según carga de CPU (`HA-06`).
  - Módulo RDS PostgreSQL 16 Multi-AZ (`multi_az = true`) con almacenamiento cifrado con KMS (`HA-15`).
  - Módulo de almacenamiento y eventos con bucket S3 versionado y cifrado SSE-KMS (`HA-08`), y cola SQS con Dead Letter Queue (DLQ).
  - Módulo ECR para registro de imágenes Docker del backend con escaneo de seguridad.
  - Módulo de hosting web con S3 y CloudFront con Origin Access Control (OAC) para el frontend Angular SPA.
  - Soporte para emulación local de servicios AWS con **Floci** (`floci.io/aws`), integrando `environments/local.tfvars` para planificar infraestructura sin costos de nube.
- Migración del emulador local en `docker-compose.yml` de LocalStack a **Floci** (`floci/floci:latest`), con montaje de socket Docker para emulación ligera con Quarkus Native.
- Configuración de `.mise.toml` para gestión automatizada de Java 17 LTS, Gradle 8.10.2 y Android SDK con `mise`.
- Estructura base de la aplicación móvil para asegurados en `/movil` con Android nativo (Android 13+ / minSdk 33), Kotlin 2.1.0 y Jetpack Compose.
- Arquitectura limpia y MVVM con `AuthViewModel`, modelos de dominio (`Policy`, `UserSession`) y gestor de persistencia segura `EncryptedStorageManager` (SQLCipher).
- Métodos placeholder estructurados que lanzan `NotImplementedError` para biometría (`HU-MOV-01`), PIN (`HU-MOV-02`), almacén cifrado (`HU-MOV-03`) y logout (`HU-MOV-12`).
- Suite de pruebas unitarias con JUnit 4/5 y MockK en `movil/app/src/test/` con validación de estado inicial y excepciones de placeholder.
- Estructura base del Portal Web para Asesores Comerciales en `/web` con Angular 22 (Standalone Components, Vitest, esbuild).
- Configuración de rutas (`/login`, `/cotizador`) y componentes con placeholders de interfaz de usuario (`LoginComponent` para `HU-WEB-13` y `CotizadorComponent` para flujo comercial `HU-WEB-01..06`).
- Servicio de autenticación `AuthService` con métodos placeholder que lanzan excepciones explicativas previas a la implementación funcional.
- Interceptor HTTP funcional para inyección de token JWT (`jwtInterceptor`).
- Suite de pruebas unitarias en TypeScript (12 pruebas pasando con Vitest) validando instanciación, componentes y contratos del arnés base.
- Estructura base del Monolito Modular en Python 3.12 con FastAPI bajo Arquitectura Hexagonal y patrón BFF (`experience-edge` y `quote-edge`).
- Configuración y gestión de dependencias del backend centralizada con **Poetry** (`pyproject.toml` y `poetry.lock`).
- Endpoint de infraestructura `/health` para sondeo activo por parte del AWS Application Load Balancer (`HA-09`).
- Modelos de dominio y contratos DTO (Pydantic) para los módulos `rating`, `profile`, `policy`, `payments` y `claims`.
- Métodos de servicio y endpoints BFF estructurados como placeholders (`NotImplementedError` y HTTP 501) listos para la codificación progresiva de historias de usuario.
- Arnés técnico de pruebas con `pytest` ejecutado vía `poetry run pytest` (93% de cobertura), con casos de prueba de HU (`TC-S1-*`) preparados para TDD.
- Configuración inicial de entorno local base con `docker-compose.yml` (PostgreSQL 16 y LocalStack para emulación de S3/SQS).
- Archivo `.gitignore` con exclusiones para Python, Poetry, Node, Android, Terraform y secretos.
- Documentación inicial del proyecto en `README.md` con guías de arquitectura, ejecución local y configuración de secretos AWS.

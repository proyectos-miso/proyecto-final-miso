# Security and privacy assessment · HU-WEB-12

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Datos de solicitud de cotización (tipo y número de documento, fecha de nacimiento, valor asegurado, ciudad, riesgo) | CONFIDENTIAL | YES | NO | Cálculo actuarial y emisión de primas de seguro para socios B2B |
| DATA-02 | Telemetría y estado de cuota (`quota_limit`, `quota_used`, `quota_window_seconds`, `quota_reset_at`, `Retry-After`) | INTERNAL | NO | NO | Control de consumo, prevención de saturación de recursos y diagnóstico para socios |
| DATA-03 | Consentimientos informados y verificación OTP de clientes asegurados | CONFIDENTIAL | YES | NO | Validar autorización del titular para consulta y cotización conforme a ley de protección de datos |
| DATA-04 | Constancia inmutable de firma de póliza, hash SHA-256 de carátula PDF y evidencia OTP | CONFIDENTIAL | YES | NO | Asegurar no repudio, trazabilidad e integridad legal en la emisión de pólizas |
| DATA-05 | Credenciales de sandbox de socios B2B (`client_id`, hash de secreto, API keys y correos de contacto) | CONFIDENTIAL | NO | NO | Acceso controlado y aislado en entorno de pruebas para socios B2B sin privilegios de producción |

## 2. Data lifecycle

### DATA-01 · Datos de cotización y cliente

| Question | Answer |
|---|---|
| What it contains | Tipo y número de documento de identidad, fecha de nacimiento, ciudad y monto asegurado |
| Why it is needed | Es el insumo indispensable para calcular la tarifa actuarial y evaluar la asegurabilidad |
| Who can access it | El socio B2B autenticado titular de la solicitud y el backend de Solventa |
| Where it is captured | Cuerpo JSON recibido en `POST /api/v1/quote-edge/quotes` |
| How it is transmitted | HTTPS / TLS 1.3 con cifrado obligatorio en tránsito |
| Where it is stored | Tabla `quotes` y tabla `idempotency_records` en PostgreSQL |
| Copies, caches and backups | Respaldos periódicos cifrados de base de datos en AWS RDS |
| How long it is kept | Conservación según términos legales de pólizas y vigencia comercial |
| How access is audited | Eventos de auditoría estructurados en CloudWatch identificando al socio |
| How it is deleted or anonymized | Purgas programadas de cotizaciones no formalizadas |
| What happens if it leaks | Exposición de identificaciones de clientes potenciales |
| Transfers to third parties | Consulta simulada a buró crediticio para obtención de score de riesgo |

### DATA-02 · Telemetría y métricas de cuota

| Question | Answer |
|---|---|
| What it contains | Límite contratado, solicitudes consumidas, ventana en segundos y fecha de reinicio |
| Why it is needed | Administrar la capacidad asignada a cada socio y prevenir denegación de servicio |
| Who can access it | El socio autenticado (mediante cabeceras HTTP y respuesta 429) y el equipo de operaciones |
| Where it is captured | Registro interno de configuración en tabla `partners` y cómputo en memoria |
| How it is transmitted | Cabeceras HTTP `X-RateLimit-*` y `Retry-After` sobre TLS 1.3 |
| Where it is stored | Columnas en tabla `partners` de PostgreSQL |
| Copies, caches and backups | Respaldos de base de datos |
| How long it is kept | Vigencia permanente durante la relación contractual con el socio |
| How access is audited | Eventos de auditoría ante rechazos por cuota (`PARTNER_QUOTA_EXCEEDED`) |
| How it is deleted or anonymized | Eliminación lógica al revocar las credenciales del socio |
| What happens if it leaks | Un tercero podría estimar el volumen comercial del socio; no expone secretos |
| Transfers to third parties | No se transfiere a terceros |

### DATA-03 · Consentimientos informados y verificación OTP

| Question | Answer |
|---|---|
| What it contains | Tipo y número de documento, fecha de autorización, canal, hash del texto de consentimiento y código OTP verificado |
| Why it is needed | Soporte probatorio de autorización de tratamiento de datos antes de consultar burós o tarificar |
| Who can access it | El cliente, el asesor autorizado y el backend de Solventa |
| Where it is captured | Endpoints `POST /api/v1/experience/consents/otp-request` y `POST /api/v1/experience/consents` |
| How it is transmitted | HTTPS / TLS 1.3 con cifrado obligatorio en tránsito |
| Where it is stored | Tabla `consents` en PostgreSQL |
| Copies, caches and backups | Respaldos periódicos cifrados de base de datos en AWS RDS |
| How long it is kept | Conservación durante la vigencia legal de la relación comercial o prescripción legal |
| How access is audited | Eventos de auditoría estructurados en CloudWatch identificando la operación |
| How it is deleted or anonymized | Revocación de consentimiento y purga según política de retención |
| What happens if it leaks | Exposición del registro de autorización del titular |
| Transfers to third parties | No se transfiere a terceros |

### DATA-04 · Constancia inmutable de firma de póliza

| Question | Answer |
|---|---|
| What it contains | Número de póliza, código OTP verificado, timestamp UTC de firma, hash SHA-256 de la carátula PDF y `consent_id` |
| Why it is needed | Constancia legal probatoria de aceptación de la póliza para garantizar no repudio |
| Who can access it | El tomador/asegurado, analistas de operaciones y el backend de Solventa |
| Where it is captured | Endpoint `POST /api/v1/experience/policies/issue` |
| How it is transmitted | HTTPS / TLS 1.3 con cifrado en tránsito |
| Where it is stored | Tabla `policy_signatures` en PostgreSQL con trigger `make_immutable` |
| Copies, caches and backups | Respaldos de base de datos en AWS RDS |
| How long it is kept | Conservación obligatoria por el término de prescripción de acciones de seguros (10 años) |
| How access is audited | Trazabilidad inmutable en base de datos y eventos en CloudWatch |
| How it is deleted or anonymized | Retención inmutable durante el periodo legal obligatorio |
| What happens if it leaks | Posible conocimiento de suscripción de póliza por un titular específico |
| Transfers to third parties | Disponible para auditorías de la aseguradora y requerimientos judiciales |

### DATA-05 · Credenciales de sandbox de socios B2B

| Question | Answer |
|---|---|
| What it contains | Identificador de socio sandbox, nombre comercial, correo de contacto y hash del secreto de cliente |
| Why it is needed | Permitir integración y pruebas en entorno aislado sin riesgo para datos de producción |
| Who can access it | El socio titular y el equipo de operaciones de Solventa |
| Where it is captured | Endpoint `POST /api/v1/quote-edge/partners/register` |
| How it is transmitted | HTTPS / TLS 1.3 |
| Where it is stored | Tabla `partners` con flag `environment='sandbox'` en PostgreSQL |
| Copies, caches and backups | Respaldos de base de datos |
| How long it is kept | Durante la etapa de integración o hasta revocación o promoción a producción |
| How access is audited | Eventos de auditoría de registro y consumo en CloudWatch |
| How it is deleted or anonymized | Eliminación o revocación al finalizar las pruebas o promover a producción |
| What happens if it leaks | Acceso indebido al entorno sandbox sin impacto en datos reales ni producción |
| Transfers to third parties | No se transfiere a terceros |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Socio B2B | partner | Autenticado | Emitir cotizaciones y consultar su telemetría de cuota en Quote Edge |
| Atacante externo | Ninguno | No confiable | Intentar saturar la API enviando peticiones masivas |
| Socio B2B abusivo | partner | Autenticado | Intentar cotizar por encima de la cuota contratada o agotar recursos compartidos |
| Cliente Asegurado | client | Autenticado | Otorgar consentimiento mediante OTP y firmar su póliza |
| Asesor Comercial | advisor | Autenticado | Operar el cotizador, gestionar consentimiento y asistir en pagos |
| Analista Operaciones | ops_analyst | Autenticado | Aprobar socios a producción y renovar pólizas vencidas |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| Borde API Quote Edge | Internet y backend de Solventa | Token OAuth2 JWT del socio, validez de credenciales y disponibilidad de cuota en ventana |
| Borde API Portal Web (BFF) | Navegador web de asesores y backend | Sesión autenticada, tokens de asesor/cliente, OTP verificado y roles |
| Persistencia Multi-tenant | Servicios backend y PostgreSQL | Particionamiento estricto por `partner_id` para cuotas, cobros y cotizaciones |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Socio B2B | Quote Edge API | DATA-01, DATA-02, DATA-05 | HTTPS / TLS 1.3 | CRY-01, CTL-01 |
| Navegador Asesor/Cliente | BFF Experience API | DATA-01, DATA-03, DATA-04 | HTTPS / TLS 1.3 | CRY-01, CTL-06, CTL-07 |
| Quote Edge API | PostgreSQL (`partners`, `quotes`) | DATA-01, DATA-02, DATA-05 | Conexión privada TLS | CTL-01, CTL-03, CTL-10 |
| BFF Experience API | PostgreSQL (`consents`, `policy_signatures`, `policies`) | DATA-01, DATA-03, DATA-04 | Conexión privada TLS | CTL-06, CTL-07, CTL-09, CTL-11 |

## 6. Third-party services

| Service | Use | Data shared | Location | Controls |
|---|---|---|---|---|
| Buró de crédito (simulado) | Obtención de score para tarificación de prima | DATA-01 (`document_number`) | Colombia / Local | CTL-05 |
| Open Data Colombia (`datos.gov.co`) | Validación de indicadores socioeconómicos y asegurabilidad | DATA-01 (`document_type`, `city`) | Colombia | CTL-05 |

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| `POST /api/v1/quote-edge/quotes` | Autenticado (partner) | Encabezado `Idempotency-Key` y payload JSON `QuoteRequest` |
| `POST /api/v1/quote-edge/partners/register` | Público | Payload JSON con datos comerciales y contacto del socio para sandbox |
| `POST /api/v1/experience/consents/otp-request` | Autenticado (web) | Payload JSON con tipo y número de documento del cliente |
| `POST /api/v1/experience/consents` | Autenticado (web) | Payload JSON con documento, canal y código OTP |
| `POST /api/v1/experience/policies/issue` | Autenticado (web) | Payload JSON con `quote_id` y código OTP de firma |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | D | Disponibilidad de la API | Un socio con cuota agotada o mal configurado satura el servicio con cotizaciones continuas | Socio abusivo | MEDIUM | HIGH | HIGH | CTL-01 |
| THR-02 | I | Información interna del sistema | Respuestas de error por cuota que expongan stack traces, consultas SQL o detalles de infraestructura | Atacante | LOW | MEDIUM | LOW | CTL-02 |
| THR-03 | D | Disponibilidad de otros socios | La saturación de cuota de un socio consume conexiones o CPU afectando a los demás socios | Socio abusivo | MEDIUM | HIGH | HIGH | CTL-03 |
| THR-04 | R | Trazabilidad y auditoría | Rechazos de cuota no auditados impiden detectar abusos o dimensionar ampliaciones de plan | Operaciones | LOW | MEDIUM | LOW | CTL-04 |
| THR-05 | I | Integridad de cotizaciones y consentimientos | Cálculo de cotizaciones sin autorización previa del cliente o falsificación de `consent_id` | Asesor malicioso / Atacante | MEDIUM | HIGH | HIGH | CTL-06 |
| THR-06 | R | No repudio de emisión de póliza | Repudio del tomador alegando no haber contratado o manipulación del documento de póliza | Tomador malicioso / Atacante | MEDIUM | HIGH | HIGH | CTL-07 |
| THR-07 | I | Vigencia de pólizas y cobertura activa | Pólizas vencidas que continúan figurando como activas sin control de vigencia ni renovación | Fallo de sistema / Operación | MEDIUM | HIGH | HIGH | CTL-11 |
| THR-08 | D | Disponibilidad de la API de cotización | Manipulación de parámetros de depuración (`simulated_delay`) en producción para inducir latencias | Atacante externo | LOW | MEDIUM | LOW | CTL-08 |
| THR-09 | S | Atribución multi-tenant de pagos | Asignación de pagos a socios genéricos ('default') o manipulación de `partner_id` en cobros | Socio malicioso / Atacante | MEDIUM | HIGH | HIGH | CTL-09 |
| THR-10 | E | Privilegios de socios y entornos | Socio registrado en autoservicio intenta cotizar en producción con credenciales de sandbox | Socio abusivo | MEDIUM | HIGH | HIGH | CTL-10 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | Verificación bloqueante de cuota en `POST /quotes` con rechazo HTTP 429 Too Many Requests cuando `quota_used >= quota_limit` | API | EXISTING | THR-01, DATA-02 | TC-02 | T-01, T-02 | RFC 6585 | Detiene la ejecución en el borde antes de invocar buró de crédito o calcular tarifas |
| CTL-02 | Formato estandarizado de respuesta 429 con cabecera `Retry-After` y modelo `PartnerQuotaExceededResponse` genérico sin trazas internas | API | EXISTING | THR-02, DATA-02 | TC-02 | T-02, T-03 | RFC 6585, RFC 7807 | Proporciona diagnóstico accionable al socio sin fuga de información |
| CTL-03 | Particionamiento multi-tenant estricto por `partner_id` en PostgreSQL asegurando aislamiento de cuotas | DATA | EXISTING | THR-03, DATA-02 | TC-03 | T-01 | HA-07, HA-14 | El estado de cuota de cada socio es totalmente independiente en la base de datos |
| CTL-04 | Emisión de evento estructurado de auditoría `PARTNER_QUOTA_EXCEEDED` en CloudWatch con socio y timestamp | API | EXISTING | THR-04, DATA-02 | TC-07 | T-02 | OWASP API8:2023 | Facilita monitoreo operativo y detección de tráfico anómalo |
| CTL-05 | Timeouts y degradación controlada en consultas a buró crediticio y Open Data externo | BACKEND | EXISTING | THR-01, DATA-01 | TC-01, TC-15 | T-08 | HA-05, HA-24 | Protege la estabilidad del servicio ante latencias de terceros degradando a score base |
| CTL-06 | Persistencia relacional de consentimientos con validación obligatoria de código OTP en PostgreSQL antes de cotizar | DATA, API | EXISTING | THR-05, DATA-03 | TC-08, TC-16, TC-17 | T-06, T-07 | ISO 27001 A.8.2 | Garantiza validez legal y autorización probatoria previa al tratamiento de datos |
| CTL-07 | Registro inmutable de firma de póliza `policy_signatures` mediante disparadores PostgreSQL `make_immutable` y hash SHA-256 en carátula PDF | DATA, CRYPTO | EXISTING | THR-06, DATA-04 | TC-09 | T-10 | Ley 527 de 1999 | Asegura no repudio e integridad de la contratación de seguro |
| CTL-08 | Supresión definitiva del parámetro `simulated_delay` de contratos de API en producción | API | EXISTING | THR-08 | TC-11 | T-08 | OWASP API4:2023 | Previene vectores de denegación de servicio por demoras artificiales inyectadas |
| CTL-09 | Extracción estricta de `partner_id` de los claims de token JWT verificado en servidor para registro de cobros | API, DATA | EXISTING | THR-09, DATA-01 | TC-12 | T-11 | OWASP API1:2023 | Evita adjudicación indebida o mezcla de ingresos en arquitectura multi-tenant |
| CTL-10 | Separación estricta de entornos sandbox y producción con flujo de aprobación manual por analista para socios | PROCESS, API | EXISTING | THR-10, DATA-05 | TC-13 | T-14 | HA-23 | Confinamiento seguro de socios en fase de integración sin afectar producción |
| CTL-11 | Rutina transaccional periódica de expiración que marca pólizas vencidas con estado `EXPIRED` y habilita renovación | BACKEND, DATA | EXISTING | THR-07 | TC-14 | T-13 | HA-07 | Mantiene la integridad del estado de cobertura de pólizas según su vigencia real |
| CTL-12 | Componentes web sanitizados sin datos de prueba en producción y gestión granular de códigos de error HTTP | WEB | EXISTING | THR-02, QR-03 | TC-10, TC-18 | T-09, T-12, T-15 | WCAG 2.2 AA | Proporciona diagnóstico claro al usuario sin exponer datos quemados ni inducir a confusión |

## 10. Cryptography

### CRY-01 · Cifrado en tránsito TLS 1.3
| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02, DATA-03, DATA-04, DATA-05 |
| Type | TLS |
| Algorithm or service | TLS 1.3 / HTTPS |
| Rationale | Protección de datos personales, credenciales y cabeceras durante el transporte de red |
| Key management | Certificados TLS administrados por AWS Certificate Manager |
| Permissions | Conexiones seguras HTTPS terminadas en CloudFront / ALB |
| Rotation | Renovación anual automática en ACM |
| Recovery | Despliegue de certificados de respaldo |
| Failure behavior | Rechazo inmediato de conexiones HTTP inseguras |
| Tests | TC-01 |

### CRY-02 · Hash SHA-256 para carátula de póliza y consentimiento
| Field | Value |
|---|---|
| Protects | DATA-03, DATA-04 |
| Type | HASH |
| Algorithm or service | SHA-256 |
| Rationale | Generación de resumen criptográfico inmutable para comprobar la integridad del PDF de la póliza y el texto del consentimiento |
| Key management | No aplica llaves (función de hash unidireccional estándar NIST FIPS 180-4) |
| Permissions | Acceso de cálculo en servicios backend de pólizas y perfilamiento |
| Rotation | No aplica rotación de llaves para algoritmos de hash sin clave |
| Recovery | Recálculo determinista a partir del documento o texto original |
| Failure behavior | Rechazo de emisión si el hash generado no concuerda |
| Tests | TC-09 |

### CRY-03 · Códigos de un solo uso (OTP) basados en HMAC SHA-256
| Field | Value |
|---|---|
| Protects | DATA-03, DATA-04 |
| Type | MAC |
| Algorithm or service | HMAC-SHA256 |
| Rationale | Generación y verificación de códigos de un solo uso para consentimiento y firma de póliza con expiración a 10 minutos |
| Key management | Llave simétrica `OTP_SECRET_KEY` almacenada en variables de entorno / Secrets Manager |
| Permissions | Backend de Solventa (módulos de identidad, perfilamiento y pólizas) |
| Rotation | Rotación semestral de la clave secreta |
| Recovery | Generación de nuevo código OTP ante expiración o invalidación |
| Failure behavior | Invalidación inmediata tras 3 intentos fallidos o transcurrido el tiempo límite |
| Tests | TC-08, TC-09 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| `JWT_SECRET_KEY` | Variables de entorno / AWS Secrets Manager | Backend API | dev, staging, prod | Semestral | Inmediata vía actualización de variable en ECS | Escaneo de código estático en CI |
| `OTP_SECRET_KEY` | Variables de entorno / AWS Secrets Manager | Backend API | dev, staging, prod | Semestral | Inmediata vía actualización de variable en ECS | Escaneo de código estático en CI |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-03 | Identificación inequívoca del socio vía claim `partner_id` en token JWT |
| Secure authentication | YES | CTL-03 | Flujo OAuth2 Client Credentials con secreto validado por hash |
| Session management | YES | CTL-03 | Tokens JWT independientes sin estado de sesión en servidor |
| Token expiration | YES | CTL-03 | Expiración estricta de tokens de acceso de socio a 60 minutos |
| Revocation | YES | CTL-03 | Verificación inmediata del estado `ACTIVE` del socio en base de datos |
| Server-side permission checks | YES | CTL-03, CTL-10 | Dependencias `get_current_partner` y control de roles obligatorias en FastAPI |
| Per-resource access (ownership) | YES | CTL-03, CTL-09 | Consultas filtradas por `partner_id` de forma no manipulable en cotizaciones y cobros |
| Roles | YES | CTL-03, CTL-10 | Verificación estricta de claims `role == "partner"` y roles operativos |
| Privilege separation | YES | CTL-03, CTL-10 | Rutas de Quote Edge desacopladas del portal web interno y sandbox aislado |
| Brute-force protection | YES | CTL-01 | Bloqueo por cuota contratada (`quota_limit`) en ventana de tiempo |
| Rate limiting | YES | CTL-01 | Control de tasa de peticiones con respuesta 429 y `Retry-After` |
| One-time code (OTP) handling | YES | CTL-06, CTL-07 | Verificación de códigos OTP de 6 dígitos con HMAC-SHA256 y expiración temporal para consentimientos y firma |
| User enumeration prevention | YES | CTL-02 | Errores genéricos 401 y 404 que no revelan existencia de recursos |
| CSRF protection | NO | — | N/A: API REST consumida con encabezado `Authorization: Bearer` y protección de origen CORS |

## 13. Data isolation and multi-tenancy

| Check | Applies | Controls | Tests | Rationale |
|---|---|---|---|---|
| Tenant isolation | YES | CTL-03 | TC-03 | Cuotas independientes por fila en tabla `partners` con clave primaria `partner_id` |
| Server-side authorization | YES | CTL-03, CTL-09 | TC-03, TC-12 | Extracción de `partner_id` del token JWT verificado en el servidor para cotizaciones y cobros |
| Horizontal access | YES | CTL-03, CTL-09 | TC-03, TC-12 | Imposible que un socio consuma cuota, adjudique cobros o consulte cotizaciones de otro socio |
| Vertical access | YES | CTL-03, CTL-10 | TC-03, TC-13 | Rol partner acotado exclusivamente a los endpoints de Quote Edge y entorno sandbox |
| Resource identifiers | YES | CTL-03 | TC-03 | Identificadores UUID opacos generados en servidor |
| Queries and filters | YES | CTL-03, CTL-09 | TC-03, TC-12 | Sentencias SQL parametrizadas con filtro estricto por `partner_id` |
| Shared caches | YES | CTL-03 | TC-03 | Persistencia particionada en base de datos por tenant sin cachés globales compartidas |
| Storage and logs | YES | CTL-04 | TC-02 | Eventos de auditoría asociados al socio sin mezclar datos |
| Negative cross-tenant tests | YES | CTL-03 | TC-03 | Prueba explícita: Socio A con cuota agotada no afecta a Socio B |

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | fastapi | 0.115.0 | backend | Framework Web API | HIGH | Poetry lock / Safety 2026-11-07 | 0 vulnerabilidades | EVALUATED |
| DEP-02 | sqlalchemy | 2.0.35 | backend | ORM y base de datos async | HIGH | Poetry lock / Safety 2026-11-07 | 0 vulnerabilidades | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | Desfase temporal en el cálculo de `Retry-After` debido a diferencias de reloj entre réplicas | THR-01 | LOW | LOW | LOW | MITIGATE | Bajo: se utiliza `datetime.now(timezone.utc)` y sincronización NTP en servidores | MITIGATED |
| RISK-02 | Indisponibilidad o latencia excesiva en API externa de Open Data (`datos.gov.co`) | THR-01 | MEDIUM | LOW | LOW | MITIGATE | Bajo: timeout de 400 ms y degradación automática a score crediticio base sin bloquear | MITIGATED |

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Unidad y duración de la ventana de cuota por defecto | Ventana horaria (3600s) vs ventana diaria vs mensual | Ventana de 1 hora (3600 segundos) por defecto, configurable por socio | Architect | NO | APPROVED |

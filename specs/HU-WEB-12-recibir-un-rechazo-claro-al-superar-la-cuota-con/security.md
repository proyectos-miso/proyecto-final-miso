# Security and privacy assessment · HU-WEB-12

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Datos de solicitud de cotización (tipo y número de documento, fecha de nacimiento, valor asegurado, ciudad, riesgo) | CONFIDENTIAL | YES | NO | Cálculo actuarial y emisión de primas de seguro para socios B2B |
| DATA-02 | Telemetría y estado de cuota (`quota_limit`, `quota_used`, `quota_window_seconds`, `quota_reset_at`, `Retry-After`) | INTERNAL | NO | NO | Control de consumo, prevención de saturación de recursos y diagnóstico para socios |

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

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Socio B2B | partner | Autenticado | Emitir cotizaciones y consultar su telemetría de cuota en Quote Edge |
| Atacante externo | Ninguno | No confiable | Intentar saturar la API enviando peticiones masivas |
| Socio B2B abusivo | partner | Autenticado | Intentar cotizar por encima de la cuota contratada o agotar recursos compartidos |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| Borde API Quote Edge | Internet y backend de Solventa | Token OAuth2 JWT del socio, validez de credenciales y disponibilidad de cuota en ventana |
| Persistencia Multi-tenant | Servicios backend y PostgreSQL | Particionamiento estricto por `partner_id` para cuotas y cotizaciones |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Socio B2B | Quote Edge API | DATA-01, DATA-02 | HTTPS / TLS 1.3 | CRY-01, CTL-01 |
| Quote Edge API | PostgreSQL (`partners`, `quotes`) | DATA-01, DATA-02 | Conexión privada TLS | CTL-01, CTL-03 |

## 6. Third-party services

| Service | Use | Data shared | Location | Controls |
|---|---|---|---|---|
| Buró de crédito (simulado) | Obtención de score para tarificación de prima | DATA-01 (`document_number`) | Colombia / Local | CTL-05 |

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| `POST /api/v1/quote-edge/quotes` | Autenticado (partner) | Encabezado `Idempotency-Key` y payload JSON `QuoteRequest` |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | D | Disponibilidad de la API | Un socio con cuota agotada o mal configurado satura el servicio con cotizaciones continuas | Socio abusivo | MEDIUM | HIGH | HIGH | CTL-01 |
| THR-02 | I | Información interna del sistema | Respuestas de error por cuota que expongan stack traces, consultas SQL o detalles de infraestructura | Atacante | LOW | MEDIUM | LOW | CTL-02 |
| THR-03 | D | Disponibilidad de otros socios | La saturación de cuota de un socio consume conexiones o CPU afectando a los demás socios | Socio abusivo | MEDIUM | HIGH | HIGH | CTL-03 |
| THR-04 | R | Trazabilidad y auditoría | Rechazos de cuota no auditados impiden detectar abusos o dimensionar ampliaciones de plan | Operaciones | LOW | MEDIUM | LOW | CTL-04 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | Verificación bloqueante de cuota en `POST /quotes` con rechazo HTTP 429 Too Many Requests cuando `quota_used >= quota_limit` | API | EXISTING | THR-01, DATA-02 | TC-02 | T-01, T-02 | RFC 6585 | Detiene la ejecución en el borde antes de invocar buró de crédito o calcular tarifas |
| CTL-02 | Formato estandarizado de respuesta 429 con cabecera `Retry-After` y modelo `PartnerQuotaExceededResponse` genérico sin trazas internas | API | EXISTING | THR-02, DATA-02 | TC-02 | T-02, T-03 | RFC 6585, RFC 7807 | Proporciona diagnóstico accionable al socio sin fuga de información |
| CTL-03 | Particionamiento multi-tenant estricto por `partner_id` en PostgreSQL asegurando aislamiento de cuotas | DATA | EXISTING | THR-03, DATA-02 | TC-03 | T-01 | HA-07, HA-14 | El estado de cuota de cada socio es totalmente independiente en la base de datos |
| CTL-04 | Emisión de evento estructurado de auditoría `PARTNER_QUOTA_EXCEEDED` en CloudWatch con socio y timestamp | API | EXISTING | THR-04, DATA-02 | TC-07 | T-02 | OWASP API8:2023 | Facilita monitoreo operativo y detección de tráfico anómalo |
| CTL-05 | Timeouts y degradación controlada en consultas a buró crediticio | BACKEND | EXISTING | THR-01 | TC-01 | — | HA-05 | Protege la estabilidad del servicio ante latencias externas |

## 10. Cryptography

### CRY-01 · Cifrado en tránsito TLS 1.3
| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02 |
| Type | TLS |
| Algorithm or service | TLS 1.3 / HTTPS |
| Rationale | Protección de datos personales y cabeceras de cuota durante el transporte de red |
| Key management | Certificados TLS administrados por AWS Certificate Manager |
| Permissions | Conexiones seguras HTTPS terminadas en CloudFront / ALB |
| Rotation | Renovación anual automática en ACM |
| Recovery | Despliegue de certificados de respaldo |
| Failure behavior | Rechazo inmediato de conexiones HTTP inseguras |
| Tests | TC-01 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| `JWT_SECRET_KEY` | Variables de entorno / AWS Secrets Manager | Backend API | dev, staging, prod | Semestral | Inmediata vía actualización de variable en ECS | Escaneo de código estático en CI |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-03 | Identificación inequívoca del socio vía claim `partner_id` en token JWT |
| Secure authentication | YES | CTL-03 | Flujo OAuth2 Client Credentials con secreto validado por hash |
| Session management | YES | CTL-03 | Tokens JWT independientes sin estado de sesión en servidor |
| Token expiration | YES | CTL-03 | Expiración estricta de tokens de acceso de socio a 60 minutos |
| Revocation | YES | CTL-03 | Verificación inmediata del estado `ACTIVE` del socio en base de datos |
| Server-side permission checks | YES | CTL-03 | Dependencia `get_current_partner` obligatoria en FastAPI |
| Per-resource access (ownership) | YES | CTL-03 | Consultas filtradas por `partner_id` de forma no manipulable |
| Roles | YES | CTL-03 | Verificación estricta de claim `role == "partner"` en Quote Edge |
| Privilege separation | YES | CTL-03 | Rutas de Quote Edge desacopladas del portal web interno |
| Brute-force protection | YES | CTL-01 | Bloqueo por cuota contratada (`quota_limit`) en ventana de tiempo |
| Rate limiting | YES | CTL-01 | Control de tasa de peticiones con respuesta 429 y `Retry-After` |
| One-time code (OTP) handling | NO | — | N/A: Comunicación B2B M2M mediante credenciales OAuth2 |
| User enumeration prevention | YES | CTL-02 | Errores genéricos 401 y 404 que no revelan existencia de recursos |
| CSRF protection | NO | — | N/A: API REST M2M consumida con encabezado `Authorization: Bearer` |

## 13. Data isolation and multi-tenancy

| Check | Applies | Controls | Tests | Rationale |
|---|---|---|---|---|
| Tenant isolation | YES | CTL-03 | TC-03 | Cuotas independientes por fila en tabla `partners` con clave primaria `partner_id` |
| Server-side authorization | YES | CTL-03 | TC-03 | Extracción de `partner_id` del token JWT verificado en el servidor |
| Horizontal access | YES | CTL-03 | TC-03 | Imposible que un socio consuma cuota o consulte cotizaciones de otro socio |
| Vertical access | YES | CTL-03 | TC-03 | Rol partner acotado exclusivamente a los endpoints de Quote Edge |
| Resource identifiers | YES | CTL-03 | TC-03 | Identificadores opacos generados en servidor |
| Queries and filters | YES | CTL-03 | TC-03 | Sentencias SQL parametrizadas con filtro estricto por `partner_id` |
| Shared caches | YES | CTL-03 | TC-03 | Persistencia particionada en base de datos por tenant |
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

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Unidad y duración de la ventana de cuota por defecto | Ventana horaria (3600s) vs ventana diaria vs mensual | Ventana de 1 hora (3600 segundos) por defecto, configurable por socio | Architect | NO | APPROVED |

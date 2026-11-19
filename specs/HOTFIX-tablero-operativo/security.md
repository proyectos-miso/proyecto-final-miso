# Security and privacy assessment · HOTFIX

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Conteos agregados: cotizaciones, pólizas, conversión, siniestros en curso y pendientes del outbox | INTERNAL | NO | NO | Ver el volumen y la salud del canal |
| DATA-02 | Duraciones de las rutas críticas por grupo (`quote`, `issue`, `payment`, `claim`) | INTERNAL | NO | NO | Comparar p50, p95 y p99 con la meta |
| DATA-03 | Estado y tiempo de respuesta de base, S3, SQS y Firebase | INTERNAL | NO | NO | Detectar una caída o una degradación |

## 2. Data lifecycle

N/A: no hay datos CONFIDENTIAL ni RESTRICTED. Los conteos se calculan en cada consulta y no se guardan. Las duraciones
viven en la memoria de la réplica (500 por grupo) y se pierden al reiniciar. La respuesta completa, que solo lleva
DATA-01 a DATA-03, se guarda en la memoria de la réplica (el estado de las dependencias 15 s para todos los periodos, cada respuesta hasta que ese estado vence); los estados no se guardan en otro lugar.

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Analista de Solventa | analyst sin socio | Autenticado con TOTP | Ver el tablero |
| Analista de un socio | analyst con `partner_id` | Autenticado con TOTP | Nada del tablero: 403 |
| Asesor | advisor | Autenticado | Nada del tablero: 403 |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| Portal → backend | Navegador del analista e internet | Token del analista, rol `analyst`, usuario activo y sin socio (leído de la base), `period` en `today`, `7d` o `30d` |
| Backend → AWS y Firebase | Tarea de ECS y servicios gestionados | Rol de la tarea con permisos existentes; solo lecturas de metadatos (`HeadBucket`, `GetQueueUrl`) con clientes propios de 1 s y un intento; nada del resultado sale al cliente salvo el estado |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Rutas críticas | Memoria de la réplica | DATA-02 | Proceso | CTL-02 |
| Backend | Portal | DATA-01, DATA-02, DATA-03 | HTTPS | CRY-01, CTL-01, CTL-03 |
| Backend | S3, SQS, Firebase | Nombre del bucket y de la cola | HTTPS de AWS | CRY-01, CTL-04 |

## 6. Third-party services

| Service | Use | Data shared | Location | Controls |
|---|---|---|---|---|
| Amazon S3 | `head_bucket` del bucket de documentos | Ninguno de DATA-01 a DATA-03: solo el nombre del bucket | us-east-1 | CTL-03, CTL-04 |
| Amazon SQS | `get_queue_url` de la cola de eventos | Ninguno: solo el nombre de la cola | us-east-1 | CTL-03, CTL-04 |
| Firebase Cloud Messaging | Solo se revisa si el cliente local está iniciado; no hay llamada de red nueva | Ninguno | Google | CTL-03 |

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| GET /experience/operations/indicators | Autenticado (analyst de Solventa) | Token y `period` |
| `LatencyMiddleware` | Todo pedido HTTP | Método y ruta: solo se comparan con un conjunto cerrado de plantillas |
| Pantalla «Operación» | Navegador del analista | Selector de periodo |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | E | DATA-01, DATA-02, DATA-03 | El analista de un socio o un asesor ve los indicadores de todo Solventa, que incluyen el volumen de otros socios | Analista de socio, asesor | MEDIUM | MEDIUM | MEDIUM | CTL-01, CTL-05 |
| THR-02 | I | Configuración de AWS | La respuesta filtra mensajes de error de boto3 o de la base con ARN, cuenta, bucket, cola o credenciales | Analista o quien obtenga la respuesta | MEDIUM | MEDIUM | MEDIUM | CTL-03 |
| THR-03 | I | Datos del pedido | La ventana de latencia guarda URLs con radicados, números de póliza o documentos | Quien lea un volcado de memoria | LOW | MEDIUM | LOW | CTL-02 |
| THR-04 | D | Backend y base | Una dependencia colgada o muchas pestañas abiertas agotan hilos, conexiones o la base | Dependencia caída, analistas | MEDIUM | MEDIUM | MEDIUM | CTL-04, CTL-05 |
| THR-05 | D | Rutas críticas | Una falla o el costo de la medición rompe o frena una cotización, una emisión, un cobro o un reporte | Falla interna | LOW | HIGH | MEDIUM | CTL-02 |
| THR-06 | T | DATA-03 | El semáforo muestra una dependencia como operativa sin que lo esté (un 403 de S3 que no prueba el bucket ni los permisos, o un estado viejo que parece actual) y el equipo no actúa | Falla de configuración, dependencia caída | LOW | MEDIUM | LOW | CTL-05 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | `solventa_operator`: rol `analyst`, usuario activo y `partner_id` nulo leído de la base en cada pedido | API | EXISTING | THR-01 | TC-02 | T-03 | API5:2023, ASVS V4.1 | Reutilizado de HOTFIX-gestion-de-socios |
| CTL-02 | `LatencyMiddleware` ASGI puro: clasifica por método y plantilla de ruta en un conjunto cerrado de cuatro grupos, guarda solo la duración en un `deque(maxlen=500)` con candado y aísla toda falla de la medición | BACKEND | EXISTING | THR-03, THR-05 | TC-04, TC-05 | T-01 | CWE-532, CWE-359 | Nuevo en este cambio (implementado y probado) |
| CTL-03 | La respuesta solo trae números y, por dependencia, `name`, `status` y `latency_ms`; el registro guarda el tipo de excepción sin el mensaje; `Cache-Control: no-store` | API | EXISTING | THR-02 | TC-03 | T-02 | CWE-209, ASVS V7.4 | Nuevo en este cambio (implementado y probado) |
| CTL-04 | Cada comprobación con `asyncio.wait_for` de 1 s, todas en paralelo; S3, SQS y Firebase en hilos daemon propios (a lo sumo 4) con clientes de sonda de 1 s por conexión y lectura y un intento; la base con su propia conexión del pool; los conteos en una sentencia con `statement_timeout` de 2 s; la lectura del analista con 2 s de límite y 503 genérico solo ante errores de tiempo, de conexión o de SQLAlchemy; el estado de las dependencias compartido 15 s entre periodos, con un solo cálculo en curso, y la respuesta por periodo hasta que vence, siempre después de autorizar | BACKEND | EXISTING | THR-04 | TC-03, TC-10 | T-02, T-07 | CWE-400 | Nuevo en este cambio (implementado y probado) |
| CTL-05 | El portal refresca cada 30 s solo con la pestaña visible, detiene el temporizador al ocultarse o al salir y no cancela la carga pedida por el analista; ante un error marca las tarjetas, la latencia y el semáforo «Sin datos desde HH:mm:ss»; la pestaña y `solventaGuard` solo ocultan; el error es genérico | WEB | EXISTING | THR-01, THR-04, THR-06 | TC-06 | T-04, T-07 | CWE-400 | Nuevo en este cambio (implementado y probado) |

## 10. Cryptography

### CRY-01 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02, DATA-03 |
| Type | TLS |
| Algorithm or service | HTTPS de CloudFront y del ALB (TLS 1.2 o superior); HTTPS de los SDK de AWS |
| Rationale | Viaja por internet; sin cambios |
| Key management | Certificados de ACM |
| Permissions | Administración de AWS |
| Rotation | La de ACM |
| Recovery | ACM renueva el certificado |
| Failure behavior | Sin TLS no hay conexión |
| Tests | TC-02 |

## 11. Secrets and keys

Sin secretos nuevos; ninguna credencial sale en la respuesta ni en el registro.

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| Credenciales temporales del rol de la tarea de ECS (S3 y SQS) | Las entrega AWS al contenedor; no hay llaves estáticas | Backend | DEV, STAGING, producción | Automática de AWS (STS) | Quitar la política del rol en Terraform | CloudTrail y el gate de secretos |
| `FIREBASE_CREDENTIALS_JSON` (existente, sin cambio) | Variable `environment` de la tarea de ECS desde `var.firebase_credentials_json` (`terraform/modules/ecs/main.tf`), no un bloque `secrets`; en local, `FIREBASE_CREDENTIALS_FILE` | Backend; el tablero solo revisa si el cliente quedó iniciado | DEV, STAGING, producción | Desde la consola de Firebase y el secreto | Revocar la cuenta de servicio en Firebase | Gate de secretos |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-01 | — |
| Secure authentication | NO | — | No cambia: sesión del analista con TOTP |
| Session management | NO | — | No cambia |
| Token expiration | NO | — | No cambia |
| Revocation | NO | — | No cambia |
| Server-side permission checks | YES | CTL-01 | — |
| Per-resource access (ownership) | YES | CTL-01 | Los indicadores son globales: solo Solventa |
| Roles | YES | CTL-01 | — |
| Privilege separation | YES | CTL-01 | Analista de socio frente a analista de Solventa |
| Brute-force protection | NO | — | No hay autenticación nueva |
| Rate limiting | NO | — | Endpoint de un rol interno con TOTP; el portal refresca cada 30 s solo visible (CTL-05) |
| One-time code (OTP) handling | NO | — | No aplica |
| User enumeration prevention | NO | — | No expone usuarios |
| CSRF protection | NO | — | Token Bearer, sin cookies |

## 13. Data isolation and multi-tenancy

| Check | Applies | Controls | Tests | Rationale |
|---|---|---|---|---|
| Tenant isolation | YES | CTL-01 | TC-02 | El analista de un socio no ve los indicadores de todos |
| Server-side authorization | YES | CTL-01 | TC-02 | — |
| Horizontal access | YES | CTL-01 | TC-02 | Un socio no ve el volumen de otro |
| Vertical access | YES | CTL-01 | TC-02 | Asesor y analista de socio: 403 |
| Resource identifiers | NO | — | — | El endpoint no recibe identificadores; `period` es un literal cerrado |
| Queries and filters | YES | CTL-01 | TC-01 | Conteos globales solo para Solventa |
| Shared caches | YES | CTL-03 | TC-03 | `no-store` |
| Storage and logs | YES | CTL-02, CTL-03 | TC-03, TC-05 | La ventana no guarda rutas; el registro, solo el tipo de excepción |
| Negative cross-tenant tests | YES | CTL-01 | TC-02 | — |

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | boto3, botocore y firebase-admin | Sin cambio de versión | backend | Comprobación de S3, SQS y Firebase | MEDIUM | Revisión manual 2026-11-15 | Sin cambio; ya en el lock | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | La latencia es por réplica: con varias tareas de ECS, cada consulta muestra la ventana de la tarea que la atendió y los percentiles no representan el total | THR-05 | HIGH | LOW | MEDIUM | MITIGATE | Bajo: el portal lo dice en la tabla; propuesta: publicar las duraciones en CloudWatch (EMF) y leer el agregado | OPEN |
| RISK-02 | En AWS el rol no tiene `s3:ListBucket`, así que `head_bucket` da 403 y el tablero lo cuenta como «S3 responde». Como `HeadBucket` no trae cuerpo, el mismo 403 sale con credenciales inválidas o vencidas, con una firma mala o con un bucket de otra cuenta: en la práctica la comprobación solo prueba que el DNS y el TLS de S3 responden, no que el bucket sea el correcto ni que el rol pueda leer o escribir | THR-06 | LOW | MEDIUM | LOW | MITIGATE | Bajo: la carátula y las fotos fallan con su propio aviso y el registro; propuesta: un `HeadObject` sobre un objeto testigo que Terraform cree en un prefijo que el rol ya lee, que distingue 200 (todo bien), 404 (bucket o prefijo equivocado) y 403 (credenciales o permisos) sin ampliar permisos | OPEN |
| RISK-03 | Una comprobación colgada sigue ocupando un hilo después del segundo de límite. Con los clientes de sonda (1 s de conexión, 1 s por lectura, un intento) el hilo se libera en unos 2 s; mientras tanto cuenta para el tope de 4 y, sin hilos libres, las comprobaciones siguientes dan `unchecked` («Sin comprobar») en seguida, no `down`, y ese resultado no se guarda en caché: el pedido siguiente vuelve a intentarlo. Las sondas se comparten entre periodos y un cálculo en curso lo reutiliza quien llega después, así que varias pestañas o periodos no disparan sondas en paralelo. Los hilos son daemon y no retrasan el apagado | THR-04 | LOW | LOW | LOW | MITIGATE | Remanente: el `read_timeout` es por lectura del socket, así que un servidor que responde byte a byte podría alargar un hilo más de 2 s; con el caché de 15 s compartido y el tope de 4, a lo sumo 4 hilos quedan ocupados y el resto del backend no los comparte; mientras tanto el semáforo dice «Sin comprobar», no «Caída» | MITIGATED |
| RISK-04 | Sin réplica de lectura, el tablero cuenta en la base principal; `quotes.created_at` no tiene índice y el conteo de 30 días crece con el volumen | THR-04 | MEDIUM | LOW | LOW | MITIGATE | Bajo: una sentencia con `statement_timeout` de 2 s, a lo sumo una vez cada 15 s por periodo y réplica gracias al caché; propuesta: índice en `quotes.created_at` y `policies.issued_at` en una migración propia | OPEN |
| RISK-05 | El 403 del analista de un socio reutiliza el texto de `solventa_operator`, que habla de «gestionar socios» | THR-01 | LOW | LOW | LOW | MITIGATE | Bajo: el acceso se niega igual; propuesta: un texto común para las pantallas de Solventa al integrar «Auditoría» | OPEN |

## 16. Pending decisions

N/A: sin decisiones abiertas.

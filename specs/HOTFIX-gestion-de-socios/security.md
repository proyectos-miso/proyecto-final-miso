# Security and privacy assessment · HOTFIX

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | `client_secret` del socio (y su hash bcrypt) | RESTRICTED | NO | NO | Autenticar al socio en el canal quote-edge |
| DATA-02 | Correo de contacto del socio | CONFIDENTIAL | YES | NO | Contactar al socio; en el portal solo se ve enmascarado |
| DATA-03 | Estado, entorno y cuota del socio | INTERNAL | NO | NO | Decidir qué puede hacer el socio y operar el canal |

## 2. Data lifecycle

### DATA-01 · Secreto del socio

| Question | Answer |
|---|---|
| What it contains | `sec_` más 24 bytes aleatorios en base64url; en la base solo el hash bcrypt |
| Why it is needed | AC-02, AC-04 |
| Who can access it | El analista de Solventa que lo genera, una sola vez; después nadie, ni Solventa |
| Where it is captured | Lo genera el backend al registrar o rotar |
| How it is transmitted | HTTPS (CRY-02) con `Cache-Control: no-store`; del analista al socio por un canal seguro fuera del sistema |
| Where it is stored | Hash bcrypt en `partners.client_secret_hash` (CRY-01); en el portal, solo en una señal del componente |
| Copies, caches and backups | Respaldos de RDS con el hash; el portapapeles del analista si pulsa «Copiar» |
| How long it is kept | El hash hasta la siguiente rotación; en el portal hasta cerrar el panel o salir de la pestaña |
| How access is audited | Eventos `PARTNER_REGISTERED_BY_OPERATOR`, `PARTNER_CREDENTIALS_ROTATED` (con `operator_ref`) y de autenticación en `solventa.security.partner`, sin el secreto |
| How it is deleted or anonymized | La rotación reemplaza el hash; la revocación lo deja inservible |
| What happens if it leaks | Un tercero cotiza o emite a nombre del socio hasta que se rote o revoque desde el portal |
| Transfers to third parties | Solo al socio dueño, fuera del sistema |

### DATA-02 · Correo de contacto

| Question | Answer |
|---|---|
| What it contains | Un correo corporativo del socio |
| Why it is needed | Contacto operativo; `has_contact` dice si existe |
| Who can access it | En la base, el backend; en el portal, solo enmascarado (`s***@dominio`) |
| Where it is captured | Formulario de alta de «Socios» |
| How it is transmitted | HTTPS (CRY-02) |
| Where it is stored | `partners.contact_email` en PostgreSQL |
| Copies, caches and backups | Respaldos de RDS; el listado va con `no-store` |
| How long it is kept | Mientras exista el socio |
| How access is audited | Registros de acceso del backend sin el correo |
| How it is deleted or anonymized | No hay borrado desde el portal; se haría en la base a pedido del socio |
| What happens if it leaks | Spam o phishing dirigido al contacto del socio: impacto bajo |
| Transfers to third parties | Ninguna |

### DATA-03 · Estado, entorno y cuota

| Question | Answer |
|---|---|
| What it contains | `status`, `environment`, `quota_limit`, `quota_used`, `created_at` |
| Why it is needed | AC-01, AC-05, AC-06 |
| Who can access it | El analista de Solventa |
| Where it is captured | Alta, aprobación, revocación y consumo de cuota |
| How it is transmitted | HTTPS (CRY-02) |
| Where it is stored | Tabla `partners` |
| Copies, caches and backups | Respaldos de RDS |
| How long it is kept | Mientras exista el socio |
| How access is audited | Eventos de seguridad de cada cambio |
| How it is deleted or anonymized | No se borra: un socio revocado queda como registro |
| What happens if it leaks | Revela qué socios hay y su volumen: impacto bajo |
| Transfers to third parties | Ninguna |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Analista de Solventa | analyst sin socio | Autenticado con TOTP | Listar, registrar, aprobar, rotar y revocar socios |
| Analista de un socio | analyst con `partner_id` | Autenticado con TOTP | Nada de esta pantalla: 403 |
| Asesor | advisor | Autenticado | Nada de esta pantalla: 403 |
| Socio B2B | partner (OAuth2 client credentials) | Autenticado por secreto | Cotizar; emitir solo en producción |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| Portal → backend | Navegador del analista e internet | Token del analista, rol `analyst`, usuario activo y sin socio (leído de la base), formato del `partner_id` |
| Socio → quote-edge | Sistema del socio e internet | Secreto contra el hash; en cada pedido, token y estado `ACTIVE` releído; entorno para emitir |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Backend | Portal | DATA-01 (una vez), DATA-02 enmascarado, DATA-03 | HTTPS | CRY-02, CTL-01, CTL-02, CTL-03, CTL-08 |
| Portal | Backend | DATA-02 (alta) | HTTPS | CRY-02, CTL-01 |
| Socio | Backend | DATA-01 | HTTPS | CRY-01, CRY-02, CTL-04 |

## 6. Third-party services

| Service | Use | Data shared | Location | Controls |
|---|---|---|---|---|
| Socios B2B | Consumen quote-edge con sus credenciales | DATA-01 propio | Sus sistemas | CTL-04, CTL-05 |

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| GET /experience/operator y /experience/partners | Autenticado (analyst) | Token |
| POST /experience/partners/{id}/revoke, /credentials, /approve | Autenticado (analyst de Solventa) | `partner_id` en la ruta (2 a 64, `[\w-]`), cuota |
| POST /quote-edge/policies | Autenticado (partner) | Token del socio, cuerpo de emisión |
| POST /experience/policies y /experience/payments/charge | Autenticado (advisor) | `quote_id` o número de póliza de un socio; `partner_id` del token del asesor |
| POST /experience/policies/{n}/renewal y /insured/policies/{n}/renewal | Autenticado (analyst o asegurado) | Número de póliza de un socio |
| Pantalla «Socios» | Navegador del analista | Nombre y correo del socio |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | E | DATA-01, DATA-03 | El analista de un socio o un asesor gestiona socios | Analista de socio | MEDIUM | HIGH | HIGH | CTL-01, CTL-06 |
| THR-02 | I | DATA-01, DATA-02 | El secreto o el correo completo quedan expuestos en el listado, la caché, el navegador o los registros, o el secreto anterior sigue sirviendo | Atacante con acceso al navegador o a los registros | MEDIUM | HIGH | HIGH | CTL-02, CTL-03, CTL-08 |
| THR-03 | S | Canal quote-edge | Un socio revocado sigue usando un token vigente o se reactiva al aprobarlo | Socio revocado | MEDIUM | HIGH | HIGH | CTL-04, CTL-06 |
| THR-04 | T | DATA-03 | Registro duplicado que pisa o confunde a otro socio | Analista de Solventa | LOW | MEDIUM | LOW | CTL-07 |
| THR-05 | E | Pólizas y cobros | Se emite o se cobra en nombre de un socio en sandbox, revocado o inexistente, por el canal del socio o por el del asesor | Socio en sandbox o revocado, asesor | MEDIUM | HIGH | HIGH | CTL-05 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | `solventa_operator`: rol `analyst`, usuario activo y `partner_id` nulo leído de la base en cada pedido; el guard del portal solo oculta | API | EXISTING | THR-01 | TC-02 | T-01, T-03 | API5:2023, ASVS V4.1 | — |
| CTL-02 | Secreto aleatorio de 192 bits, guardado como hash bcrypt y entregado una vez con `no-store`; nunca en registros | SECRETS | EXISTING | THR-02, DATA-01 | TC-01, TC-03, TC-04 | T-01, T-04 | ASVS V2.4, CWE-312, CWE-525 | — |
| CTL-03 | El listado no trae hash ni secreto y enmascara el correo; responde `no-store` | DATA | EXISTING | THR-02, DATA-02 | TC-01 | T-01 | API3:2023, CWE-359 | — |
| CTL-04 | Revocar es idempotente y `get_current_partner` y `authenticate_partner` releen el estado; rotar y aprobar dan 409 a un revocado; las mutaciones bloquean la fila | API | EXISTING | THR-03 | TC-03 | T-01 | ASVS V3.3, CWE-613 | — |
| CTL-05 | `ensure_partner_can_issue`, regla única en `partners.py`: sin socio no hace nada; inexistente o revocado da 403 «Socio revocado o inexistente»; en sandbox 403 «Un socio en sandbox no emite pólizas reales»; deja evento `PARTNER_ISSUE_BLOCKED` o `PARTNER_SANDBOX_ISSUE_BLOCKED`. La aplican la emisión nueva del asesor (socio de la cotización y del asesor), la emisión nueva del socio por quote-edge, todo cobro nuevo (socio del asesor y de la póliza) y la renovación del analista y de la app (socio de la póliza, sin cambiarla); repetir una emisión o un cobro ya hechos devuelve lo existente (RC-02); cotizar sigue con la cuota de 1000 | API | EXISTING | THR-05 | TC-05 | T-02 | API5:2023, ASVS V4.1 | Nuevo en este cambio (implementado y probado); el validador solo admite EXISTING, MISSING o NOT APPLICABLE |
| CTL-06 | Eventos `PARTNER_REGISTERED_BY_OPERATOR`, `PARTNER_REVOKED_BY_OPERATOR`, `PARTNER_CREDENTIALS_ROTATED` y `PARTNER_APPROVED_BY_OPERATOR` con `partner_id`, `client_id` y `operator_ref` (12 hex del sha256 del usuario), y `PARTNER_ISSUE_BLOCKED` y `PARTNER_SANDBOX_ISSUE_BLOCKED`; sin secretos ni correos | BACKEND | EXISTING | THR-01, THR-03 | TC-03 | T-01, T-06 | ASVS V7.1 | Nuevo en este cambio (implementado y probado) |
| CTL-07 | `partner_id` y `client_id` en ASCII con sufijo aleatorio de 32 bits y `client_id` único en la base | BACKEND | EXISTING | THR-04 | TC-01, TC-03 | T-06 | CWE-694 | — |
| CTL-08 | En el portal el secreto vive solo en una señal del componente, se borra al cerrar el panel o salir de la pestaña y nunca va a `sessionStorage` ni `localStorage`; la pestaña «Socios» y `solventaGuard` solo ocultan; renovar el token de la misma persona no cambia lo que se ve; el contrato `/openapi.json` se pide sin el token del analista | WEB | EXISTING | THR-02, THR-01 | TC-04 | T-03, T-04 | ASVS V8.2, CWE-922 | — |

## 10. Cryptography

### CRY-01 · Hash del secreto del socio

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | PASSWORD_HASH |
| Algorithm or service | bcrypt (`get_password_hash` de `app/core/security.py`, sin cambio) |
| Rationale | El backend solo necesita verificar el secreto, nunca recuperarlo; se reutiliza el bcrypt existente del proyecto (Argon2id quedaría para un cambio propio de `security.py`) |
| Key management | Sin llave: sal por hash |
| Permissions | Backend |
| Rotation | El analista rota el secreto desde «Socios» |
| Recovery | No se recupera: se rota |
| Failure behavior | Secreto que no coincide: 401 y evento `PARTNER_AUTH_FAILED` |
| Tests | TC-03 |

### CRY-02 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02, DATA-03 |
| Type | TLS |
| Algorithm or service | HTTPS de CloudFront y del ALB (TLS 1.2 o superior) |
| Rationale | Viaja por internet |
| Key management | Certificados de ACM |
| Permissions | Administración de AWS |
| Rotation | La de ACM |
| Recovery | ACM renueva el certificado |
| Failure behavior | Sin TLS no hay conexión |
| Tests | TC-03 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| `client_secret` de cada socio | Hash bcrypt en `partners`; el original solo lo tiene el socio | Backend (verificación) | DEV, STAGING, producción | A pedido, desde «Socios» | «Revocar» en «Socios» | Eventos `PARTNER_AUTH_FAILED` y el gate de secretos |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-01 | — |
| Secure authentication | YES | CTL-02 | Secreto aleatorio con hash |
| Session management | NO | — | No cambia |
| Token expiration | NO | — | No cambia: `PARTNER_TOKEN_EXPIRE_MINUTES` |
| Revocation | YES | CTL-04 | — |
| Server-side permission checks | YES | CTL-01 | — |
| Per-resource access (ownership) | YES | CTL-01 | Solo Solventa gestiona socios; no hay recursos por socio en esta pantalla |
| Roles | YES | CTL-01 | — |
| Privilege separation | YES | CTL-01 | Analista de socio frente a analista de Solventa |
| Brute-force protection | NO | — | No cambia la autenticación del socio |
| Rate limiting | NO | — | Endpoints de un rol interno con TOTP |
| One-time code (OTP) handling | NO | — | No aplica |
| User enumeration prevention | NO | — | Solo Solventa ve el listado completo |
| CSRF protection | NO | — | Token Bearer, sin cookies |

## 13. Data isolation and multi-tenancy

| Check | Applies | Controls | Tests | Rationale |
|---|---|---|---|---|
| Tenant isolation | YES | CTL-01 | TC-02 | Un analista de socio no ve ni toca a ningún socio |
| Server-side authorization | YES | CTL-01 | TC-02 | — |
| Horizontal access | YES | CTL-01 | TC-02 | Un socio no gestiona a otro |
| Vertical access | YES | CTL-01 | TC-02 | Asesor y analista de socio: 403 |
| Resource identifiers | YES | CTL-01, CTL-07 | TC-02, TC-03 | `partner_id` validado por formato (`[\w-]`, 2 a 64); inexistente da 404 |
| Queries and filters | YES | CTL-01 | TC-01 | El listado es global solo para Solventa |
| Shared caches | YES | CTL-02, CTL-03, CTL-08 | TC-01, TC-04 | `no-store` en el listado y en las credenciales |
| Storage and logs | YES | CTL-06 | TC-03 | Eventos sin secretos ni correos |
| Negative cross-tenant tests | YES | CTL-01, CTL-04 | TC-02, TC-03 | — |

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | bcrypt y PyJWT | Sin cambio de versión | backend | Hash del secreto y tokens del socio | HIGH | Revisión manual 2026-11-14 | Sin cambio; ya en el lock | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | El secreto queda en el portapapeles o a la vista mientras el panel está abierto | THR-02 | LOW | MEDIUM | LOW | ACCEPT | Bajo: el panel avisa, se borra al salir y el secreto se puede rotar | ACCEPTED |
| RISK-02 | Dos socios con el mismo nombre comercial | THR-04 | LOW | LOW | LOW | ACCEPT | Bajo: los identificadores son distintos y el listado muestra el `partner_id` | ACCEPTED |
| RISK-03 | La revocación no se deshace desde el portal | THR-03 | LOW | LOW | LOW | ACCEPT | Bajo: se registra un socio nuevo con credenciales nuevas | ACCEPTED |
| RISK-04 | Tras rotar, un token ya emitido con el secreto anterior sigue vivo hasta 60 minutos (`PARTNER_TOKEN_EXPIRE_MINUTES`) | THR-02, THR-03 | LOW | MEDIUM | LOW | MITIGATE | Bajo: si hubo compromiso se revoca, lo que corta los tokens vivos en el siguiente pedido; el portal lo advierte al rotar | OPEN |
| RISK-05 | TOCTOU entre la revocación y una emisión, un cobro o una renovación en curso: `ensure_partner_can_issue` lee el socio sin bloquearlo, así que una operación que ya pasó la regla termina aunque la revocación se confirme entre la lectura y su commit | THR-05 | LOW | MEDIUM | LOW | MITIGATE | Bajo: la ventana es de milisegundos y la operación siguiente ya se rechaza; propuesta: leer el socio con `FOR SHARE` en la regla para que la revocación espere | OPEN |
| RISK-06 | `operator_ref` (12 hex del sha256 del usuario) es un seudónimo reversible por diccionario: quien lea los eventos y conozca la lista de correos de los analistas puede saber quién actuó | THR-01 | LOW | LOW | LOW | MITIGATE | Bajo: los eventos solo los leen quienes ya administran la plataforma y los analistas son pocos; propuesta: HMAC con una llave del servidor | OPEN |
| RISK-07 | La prueba de dos rotaciones simultáneas no detecta si falta el `FOR UPDATE`: con el bloqueo o sin él, solo el último hash queda en la base y la prueba pasa | THR-02, THR-03 | LOW | LOW | LOW | MITIGATE | Bajo: el bloqueo está en `_locked` y se revisa en el código; propuesta: una prueba que verifique el `FOR UPDATE` de la consulta o que serialice dos transacciones | OPEN |

## 16. Pending decisions

N/A: sin decisiones abiertas.

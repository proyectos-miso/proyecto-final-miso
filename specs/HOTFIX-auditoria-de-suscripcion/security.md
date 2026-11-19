# Security and privacy assessment · HOTFIX

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Documento del asegurado en `quotes` y `underwriting_rejections`; fecha de nacimiento solo en `quotes` | CONFIDENTIAL | YES | NO | Identificar la decisión y recalcular la edad a la fecha de la cotización |
| DATA-02 | Puntaje del buró en `quotes.score_applied`; los rechazos no lo guardan (CTL-03 de HU-WEB-07) | RESTRICTED | YES | YES | Recalcular la tarifa y el cupo de la cotización |
| DATA-03 | Suma asegurada, riesgo de ocupación, versión de reglas, tiers y desglose de la decisión | CONFIDENTIAL | YES | NO | Entradas y salida para la auditoría; ligadas al documento |
| DATA-04 | Estado del consentimiento (`consents.status`) | CONFIDENTIAL | YES | NO | Saber si se puede cotizar y si la auditoría muestra las entradas personales |

## 2. Data lifecycle

### DATA-01 · Documento y fecha de nacimiento

| Question | Answer |
|---|---|
| What it contains | Número de documento y fecha de nacimiento del asegurado |
| Why it is needed | AC-01, AC-02, AC-03: el documento identifica la decisión; la edad a la fecha de la cotización entra en la tarifa y en el cupo |
| Who can access it | El backend; en el portal, solo el analista de Solventa, con el documento enmascarado (cuatro últimos) y nada si el consentimiento está revocado |
| Where it is captured | Cotizador del asesor y canal de socios |
| How it is transmitted | HTTPS (CRY-01); el detalle, el recálculo y la revocación responden con `no-store`, también en sus errores; el documento de la revocación va en el cuerpo, no en la URL |
| Where it is stored | `quotes.document_number`, `quotes.birth_date` y `underwriting_rejections.document_number` en PostgreSQL; el rechazo no guarda la fecha de nacimiento (CTL-08) |
| Copies, caches and backups | Respaldos de RDS; en el portal, solo en señales del componente mientras la pestaña está abierta; nunca en storage del navegador |
| How long it is kept | Mientras exista la decisión; las dos tablas son de solo inserción y no hay purga definida (RISK-01) |
| How access is audited | Evento `DECISION_VIEWED` en `solventa.audit` con `operator_ref` y el id de la decisión, sin el dato |
| How it is deleted or anonymized | No se borra desde el sistema: las tablas son inmutables. Con la revocación se oculta en la auditoría; una purga exige un procedimiento de base que deshabilite el disparador (RISK-01) |
| What happens if it leaks | Identifica al asegurado y su edad: impacto medio |
| Transfers to third parties | Ninguna nueva |

### DATA-02 · Puntaje del buró

| Question | Answer |
|---|---|
| What it contains | El puntaje aplicado a la decisión (el neutro 700 si el buró no respondió a tiempo) |
| Why it is needed | AC-03, AC-04: el cupo y el multiplicador de historial de pago de la cotización dependen de él |
| Who can access it | El backend; en el portal, el analista de Solventa, y nadie si el consentimiento está revocado |
| Where it is captured | Lo calcula el motor al cotizar |
| How it is transmitted | HTTPS (CRY-01), `no-store` |
| Where it is stored | `quotes.score_applied` (ya existía); `underwriting_rejections` no lo guarda: se respeta CTL-03 de HU-WEB-07, decidido por el usuario el 2026-11-15 (CTL-08) |
| Copies, caches and backups | Respaldos de RDS |
| How long it is kept | Mientras exista la decisión (RISK-01) |
| How access is audited | `DECISION_VIEWED` sin el puntaje |
| How it is deleted or anonymized | Igual que DATA-01: oculto tras la revocación, sin purga (RISK-01) |
| What happens if it leaks | Revela el perfil crediticio del asegurado: impacto alto |
| Transfers to third parties | Ninguna |

### DATA-03 · Entradas y salida de la decisión

| Question | Answer |
|---|---|
| What it contains | Suma asegurada, riesgo de ocupación, versión de reglas, estado y tiers con desglose de la cotización; monto solicitado, regla, motivo, monto máximo y versión de reglas del rechazo |
| Why it is needed | AC-01 a AC-05 |
| Who can access it | El analista de Solventa; con el consentimiento revocado, solo el nombre de cada plan y los de sus coberturas, sin entradas, primas, desglose, límites ni deducibles (RISK-08, RISK-11) |
| Where it is captured | `create_quote` y `record_rejection` (este solo agrega la versión de reglas) |
| How it is transmitted | HTTPS (CRY-01), `no-store` |
| Where it is stored | `quotes` y `underwriting_rejections` |
| Copies, caches and backups | Respaldos de RDS; señales del componente en el portal |
| How long it is kept | Mientras exista la decisión (RISK-01) |
| How access is audited | `DECISION_VIEWED` y `DECISION_REPLAYED` |
| How it is deleted or anonymized | No se borra: es el registro de la decisión |
| What happens if it leaks | Sin el documento, poco identificable; con él, revela la suma del crédito: impacto medio |
| Transfers to third parties | Ninguna |

### DATA-04 · Estado del consentimiento

| Question | Answer |
|---|---|
| What it contains | GRANTED o REVOKED por consentimiento |
| Why it is needed | AC-07 |
| Who can access it | El backend; el analista de Solventa solo ve si la decisión quedó sin consentimiento |
| Where it is captured | Registro del consentimiento con OTP y revocación desde «Auditoría» |
| How it is transmitted | HTTPS (CRY-01) |
| Where it is stored | `consents.status` |
| Copies, caches and backups | Copia en memoria del servicio de perfil por réplica (ya no vale si la base dice otra cosa); respaldos de RDS |
| How long it is kept | Mientras exista el consentimiento |
| How access is audited | Evento `CONSENT_REVOKED_BY_OPERATOR` con `operator_ref` y el número de consentimientos revocados, sin el documento |
| How it is deleted or anonymized | No se borra: la revocación es un cambio de estado; no guarda fecha ni autor en la fila, solo en el evento del registro (RISK-04) |
| What happens if it leaks | Revela que un documento tuvo o revocó un consentimiento: impacto bajo |
| Transfers to third parties | Ninguna |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Analista de Solventa | analyst sin socio | Autenticado con TOTP | Consultar y recalcular decisiones de todos los canales y registrar revocaciones |
| Analista de un socio | analyst con `partner_id` | Autenticado con TOTP | Nada de esta pantalla: 403 |
| Asesor | advisor | Autenticado | Cotizar con un consentimiento vigente; nada de la auditoría: 403 |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| Portal → backend | Navegador del analista e internet | Token, rol `analyst`, usuario activo sin socio (leído de la base), formato del id y del documento |
| Backend → PostgreSQL | Aplicación y base | Consultas parametrizadas; el disparador rechaza UPDATE y DELETE en `quotes` y `underwriting_rejections`; el rechazo no guarda puntaje ni fecha de nacimiento |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Backend | Portal | DATA-01 enmascarado, DATA-02, DATA-03 | HTTPS | CRY-01, CTL-01, CTL-02, CTL-04 |
| Portal | Backend | Documento para revocar (DATA-01) en el cuerpo | HTTPS | CRY-01, CTL-01, CTL-06 |
| Backend | Registros (`solventa.audit`) | Id de la decisión, `operator_ref`, conteos | Registro de la aplicación | CTL-05 |

## 6. Third-party services

N/A: el recálculo de una cotización usa su puntaje guardado y no consulta el buró ni Datos Abiertos; un rechazo no se recalcula.

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| GET /experience/decisions/{id} | Autenticado (analyst de Solventa) | `id` (`QUO-` o `UWR-` más 12 hexadecimales en mayúsculas) |
| POST /experience/decisions/{id}/replay | Autenticado (analyst de Solventa) | `id` |
| POST /experience/consents/revoke | Autenticado (analyst de Solventa) | `document_number` (`^[A-Za-z0-9]{4,20}$`) |
| POST /experience/quotes y /quote-edge/quotes | Autenticado (advisor o partner) | `consent_id` revocado o de otro titular |
| Pantalla «Auditoría» | Navegador del analista | Identificador y documento |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | E | DATA-01, DATA-02, DATA-03, DATA-04 | El analista de un socio o un asesor consulta decisiones de otros o revoca consentimientos | Analista de socio, asesor | MEDIUM | HIGH | HIGH | CTL-01, CTL-05 |
| THR-02 | I | DATA-01, DATA-02 | El documento, la fecha o el puntaje quedan expuestos en la pantalla, la caché, la URL o los registros, o se guardan donde no hacen falta | Atacante con acceso al navegador, a los registros o a la base | MEDIUM | HIGH | HIGH | CTL-02, CTL-04, CTL-05, CTL-08 |
| THR-03 | T | DATA-03 | Se altera o borra una cotización para que la decisión parezca otra | Operador con acceso a la base, código defectuoso | LOW | HIGH | MEDIUM | CTL-03 |
| THR-04 | E | DATA-04 | Se sigue cotizando con un consentimiento revocado, por su id o por la copia en memoria | Asesor | MEDIUM | HIGH | HIGH | CTL-04 |
| THR-05 | T | API | Inyección o recorrido con el id o el documento | Analista de Solventa | LOW | MEDIUM | LOW | CTL-06 |
| THR-06 | S | DATA-02, DATA-04 | Se perfila un documento ante el buró con el `consent_id` vigente de otro titular, o una cotización vieja con un consentimiento ajeno sigue mostrando los datos de un titular que revocó | Asesor, socio | MEDIUM | HIGH | HIGH | CTL-04 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | `solventa_operator`: rol `analyst`, usuario activo y `partner_id` nulo leído de la base en cada pedido; 403 genérico; la pestaña y `solventaGuard` solo ocultan | API | EXISTING | THR-01 | TC-05, TC-09 | T-04, T-05 | API5:2023, ASVS V4.1 | Reutilizado de HOTFIX-gestion-de-socios |
| CTL-02 | El detalle enmascara el documento a los cuatro últimos; el detalle, el recálculo y la revocación responden `no-store`, también en sus errores 404 y 409; la revocación lleva el documento en el cuerpo; el portal no guarda nada en storage | DATA | EXISTING | THR-02, DATA-01 | TC-01, TC-07, TC-09 | T-02, T-05 | API3:2023, CWE-359, CWE-598 | Nuevo en este cambio (implementado y probado); el validador solo admite EXISTING, MISSING o NOT APPLICABLE |
| CTL-03 | `quotes` y `underwriting_rejections` de solo inserción por disparador, creado con la tabla y por la migración `0005`; el recálculo usa las mismas funciones puras que la cotización | DATA | EXISTING | THR-03 | TC-02, TC-03, TC-06 | T-01, T-02 | ASVS V7.3, CWE-345 | Nuevo en este cambio (implementado y probado) |
| CTL-04 | La revocación pasa a REVOKED todos los consentimientos GRANTED del documento con `FOR UPDATE`; `create_quote` rechaza en los dos canales, antes de consultar el buró y sin guardar nada, un `consent_id` inexistente o de otro documento (422 «El consentimiento no es válido para este cliente.», el mismo mensaje para no revelar si el `consent_id` existe) o revocado (422); la copia en memoria no vale si la base no lo tiene GRANTED; una decisión cuenta como revocada si su `consent_id` está revocado o si su documento tiene revocados y ninguno vigente; entonces el detalle de una cotización omite todas las entradas (documento, fecha, puntaje, suma asegurada y riesgo de ocupación), las primas, el desglose y el límite y el deducible de las coberturas (deja el nombre de cada plan y el id y el nombre de sus coberturas) con `replayable: false` y, en un rechazo, la regla, el motivo, el monto máximo aprobable y el monto solicitado, con `consent_revoked: true`, el recálculo responde 409 «El consentimiento del titular fue revocado: no se recalcula con sus datos personales.» y el portal muestra una sola nota «Se omiten los datos personales y los montos derivados de ellos.», los planes con los nombres de sus coberturas y ese texto sin «Recalcular», sin «Primas por plan» ni tablas de desglose | DATA | EXISTING | THR-04, THR-06, THR-02, DATA-01, DATA-02, DATA-04 | TC-07, TC-13, TC-09, TC-14, TC-15 | T-03, T-08, T-09, T-10, T-11, T-12 | CWE-285, CWE-359, CWE-639 | Nuevo en este cambio (implementado y probado); ampliado tras la revisión independiente (B-1, B-2), la segunda (N-B1, `consent_id` inexistente) y la decisión del usuario del 2026-11-15 sobre RISK-11 (sin primas ni montos) |
| CTL-05 | Eventos `DECISION_VIEWED`, `DECISION_REPLAYED` y `CONSENT_REVOKED_BY_OPERATOR` en `solventa.audit` con `operator_ref` (12 hex del sha256 del usuario), sin documento, fecha, puntaje ni correo | BACKEND | EXISTING | THR-01, THR-02 | TC-08 | T-04 | ASVS V7.1 | Nuevo en este cambio (implementado y probado) |
| CTL-07 | En el portal la pestaña «Auditoría» y `solventaGuard` solo ocultan; la pantalla guarda la decisión y el documento solo en señales del componente, nunca en `sessionStorage` ni `localStorage`, muestra el documento enmascarado en la confirmación y borra el campo al terminar; textos con interpolación de Angular, sin `innerHTML` | WEB | EXISTING | THR-02, THR-01 | TC-09 | T-05 | ASVS V8.2, CWE-922, CWE-79 | — |
| CTL-08 | Minimización en `underwriting_rejections`: solo agrega `rules_version`, sin puntaje del buró ni fecha de nacimiento (se respeta CTL-03 de HU-WEB-07, decidido por el usuario el 2026-11-15); el detalle del rechazo devuelve solo lo guardado y su recálculo responde 409 «Los rechazos no guardan el puntaje del buró (HU-WEB-07), así que no se pueden recalcular.»; el portal muestra ese texto en lugar de «Recalcular» | DATA | EXISTING | THR-02, DATA-01, DATA-02 | TC-01, TC-12 | T-01, T-02, T-05, T-07 | CWE-359, ASVS V8.3 | Nuevo en este cambio (implementado y probado); mantiene la decisión de minimización de HU-WEB-07 |
| CTL-06 | El id se valida con `QUO-` o `UWR-` más 12 hexadecimales en mayúsculas y el documento con `^[A-Za-z0-9]{4,20}$` en el servidor y en el portal; consultas parametrizadas | API | EXISTING | THR-05 | TC-05, TC-09 | T-04, T-05 | ASVS V5.1, CWE-20 | — |

## 10. Cryptography

### CRY-01 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02, DATA-03, DATA-04 |
| Type | TLS |
| Algorithm or service | HTTPS de CloudFront y del ALB (TLS 1.2 o superior) |
| Rationale | Viaja por internet |
| Key management | Certificados de ACM |
| Permissions | Administración de AWS |
| Rotation | La de ACM |
| Recovery | ACM renueva el certificado |
| Failure behavior | Sin TLS no hay conexión |
| Tests | TC-11 |

## 11. Secrets and keys

N/A: sin secretos nuevos; usa la sesión del analista existente.

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-01 | — |
| Secure authentication | NO | — | No cambia |
| Session management | NO | — | No cambia |
| Token expiration | NO | — | No cambia |
| Revocation | NO | — | La revocación de este cambio es del consentimiento, no de la sesión (CTL-04) |
| Server-side permission checks | YES | CTL-01 | — |
| Per-resource access (ownership) | YES | CTL-01 | Solo Solventa audita; el analista de un socio no ve ni las de su socio |
| Roles | YES | CTL-01 | — |
| Privilege separation | YES | CTL-01 | Analista de socio frente a analista de Solventa |
| Brute-force protection | NO | — | Rol interno con TOTP; ids aleatorios de 48 bits |
| Rate limiting | NO | — | Endpoints de un rol interno con TOTP |
| One-time code (OTP) handling | NO | — | No aplica |
| User enumeration prevention | NO | — | El analista de Solventa ya ve todas las decisiones |
| CSRF protection | NO | — | Token Bearer, sin cookies |

## 13. Data isolation and multi-tenancy

| Check | Applies | Controls | Tests | Rationale |
|---|---|---|---|---|
| Tenant isolation | YES | CTL-01 | TC-05 | Un analista de socio no audita nada |
| Server-side authorization | YES | CTL-01 | TC-05 | — |
| Horizontal access | YES | CTL-01 | TC-05 | — |
| Vertical access | YES | CTL-01 | TC-05 | Asesor y analista de socio: 403 |
| Resource identifiers | YES | CTL-06 | TC-05 | Formato validado; inexistente 404 |
| Queries and filters | YES | CTL-01, CTL-06 | TC-05 | Búsqueda por clave primaria, parametrizada |
| Shared caches | YES | CTL-02 | TC-01, TC-07 | `no-store` en el detalle, el recálculo y la revocación |
| Storage and logs | YES | CTL-05 | TC-08 | Eventos sin datos personales |
| Negative cross-tenant tests | YES | CTL-01 | TC-05 | — |

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | SQLAlchemy, asyncpg, Alembic y Angular | Sin cambio de versión | backend y web | Consultas, disparador, migración y pantalla | HIGH | Revisión manual 2026-11-15 | Sin cambio; ya en los locks | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | `quotes` y `underwriting_rejections` son inmutables y no tienen purga: el documento (y, en las cotizaciones, la fecha de nacimiento y el puntaje) no se pueden borrar desde el sistema, ni tras la revocación del consentimiento; solo se ocultan en la auditoría | THR-02 | MEDIUM | MEDIUM | MEDIUM | MITIGATE | Medio: la auditoría los oculta y solo Solventa los ve; propuesta: definir con el responsable de datos la retención y un procedimiento de purga o seudonimización autorizado que deshabilite el disparador en una transacción auditada | OPEN |
| RISK-03 | La cotización calcula la edad con `date.today()` del servidor (hora local) y no guarda la edad ni la fecha usada; el recálculo la reconstruye con `created_at` pasado a la hora local del servidor. Puede diferir si el servidor cambió de zona horaria entre la cotización y el recálculo, o en la ventana de la medianoche (la fecha se toma antes de consultar el buró y `created_at` después, hasta el tiempo de espera del buró), y solo si ese día es el cumpleaños; aun así el resultado cambia solo si la edad cruza un límite de `AGE_BANDS` (30, 40 o 50 años) o de las reglas de suscripción: entonces el recálculo dice «No coincide» sin que cambie una regla | THR-03 | LOW | LOW | LOW | MITIGATE | Bajo: el recálculo ya usa la misma fecha local que la cotización y en ECS el servidor corre en UTC; propuesta: guardar en la cotización la fecha o la edad usada (requiere una columna, fuera de este cambio) | OPEN |
| RISK-04 | La fila del consentimiento no guarda cuándo ni quién lo revocó: solo el evento `CONSENT_REVOKED_BY_OPERATOR`, sin documento, lo registra, así que esa constancia depende de que se conserven los registros de la aplicación (retención de CloudWatch); si se pierden o vencen, solo queda el estado REVOKED | THR-01 | LOW | MEDIUM | LOW | MITIGATE | Bajo: el evento tiene la hora y el `operator_ref`; propuesta: columnas `revoked_at` y `revoked_by_ref` en `consents` o una retención de registros acordada con el responsable de datos | OPEN |
| RISK-05 | `operator_ref` es un seudónimo reversible por diccionario, como en HOTFIX-gestion-de-socios | THR-01 | LOW | LOW | LOW | MITIGATE | Bajo: los analistas son pocos y los eventos los leen administradores; propuesta: HMAC con una llave del servidor | OPEN |
| RISK-06 | El canal de socios cotiza sin consentimiento cuando no envía `consent_id`: tras la revocación, un socio puede seguir cotizando ese documento | THR-04 | MEDIUM | MEDIUM | MEDIUM | MITIGATE | Medio: comportamiento previo del canal; con un `consent_id` revocado ya responde 422; propuesta: exigir consentimiento vigente también al socio o un acuerdo contractual que lo cubra | OPEN |
| RISK-07 | El analista escribe el documento a mano y podría revocar el consentimiento de otro titular | THR-04 | LOW | MEDIUM | LOW | MITIGATE | Bajo: la confirmación muestra el documento enmascarado; el titular puede otorgar uno nuevo con OTP; propuesta: revocar desde la decisión abierta o con un pedido identificado | OPEN |
| RISK-08 | B-1 de la revisión independiente: con el consentimiento revocado, el detalle copiaba los tiers con su desglose, cuyas etiquetas dicen la edad («Ajuste por edad (36 años)») y cuyo multiplicador `payment_history` delata la franja del puntaje (`SCORE_BANDS` es pública), y el recálculo seguía usando esos datos | THR-02, THR-06 | MEDIUM | HIGH | HIGH | MITIGATE | Corregido: sin `breakdown` con el consentimiento revocado, recálculo 409 y el portal sin desglose ni «Recalcular» (CTL-04) | MITIGATED |
| RISK-09 | B-2 de la revisión independiente: `create_quote` aceptaba el `consent_id` de otro titular y consultaba el buró con él; y una cotización con un consentimiento ajeno vigente seguía mostrando los datos tras revocar los del documento | THR-06 | MEDIUM | HIGH | HIGH | MITIGATE | Corregido: 422 «El consentimiento no es válido para este cliente.» antes de consultar el buró en los dos canales y la decisión cuenta como revocada si su documento solo tiene revocados (CTL-04) | MITIGATED |
| RISK-10 | TOCTOU de la revocación: una cotización que ya leyó el consentimiento GRANTED antes de que la revocación lo cambie (la lectura no toma `FOR UPDATE`) puede terminar y guardarse después de la revocación | THR-04 | LOW | LOW | LOW | MITIGATE | Bajo: la ventana es de milisegundos a lo que tarde el buró; la cotización queda con `consent_id` revocado, así que la auditoría la oculta; la emisión (`PolicyService.create`) no revisa el consentimiento, así que esa cotización todavía se puede emitir; propuesta: leer el consentimiento con `FOR SHARE` en la transacción de la cotización o volver a comprobarlo antes de guardar, y validar el consentimiento vigente al emitir | OPEN |
| RISK-11 | Con el consentimiento revocado el detalle sigue mostrando la prima de cada tier junto con la suma asegurada y el riesgo de ocupación; como la base, la ocupación y el plan son públicos, la prima permite despejar el producto de los multiplicadores de edad y de historial de pago, y las 16 combinaciones de `AGE_BANDS` y `SCORE_BANDS` dan productos distintos: la franja de edad y la del puntaje se pueden inferir | THR-02 | LOW | HIGH | MEDIUM | MITIGATE | Corregido en código por decisión del usuario del 2026-11-15 (opción a): con el consentimiento revocado, una cotización solo devuelve sus planes y el id y el nombre de sus coberturas, sin entradas, primas, desglose, límites ni deducibles, con `replayable: false`, y el portal solo una nota, los planes y los nombres de las coberturas, sin «Primas por plan» (CTL-04, T-12) | MITIGATED |
| RISK-12 | N-B1 de la segunda revisión independiente: con el consentimiento revocado, el detalle de un rechazo seguía devolviendo la regla, el motivo, el monto solicitado y el monto máximo aprobable, que sale de `SCORE_LIMITS` o `AGE_LIMITS` uno a uno con la franja del buró o de la edad | THR-02 | MEDIUM | HIGH | HIGH | MITIGATE | Corregido: el rechazo con el consentimiento revocado solo lleva identificador, tipo, estado, versión, fecha, `consent_revoked` y `replayable: false`, y el portal solo una nota (CTL-04) | MITIGATED |
| RISK-13 | Aun con el consentimiento revocado, que exista un rechazo indica que la suma pedida superó algún cupo de `SCORE_LIMITS` o `AGE_LIMITS`: quien conozca por fuera la suma pedida puede acotar la franja del buró o la edad del titular | THR-02 | LOW | LOW | LOW | MITIGATE | Bajo: inherente a conservar el registro del rechazo; el detalle ya omite la regla, el motivo y los montos y solo lo ve el analista de Solventa; propuesta: decidir con el responsable de datos la retención o seudonimización de los rechazos (RISK-01). Sin aceptación de riesgo firmada | OPEN |

## 16. Pending decisions

N/A: sin decisiones abiertas; las propuestas de los riesgos quedan para el responsable de datos. Decisión tomada:
el 2026-11-15 el usuario eligió la opción (a) para RISK-11: con el consentimiento revocado, el detalle de una
cotización no devuelve primas, entradas ni montos de las coberturas (CTL-04).

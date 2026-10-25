# Security and privacy assessment · HU-WEB-07

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Registro de rechazo: documento del solicitante, regla, motivo, suma pedida, suma máxima aprobable y fecha | CONFIDENTIAL | YES | NO | Auditar por qué no se aprobó una solicitud (HA-17) |

## 2. Data lifecycle

### DATA-01 · Registro de rechazos

| Question | Answer |
|---|---|
| What it contains | Documento, regla (UW-01 o UW-02), motivo, suma pedida, suma máxima aprobable y fecha; no el puntaje del buró |
| Why it is needed | Demostrar por qué el sistema no aprobó una solicitud y con qué regla |
| Who can access it | El backend y quien administre la base; ningún endpoint lo expone en esta historia |
| Where it is captured | Al cotizar desde el portal del asesor |
| How it is transmitted | Dentro de la red privada de AWS hacia RDS (HOTFIX de transporte y borde) |
| Where it is stored | Tabla `underwriting_rejections` en PostgreSQL |
| Copies, caches and backups | Respaldos de RDS |
| How long it is kept | Lo que defina la política de retención de suscripción (DEC-02); no se borra desde la aplicación |
| How access is audited | Acceso a la base con credenciales de RDS; el registro no se puede cambiar |
| How it is deleted or anonymized | Solo por un proceso de retención aprobado que desactive el disparador de forma explícita (DEC-02) |
| What happens if it leaks | Se sabe que una persona pidió un seguro por cierta suma y no se aprobó; impacto moderado |
| Transfers to third parties | Ninguna |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Asesor | advisor | Autenticado | Cotizar y recotizar con la suma sugerida |
| Persona con acceso a la base | Operación | Interno | Leer los rechazos; no cambiarlos |
| Atacante externo | Ninguno | No confiable | Llamar al endpoint sin token (401) |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| API | Portal y backend | Token y rol advisor; formato de la solicitud (Pydantic) |
| Base de datos | Backend y PostgreSQL | El disparador impide cambiar o borrar rechazos |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Portal | Backend | Datos de la solicitud | HTTPS por CloudFront | CRY-01 |
| Backend | PostgreSQL | DATA-01 | Red privada de AWS | CTL-02, CTL-03 |
| Backend | Portal | Motivo y sumas | HTTPS por CloudFront | CTL-04 |

## 6. Third-party services

N/A: el buró ya se usa en la tarifa (HU-WEB-04) y esta historia no cambia esa integración.

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| POST /api/v1/experience/quotes | Autenticada (advisor) | Suma, fecha de nacimiento, documento |
| POST /api/v1/experience/policies | Autenticada (advisor) | El quote_id |
| Tabla underwriting_rejections | Interna | Sentencias SQL de quien tenga acceso |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | E | Cotización | Emitir con el identificador de un rechazo | Asesor malicioso | MEDIUM | HIGH | HIGH | CTL-01 |
| THR-02 | T, R | DATA-01 | Cambiar o borrar un rechazo para ocultarlo | Persona con acceso a la base | LOW | HIGH | MEDIUM | CTL-02 |
| THR-03 | T | Reglas | Probar sumas o fechas hasta pasar el cupo | Asesor malicioso | MEDIUM | MEDIUM | MEDIUM | CTL-02 |
| THR-04 | I | DATA-01 | Exponer el puntaje del buró o códigos internos en el portal o en el registro | Cualquiera que vea el portal | LOW | MEDIUM | LOW | CTL-03, CTL-04 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | El rechazo no se guarda en `quotes`; su identificador no sirve para emitir | BACKEND | MISSING | THR-01 | TC-04 | T-01 | CWE-841 | — |
| CTL-02 | Disparador de PostgreSQL que impide UPDATE y DELETE en `underwriting_rejections` | DATA | MISSING | THR-02, THR-03 | TC-05 | T-02 | CWE-284, OWASP ASVS V7 | — |
| CTL-03 | El registro no guarda el puntaje del buró | DATA | MISSING | THR-04, DATA-01 | TC-05 | T-02 | CWE-359 | — |
| CTL-04 | El portal muestra motivo y suma, nunca la regla interna ni el puntaje | WEB | MISSING | THR-04 | TC-06 | T-03 | CWE-209 | — |

## 10. Cryptography

### CRY-01 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | TLS |
| Algorithm or service | HTTPS de CloudFront (TLS 1.2 o 1.3) y red privada hasta el balanceador y RDS, sin cambios |
| Rationale | La historia no agrega canales; usa los de HOTFIX de transporte y borde |
| Key management | Certificados administrados por AWS |
| Permissions | Solo el backend y la administración de AWS |
| Rotation | La de AWS |
| Recovery | No aplica |
| Failure behavior | Sin TLS no hay conexión |
| Tests | TC-02 |

## 11. Secrets and keys

N/A: sin secretos nuevos.

## 12. Authentication, authorization and sessions

N/A: el endpoint ya exige el rol advisor y no cambia.

## 13. Data isolation and multi-tenancy

N/A: canal del asesor; el canal de socios queda fuera.

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | sqlalchemy | 2.0.x (sin cambio) | backend | Tabla y disparador DDL | HIGH | Revisión manual 2026-10-25 | Sin cambio de versión | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | Un asesor puede probar sumas por debajo del cupo hasta encontrarlo; los cupos no son secretos de negocio y cada intento queda registrado | THR-03 | MEDIUM | LOW | LOW | ACCEPT | Bajo: la suma máxima aprobable se muestra a propósito | ACCEPTED |
| RISK-02 | Los cupos propuestos no están validados por el negocio | THR-03 | MEDIUM | MEDIUM | MEDIUM | MITIGATE | Se validan en DEC-01 antes de producción | OPEN |

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Cupos de aprobación directa | Los propuestos en AC-05 u otros del área de suscripción | Validar con negocio antes de producción; el código los tiene en un solo archivo | Líder de producto | NO | PENDING |
| DEC-02 | Retención de los registros de rechazo | Plazo legal de la aseguradora | Definirla con el área legal; hoy no se borran | Responsable de datos | NO | PENDING |

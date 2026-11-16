# Security and privacy assessment · HOTFIX

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Páginas de los documentos del siniestro (facturas, fórmulas, historias clínicas, denuncias) | RESTRICTED | YES | YES | Soportar la reclamación ante el analista |
| DATA-02 | Metadatos del envío: `upload_id`, posición, sha256, tipo, tamaño y hora, ligados al radicado | CONFIDENTIAL | YES | NO | Idempotencia, integridad y orden de las páginas en el expediente |

## 2. Data lifecycle

### DATA-01 · Páginas de los documentos

| Question | Answer |
|---|---|
| What it contains | Imágenes JPEG (o PDF desde otros clientes) de documentos que pueden traer nombres, números de documento, diagnósticos y valores de terceros |
| Why it is needed | AC-04, AC-09: el analista decide el siniestro con esos soportes |
| Who can access it | El asegurado que las escanea; el backend; el analista de Solventa y el de un socio solo para siniestros de sus pólizas (CTL-04) |
| Where it is captured | Escáner de ML Kit (UI de Google en el teléfono) o la cámara de la app |
| How it is transmitted | Multipart por HTTPS (CRY-01); el portal las abre con enlaces prefirmados HTTPS de 15 minutos |
| Where it is stored | S3 `siniestros/{claim}/documentos/{upload}/{page}` cifrado con la llave KMS del bucket (CRY-02); en la app solo en memoria |
| Copies, caches and backups | ML Kit deja cada página en un archivo de la caché de la app hasta que `ScannedPageReader` la lee y la borra (RISK-02); la caché no entra en respaldos (`allowBackup=false` y `dataExtractionRules` excluyen todo); Starlette puede escribir partes de más de 1 MB a un archivo temporal del contenedor durante la petición; versiones del bucket si están activas; el navegador del analista puede guardar en caché lo que abre |
| How long it is kept | Mientras exista el siniestro, como las fotos del reporte; sin purga definida (RISK-03) |
| How access is audited | El evento `DOCUMENTS_ADDED` en el historial inmutable; las consultas del expediente, como hoy; CloudTrail de S3 según la configuración del bucket |
| How it is deleted or anonymized | Sin borrado en este cambio (RISK-03); en la app los bytes se ponen en cero al quitar, enviar o salir |
| What happens if it leaks | Datos de salud y financieros de asegurados y terceros: impacto alto |
| Transfers to third parties | Ninguna desde el backend. En el teléfono las procesa ML Kit; según sus términos el escaneo es local, sin verificar aquí (RISK-01) |

### DATA-02 · Metadatos del envío

| Question | Answer |
|---|---|
| What it contains | `upload_id` aleatorio, posición, sha256, tipo, tamaño, hora y radicado |
| Why it is needed | AC-05 idempotencia, AC-07 integridad y el orden en el portal |
| Who can access it | Backend y analistas autorizados del expediente |
| Where it is captured | La app genera el `upload_id` y calcula la huella |
| How it is transmitted | HTTPS dentro del envío |
| Where it is stored | `claim_documents`; el outbox y `claim_events` guardan solo radicado, `upload_id` y número de páginas |
| Copies, caches and backups | Respaldos de RDS; mensajes de SQS hasta procesarse |
| How long it is kept | Como el siniestro |
| How access is audited | Historial del siniestro |
| How it is deleted or anonymized | Sin borrado en este cambio (RISK-03) |
| What happens if it leaks | Revela que un siniestro tiene documentos y cuántos: impacto bajo |
| Transfers to third parties | Ninguna |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Asegurado aprobado | insured, APPROVED | Autenticado | Agregar páginas a sus siniestros abiertos |
| Asegurado en verificación | insured, PENDING | Autenticado | Nada en esta ruta (403) |
| Analista de Solventa | analyst sin socio | Interno | Ver los documentos de cualquier siniestro |
| Analista de un socio | analyst con socio | Socio | Ver los documentos de siniestros de pólizas de su socio |
| Google ML Kit | Biblioteca en el teléfono | Tercero | Presentar la UI del escaneo y entregar las páginas |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| ML Kit → app | Biblioteca de Google y la app | Las páginas se leen, se recodifican a JPEG ≤ 2 MB sin EXIF y su archivo se borra |
| App → backend | Teléfono e internet | Token de acceso, identidad aprobada, dueño y estado del siniestro, manifiesto estricto, firma, tamaño, número de páginas, huella |
| Backend → S3 | Backend y AWS | Claves construidas en el servidor; rol limitado a `siniestros/*` |
| Portal → S3 | Navegador y AWS | Enlace prefirmado de una clave, solo lectura, 15 minutos |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| ML Kit | App | DATA-01 | Archivo en la caché de la app | CTL-06 |
| App | Backend | DATA-01, DATA-02 | HTTPS | CRY-01, CTL-01, CTL-02, CTL-03 |
| Backend | S3 | DATA-01 | HTTPS de AWS | CRY-02 |
| Backend | Outbox y SQS | DATA-02 (solo identificadores) | Base y HTTPS de AWS | CTL-05 |
| S3 | Navegador del analista | DATA-01 | HTTPS con enlace prefirmado | CTL-04 |

## 6. Third-party services

| Service | Use | Data shared | Location | Controls |
|---|---|---|---|---|
| Google ML Kit Document Scanner (Google Play services) | Escanear con recorte y filtros en el teléfono | DATA-01 en el teléfono; telemetría de uso de ML Kit por `datatransport` | En el teléfono; la telemetría va a Google (UNKNOWN su contenido exacto) | CTL-06, CTL-07 |

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| POST /insured/claims/{claim_number}/documents | Autenticada (insured) | Radicado, manifiesto JSON, hasta 10 archivos y sus nombres de campo |
| GET /experience/claims/{claim_number} | Autenticada (analyst) | Radicado |
| Pantalla de documentos de la app | Teléfono del asegurado | Lo que entregue el escáner o la cámara |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | E | Expediente del siniestro | Agregar documentos a un siniestro ajeno, decidido o desde una cuenta sin identidad aprobada | Asegurado malicioso | MEDIUM | HIGH | HIGH | CTL-01 |
| THR-02 | D | Backend y bucket | Archivos enormes, ejecutables, cientos de páginas o de partes del multipart | Persona maliciosa | MEDIUM | MEDIUM | MEDIUM | CTL-02 |
| THR-03 | T | DATA-01 | Página cambiada después del escaneo, o un reintento que duplica | Atacante o error de red | LOW | MEDIUM | LOW | CTL-03 |
| THR-04 | I | DATA-01 | Un analista de un socio ve documentos de otro socio, o un enlace sirve demasiado tiempo | Analista de un socio | LOW | HIGH | MEDIUM | CTL-04 |
| THR-05 | I | DATA-01 | Páginas recuperables del teléfono (disco, respaldo, recientes, registros) | Ladrón o malware | LOW | HIGH | MEDIUM | CTL-06 |
| THR-06 | I | DATA-01, DATA-02 | Contenido o datos del asegurado en registros, outbox o historial | Interno | LOW | MEDIUM | LOW | CTL-05 |
| THR-07 | I | DATA-01 | La dependencia de Google envía contenido o tiene vulnerabilidades | Proveedor | LOW | HIGH | MEDIUM | CTL-07 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | `insured_account` (sesión de acceso con identidad APPROVED); el siniestro se busca por radicado y `insured_document` del token con `FOR UPDATE`; ajeno o inexistente 404, decidido 409; un `upload_id` de otra persona 404 | API | EXISTING | THR-01 | TC-03, TC-07, TC-08 | T-01 | API1:2023, CWE-639 | — |
| CTL-02 | Manifiesto Pydantic con `extra="forbid"`, UUID y huellas hexadecimales; de 1 a 10 páginas consecutivas; `request.form(max_files=10, max_fields=1)`; lectura con tope de 5 MB; firma JPEG o `%PDF-`; 50 páginas por siniestro | BACKEND | EXISTING | THR-02 | TC-04, TC-05 | T-01 | CWE-20, CWE-434, CWE-770 | — |
| CTL-03 | SHA-256 de cada página contra el manifiesto; índice único `(upload_id, position)`, repetición 200 con la misma constancia y carrera resuelta al ganador | BACKEND | EXISTING | THR-03 | TC-01, TC-02, TC-06 | T-01 | CWE-345 | — |
| CTL-04 | Documentos dentro de `ClaimService.detail`, que filtra por el socio del analista; enlaces prefirmados de solo lectura de 900 s y `Cache-Control: no-store` en el expediente; el portal abre con `noopener noreferrer` | API | EXISTING | THR-04 | TC-09, TC-10 | T-02 | API1:2023, CWE-284 | — |
| CTL-05 | Outbox e historial con radicado, `upload_id` y número de páginas; sin registros con contenido ni documento del asegurado | BACKEND | EXISTING | THR-06 | TC-01, TC-13 | T-01 | CWE-532 | — |
| CTL-06 | Páginas solo en memoria en la app (sin disco, registros ni estado guardado) y en cero al quitar, enviar o salir; archivos de ML Kit leídos y borrados siempre, solo dentro de la app; recodificadas sin EXIF; ventana segura; sin galería; sin cola en disco | MOBILE | EXISTING | THR-05 | TC-11, TC-12 | T-03, T-04 | MASVS-STORAGE-2, MASVS-PLATFORM-3 | — |
| CTL-07 | Dependencia evaluada con OSV.dev y su POM; detrás del puerto `DocumentScanner` con respaldo de cámara; componentes de ML Kit no exportados y sin permisos nuevos | MOBILE | EXISTING | THR-07 | TC-14 | T-03 | MASVS-CODE-3 | — |
| CTL-08 | El portal muestra los documentos solo como enlaces con `rel="noopener noreferrer"` e interpolación de Angular, sin `innerHTML`; el token del portal nunca va a S3 porque el enlace prefirmado no lleva credenciales del usuario | WEB | EXISTING | THR-04 | TC-10 | T-02 | CWE-79, CWE-1022 | — |

## 10. Cryptography

### CRY-01 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02 |
| Type | TLS |
| Algorithm or service | HTTPS de CloudFront y del ALB (TLS 1.2 o superior); la app exige HTTPS fuera de desarrollo; enlaces prefirmados de S3 con HTTPS |
| Rationale | Viajan por internet |
| Key management | Certificados de ACM |
| Permissions | Administración de AWS |
| Rotation | La de ACM |
| Recovery | ACM renueva el certificado |
| Failure behavior | Sin TLS no hay conexión; `S3DocumentStorage` rechaza un enlace que no sea HTTPS en AWS |
| Tests | TC-09 |

### CRY-02 · Documentos en reposo

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | AT_REST |
| Algorithm or service | SSE-KMS del bucket de documentos con la llave de almacenamiento |
| Rationale | Datos sensibles de salud y financieros |
| Key management | KMS gestionado por Terraform (`storage_kms_key_arn`), sin cambio |
| Permissions | El rol de la tarea con `kms:GenerateDataKey` y `kms:Decrypt` |
| Rotation | Rotación automática de KMS |
| Recovery | Sin la llave no se leen; el expediente lista sin enlaces |
| Failure behavior | Si S3 o KMS fallan, la subida responde 503 sin registrar nada y se puede reintentar con el mismo `upload_id` |
| Tests | TC-08 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| Credenciales temporales del rol de la tarea (sin cambio; firman los enlaces de los documentos) | Metadatos de ECS, sin valor en el código | Backend | DEV, STAGING, PROD | Automática de STS | Quitar la política del rol | CloudTrail y el gate de secretos |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-01 | El documento sale del token, nunca del cliente |
| Secure authentication | NO | — | Sin cambio |
| Session management | NO | — | Usa la sesión existente y su renovación |
| Token expiration | NO | — | Sin cambio |
| Revocation | NO | — | Sin cambio |
| Server-side permission checks | YES | CTL-01, CTL-04 | — |
| Per-resource access (ownership) | YES | CTL-01, CTL-04 | Siniestro del titular; expediente del socio |
| Roles | YES | CTL-01 | Solo insured sube; solo analyst ve |
| Privilege separation | YES | CTL-01 | El token de solo reporte no sirve aquí |
| Brute-force protection | NO | — | Radicados no secretos; el dueño se verifica en cada petición |
| Rate limiting | NO | — | Sin límite por usuario; el tope por siniestro acota el almacenamiento (RISK-04) |
| One-time code (OTP) handling | NO | — | No aplica |
| User enumeration prevention | YES | CTL-01 | Ajeno e inexistente responden igual |
| CSRF protection | NO | — | Endpoints con bearer, sin cookies |

## 13. Data isolation and multi-tenancy

| Check | Applies | Controls | Tests | Rationale |
|---|---|---|---|---|
| Tenant isolation | YES | CTL-04 | TC-09 | El expediente filtra por el socio del analista |
| Server-side authorization | YES | CTL-01, CTL-04 | TC-03, TC-09 | — |
| Horizontal access | YES | CTL-01 | TC-03 | Siniestro y envío de otro asegurado |
| Vertical access | YES | CTL-01 | TC-07 | Un analista no sube; una cuenta PENDING tampoco |
| Resource identifiers | YES | CTL-01, CTL-02 | TC-03, TC-05 | Radicado con patrón; `upload_id` UUID; claves de S3 armadas en el servidor |
| Queries and filters | YES | CTL-01, CTL-04 | TC-03, TC-09 | `insured_document` y `partner_id` en cada consulta |
| Shared caches | YES | CTL-04 | TC-09 | `no-store` en el expediente; los enlaces vencen en 15 minutos |
| Storage and logs | YES | CTL-05 | TC-13 | — |
| Negative cross-tenant tests | YES | CTL-04 | TC-09 | El analista del socio no ve el expediente de Solventa |

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | com.google.android.gms:play-services-mlkit-document-scanner | 16.0.0 (última en Google Maven, `lastUpdated` 2025-09-29) | app | Escáner de documentos detrás del puerto `DocumentScanner` | HIGH | OSV.dev 2026-11-15 (paquete y transitivas `mlkit:common` 18.11.0, `play-services-base` 18.5.0, `datatransport` 2.2.6 y 2.3.3); POM de Google Maven | Sin vulnerabilidades conocidas en OSV hoy; licencia ML Kit Terms of Service (propietaria); mantenedor Google; exige Google Play services; la UI del escaneo la presta Google; agrega componentes no exportados y ningún permiso | EVALUATED |
| DEP-02 | CameraX (`androidx.camera`) | Sin cambio de versión (1.5.3) | app | Respaldo sin Play services | MEDIUM | Revisión manual 2026-11-15 | Sin cambio; evaluada en HU-MOV-08 | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | Términos propietarios de ML Kit y telemetría de uso hacia Google por `datatransport`; no se verificó qué envía ni que el contenido no salga del teléfono | THR-07 | LOW | HIGH | MEDIUM | MITIGATE | Medio; propuesta: revisar los términos y la telemetría con el responsable de datos y, si hace falta, desactivarla o volver solo a la cámara | OPEN |
| RISK-02 | ML Kit escribe cada página en claro en la caché de la app hasta que se lee; borrar en memoria flash no es un borrado seguro y un cierre forzado en ese instante deja el archivo | THR-05 | LOW | HIGH | MEDIUM | MITIGATE | Medio; propuesta: limpiar al arrancar la carpeta de la caché del escáner y valorar solo la cámara en teléfonos sin cifrado de almacenamiento | OPEN |
| RISK-03 | Los documentos no tienen retención ni purga definidas, igual que las fotos del reporte | THR-06 | MEDIUM | MEDIUM | MEDIUM | MITIGATE | Medio; propuesta: definir la retención de los expedientes con el responsable de datos | OPEN |
| RISK-04 | Sin límite de envíos por usuario o IP; el tope de 50 páginas por siniestro acota el almacenamiento, no el ancho de banda | THR-02 | LOW | MEDIUM | LOW | MITIGATE | Bajo; propuesta: límite por usuario en el borde | OPEN |
| RISK-05 | El backend no inspecciona el contenido: un PDF con contenido activo llega al navegador del analista | THR-02 | LOW | MEDIUM | LOW | MITIGATE | Bajo; el visor PDF del navegador aísla el contenido; propuesta: antivirus del bucket o aceptar solo JPEG | OPEN |
| RISK-06 | Sin cola sin conexión: si el asegurado sale de la pantalla sin red pierde las páginas y debe escanear de nuevo; es el costo de no guardarlas en el teléfono | THR-05 | MEDIUM | LOW | LOW | MITIGATE | Bajo; DEC-02 | OPEN |

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Mantener ML Kit en producción tras revisar sus términos y telemetría (RISK-01) | ML Kit con respaldo de cámara; solo la cámara de la app | ML Kit tras la revisión; el puerto permite volver a la cámara sin tocar el resto | Responsable de datos y Juan Camilo Peña | NO | PENDING |
| DEC-02 | Envío sin conexión de los documentos | Aviso sin guardar (implementado); cola cifrada como `EncryptedClaimStore` con su propio trabajo en segundo plano | Mantener el aviso hasta que el uso muestre que hace falta la cola | Juan Camilo Peña | NO | PENDING |

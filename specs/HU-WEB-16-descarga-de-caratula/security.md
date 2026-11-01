# Security and privacy assessment · HU-WEB-16

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Carátula en PDF: número de póliza, nombre del titular, documento enmascarado, plan, vigencia, socio, coberturas, suma asegurada y primas | CONFIDENTIAL | YES | NO | Entregar al cliente lo que contrató |
| DATA-02 | Enlace prefirmado de la carátula | CONFIDENTIAL | NO | NO | Descargar el PDF directo de S3 durante 15 minutos |
| DATA-03 | Credenciales temporales del rol de la tarea de ECS | RESTRICTED | NO | NO | Guardar el PDF y firmar el enlace |

## 2. Data lifecycle

### DATA-01 · Carátula en PDF

| Question | Answer |
|---|---|
| What it contains | Número de póliza, nombre del titular, tipo de documento y documento con los últimos 4 dígitos, plan, vigencia, socio, coberturas con suma y deducible, suma asegurada y primas; sin correo, teléfono, beneficiarios ni estado |
| Why it is needed | Soporte de las condiciones contratadas (HU-WEB-16) |
| Who can access it | El backend; el analista y el usuario de un socio con alcance sobre la póliza, a través del enlace |
| Where it is captured | Se genera en el backend con los datos guardados al emitir (HU-WEB-05 y HU-WEB-08) |
| How it is transmitted | Backend → S3 por HTTPS con las credenciales del rol; S3 → navegador por HTTPS con el enlace prefirmado |
| Where it is stored | Bucket de documentos, `caratulas/{número}.pdf`, cifrado con la llave KMS del bucket y versionado; la clave queda en `policies.pdf_s3_key` |
| Copies, caches and backups | Versiones del bucket; el navegador guarda el archivo descargado; la respuesta con el enlace es `no-store` |
| How long it is kept | Lo que dure la póliza más la retención legal que defina la aseguradora (DEC-01) |
| How access is audited | Registros de acceso de la API con el número de póliza; registros de S3 y CloudTrail si se activan en la cuenta |
| How it is deleted or anonymized | Con la póliza, según la retención (DEC-01); el rol del backend no puede borrar objetos |
| What happens if it leaks | Se conoce el nombre del cliente, su plan y lo que paga; el documento va enmascarado y no hay contacto |
| Transfers to third parties | AWS como proveedor de almacenamiento, en la cuenta del proyecto |

### DATA-02 · Enlace prefirmado

| Question | Answer |
|---|---|
| What it contains | URL HTTPS del objeto con la firma SigV4, el vencimiento de 900 s, el identificador de la llave temporal y el token de sesión del rol; nunca el secreto |
| Why it is needed | Que el navegador descargue de S3 sin pasar el archivo por el backend |
| Who can access it | El usuario que la pidió y quien la reciba de él durante 15 minutos |
| Where it is captured | La firma el backend en cada pedido |
| How it is transmitted | Respuesta de la API por HTTPS con `Cache-Control: no-store` |
| Where it is stored | En ningún lado del backend; el navegador la usa para la descarga |
| Copies, caches and backups | Ninguna en el servidor; los registros del backend solo tienen la ruta de la API |
| How long it is kept | Vence a los 15 minutos, o antes si vencen las credenciales temporales del rol |
| How access is audited | Registros de acceso de la API |
| How it is deleted or anonymized | Deja de servir al vencer |
| What happens if it leaks | Quien la tenga descarga esa carátula hasta que venza (RISK-01) |
| Transfers to third parties | Ninguna |

### DATA-03 · Credenciales del rol de la tarea

| Question | Answer |
|---|---|
| What it contains | Llave temporal, secreto y token de sesión que ECS entrega al contenedor |
| Why it is needed | Firmar el enlace y guardar el PDF |
| Who can access it | Solo el proceso del backend |
| Where it is captured | Metadatos de credenciales de ECS; en local y en el CI, valores de prueba que solo acepta Floci |
| How it is transmitted | Dentro de la tarea, desde el servicio de metadatos de ECS |
| Where it is stored | En la memoria del proceso; nunca en código, imágenes, variables del repositorio ni registros |
| Copies, caches and backups | Ninguna |
| How long it is kept | AWS las rota antes de que venzan |
| How access is audited | CloudTrail de la cuenta |
| How it is deleted or anonymized | Vencen solas |
| What happens if it leaks | Permiten leer y escribir carátulas y usar la llave del bucket hasta que venzan; nada más (CTL-06) |
| Transfers to third parties | Ninguna |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Analista de operaciones | analyst, sin socio | Autenticado | Descargar la carátula de cualquier póliza no cancelada |
| Usuario de un socio | analyst, con socio | Autenticado | Descargar las carátulas de las pólizas de su socio |
| Asesor | advisor | Autenticado | No pide la carátula (403) |
| Quien recibe un enlace | Ninguno | No confiable | Descargar esa carátula hasta que venza el enlace |
| Atacante externo | Ninguno | No confiable | Llamar sin token (401); el bucket no es público |
| Backend | Rol de la tarea de ECS | Confiable | Leer y escribir en `caratulas/` y usar la llave del bucket |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| API | Portal y backend | Token, rol analyst, usuario activo y su socio en la base; número de póliza con patrón |
| Almacenamiento | Backend y S3 | Firma SigV4 con las credenciales del rol; política del rol sobre `caratulas/*` y la llave KMS; HTTPS |
| Descarga | Navegador y S3 | Firma y vencimiento del enlace, que S3 comprueba; HTTPS |
| Base de datos | Backend y PostgreSQL | Consulta parametrizada con el filtro de socio |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| Portal | Backend | Número de póliza | HTTPS por CloudFront | CRY-01, CTL-02, CTL-05 |
| Backend | S3 | DATA-01 | HTTPS con el rol de la tarea | CRY-01, CRY-02, CTL-06 |
| Backend | Portal | DATA-02 | HTTPS por CloudFront, `no-store` | CRY-01, CTL-03 |
| S3 | Navegador | DATA-01 | HTTPS con el enlace prefirmado | CRY-01, CRY-03, CTL-03 |

## 6. Third-party services

| Service | Use | Data shared | Location | Controls |
|---|---|---|---|---|
| AWS S3 y KMS | Guardar la carátula cifrada y entregarla con enlaces firmados | DATA-01 | Cuenta AWS del proyecto, región `us-east-1` | CRY-02, CRY-03, CTL-06 |
| Floci (local y CI) | Emular S3 en las pruebas | Solo datos ficticios | La máquina del desarrollador o el runner del CI | CTL-08 (versión fija, DEP-04) |

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| GET /api/v1/experience/policies/{policy_number}/caratula | Autenticada (analyst) | policy_number |
| Enlace prefirmado en S3 | Cualquiera con el enlace, 15 minutos | La URL completa |
| Bucket de documentos | Privado; sin acceso público | Ninguno sin credenciales |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | I, E | DATA-01 | Un usuario de un socio descarga la carátula de una póliza de otro socio o confirma que existe | Usuario de un socio | MEDIUM | HIGH | HIGH | CTL-01 |
| THR-02 | E | DATA-01 | Un asesor o alguien sin sesión pide la carátula | Asesor, atacante externo | MEDIUM | MEDIUM | MEDIUM | CTL-02 |
| THR-03 | I | DATA-01, DATA-02 | El enlace se reusa después de 15 minutos, queda en una caché o en los registros, o abre otro objeto | Quien recibe un enlace | MEDIUM | MEDIUM | MEDIUM | CTL-03 |
| THR-04 | I | DATA-01 | El PDF expone el documento completo, el contacto o los beneficiarios | Analista | MEDIUM | MEDIUM | MEDIUM | CTL-04 |
| THR-05 | T, I | Bucket | Un número manipulado en la ruta lleva a otra clave del bucket | Analista | LOW | HIGH | MEDIUM | CTL-05 |
| THR-06 | I, E | DATA-01, DATA-03 | Leer el bucket desde fuera o usar un rol con más permisos de los necesarios | Atacante externo | LOW | HIGH | MEDIUM | CTL-06 |
| THR-07 | D | Backend | Pedidos repetidos regeneran el PDF o un S3 sin respuesta deja hilos esperando | Analista, falla de S3 | MEDIUM | LOW | LOW | CTL-07 |
| THR-08 | T | Backend | Una dependencia nueva con una vulnerabilidad conocida | Tercero | LOW | HIGH | MEDIUM | CTL-08 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | El socio del usuario se lee en la base con la dependencia del detalle y filtra la consulta; fuera del alcance responde 404 como una póliza inexistente y no se genera nada | API, DATA | EXISTING | THR-01 | TC-04 | T-04 | API1:2023, CWE-639 | — |
| CTL-02 | Rol analyst, usuario activo y socio habilitado, con la dependencia compartida con la búsqueda y el detalle | API | EXISTING | THR-02 | TC-05 | T-04 | API5:2023, CWE-285 | — |
| CTL-03 | Enlace `GetObject` de esa clave, firmado con SigV4, que vence a los 900 s y es `https://` contra AWS; respuesta `no-store`; el enlace no se registra | API, CRYPTO | EXISTING | THR-03 | TC-01, TC-03, TC-14 | T-02, T-04 | CWE-598, CWE-524, CWE-613 | — |
| CTL-04 | El PDF lleva el documento enmascarado y no lleva correo, teléfono, beneficiarios ni estado; solo texto, sin imágenes ni HTML | BACKEND | EXISTING | THR-04 | TC-02 | T-03 | API3:2023, CWE-359 | — |
| CTL-05 | Patrón del número en la ruta; la clave `caratulas/{número}.pdf` la arma el servidor | BACKEND | EXISTING | THR-05 | TC-06 | T-04 | CWE-22, CWE-20 | — |
| CTL-06 | Bucket privado con bloqueo de acceso público, versionado y cifrado por defecto con su llave KMS; el backend no manda cabeceras de cifrado; el rol solo tiene `s3:PutObject` y `s3:GetObject` sobre `caratulas/*` y `kms:GenerateDataKey` y `kms:Decrypt` sobre esa llave | INFRA | EXISTING | THR-06 | TC-09 | T-06 | CWE-732, CWE-311 | — |
| CTL-07 | El PDF se genera una vez por póliza; el cliente de S3 espera 2 s para conectar y 2 s para leer, con 2 intentos, y responde 503 con `Retry-After` | BACKEND | EXISTING | THR-07 | TC-07, TC-08 | T-02, T-04 | CWE-400 | — |
| CTL-09 | El portal solo abre el enlace para la descarga: no lo guarda en el almacenamiento del navegador ni en la dirección del portal, y muestra los mensajes con interpolación de Angular | WEB | EXISTING | THR-03 | TC-11, TC-15 | T-05 | CWE-922, CWE-79 | — |
| CTL-10 | En AWS el backend toma las credenciales temporales del rol de la tarea por la cadena por defecto de boto3; ninguna llave de AWS va en código, configuración ni registros; con el emulador el adaptador usa credenciales de prueba, así nunca usa las reales contra Floci | SECRETS | EXISTING | THR-06 | TC-14 | T-02, T-06 | CWE-798 | — |
| CTL-08 | boto3 y fpdf2 evaluados en OSV.dev en las versiones del lock, con fpdf2 desde 2.8.8; Floci con versión fija en el CI | PROCESS | EXISTING | THR-08 | TC-14 | T-01, T-07 | CWE-1395 | — |

## 10. Cryptography

### CRY-01 · Transporte

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02 |
| Type | TLS |
| Algorithm or service | HTTPS (TLS 1.2 o 1.3) de CloudFront hacia el portal y de los endpoints de S3 hacia el backend y el navegador |
| Rationale | El cliente de S3 contra AWS usa HTTPS y el adaptador rechaza un enlace que no lo sea |
| Key management | Certificados administrados por AWS |
| Permissions | No aplica |
| Rotation | La de AWS |
| Recovery | No aplica |
| Failure behavior | Sin TLS no hay conexión; un enlace sin HTTPS responde 503 |
| Tests | TC-01 |

### CRY-02 · Carátula en reposo

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | AT_REST |
| Algorithm or service | SSE-KMS de S3 con la llave del bucket (`aws_kms_key.storage`), cifrado por defecto del bucket |
| Rationale | El bucket ya cifra todo con su llave; no mandar cabeceras evita que un pedido cambie a la llave administrada por AWS |
| Key management | KMS de AWS; el rol de la tarea solo puede generar llaves de datos y descifrar con ella |
| Permissions | `kms:GenerateDataKey` y `kms:Decrypt` para el rol de la tarea |
| Rotation | Rotación automática anual de KMS (`enable_key_rotation = true`) |
| Recovery | Versionado del bucket; la llave tiene 7 días de espera antes de borrarse |
| Failure behavior | Sin permiso sobre la llave, S3 rechaza el guardado o la descarga y el portal informa que la carátula está en generación |
| Tests | TC-09 |

### CRY-03 · Firma del enlace

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-03 |
| Type | MAC |
| Algorithm or service | Firma SigV4 de AWS (HMAC-SHA256) de boto3 con las credenciales temporales del rol |
| Rationale | S3 comprueba el código y el vencimiento; cubre el método, el bucket, la clave y los parámetros de la respuesta, y el secreto del rol nunca viaja: solo el código derivado de él |
| Key management | Credenciales temporales del rol de la tarea (DATA-03), rotadas por AWS |
| Permissions | Solo el backend firma |
| Rotation | La de las credenciales de ECS |
| Recovery | No aplica: se pide un enlace nuevo |
| Failure behavior | S3 rechaza un enlace vencido o alterado con 403; sin credenciales el backend responde 503 |
| Tests | TC-01, TC-03 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| Credenciales temporales del rol de la tarea | Metadatos de credenciales de ECS | Solo el proceso del backend | AWS (en local y en el CI, valores de prueba que solo acepta Floci) | Automática de AWS | Quitar la política `task_documents` del rol | CloudTrail; `sdd.py scan secrets` en cada cambio |
| `aws_kms_key.storage` | KMS (la llave no sale de AWS) | S3 en nombre del rol de la tarea | AWS | Anual automática | Deshabilitar la llave | CloudTrail |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | YES | CTL-01 | — |
| Secure authentication | NO | — | El ingreso con contraseña y TOTP no cambia (HU-WEB-13) |
| Session management | NO | — | Sin cambios en la sesión |
| Token expiration | YES | CTL-03 | El enlace prefirmado vence a los 15 minutos |
| Revocation | YES | CTL-01 | — |
| Server-side permission checks | YES | CTL-02 | — |
| Per-resource access (ownership) | YES | CTL-01 | — |
| Roles | YES | CTL-02 | — |
| Privilege separation | YES | CTL-06 | — |
| Brute-force protection | NO | — | El bloqueo del ingreso ya existe (HU-WEB-13) |
| Rate limiting | NO | — | Consulta interna de personal autenticado; el PDF se genera una vez (CTL-07) |
| One-time code (OTP) handling | NO | — | Sin cambios en el TOTP |
| User enumeration prevention | YES | CTL-01 | — |
| CSRF protection | NO | — | Token en cabecera, sin cookies |

## 13. Data isolation and multi-tenancy

| Check | Applies | Controls | Tests | Rationale |
|---|---|---|---|---|
| Tenant isolation | YES | CTL-01 | TC-04 | — |
| Server-side authorization | YES | CTL-01, CTL-02 | TC-04, TC-05 | — |
| Horizontal access | YES | CTL-01 | TC-04 | — |
| Vertical access | YES | CTL-02 | TC-05 | — |
| Resource identifiers | YES | CTL-01, CTL-05 | TC-04, TC-06 | — |
| Queries and filters | YES | CTL-01, CTL-05 | TC-04, TC-06 | — |
| Shared caches | YES | CTL-03 | TC-01 | La respuesta con el enlace es `no-store` |
| Storage and logs | YES | CTL-03, CTL-05, CTL-06 | TC-06, TC-09, TC-14 | — |
| Negative cross-tenant tests | YES | CTL-01 | TC-04 | — |

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | boto3 | 1.43.123 (`>=1.43.0`) | backend | Guardar el PDF y firmar el enlace | HIGH | OSV.dev 2026-11-01; PyPI | No known vulnerabilities; Apache-2.0; mantenido por AWS | EVALUATED |
| DEP-02 | fpdf2 | 2.9.0 (`>=2.8.8`) | backend | Generar el PDF de la carátula | MEDIUM | OSV.dev 2026-11-01; PyPI | No known vulnerabilities en 2.9.0; GHSA-rw7v-4hrq-x6r9 y GHSA-wqw6-wpr3-45rh (imágenes, SVG y HTML) se corrigieron en 2.8.8 y la carátula no usa esas funciones; LGPL-3.0 | EVALUATED |
| DEP-03 | Transitivas: botocore 1.43.123, s3transfer 0.19.3, jmespath 1.1.0, python-dateutil 2.9.0.post0, six 1.17.0, urllib3 2.8.0, pillow 13.0.0, fonttools 4.68.0 | Las del lock | backend | Dependencias de DEP-01 y DEP-02 | MEDIUM | OSV.dev 2026-11-01 | No known vulnerabilities en esas versiones | EVALUATED |
| DEP-04 | floci/floci | 2.3.0 | CI | S3 emulado en las pruebas del backend | LOW | Docker Hub 2026-11-01 | Versión del 15 de octubre de 2026, fija en el workflow; solo datos ficticios | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | Quien reciba un enlace vigente descarga esa carátula; el enlace lleva el identificador de la llave temporal y el token de sesión del rol, sin el secreto | THR-03 | LOW | MEDIUM | LOW | MITIGATE | Bajo: vence a los 15 minutos, abre un solo objeto, no queda en cachés ni registros y no sirve para firmar otra cosa | MITIGATED |
| RISK-02 | Floci no comprueba firmas, así que las pruebas no demuestran contra un emulador que el enlace solo abre su clave | THR-03 | LOW | MEDIUM | LOW | MITIGATE | Bajo: lo garantiza SigV4 en AWS y las pruebas unitarias revisan la clave, el método y los parámetros firmados; Floci sí comprueba el vencimiento | MITIGATED |
| RISK-03 | Si el objeto se pierde con la clave anotada, el enlace lleva a un 404; si S3 deja de responder después de firmar, el portal ya anunció la descarga y es el navegador quien espera o muestra su error | THR-07 | LOW | LOW | LOW | MITIGATE | Bajo: el bucket es versionado y el rol no puede borrar; en el teléfono Chrome esperó y terminó la descarga al volver S3, y el analista puede pulsar otra vez | MITIGATED |
| RISK-04 | Dos primeros pedidos a la vez generan el PDF dos veces | THR-07 | LOW | LOW | LOW | ACCEPT | Bajo: guardan el mismo contenido en la misma clave y el versionado conserva ambos | ACCEPTED |

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Retención de las carátulas | Plazo legal de la aseguradora | Definirlo con el área legal junto con la de la póliza | Responsable de datos | NO | PENDING |
| DEC-02 | Firma digital del PDF con certificado | Certificado de una entidad de certificación o seguir sin firma | Decidirlo cuando la aseguradora tenga certificado | Equipo y aseguradora | NO | PENDING |

# Security and privacy assessment · HU-MOV-08

<!-- Proportional to the profile in spec.md. A section that does not apply is replaced by
     "N/A: <rationale>" (no table). The validator rejects N/A when a trigger requires the section.
     Write UNKNOWN when you do not know: it is not an error, but it keeps the gate at IMPLEMENTED.
     Never write secrets or real personal data in this file. -->

## 1. Data inventory and classification

<!-- Classification: PUBLIC, INTERNAL, CONFIDENTIAL (personal data) or RESTRICTED (credentials, secrets,
     special-category data, detailed financial data). Collect only what the purpose needs. -->

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Fotos del siniestro en JPEG, sin metadatos EXIF | RESTRICTED | YES | YES | Evidencia del lugar de los hechos; pueden mostrar heridas, personas y placas |
| DATA-02 | Descripción de lo ocurrido | RESTRICTED | YES | YES | Contexto del siniestro; texto libre que puede contar lesiones |
| DATA-03 | Ubicación y horas de cada foto: latitud, longitud, precisión, hora del teléfono, hora independiente del reloj del teléfono y marca de ubicación simulada | CONFIDENTIAL | YES | NO | Probar dónde y cuándo se tomó cada foto |
| DATA-04 | Póliza del reporte, estado (borrador o listo) y huella SHA-256 de cada foto | CONFIDENTIAL | YES | NO | Ligar el reporte a la póliza y permitir verificar cada foto al recibirla (HU-MOV-09) |
| DATA-05 | Llave del Keystore y frase de la base del reporte por cuenta, y llave de cada foto | RESTRICTED | NO | NO | Abrir la base del reporte y descifrar cada foto |

El identificador de la cuenta en los nombres de archivo es el mismo de la billetera: el SHA-256 truncado del correo (ADR 0021). La foto se recomprime sin EXIF: la ubicación y la hora van aparte, en DATA-03, y el modelo del teléfono no hace falta.

## 2. Data lifecycle

<!-- One block per CONFIDENTIAL or RESTRICTED item. One block may cover several: ### DATA-01, DATA-02 · Name -->

### DATA-01, DATA-02 · Fotos y descripción

| Question | Answer |
|---|---|
| What it contains | De 1 a 5 fotos JPEG de a lo sumo 2 MB y un texto de hasta 500 caracteres |
| Why it is needed | Son la evidencia y el relato del siniestro que pide la historia |
| Who can access it | La app, con el teléfono desbloqueado y la sesión de esa cuenta abierta; después del envío, quien dictamina (HU-MOV-09, HU-WEB-15) |
| Where it is captured | La foto con CameraX dentro de la app, en memoria; la descripción en el formulario del reporte |
| How it is transmitted | No sale del teléfono en esta historia; el envío por HTTPS es de HU-MOV-09 |
| Where it is stored | Fotos en `files/claims/<cuenta>/` cifradas con CRY-03; descripción en `claims_<cuenta>.db`, cifrada con CRY-01; ambos en el almacenamiento privado |
| Copies, caches and backups | Ninguna: la cámara entrega la foto en memoria, no hay archivos temporales ni MediaStore, las miniaturas se descifran en memoria sin caché en disco, la descripción no va al estado guardado de la pantalla, y las copias de seguridad y transferencias excluyen todo (CTL-05, CTL-06, CTL-09) |
| How long it is kept | Hasta que el asegurado borra la foto o descarta el reporte, cierra sesión, cambia la contraseña, el servidor desvincula el teléfono (AC-12) o el reporte se envía (HU-MOV-09); el vencimiento de la sesión no lo borra (DEC-08) |
| How access is audited | No aplica en el teléfono; el backend registra la recepción (HU-MOV-09) |
| How it is deleted or anonymized | Se borran el archivo de la foto y su fila; al cerrar sesión, al cambiar la contraseña o cuando el servidor desvincula el teléfono, la base, la carpeta de fotos, la frase envuelta y la llave de la cuenta |
| What happens if it leaks | Sin la llave no se leen; con ellas, fotos que pueden mostrar heridas y el relato del siniestro de una persona |
| Transfers to third parties | Ninguna |

### DATA-03, DATA-04 · Ubicación, horas, póliza y huellas

| Question | Answer |
|---|---|
| What it contains | Coordenadas, precisión, hora del teléfono, hora independiente (GNSS o red de Android) y marca de simulada por foto; número de póliza, estado del reporte y huella de cada foto |
| Why it is needed | Probar dónde y cuándo se tomó cada foto, ligar el reporte a la póliza y verificar la foto al recibirla |
| Who can access it | La app con el teléfono desbloqueado y la sesión de la cuenta |
| Where it is captured | La ubicación con el `LocationManager` de Android en el momento del disparo; la póliza de la copia de la billetera; la huella al guardar la foto |
| How it is transmitted | No sale del teléfono en esta historia |
| Where it is stored | `claims_<cuenta>.db`, SQLCipher (CRY-01) |
| Copies, caches and backups | Ninguna (CTL-06, CTL-09) |
| How long it is kept | Lo mismo que la foto o el reporte al que pertenece |
| How access is audited | No aplica en el teléfono |
| How it is deleted or anonymized | Con la fila de la foto o con la base de la cuenta |
| What happens if it leaks | Sin la llave no se lee; con ella, dónde estuvo una persona a una hora |
| Transfers to third parties | Ninguna desde la app. En teléfonos con Google, el proveedor de ubicación del sistema lo implementan los servicios de Google según los ajustes de la persona; la app no integra ningún SDK |

### DATA-05 · Llaves del reporte

| Question | Answer |
|---|---|
| What it contains | Llave AES-256 del Keystore por cuenta, frase aleatoria de 32 bytes de la base por cuenta y llave aleatoria de 32 bytes por foto |
| Why it is needed | Abrir la base del reporte y descifrar cada foto |
| Who can access it | La llave del Keystore no sale de él y solo se usa con el teléfono desbloqueado; la frase y las llaves de foto, solo dentro de la app |
| Where it is captured | Se crean al empezar el primer reporte de la cuenta y al guardar cada foto |
| How it is transmitted | No se transmiten |
| Where it is stored | Llave `solventa_claims_<cuenta>` en el Keystore; frase envuelta en `solventa_claims`; llave de cada foto dentro de la base cifrada |
| Copies, caches and backups | Ninguna (CTL-06) |
| How long it is kept | Mientras exista el reporte de la cuenta |
| How access is audited | No aplica: no salen del teléfono |
| How it is deleted or anonymized | Con la foto, con el reporte o al cerrar sesión: entrada del Keystore, frase envuelta y base |
| What happens if it leaks | La frase envuelta no sirve sin la llave del Keystore; en claro solo viven en memoria durante cada operación |
| Transfers to third parties | Ninguna |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Asegurado | insured | Autenticado | Armar, retomar y descartar el reporte de su cuenta |
| Asegurado que quiere defraudar | insured | Autenticado, con intereses contrarios | Elegir qué fotografía, simular la ubicación, cambiar el reloj del teléfono |
| Otro usuario del teléfono | insured de otra cuenta | Autenticado en su cuenta | Ver y armar solo su propio reporte |
| Quien tenga el teléfono | Ninguno | No confiable | Acceso físico; desbloqueado, ver la pantalla, las apps recientes y lanzar una transferencia |
| Otras apps del teléfono | Ninguno | No confiable | Leer la galería y el almacenamiento compartido; con permisos de accesibilidad o de grabación, ver la pantalla |
| Malware o root | Ninguno | No confiable | Leer, alterar o borrar el almacenamiento privado y leer logcat |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| Sensores de Android | Cámara y ubicación del sistema, y la app | Permisos de cámara y de ubicación precisa concedidos; ubicación fresca, con su precisión y su marca de simulada (CTL-02, CTL-11) |
| Keystore | App y Android Keystore | Teléfono desbloqueado; AES-GCM rechaza una frase envuelta alterada |
| Almacenamiento privado | App y sus archivos | La base solo abre con su frase y su HMAC por página; cada foto solo descifra con su llave y su etiqueta GCM |
| Pantalla del sistema | App, capturas, grabación y apps recientes | En release la ventana del reporte es segura (CTL-08) |
| Copias y transferencias | Teléfono y servicio de copias o dispositivo de destino | Las reglas excluyen todos los dominios (CTL-06) |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| CameraX | Memoria de la app | DATA-01 | API de la cámara, en el proceso | CTL-01, CTL-05 |
| `LocationManager` | App | DATA-03 | API de Android | CTL-02, CTL-03, CTL-11 |
| App | Base del reporte | DATA-02, DATA-03, DATA-04, DATA-05 | Archivo privado | CRY-01, CTL-04, CTL-07 |
| App | Archivos de fotos | DATA-01 | Archivo privado | CRY-03, CRY-04, CTL-05 |
| App | Keystore y `solventa_claims` | DATA-05 | API del Keystore y preferencias privadas | CRY-02, CTL-09 |
| Teléfono | Copia o dispositivo de destino | DATA-01 a DATA-05 | Copia de seguridad o transferencia | CTL-06 |

## 6. Third-party services

N/A: la app no envía datos a servicios externos ni integra SDK de terceros; la ubicación viene de Android y las copias y transferencias quedan excluidas (CTL-06).

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| Pantallas del reporte | Sesión del asegurado | La descripción, lo que se fotografía y las respuestas a los permisos |
| Proveedor de ubicación y reloj | Teléfono | Apps de ubicación simulada, root y la hora del teléfono |
| Almacenamiento privado | Root | La base y los archivos de fotos |
| Galería y almacenamiento compartido | Otras apps | Nada de la app debería estar ahí |
| Pantalla y apps recientes | Otras apps o quien tenga el teléfono | Capturas, grabación y miniaturas |
| Logcat | Root o depuración | Leer los registros |

## 8. Threat model

<!-- STRIDE: S spoofing, T tampering, R repudiation, I information disclosure, D denial of service,
     E elevation of privilege. Risk = likelihood (LOW, MEDIUM, HIGH) × impact (LOW, MEDIUM, HIGH, CRITICAL);
     the validator rejects a risk below the matrix. Every MEDIUM or higher threat needs an applicable control. -->

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | S, T | DATA-01 | Adjuntar una foto vieja, editada o de internet como si fuera del lugar | Asegurado que quiere defraudar | MEDIUM | HIGH | HIGH | CTL-01 |
| THR-02 | S | DATA-03 | Simular la ubicación con una app de ubicación falsa | Asegurado que quiere defraudar | MEDIUM | HIGH | HIGH | CTL-02, CTL-11 |
| THR-03 | T | DATA-03 | Cambiar el reloj del teléfono para falsear la hora de la foto | Asegurado que quiere defraudar | MEDIUM | MEDIUM | MEDIUM | CTL-03 |
| THR-04 | I | DATA-01, DATA-02, DATA-03 | Leer las fotos, la descripción o la ubicación por la galería, el almacenamiento compartido, un archivo temporal, una copia de seguridad o un teléfono perdido | Quien tenga el teléfono u otras apps | MEDIUM | HIGH | HIGH | CTL-04, CTL-05, CTL-06 |
| THR-05 | T | DATA-01 | Cambiar una foto guardada por otra antes del envío | Malware o root | LOW | HIGH | MEDIUM | CTL-04, CTL-13 |
| THR-06 | I | DATA-01, DATA-02, DATA-03, DATA-04 | Ver o continuar el reporte de otra cuenta del mismo teléfono | Otro usuario del teléfono | LOW | HIGH | MEDIUM | CTL-07 |
| THR-07 | I | DATA-01, DATA-02 | Una captura, una grabación o la miniatura de las apps recientes muestran las fotos | Otras apps o quien tenga el teléfono | MEDIUM | MEDIUM | MEDIUM | CTL-08 |
| THR-08 | I | DATA-02, DATA-03, DATA-05 | La descripción, la ubicación o las llaves quedan en logcat o en el estado guardado de la pantalla | Malware o root | LOW | HIGH | MEDIUM | CTL-09 |
| THR-09 | D | DATA-01, DATA-02 | Una llave invalidada, una base dañada o el cierre de la app borran el reporte sin que la persona lo decida o cierran la app | Falla del teléfono o de diseño | MEDIUM | MEDIUM | MEDIUM | CTL-10 |
| THR-10 | I | DATA-03 | La app lee la ubicación fuera de la cámara del reporte o en segundo plano | Falla de diseño | LOW | MEDIUM | LOW | CTL-11 |
| THR-11 | D | Almacenamiento del teléfono | Llenar el teléfono con fotos grandes o con reportes sin límite | Asegurado | LOW | LOW | LOW | CTL-12 |
| THR-12 | D | DATA-01, DATA-02 | Una versión nueva cambia el esquema con una migración destructiva y borra los reportes no enviados | Falla de diseño | MEDIUM | HIGH | HIGH | CTL-15 |
| THR-13 | I | DATA-01, DATA-02 | Un reporte guardado queda inalcanzable (sin pólizas vigentes o con la copia descartada) y sus fotos no se pueden borrar | Falla de diseño | MEDIUM | MEDIUM | MEDIUM | CTL-16 |

## 9. Controls

<!-- Status: EXISTING (implemented and verifiable), MISSING (required, not yet implemented: needs a task) or
     NOT APPLICABLE (with rationale). Domain: BACKEND, API, WEB, MOBILE, INFRA, DATA, CRYPTO, SECRETS or PROCESS.
     Every control that is not NOT APPLICABLE needs at least one test case. -->

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | La evidencia sale solo de CameraX dentro de la app: sin selector de fotos, galería ni intents de cámara de otras apps | MOBILE | EXISTING | THR-01, DATA-01 | TC-02 | T-04 | MASVS-PLATFORM-1 · CWE-345 | — |
| CTL-02 | Ubicación fresca del disparo, con su precisión y marcada si es simulada (`Location.isMock`) | MOBILE | EXISTING | THR-02, DATA-03 | TC-01, TC-05 | T-03 | MASVS-RESILIENCE-1 · CWE-345 | — |
| CTL-03 | Además de la hora del teléfono, una hora que no depende del reloj que ajusta la persona (GNSS o red de Android) cuando el teléfono la tiene | MOBILE | EXISTING | THR-03, DATA-03 | TC-01, TC-05 | T-03 | CWE-345 | — |
| CTL-04 | Base SQLCipher del reporte por cuenta y cada foto cifrada con AES-256-GCM y su propia llave (CRY-01 a CRY-03) | MOBILE, CRYPTO | EXISTING | THR-04, THR-05, DATA-01, DATA-02, DATA-03, DATA-04 | TC-06, TC-07, TC-21 | T-02, T-10 | MASVS-STORAGE-1, MASVS-CRYPTO-2 | — |
| CTL-05 | Nada en claro en disco: captura en memoria, recompresión y cifrado en memoria, sin MediaStore, archivos temporales ni caché de miniaturas, y sin EXIF | MOBILE | EXISTING | THR-04, DATA-01 | TC-06 | T-02, T-04 | MASVS-STORAGE-1, MASVS-STORAGE-2 · CWE-312 | — |
| CTL-06 | Copias de seguridad y transferencias excluyen todos los dominios (`allowBackup="false"` y `data_extraction_rules.xml`) | MOBILE | EXISTING | THR-04, DATA-01, DATA-02, DATA-03, DATA-04, DATA-05 | TC-06 | — | MASVS-STORAGE-2 | Implementado en HU-MOV-05: `AndroidManifest.xml:14`, `AndroidManifest.xml:15`, `data_extraction_rules.xml`; cubre `files/` y `databases/`, donde va el reporte |
| CTL-07 | Base, fotos, frase y llave por cuenta; se borran al cerrar sesión, al cambiar la contraseña y cuando el servidor desvincula el teléfono, y se conservan con la sesión vencida | MOBILE | EXISTING | THR-06, DATA-01, DATA-02, DATA-03, DATA-04 | TC-08 | T-02, T-06 | MASVS-STORAGE-1, MASVS-CRYPTO-2 | — |
| CTL-08 | Ventana segura (`FLAG_SECURE`) mientras se muestra el reporte, en la build release | MOBILE | EXISTING | THR-07, DATA-01, DATA-02 | TC-13 | T-05 | MASVS-PLATFORM-3 | — |
| CTL-09 | Sin registros de la descripción, la ubicación, las rutas ni las llaves, y sin datos del reporte en el estado guardado de la pantalla | MOBILE | EXISTING | THR-08, DATA-02, DATA-03, DATA-05 | TC-14 | T-02, T-05 | MASVS-STORAGE-2 · CWE-532 | — |
| CTL-10 | El reporte se guarda mientras se edita; una llave invalidada o una base dañada lo dejan como «no se puede abrir» y la persona decide descartarlo; el bloqueo o una falla pasajera dan "no disponible"; nada cierra la app ni borra el reporte solo | MOBILE | EXISTING | THR-09, DATA-01, DATA-02 | TC-11, TC-15, TC-25 | T-02, T-05 | MASVS-STORAGE-1 · CWE-755 | — |
| CTL-11 | Solo permisos de cámara y de ubicación precisa, pedidos al usar la cámara; la ubicación se lee solo con la cámara del reporte abierta y en primer plano y se detiene al salir; la aproximada se trata como negada | MOBILE | EXISTING | THR-02, THR-10, DATA-03 | TC-03, TC-19, TC-22 | T-01, T-03 | MASVS-PRIVACY-1 | — |
| CTL-12 | Límites: 5 fotos de a lo sumo 2 MB, descripción de 500 caracteres y un reporte por cuenta | MOBILE | EXISTING | THR-11 | TC-09 | T-05 | CWE-400 | — |
| CTL-13 | Huella SHA-256 de cada foto calculada al guardarla, sobre los bytes que se enviarán | MOBILE, CRYPTO | EXISTING | THR-05, DATA-01, DATA-04 | TC-07 | T-02 | CWE-353 | — |
| CTL-14 | Dependencias nuevas sin vulnerabilidades conocidas | PROCESS | EXISTING | DATA-01 | TC-18 | T-01 | MASVS-CODE-3 | — |
| CTL-15 | Esquema exportado de la base del reporte y migraciones que conservan los datos; sin migración destructiva | MOBILE, DATA | EXISTING | THR-12, DATA-01, DATA-02 | TC-23 | T-02 | MASVS-STORAGE-1 | — |
| CTL-16 | El reporte guardado se abre y se descarta desde la billetera aunque no haya pólizas vigentes ni copia | MOBILE | EXISTING | THR-13, DATA-01, DATA-02 | TC-24 | T-05 | MASVS-STORAGE-1 | — |

## 10. Cryptography

<!-- One block per mechanism. Type: TLS, AT_REST, APP_LEVEL, PASSWORD_HASH, MAC, SIGNATURE, TOKENIZATION or HASH.
     Do not mix concepts: a hash is not decrypted, HMAC is not a digital signature, tokenization is not encryption. -->

### CRY-01 · Base del reporte

| Field | Value |
|---|---|
| Protects | DATA-02, DATA-03, DATA-04, DATA-05 (llaves de las fotos) |
| Type | AT_REST |
| Algorithm or service | SQLCipher 4: AES-256-CBC por página con HMAC-SHA512 |
| Rationale | El mismo mecanismo de la billetera (HU-MOV-04), en una base aparte: el descarte de la copia de pólizas no debe borrar evidencia que no se puede volver a descargar |
| Key management | Frase aleatoria de 32 bytes por cuenta, envuelta por CRY-02; en claro solo durante cada operación y puesta en cero después |
| Permissions | Solo la app |
| Rotation | Nueva con cada base del reporte |
| Recovery | Base dañada o frase inválida: el reporte queda como «no se puede abrir» y la persona decide descartarlo (CTL-10) |
| Failure behavior | La app no se cierra |
| Tests | TC-06, TC-15, TC-21 |

### CRY-02 · Envoltura de la frase de la base

| Field | Value |
|---|---|
| Protects | DATA-05 |
| Type | APP_LEVEL |
| Algorithm or service | AES-256-GCM con una llave del Android Keystore por cuenta, no exportable y usable solo con el teléfono desbloqueado (`setUnlockedDeviceRequired`) |
| Rationale | La frase nunca queda en claro en el almacenamiento; una llave por propósito y por cuenta, separada de la de la billetera |
| Key management | Llave por cuenta con el alias `solventa_claims_<cuenta>`, distinta de `solventa_wallet_<cuenta>`; IV aleatorio de 96 bits guardado con la frase envuelta |
| Permissions | Solo la app |
| Rotation | Se crea con la base del reporte y se elimina con ella |
| Recovery | Llave invalidada: el reporte queda como «no se puede abrir» y la persona decide descartarlo (CTL-10); bloqueo o falla pasajera: "no disponible" sin borrar |
| Failure behavior | La app no se cierra |
| Tests | TC-06, TC-15, TC-21 |

### CRY-03 · Cifrado de cada foto

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | APP_LEVEL |
| Algorithm or service | AES-256-GCM de la plataforma (JCA), con etiqueta de 128 bits |
| Rationale | Cifra y autentica cada archivo; una foto alterada o cambiada no descifra |
| Key management | Llave aleatoria de 32 bytes por foto (`SecureRandom`) y nonce aleatorio de 96 bits; los dos se guardan en la fila de la foto, dentro de CRY-01. Cada llave cifra un solo archivo una sola vez |
| Permissions | Solo la app |
| Rotation | Una llave nueva por foto |
| Recovery | Archivo alterado o ausente: la foto se marca como dañada y se pide tomarla de nuevo |
| Failure behavior | La app no se cierra y no muestra la foto alterada |
| Tests | TC-06, TC-07 |

### CRY-04 · Huella de cada foto

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | HASH |
| Algorithm or service | SHA-256 de los bytes JPEG que se envían |
| Rationale | Integridad: el servidor la compara al recibir la foto (HU-MOV-09, RC-11). La foto es de alta entropía, así que la huella no revela su contenido. No protege de quien controla el teléfono, que puede recalcularla; eso lo cubre CRY-03 en reposo |
| Key management | No usa llave |
| Permissions | Solo la app |
| Rotation | No aplica: una por foto |
| Recovery | No aplica |
| Failure behavior | Si la foto descifrada no coincide con su huella, se marca como dañada |
| Tests | TC-07 |

## 11. Secrets and keys

<!-- Names, never values. -->

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| Llave del reporte por cuenta (`solventa_claims_<cuenta>`) | Android Keystore | La app, con el teléfono desbloqueado | Cada teléfono | Con cada base del reporte | Borrar el reporte o cerrar sesión | No exportable |
| Frase de la base del reporte por cuenta | `solventa_claims`, envuelta | La app | Cada teléfono | Con cada base del reporte | Borrar el reporte o cerrar sesión | No se registra (CTL-09) |
| Llave de cada foto | Fila de la foto, dentro de la base cifrada | La app | Cada teléfono | Una por foto | Borrar la foto | No se registra (CTL-09) |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | NO | — | Usa la cuenta de la sesión abierta |
| Secure authentication | NO | — | No cambia el ingreso |
| Session management | YES | CTL-07 | El reporte se borra al cerrar sesión y se conserva con la sesión vencida (DEC-08) |
| Token expiration | NO | — | Sin tokens nuevos ni llamadas al servidor |
| Revocation | YES | CTL-07 | Cambiar la contraseña o que el servidor desvincule el teléfono borra el reporte |
| Server-side permission checks | NO | — | Sin servidor en esta historia; HU-MOV-09 valida la póliza al recibir |
| Per-resource access (ownership) | YES | CTL-07 | El reporte es de la cuenta: base, fotos y llave por cuenta |
| Roles | NO | — | Un solo rol |
| Privilege separation | NO | — | Sin operaciones privilegiadas |
| Brute-force protection | NO | — | Sin credenciales nuevas |
| Rate limiting | NO | — | Sin endpoints nuevos |
| One-time code (OTP) handling | NO | — | Sin códigos |
| User enumeration prevention | NO | — | Sin entradas que identifiquen cuentas |
| CSRF protection | NO | — | Sin web |

## 13. Data isolation and multi-tenancy

N/A: canal del asegurado, sin inquilinos; el aislamiento entre cuentas del mismo teléfono es CTL-07.

## 14. Dependencies

<!-- Every new or updated dependency, and the critical ones this change relies on. Evaluation: method and date
     (e.g. "OSV.dev 2026-10-21"). Status: EVALUATED, PENDING or REJECTED. -->

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | androidx.camera:camera-core, camera-camera2, camera-lifecycle, camera-view | 1.5.3 | movil | Cámara dentro de la app con captura en memoria | HIGH | OSV.dev 2026-11-05, también las transitivas camera-video 1.5.3, featurecombinationquery 1.5.3, appcompat 1.1.0, exifinterface 1.4.2, tracing 1.2.0, concurrent-futures 1.1.0 y jspecify 1.0.0; sus manifiestos no piden permisos y solo declaran servicios no exportados; requisitos del AAR: compileSdk 35 y AGP 8.6.0, que el proyecto cumple (la 1.6 pide compileSdk 36 y AGP 8.9.1); `camera-compose` se descarta porque subiría Compose UI a 1.7.8 | No known vulnerabilities | EVALUATED |
| DEP-02 | net.zetetic:sqlcipher-android | 4.9.0 | movil | Cifrado de la base del reporte; sin cambio | CRITICAL | OSV.dev 2026-11-05 | No known vulnerabilities | EVALUATED |
| DEP-03 | androidx.room:room-runtime | 2.6.1 | movil | Base del reporte; sin cambio | HIGH | OSV.dev 2026-11-05 | No known vulnerabilities | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | La marca de simulada no detecta una ubicación falseada con root, y la cámara no impide fotografiar una pantalla o una impresión | THR-01, THR-02 | LOW | HIGH | MEDIUM | ACCEPT | Quien dictamina revisa la evidencia (HU-WEB-15) y el servidor compara con la hora de recepción (HU-MOV-09); detectar root pediría un servicio de Google y no cierra el riesgo. Aceptado por David Gutiérrez el 2026-11-05 hasta el 2026-12-31 (DEC-04) | ACCEPTED |
| RISK-02 | Una llave invalidada o una base dañada pierden un reporte que no se envió | THR-09 | LOW | MEDIUM | LOW | MITIGATE | La app avisa y no se cierra; nada se borra solo y la persona decide descartarlo; sin la llave, la evidencia no se recupera | MITIGATED |
| RISK-04 | Un cambio de contraseña hecho en otro teléfono llega aquí como sesión vencida, así que el reporte se conserva hasta el siguiente ingreso | THR-04 | LOW | MEDIUM | LOW | MITIGATE | Sigue cifrado y solo se ve con la sesión de la cuenta; el siguiente ingreso con huella lo borra cuando el servidor rechaza el teléfono (`AuthViewModel`) | MITIGATED |
| RISK-03 | La llave exige el teléfono desbloqueado, así que un envío en segundo plano con el teléfono bloqueado espera al desbloqueo | THR-09 | MEDIUM | LOW | LOW | MITIGATE | HU-MOV-09 reintenta, como la sincronización de HU-MOV-07 (DEC-06) | OPEN |

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Foto sin ubicación (apagada o sin señal en 30 s) | No tomarla; tomarla marcada sin ubicación | No tomarla | Responsable de la historia | NO | APPROVED |
| DEC-02 | Qué hace «Enviar» en esta historia | Guardar como listo; hacer HU-MOV-08 y HU-MOV-09 juntas | Guardar como listo | Responsable de la historia | NO | APPROVED |
| DEC-03 | Bloquear capturas en el reporte | Solo en release; siempre; no bloquear | Solo en release, para grabar el video de la entrega en debug | Responsable de la historia | NO | APPROVED |
| DEC-04 | Aceptar RISK-01 | Aceptar con la revisión de HU-WEB-15; detectar root con Play Integrity | Aceptar; se señala en el pull request para que quien revise lo vea | Responsable de la historia | YES | APPROVED |
| DEC-05 | Ubicación aproximada concedida | Tratarla como negada; aceptarla marcada | Tratarla como negada: unos 3 km no ubican el lugar | Responsable de la historia | NO | APPROVED |
| DEC-06 | La llave del reporte exige el teléfono desbloqueado | Sí, como la billetera; no | Sí: protege un teléfono perdido y bloqueado | Responsable de la historia | NO | APPROVED |
| DEC-07 | Dirección o coordenadas en la cámara y la evidencia | Coordenadas; dirección con la geocodificación de Android | Coordenadas: la geocodificación envía la ubicación a Google y no funciona sin red | Responsable de la historia | NO | APPROVED |
| DEC-08 | El reporte con la sesión vencida | Conservarlo; borrarlo como la copia | Conservarlo: sigue cifrado y solo se ve al volver a entrar | Responsable de la historia | NO | APPROVED |
| DEC-09 | Cuándo se lee la ubicación | Con la cámara abierta; solo al disparar | Con la cámara abierta, y se detiene al salir | Responsable de la historia | NO | APPROVED |
| DEC-10 | Navegación del reporte | Enmendar la ADR 0015 y dejar Navigation Compose para HU-MOV-11; adoptarla ahora | Enmendar la ADR 0015 | Responsable de la historia | NO | APPROVED |

# Security and privacy assessment · HU-MOV-05

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Copia local de las pólizas de cada cuenta: número, plan, suma asegurada, titular, coberturas, vigencia y hora de sincronización | CONFIDENTIAL | YES | NO | Billetera sin conexión; esta historia la descarta, la regenera y la excluye de las transferencias |
| DATA-02 | Frase de la base y llave de envoltura de cada cuenta | RESTRICTED | NO | NO | Abrir la base; esta historia maneja su invalidación y su borrado |
| DATA-03 | Sesión guardada de las cuentas vinculadas: credencial envuelta, identificador del dispositivo, correo y nombre (HU-MOV-01, HU-MOV-03) | RESTRICTED | YES | NO | Entrar con la huella; esta historia solo la excluye de copias y transferencias |

La suma asegurada es una condición del contrato, no un dato financiero del titular. La marca de descarte (`<id>.discarded`) es un booleano junto a DATA-02; `<id>` es el SHA-256 truncado y sin sal del correo (ADR 0021), un identificador seudónimo CONFIDENTIAL.

## 2. Data lifecycle

### DATA-01 · Copia local de la billetera

| Question | Answer |
|---|---|
| What it contains | Los campos de la tarjeta, sin documento, beneficiarios ni prima (HU-MOV-04) |
| Why it is needed | Demostrar la cobertura sin señal |
| Who can access it | La app con el teléfono desbloqueado; sin red, tras la huella del titular |
| Where it is captured | En cada sincronización exitosa |
| How it is transmitted | No sale del teléfono; llega del backend por HTTPS |
| Where it is stored | `wallet_<id>.db`, SQLCipher, en el almacenamiento privado |
| Copies, caches and backups | Ninguna (CTL-03) |
| How long it is kept | Hasta el cierre de sesión, un 401 o una invalidez real (CTL-02, CTL-10); el bloqueo, una falla pasajera o una cancelación no la borran |
| How access is audited | No aplica: el backend registra la consulta que la origina |
| How it is deleted or anonymized | Se borran el archivo, la frase envuelta y la llave de la cuenta |
| What happens if it leaks | Sin la llave no se lee; con ella, coberturas y vigencias de una persona |
| Transfers to third parties | Ninguna |

### DATA-02 · Frase y llave de la copia local

| Question | Answer |
|---|---|
| What it contains | Frase aleatoria de 32 bytes y llave AES-256 del Keystore por cuenta |
| Why it is needed | Abrir la base de la cuenta |
| Who can access it | La llave no sale del Keystore; la frase se recupera solo en la app, desbloqueada |
| Where it is captured | Al crear la copia y tras cada descarte |
| How it is transmitted | No se transmite |
| Where it is stored | Llave en el Keystore; frase envuelta y marca en `solventa_wallet` |
| Copies, caches and backups | Ninguna (CTL-03) |
| How long it is kept | Mientras exista la copia de la cuenta |
| How access is audited | No aplica: no sale del teléfono |
| How it is deleted or anonymized | Se borran la entrada del Keystore y la frase envuelta; una falla al borrar no cierra la app |
| What happens if it leaks | La frase envuelta no sirve sin la llave; en claro solo vive en memoria durante cada operación (CTL-07) |
| Transfers to third parties | Ninguna |

### DATA-03 · Sesión guardada de las cuentas vinculadas

| Question | Answer |
|---|---|
| What it contains | Credencial cifrada con su IV, identificador del dispositivo, correo y nombre de cada cuenta |
| Why it is needed | Entrar con la huella (HU-MOV-01, HU-MOV-03) |
| Who can access it | La app; la credencial solo con la huella del titular (ADR 0017) |
| Where it is captured | Al vincular la huella |
| How it is transmitted | No se transmite en esta historia |
| Where it is stored | `solventa_session`; llave en el Keystore |
| Copies, caches and backups | Ninguna (CTL-03); hasta ahora viajaba en las transferencias (vacío 3 de ADR 0017) |
| How long it is kept | Hasta desvincular la cuenta; una huella nueva borra todas |
| How access is audited | Los ingresos quedan en el backend |
| How it is deleted or anonymized | Se borra la entrada de la cuenta; la llave, compartida por todas, se borra cuando no queda ninguna (vacío 7 de ADR 0017) |
| What happens if it leaks | La credencial no sirve sin la llave y la huella; el correo y el nombre, en claro, sí quedarían expuestos |
| Transfers to third parties | Ninguna |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Asegurado | insured | Autenticado | Ver la copia de su cuenta |
| Otro usuario del teléfono | insured de otra cuenta | Autenticado en su cuenta | Ver solo su propia copia |
| Quien tenga el teléfono | Ninguno | No confiable | Acceso físico; desbloqueado, iniciar una transferencia a un dispositivo propio |
| Malware o root | Ninguno | No confiable | Leer, alterar o borrar el almacenamiento privado y leer logcat |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| Keystore | App y Android Keystore | Teléfono desbloqueado; AES-GCM rechaza una frase envuelta alterada |
| Almacenamiento privado | App y sus archivos | La base solo abre con su frase y el HMAC por página rechaza alteraciones |
| Copias y transferencias | Teléfono y servicio de copias o dispositivo de destino | Las reglas excluyen los nueve dominios (CTL-03) |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| App | Base local | DATA-01 | Archivo privado | CRY-01, CTL-02, CTL-08 |
| App | `solventa_wallet` | DATA-02 | Preferencias privadas | CRY-02, CTL-04, CTL-07 |
| App | Keystore | DATA-02 | API del Keystore | CRY-02, CTL-05, CTL-09 |
| Teléfono | Copia o dispositivo de destino | DATA-01, DATA-02, DATA-03 | Copia de seguridad o transferencia | CTL-03 |

## 6. Third-party services

N/A: no se envían datos a servicios externos; copias y transferencias quedan excluidas (CTL-03).

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| Base local y `solventa_wallet` | Almacenamiento privado | Su contenido, con root |
| Copias y transferencias | Teléfono desbloqueado | Iniciar una transferencia |
| Logcat | Root o depuración | Leer los registros |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | I | DATA-01 | Leer la base extraída de un teléfono perdido | Quien tenga el teléfono | MEDIUM | HIGH | HIGH | CTL-01 |
| THR-02 | T, D | DATA-01, DATA-02 | Una base o frase dañada, o una llave invalidada, cierran la app en cada apertura | Malware o falla del almacenamiento | MEDIUM | MEDIUM | MEDIUM | CTL-02 |
| THR-03 | I | DATA-01, DATA-02, DATA-03 | Una transferencia se lleva la base, la frase y la sesión con correos y nombres en claro | Quien tenga el teléfono desbloqueado | LOW | MEDIUM | LOW | CTL-03 |
| THR-04 | T, I | DATA-01 | Reemplazar la base de una cuenta por la de otra | Otro usuario del teléfono | LOW | MEDIUM | LOW | CTL-04, CTL-02 |
| THR-05 | D | DATA-01 | El bloqueo se toma como llave inválida y borra la copia | Falla de diseño | MEDIUM | MEDIUM | MEDIUM | CTL-05 |
| THR-06 | I | DATA-02 | La frase queda en registros o en memoria, o SQLCipher registra la ruta de la base | Malware o root | LOW | HIGH | MEDIUM | CTL-06, CTL-07 |
| THR-07 | D | DATA-01 | Una cancelación se toma como base dañada y borra la copia | Falla de diseño | MEDIUM | MEDIUM | MEDIUM | CTL-08 |
| THR-08 | D | DATA-01 | Una falla pasajera se toma como invalidez y borra la única copia sin red | Falla de diseño | MEDIUM | MEDIUM | MEDIUM | CTL-09 |
| THR-09 | I | DATA-01, DATA-02 | Al cancelar, la frase se pone en cero mientras Room abre la base, que queda cifrada con ceros | Falla de diseño | LOW | HIGH | MEDIUM | CTL-07, CTL-08 |
| THR-10 | I | DATA-01 | Un guardado que llega tras el cierre de sesión recrea la copia | Falla previa de HU-MOV-04 | LOW | MEDIUM | LOW | CTL-10 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | Base SQLCipher por cuenta con frase de 32 bytes envuelta por AES-GCM del Keystore (`EncryptedPolicyStore.kt:67`, `WalletKeys.kt:60`, `WalletKeys.kt:107`) | MOBILE, CRYPTO | EXISTING | THR-01, DATA-01, DATA-02 | TC-01 | T-01 | MASVS-STORAGE-1, MASVS-CRYPTO-2 | Lo implementa HU-MOV-04 (#211) |
| CTL-02 | Una invalidez real descarta solo esa cuenta, deja la marca si la copia existía y se regenera con la siguiente descarga; nada cierra la app | MOBILE | EXISTING | THR-02, THR-04, DATA-01, DATA-02 | TC-02, TC-03, TC-04, TC-07 | T-02, T-04 | MASVS-STORAGE-1, CWE-755 | Implementado en HU-MOV-05: `EncryptedPolicyStore.kt:47`, `EncryptedPolicyStore.kt:109`, `EncryptedPolicyStore.kt:143`, `WalletKeys.kt:75`, `WalletKeys.kt:123` |
| CTL-03 | Reglas que excluyen los nueve dominios en copias, transferencias y transferencias a iOS | MOBILE | EXISTING | THR-03, DATA-01, DATA-02, DATA-03 | TC-08, TC-09 | T-05 | MASVS-STORAGE-2 | Implementado en HU-MOV-05: `data_extraction_rules.xml`, `AndroidManifest.xml:11` |
| CTL-04 | Frase, llave y archivo por cuenta (`WalletKeys.kt:101`, `EncryptedPolicyStore.kt:59`) | MOBILE, CRYPTO | EXISTING | THR-04, DATA-01 | TC-10 | T-02 | MASVS-CRYPTO-2 | Lo implementa HU-MOV-04; se conserva en los caminos nuevos |
| CTL-05 | Bloqueo consultado antes y después de usar la llave; bloqueado da "no disponible" y la billetera vuelve a leer | MOBILE | EXISTING | THR-05, DATA-01 | TC-05, TC-06 | T-03 | MASVS-STORAGE-1 | Implementado en HU-MOV-05: `WalletKeys.kt:64`, `WalletKeys.kt:71`, `WalletViewModel.kt:73`, `WalletViewModel.kt:87` |
| CTL-06 | Sin registros de la frase, la llave, la ruta ni la copia; registros de SQLCipher apagados | MOBILE | EXISTING | THR-06, DATA-02 | TC-14 | T-02 | MASVS-STORAGE-2, CWE-532 | Implementado en HU-MOV-05: `data/policies` sin registros y `SolventaApplication.kt:40` |
| CTL-07 | La frase se pone en cero siempre, después de que la base la usó | CRYPTO | EXISTING | THR-06, THR-09, DATA-02 | TC-04, TC-11, TC-14 | T-02 | MASVS-CRYPTO-2, CWE-226 | Implementado en HU-MOV-05: `EncryptedPolicyStore.kt:139`, `WalletKeys.kt:131`; la frase se obtiene dentro del turno sin cancelación (`EncryptedPolicyStore.kt:82`) |
| CTL-08 | La base se usa sin cancelación, con `ensureActive()` antes y después; una cancelación nunca descarta | MOBILE | EXISTING | THR-07, THR-09, DATA-01 | TC-11 | T-02 | CWE-755 | Implementado en HU-MOV-05: `EncryptedPolicyStore.kt:80`, `EncryptedPolicyStore.kt:81`, `EncryptedPolicyStore.kt:82`, `EncryptedPolicyStore.kt:84` |
| CTL-09 | Solo una invalidez real descarta; cualquier otra falla da "no disponible" | MOBILE | EXISTING | THR-08, DATA-01 | TC-04, TC-05 | T-02, T-03 | MASVS-STORAGE-1, CWE-755 | Implementado en HU-MOV-05: `WalletKeys.kt:71`, `WalletKeys.kt:97`, `EncryptedPolicyStore.kt:143` |
| CTL-10 | La billetera se cierra antes de confirmar el cierre de sesión y las operaciones de cada cuenta van en serie; el borrado no se cancela | MOBILE | EXISTING | THR-10, DATA-01 | TC-12 | T-08 | MASVS-STORAGE-2, CWE-362 | Implementado en HU-MOV-05: `MainActivity.kt:124`, `EncryptedPolicyStore.kt:36`, `EncryptedPolicyStore.kt:72` |

## 10. Cryptography

### CRY-01 · Base local cifrada

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | AT_REST |
| Algorithm or service | SQLCipher 4: AES-256-CBC por página con HMAC-SHA512 |
| Rationale | Cifra la copia y detecta alteraciones de cada página |
| Key management | Frase de 32 bytes por cuenta envuelta por CRY-02; en claro solo durante cada operación |
| Permissions | Solo la app |
| Rotation | Nueva con cada copia y cada descarte |
| Recovery | Base dañada: se borra y se crea de nuevo con las pólizas del servidor (CTL-02) |
| Failure behavior | La app no se cierra; sin red avisa del descarte (AC-08) |
| Tests | TC-01, TC-03, TC-11 |

### CRY-02 · Envoltura de la frase

| Field | Value |
|---|---|
| Protects | DATA-02 |
| Type | APP_LEVEL |
| Algorithm or service | AES-256-GCM con una llave del Android Keystore por cuenta, no exportable y usable solo desbloqueado |
| Rationale | La frase nunca queda en claro en el almacenamiento |
| Key management | Llave por cuenta; IV aleatorio guardado con la frase envuelta |
| Permissions | Solo la app |
| Rotation | Se crea con la copia y se elimina con ella |
| Recovery | Invalidez real: descarte y frase y llave nuevas (CTL-02); bloqueo o falla pasajera: "no disponible" (CTL-05, CTL-09) |
| Failure behavior | La app no se cierra |
| Tests | TC-01, TC-02, TC-04, TC-05, TC-06 |

### CRY-03 · Envoltura de la sesión guardada

| Field | Value |
|---|---|
| Protects | DATA-03 |
| Type | APP_LEVEL |
| Algorithm or service | AES-256-GCM con una llave del Keystore que exige BIOMETRIC_STRONG y se invalida con huellas nuevas (ADR 0017) |
| Rationale | Sin cambios; esta historia solo impide que salga del teléfono |
| Key management | Una llave para todas las cuentas (`KeystoreSessionCrypto.kt`, vacío 7 de ADR 0017) |
| Permissions | Solo la app, tras la huella |
| Rotation | Nueva cuando no existe |
| Recovery | Invalidada: se borra con todas las cuentas vinculadas (HU-MOV-03) |
| Failure behavior | El de HU-MOV-03 |
| Tests | TC-08, TC-09 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| Llave de envoltura por cuenta | Android Keystore | La app, desbloqueado | Cada teléfono | Con cada copia | Borrar la copia | No exportable |
| Frase de la base por cuenta | `solventa_wallet`, envuelta | La app | Cada teléfono | Con cada copia | Borrar la copia | No se registra (CTL-06) |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | NO | — | No cambia |
| Secure authentication | NO | — | No cambia el ingreso |
| Session management | YES | CTL-03 | La sesión deja de viajar; en un teléfono nuevo se entra con contraseña y código (HU-MOV-02) |
| Token expiration | NO | — | Sin tokens nuevos |
| Revocation | YES | CTL-10 | Una descarga en curso ya no deshace el borrado al cerrar sesión |
| Server-side permission checks | NO | — | Sin cambios en el backend |
| Per-resource access (ownership) | NO | — | Aislamiento por cuenta sin cambios (CTL-04) |
| Roles | NO | — | Un solo rol |
| Privilege separation | NO | — | Sin operaciones privilegiadas |
| Brute-force protection | NO | — | Sin credenciales nuevas |
| Rate limiting | NO | — | Sin endpoints nuevos |
| One-time code (OTP) handling | NO | — | Sin códigos |
| User enumeration prevention | NO | — | Sin entradas que identifiquen cuentas |
| CSRF protection | NO | — | Sin web |

## 13. Data isolation and multi-tenancy

N/A: canal del asegurado, sin inquilinos.

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | net.zetetic:sqlcipher-android | 4.9.0 | movil | Cifrado de la base; sin cambio | CRITICAL | OSV.dev 2026-10-25 | No known vulnerabilities | EVALUATED |
| DEP-02 | androidx.room:room-runtime | 2.6.1 | movil | Base local; sin cambio | HIGH | OSV.dev 2026-10-25 | No known vulnerabilities | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | Con root se puede dañar la base para que se descarte | THR-02 | LOW | MEDIUM | LOW | ACCEPT | Solo cuesta una descarga; aceptado por David Gutiérrez el 2026-10-25 (DEC-03) | ACCEPTED |
| RISK-02 | Herramientas de migración de fabricantes podrían ignorar las reglas | THR-03 | LOW | LOW | LOW | MITIGATE | Sin las llaves nada abre allá, pero el correo y el nombre de la sesión sí se verían | MITIGATED |
| RISK-03 | El teléfono se desbloquea entre el fallo de la llave y la consulta posterior | THR-05 | LOW | MEDIUM | LOW | MITIGATE | El bloqueo también se consulta antes y un fallo por bloqueo no es invalidez real; el peor caso es una descarga | MITIGATED |
| RISK-04 | Fallas permanentes no deterministas, como un `SQLiteCantOpenDatabaseException` fijo, dejan la copia no disponible | THR-08 | LOW | MEDIUM | LOW | MITIGATE | Con red se ven los datos del servidor; sin red se ofrece reintentar (AC-13) | MITIGATED |
| RISK-05 | Un fin de sesión fuera del diálogo (cambio de contraseña, huellas invalidadas) o la muerte del proceso durante un guardado podrían dejar una copia | THR-10 | LOW | MEDIUM | LOW | MITIGATE | El borrado espera al guardado (CTL-10); la copia queda cifrada y solo se ve con la huella de esa cuenta | MITIGATED |

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Alcance de AC-09 | Toda la app; solo la billetera | Toda la app | Responsable de la historia | NO | APPROVED |
| DEC-02 | Dónde se distingue el bloqueo | HU-MOV-05; HU-MOV-07 | HU-MOV-05 | Responsable de la historia | NO | APPROVED |
| DEC-03 | Aceptar RISK-01 | Aceptar; otra mitigación | Aceptar | Responsable de la historia | NO | APPROVED |
| DEC-04 | Carrera al cerrar sesión | Aceptarla; corregirla aquí | Se recomendó aceptarla; el responsable eligió corregirla (AC-12) | Responsable de la historia | NO | APPROVED |

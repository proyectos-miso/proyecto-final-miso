# Security and privacy assessment · HU-MOV-07

## 1. Data inventory and classification

| ID | Data | Classification | Personal | Sensitive | Purpose |
|---|---|---|---|---|---|
| DATA-01 | Copia local de las pólizas de cada cuenta, con la hora de sincronización | CONFIDENTIAL | YES | NO | Billetera sin conexión; esta historia la actualiza en segundo plano |
| DATA-02 | Tokens de la sesión: access token (15 minutos) y refresh token (7 días, rotado en cada renovación) | RESTRICTED | YES | NO | Pedir las pólizas y renovar la sesión |
| DATA-03 | Credencial del teléfono vinculado, en claro solo en memoria durante un ingreso sin red | RESTRICTED | NO | NO | Obtener los tokens al volver la red sin pedir la huella |
| DATA-04 | Trabajo pendiente en la base de WorkManager: clase, restricciones, estado e intentos | INTERNAL | NO | NO | Que Android ejecute la sincronización con red |

La suma asegurada es una condición del contrato, no un dato financiero (HU-MOV-05). Los tokens son JWT con el identificador de la cuenta. DATA-04 no lleva datos de la cuenta (CTL-02).

## 2. Data lifecycle

### DATA-01 · Copia local de la billetera

| Question | Answer |
|---|---|
| What it contains | Los campos de la tarjeta, sin documento, beneficiarios ni prima (HU-MOV-04) |
| Why it is needed | Demostrar la cobertura sin señal |
| Who can access it | La app con el teléfono desbloqueado; sin red, tras la huella del titular |
| Where it is captured | En cada sincronización exitosa, también en segundo plano |
| How it is transmitted | Llega del backend por HTTPS (CRY-01) |
| Where it is stored | `wallet_<id>.db`, SQLCipher, almacenamiento privado (CRY-02) |
| Copies, caches and backups | Ninguna (reglas de extracción de HU-MOV-05) |
| How long it is kept | Hasta el cierre de sesión, un rechazo del servidor o una invalidez real; un bloqueo de la cuenta o una renovación sin respuesta no la borran (CTL-06, CTL-10) |
| How access is audited | El backend registra la consulta que la origina |
| How it is deleted or anonymized | Se borran el archivo, la frase envuelta y la llave de la cuenta (HU-MOV-05) |
| What happens if it leaks | Sin la llave no se lee; con ella, coberturas y vigencias de una persona |
| Transfers to third parties | Ninguna |

### DATA-02 · Tokens de la sesión

| Question | Answer |
|---|---|
| What it contains | Access token y refresh token del asegurado |
| Why it is needed | Autorizar la consulta y renovar la sesión |
| Who can access it | Solo la app, en memoria (ADR 0017) |
| Where it is captured | Al ingresar y en cada renovación |
| How it is transmitted | En `Authorization` y en el cuerpo de `auth/refresh` y `auth/logout`, por HTTPS (CRY-01, CTL-11) |
| Where it is stored | Solo en la instantánea en memoria de `SecureSessionRepository`; nunca en disco, en WorkManager ni en registros (CTL-02) |
| Copies, caches and backups | Ninguna |
| How long it is kept | Hasta el cierre de sesión, un rechazo del servidor, que termine la actividad o que Android cierre la app; el backend no fija vida máxima (RISK-04) |
| How access is audited | El backend registra los ingresos; las renovaciones no se auditan (`identity/service.py:240`) |
| How it is deleted or anonymized | Se sueltan las referencias; el refresh token se revoca al cerrar sesión, al terminar la actividad y cuando una renovación termina después del cierre (CTL-03, CTL-05) |
| What happens if it leaks | Acceso a las pólizas mientras el refresh token se siga renovando (RISK-04) |
| Transfers to third parties | Ninguna |

### DATA-03 · Credencial del teléfono en memoria

| Question | Answer |
|---|---|
| What it contains | Identificador del dispositivo y su secreto (HU-MOV-03) |
| Why it is needed | Obtener los tokens al volver la red tras un ingreso sin red (AC-07) |
| Who can access it | Solo la app, en memoria |
| Where it is captured | Al descifrarla con la huella en el ingreso sin red |
| How it is transmitted | En `auth/biometric`, por HTTPS (CRY-01, CTL-11) |
| Where it is stored | En memoria; en disco sigue cifrada con la llave biométrica (ADR 0017) |
| Copies, caches and backups | Ninguna |
| How long it is kept | A lo sumo 15 minutos con `elapsedRealtime`; antes si llegan los tokens, se cierra sesión, se desactiva la huella o cambia la llave (CTL-04) |
| How access is audited | El backend registra el ingreso con la credencial |
| How it is deleted or anonymized | Se suelta la referencia; las cadenas de Kotlin no se pueden poner en cero (RISK-01) |
| What happens if it leaks | Permite ingresar hasta desactivar la huella o cambiar la contraseña |
| Transfers to third parties | Ninguna |

## 3. Actors and roles

| Actor | Role | Trust level | What they can do |
|---|---|---|---|
| Asegurado | insured | Autenticado | Ver su copia y cerrar sesión |
| Otro usuario del teléfono | insured de otra cuenta | Autenticado en su cuenta | Entrar después de que el anterior cerró sesión |
| Quien tenga el teléfono | Ninguno | No confiable | Prender y apagar la red; usar la app abierta |
| Quien conozca el correo | Ninguno | No confiable | Suspender el ingreso de una cuenta con contraseñas malas |
| Quien prepare una build | Equipo | Confiable con errores posibles | Compilar una release con otra URL |
| Malware o root | Ninguno | No confiable | Leer la memoria, el almacenamiento privado y logcat |
| Backend de Solventa | Servidor | Confiable | Responder pólizas, renovar tokens y validar la credencial |

## 4. Trust boundaries

| Boundary | Between | What is validated when crossing it |
|---|---|---|
| Red | App y backend | HTTPS en release; el backend valida el token, la rotación, el bloqueo y la credencial |
| Planificador | App y WorkManager | Solo restricciones y estado; al correr, el trabajo lee la sesión en memoria |
| Hilos de la app | Hilo principal y sincronización | `compareAndSet` sobre la instantánea y una sincronización a la vez (CTL-05) |
| Almacenamiento privado | App y sus archivos | La copia abre solo con su frase (CRY-02); WorkManager no guarda datos de la cuenta |

## 5. Data flows

| Source | Destination | Data | Channel | Protection |
|---|---|---|---|---|
| App | Backend | DATA-02 | `auth/refresh` e `insured/policies` | CRY-01, CTL-05, CTL-06, CTL-11 |
| App | Backend | DATA-03 | `auth/biometric` | CRY-01, CTL-04, CTL-05, CTL-11 |
| Backend | App | DATA-01, DATA-02 | Respuestas HTTPS | CRY-01, CTL-07, CTL-09, CTL-10 |
| App | Base local | DATA-01 | Archivo privado | CRY-02, CTL-03, CTL-09 |
| App | Base de WorkManager | DATA-04 | `no_backup/androidx.work.workdb` | CTL-01, CTL-02 |

## 6. Third-party services

N/A: no se envían datos a servicios externos; WorkManager corre en el teléfono.

## 7. Attack surface

| Surface | Exposure | Inputs the attacker controls |
|---|---|---|
| Cambios de red | Quien tenga el teléfono | Cuándo y cuántas veces vuelve la red |
| `auth/login` | Público | Contraseñas malas para suspender una cuenta |
| Componentes de WorkManager | `SystemJobService` con `BIND_JOB_SERVICE` y `DiagnosticsReceiver` con `DUMP`, solo para el sistema | Ninguno sin permisos del sistema |
| Memoria y almacenamiento | Root o depuración | Leer tokens, credencial y la base de WorkManager |
| Respuestas del backend | HTTPS | Ninguno sin romper TLS; un servidor con fallas puede repetir pólizas |
| Build release | Equipo | La URL del backend |

## 8. Threat model

| ID | STRIDE | Asset | Threat | Actor | Likelihood | Impact | Risk | Controls |
|---|---|---|---|---|---|---|---|---|
| THR-01 | D | DATA-01 | Muchos cambios de red, o muchos teléfonos a la vez, generan ráfagas contra el backend | Quien tenga el teléfono | MEDIUM | MEDIUM | MEDIUM | CTL-01, CTL-05 |
| THR-02 | I | DATA-02, DATA-03, DATA-04 | Datos de la cuenta o secretos en la base de WorkManager o en registros | Malware o root | LOW | HIGH | MEDIUM | CTL-02 |
| THR-03 | I | DATA-01 | La copia reaparece o no se borra tras el cierre de sesión, o una sesión que quedó en memoria al terminar la actividad sigue renovándose | Otro usuario del teléfono | MEDIUM | MEDIUM | MEDIUM | CTL-03 |
| THR-04 | S, I | DATA-03 | Sacar la credencial de la memoria con la app abierta sin red, o usarla sin una huella nueva mucho después | Malware o root | LOW | HIGH | MEDIUM | CTL-04 |
| THR-05 | S, E, D | DATA-02 | Renovaciones que se cruzan: una que termina tras el cierre revive la sesión o deja un par sin revocar, y dos a la vez con el mismo token cierran la sesión | Falla de diseño | MEDIUM | HIGH | HIGH | CTL-05 |
| THR-06 | D | DATA-01 | Una falla de red, un 429, un 423 o una renovación sin respuesta se toman como revocación y borran la copia | Falla de diseño o red intermitente | MEDIUM | MEDIUM | MEDIUM | CTL-06 |
| THR-07 | T, D | DATA-01 | Pólizas repetidas en la respuesta hacen fallar cada reintento | Falla del servidor | LOW | LOW | LOW | CTL-07 |
| THR-08 | E | DATA-04 | Permisos y componentes de WorkManager que no se usan | Falla de configuración | LOW | LOW | LOW | CTL-08 |
| THR-09 | I | DATA-01 | Al cambiar de cuenta, la sincronización guarda o muestra en la otra cuenta, o un rechazo tardío termina la otra sesión | Otro usuario del teléfono | LOW | MEDIUM | LOW | CTL-09 |
| THR-10 | D | DATA-01 | Suspender el ingreso de otra persona para que su renovación reciba un 401 y borre la copia | Quien conozca el correo | MEDIUM | MEDIUM | MEDIUM | CTL-10 |
| THR-11 | I | DATA-02, DATA-03 | Una release sin URL `https` envía secretos sin cifrar | Quien prepare una build | LOW | HIGH | MEDIUM | CTL-11 |
| THR-12 | S, E | DATA-02 | Sin vida máxima ni revocación por familia en el backend, un refresh token robado se renueva sin fin | Malware o root | LOW | HIGH | MEDIUM | CTL-02 |

## 9. Controls

| ID | Control | Domain | Status | Mitigates | Tests | Tasks | References | Rationale |
|---|---|---|---|---|---|---|---|---|
| CTL-01 | Un trabajo único con `KEEP`, solo con red y con espera exponencial desde 10 s hasta 5 horas; al volver a la app, a lo sumo cada 30 s | MOBILE | EXISTING | THR-01, DATA-04 | TC-03 | T-05, T-06 | MASVS-RESILIENCE, CWE-770 | Implementado en HU-MOV-07: `PolicySyncWorker.kt:54` y `WalletViewModel.kt:102` (HA-08) |
| CTL-02 | Sin datos de entrada ni etiquetas en el trabajo, sin registros de datos de la cuenta, excepciones atrapadas y `OpenSession` sin datos en `toString` | MOBILE | EXISTING | THR-02, THR-12, DATA-02, DATA-03, DATA-04 | TC-10, TC-14, TC-15 | T-03, T-05 | MASVS-STORAGE-2, CWE-532 | Implementado en HU-MOV-07: `PolicySyncWorker.kt:54` sin datos de entrada, `PolicySyncWorker.kt:35` y `SecureSessionRepository.kt:30`. La base de WorkManager no está cifrada; para THR-12 solo baja la probabilidad del robo (RISK-04) |
| CTL-03 | Cerrar sesión termina la sesión, cancela y borra la copia enseguida, y luego revoca, en `appScope`; al terminar la actividad se termina y revoca solo su sesión; sin sesión no se llama al servidor | MOBILE | EXISTING | THR-03, DATA-01 | TC-08, TC-09 | T-06 | MASVS-STORAGE-2, CWE-362 | Implementado en HU-MOV-07: `AuthViewModel.kt:441`, `AuthViewModel.kt:448`, `AuthViewModel.kt:469` y `SessionPolicySync.kt:78` |
| CTL-04 | La credencial vive solo en memoria, a lo sumo 15 minutos con `elapsedRealtime`; no se usa si la llave biométrica cambió; se suelta con una instantánea nueva; vencida y con red, se pide el ingreso sin borrar la copia | MOBILE, SECRETS | EXISTING | THR-04, DATA-03 | TC-06, TC-07 | T-03, T-04, T-06 | MASVS-STORAGE-1, MASVS-AUTH-2, CWE-316 | Implementado en HU-MOV-07: `AuthViewModel.kt:236`, `SessionPolicySync.kt:89` y `SecureSessionRepository.kt:75` (DEC-03) |
| CTL-05 | Una sincronización a la vez bajo un `Mutex`; transiciones con `compareAndSet` sobre la instantánea; obtención de tokens sin cancelación y revocación del par si la sesión cambió | MOBILE | EXISTING | THR-01, THR-05, DATA-02 | TC-04 | T-03, T-04 | MASVS-AUTH-2, CWE-362, CWE-613 | Implementado en HU-MOV-07: `SessionPolicySync.kt:59`, `SessionPolicySync.kt:148`, `SessionPolicySync.kt:133` y `SecureSessionRepository.kt` (`compareAndSet`) |
| CTL-06 | Solo un 401 de la renovación, un segundo 401 o una credencial rechazada terminan la sesión, y la credencial rechazada desvincula siempre; tras una renovación sin respuesta el 401 no borra la copia y la marca se limpia al renovar; lo demás se reintenta | MOBILE | EXISTING | THR-06, DATA-01 | TC-05 | T-04 | MASVS-AUTH-2, CWE-755 | Implementado en HU-MOV-07: `SessionPolicySync.kt:137`, `SessionPolicySync.kt:121` (DEC-08) |
| CTL-07 | Las pólizas repetidas se guardan una vez | MOBILE, DATA | EXISTING | THR-07, DATA-01 | TC-02 | T-04 | CWE-20 | Implementado en HU-MOV-07: `EncryptedPolicyStore.kt:44` (HA-11) |
| CTL-08 | Sin `RECEIVE_BOOT_COMPLETED` ni `FOREGROUND_SERVICE`; WorkManager solo exporta lo que protege el sistema | MOBILE | EXISTING | THR-08, DATA-04 | TC-11 | T-05 | MASVS-PLATFORM-1 | Implementado en HU-MOV-07: `AndroidManifest.xml:9`; tras reiniciar no hay sesión |
| CTL-09 | La sincronización usa la instantánea que tomó tras obtener el turno y comprueba antes de guardar que no cambió; la billetera solo muestra su cuenta | MOBILE | EXISTING | THR-09, DATA-01 | TC-13 | T-04, T-06 | MASVS-STORAGE-1, CWE-639 | Implementado en HU-MOV-07: `SessionPolicySync.kt:155` y `WalletViewModel.kt:121` |
| CTL-10 | Con el ingreso suspendido, `auth/refresh` responde 423 sin revocar ni auditar, después de validar el jti y la versión; la app reintenta | BACKEND, API | EXISTING | THR-10, DATA-01 | TC-05, TC-17 | T-01 | ASVS V2.2, CWE-645 | Implementado en HU-MOV-07: `identity/service.py:251` y `bff/auth/router.py:123`; la app lo trata en T-04 (DEC-06) |
| CTL-11 | Release sin HTTP sin TLS y `preReleaseBuild` exige `https://` | MOBILE, INFRA | EXISTING | THR-11, DATA-02, DATA-03 | TC-18 | T-02 | MASVS-NETWORK-1, CWE-319 | Implementado en HU-MOV-07: `movil/app/build.gradle.kts:86`, `src/main/res/xml/network_security_config.xml` y `src/debug/res/xml/network_security_config.xml` (DEC-09) |

## 10. Cryptography

### CRY-01 · Transporte al backend

| Field | Value |
|---|---|
| Protects | DATA-01, DATA-02, DATA-03 |
| Type | TLS |
| Algorithm or service | TLS de la plataforma (ADR 0016); Android 13 negocia TLS 1.3 con CloudFront. La mínima no está fijada: CloudFront con su certificado por defecto (`terraform/modules/web_hosting/main.tf:191`) acepta versiones anteriores a TLS 1.2, un vacío de infraestructura fuera de esta historia |
| Rationale | Esta historia agrega `auth/refresh` y el ingreso en segundo plano, y obliga a HTTPS en release (CTL-11) |
| Key management | Certificados del sistema |
| Permissions | La app |
| Rotation | La del certificado del servidor |
| Recovery | Una falla de TLS es una falla de red y se reintenta |
| Failure behavior | No se envía el token; la billetera sigue con la copia |
| Tests | TC-04, TC-18 |

### CRY-02 · Copia local cifrada

| Field | Value |
|---|---|
| Protects | DATA-01 |
| Type | AT_REST |
| Algorithm or service | SQLCipher 4 con la frase envuelta por el Keystore (HU-MOV-05) |
| Rationale | Sin cambios; la sincronización escribe por el mismo almacén |
| Key management | El de HU-MOV-05; bloqueado no se escribe y se reintenta |
| Permissions | Solo la app |
| Rotation | La de HU-MOV-05 |
| Recovery | La de HU-MOV-05 |
| Failure behavior | Sin guardar, se reintenta y la fecha no cambia |
| Tests | TC-01 |

## 11. Secrets and keys

| Secret | Location | Access | Environments | Rotation | Revocation | Leak detection |
|---|---|---|---|---|---|---|
| Access token | Memoria | La app | Cada teléfono | Cada renovación; vence a los 15 minutos | Vence solo | `toString` lo oculta; no se registra |
| Refresh token | Memoria | La app | Cada teléfono | Cada renovación | `auth/logout` al cerrar sesión, al terminar la actividad y al descartar un par (CTL-03, CTL-05) | `toString` lo oculta; no se registra |
| Credencial del teléfono | Memoria hasta 15 minutos; en disco, cifrada con la llave biométrica | La app | Cada teléfono | Al vincular otra vez | Desactivar la huella o cambiar la contraseña | `toString` oculta el secreto; no se registra |

## 12. Authentication, authorization and sessions

| Check | Applies | Controls | Rationale |
|---|---|---|---|
| User identification | NO | — | No cambia |
| Secure authentication | YES | CTL-04, CTL-06 | La credencial se usa a lo sumo 15 minutos tras la huella; rechazada, se desvincula |
| Session management | YES | CTL-03, CTL-05 | `compareAndSet`, una renovación por sincronización y sin revivir tras el cierre |
| Token expiration | YES | CTL-05, CTL-06 | El access token se renueva una vez ante un 401; sin vida máxima en el backend (RISK-04) |
| Revocation | YES | CTL-03, CTL-05, CTL-06 | El cierre revoca y cancela; un rechazo del servidor termina la sesión |
| Server-side permission checks | YES | CTL-10 | 423 con el ingreso suspendido; lo demás no cambia |
| Per-resource access (ownership) | YES | CTL-09 | La copia se guarda en la cuenta de la instantánea |
| Roles | NO | — | Un solo rol |
| Privilege separation | NO | — | Sin operaciones privilegiadas |
| Brute-force protection | YES | CTL-10 | La suspensión sigue igual, pero ya no borra la copia de otra persona |
| Rate limiting | YES | CTL-01, CTL-05 | El backend no limita `auth/refresh`; la app limita sus intentos |
| One-time code (OTP) handling | NO | — | Sin códigos |
| User enumeration prevention | NO | — | El 423 solo lo recibe quien tiene un refresh token válido |
| CSRF protection | NO | — | Sin web |

## 13. Data isolation and multi-tenancy

N/A: canal del asegurado, sin inquilinos.

## 14. Dependencies

| ID | Dependency | Version | Component | Use | Criticality | Evaluation | Result | Status |
|---|---|---|---|---|---|---|---|---|
| DEP-01 | androidx.work:work-runtime | 2.10.5 | movil | Ejecutar la sincronización con red y reintentos | HIGH | OSV.dev 2026-10-25; metadatos en Google Maven | No known vulnerabilities; compileSdk 35 y `room-ktx` 2.6.1, la de la app (las 2.11 y 2.12 suben Room a 2.7) | EVALUATED |
| DEP-02 | androidx.work:work-testing | 2.10.5 | movil (pruebas) | WorkManager en las pruebas | LOW | OSV.dev 2026-10-25 | No known vulnerabilities | EVALUATED |
| DEP-03 | androidx.room:room-runtime | 2.6.1 | movil | Base de WorkManager y copia; sin cambio | HIGH | OSV.dev 2026-10-25 | No known vulnerabilities | EVALUATED |
| DEP-04 | Transitivas de DEP-01: androidx.startup:startup-runtime 1.1.1, androidx.lifecycle:lifecycle-service 2.6.2, androidx.lifecycle:lifecycle-livedata 2.6.2, androidx.concurrent:concurrent-futures-ktx 1.1.0, androidx.tracing:tracing-ktx 1.2.0, com.google.guava:listenablefuture 1.0 | Las indicadas | movil | Las usa WorkManager; Gradle puede resolver versiones mayores | MEDIUM | OSV.dev 2026-10-25 | No known vulnerabilities | EVALUATED |

## 15. Risk register and residual risk

| ID | Risk | Threats | Likelihood | Impact | Level | Treatment | Residual risk | Status |
|---|---|---|---|---|---|---|---|---|
| RISK-01 | La credencial vive en memoria hasta 15 minutos tras la huella: con root se puede copiar, sus cadenas no se pueden poner en cero y se usa sin una huella nueva, una desviación de "biometría en cada uso" | THR-04 | LOW | HIGH | MEDIUM | ACCEPT | Nunca en disco y con tope (CTL-04). Aceptado por David Gutiérrez el 2026-10-25 (DEC-01), vence el 2026-12-31 | ACCEPTED |
| RISK-02 | Si se pierde la respuesta de una renovación, el backend ya rotó el token y la siguiente cierra la sesión | THR-06 | MEDIUM | MEDIUM | MEDIUM | MITIGATE | Con CTL-06 la copia se conserva y solo hay que volver a ingresar | OPEN |
| RISK-03 | Un par descartado tras el cierre queda válido si falla su revocación | THR-05 | LOW | LOW | LOW | MITIGATE | La revocación siempre se intenta (CTL-05); si falla, nadie guarda el token y vence a los 7 días si no se usa | OPEN |
| RISK-04 | El backend no fija vida máxima ni revoca por familia; un refresh token robado se renueva sin fin | THR-12 | LOW | HIGH | MEDIUM | ACCEPT | Tokens solo en memoria y sin registros; el cierre revoca el actual; la corrección va en otra historia (DEC-07). Aceptado por David Gutiérrez el 2026-10-25 (DEC-01), vence el 2026-12-31 | ACCEPTED |

## 16. Pending decisions

| ID | Decision | Options | Recommendation | Decider | Blocks gate | Status |
|---|---|---|---|---|---|---|
| DEC-01 | Aceptar RISK-01 y RISK-04 | Aceptar con vencimiento; mitigarlos aquí | Aceptar hasta el 2026-12-31 | Responsable de la historia | NO | APPROVED |
| DEC-02 | Cómo sincronizar en segundo plano | WorkManager; callback de red propio | WorkManager: lo pide #198, corre aunque Android congele la app y lo reutiliza HU-MOV-09 | Responsable de la historia | NO | APPROVED |
| DEC-03 | Ingreso sin red | Credencial en memoria 15 minutos y, al vencer con red, pedir el ingreso sin borrar la copia; pedir la huella al volver la red; un tope más largo | La primera, sin acción del usuario dentro del tope (AC-07) | Responsable de la historia | NO | APPROVED |
| DEC-04 | Renovación del token | En esta historia; en otra | En esta: sin ella AC-01 falla a los 15 minutos | Responsable de la historia | NO | APPROVED |
| DEC-05 | Base de la rama | HU-MOV-05; esperar su fusión | HU-MOV-05: usa su almacén | Responsable de la historia | NO | APPROVED |
| DEC-06 | Renovación con el ingreso suspendido | 423 en el backend; 401 sin borrar en la app | 423: conserva el borrado ante una revocación real | Responsable de la historia | NO | APPROVED |
| DEC-07 | Vida máxima y revocación por familia | Otra historia; esta | Otra historia, creada por una persona en el tablero | Responsable de la historia | NO | APPROVED |
| DEC-08 | Renovación sin respuesta | No borrar la copia; aceptar el riesgo | No borrar la copia | Responsable de la historia | NO | APPROVED |
| DEC-09 | HTTPS en release | Corregir aquí; dejarlo como riesgo | Corregir aquí | Responsable de la historia | NO | APPROVED |

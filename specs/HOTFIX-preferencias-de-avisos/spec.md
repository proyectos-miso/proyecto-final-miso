# HOTFIX · Preferencias de avisos

| Field | Value |
|---|---|
| ID | HOTFIX |
| Title | Preferencias de avisos |
| User story | none: mejora del feature FE-MOV-04 (#12) sin historia en el tablero, acordada el 2026-11-14 |
| Owner | Juan Camilo Peña |
| Status | IN_PROGRESS |
| Created | 2026-11-14 |
| Security profile | HIGH |

## 1. Requirements

### User story

Mejora (no es una historia del tablero). FE-MOV-04 pide, además de los avisos que ya entregó HU-MOV-11, que el
cliente pueda desactivar una categoría de avisos y que su preferencia se conserve (criterio 4 del feature).

**Como** cliente asegurado **quiero** elegir qué avisos recibo (siniestros, vencimientos, pagos) **para** no recibir
los que no me interesan sin perder los importantes.

Qué ya existe: push de cambio de estado del siniestro y de vencimiento (HU-MOV-11); el pago de un siniestro,
incluido el paramétrico, ya avisa (`PAID`); los avisos locales al sincronizar.

### Problem and outcome

Problema: el asegurado recibe todos los avisos o ninguno (negando el permiso del sistema). Resultado: tres
categorías que se apagan por separado, guardadas en el servidor y respetadas también por los avisos locales.

### Goals and non-goals

Objetivos: preferencias por categoría en el backend con sus endpoints; el envío respeta la preferencia; pantalla de
ajustes en la app; los avisos locales de la sincronización también la respetan.

Fuera de alcance: avisos por SMS o correo; preferencias por póliza; horarios silenciosos.

### Affected paths

| Path | Change |
|---|---|
| backend/app/migrations/versions/0004_preferencias_de_avisos.py | Tabla `push_preferences` |
| backend/app/modules/notifications/service.py | Modelo, lectura y guardado de preferencias; `notify` filtra por categoría y la envía en `data` |
| backend/app/bff/insured/router.py | `GET` y `PUT /insured/devices/push-preferences` |
| backend/app/main.py | Registro de la tabla |
| backend/tests/integration/test_push_preferences_api.py | Pruebas de los endpoints y del filtro |
| backend/tests/integration/test_insured_claims_api.py | El push de siniestro y vencimiento lleva su categoría |
| backend/tests/integration/test_migrations.py | Migración `0004` y su reversión |
| movil/app/src/main/java/com/solventa/app/domain/policies/PolicyActions.kt | Contrato de las preferencias y categorías |
| movil/app/src/main/java/com/solventa/app/data/policies/HttpPolicyActionsApi.kt | GET y PUT de las preferencias |
| movil/app/src/main/java/com/solventa/app/data/policies/SessionPolicyActions.kt | Las mismas llamadas con la sesión |
| movil/app/src/main/java/com/solventa/app/data/policies/SessionPolicySync.kt | Trae o envía las preferencias en cada sincronización y filtra los avisos locales por categoría |
| movil/app/src/main/java/com/solventa/app/data/settings/NotificationPreferenceStore.kt | Copia local de las preferencias por cuenta |
| movil/app/src/main/java/com/solventa/app/ui/settings/NotificationPreferencesScreen.kt | Pantalla de preferencias |
| movil/app/src/main/java/com/solventa/app/ui/settings/NotificationPreferencesViewModel.kt | Estado de la pantalla y guardado pendiente |
| movil/app/src/main/java/com/solventa/app/ui/wallet/WalletScreen.kt | Botón «Avisos» |
| movil/app/src/main/java/com/solventa/app/MainActivity.kt | Navegación |
| movil/app/src/main/java/com/solventa/app/SolventaApplication.kt | Dependencias |
| movil/app/src/main/res/values/strings.xml | Textos |
| movil/app/src/main/res/drawable/ic_bell.xml | Ícono del acceso a «Avisos» |
| movil/app/src/main/java/com/solventa/app/data/notifications/SolventaMessagingService.kt | Descarta un push de una categoría apagada |
| movil/app/src/main/java/com/solventa/app/ui/auth/AuthViewModel.kt | Borra la copia local al cerrar sesión, revocar la huella o cambiar la contraseña |
| movil/app/src/test/** | Pruebas de la app |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Asegurado | Apagar una categoría | 1. Abre «Avisos» en la billetera. 2. Apaga «Pagos». 3. Ve «Guardado» | Sin señal: ve la copia local y se guarda al volver la red |
| UC-02 | Sistema | Enviar un aviso | 1. Ocurre un cambio. 2. Lee la preferencia del asegurado. 3. Envía solo si la categoría está encendida | Sin preferencia guardada: envía todo |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Cambiar las preferencias de otra persona | Asegurado | THR-01 | El documento sale de la cuenta del token; no se acepta ninguno del cliente | TC-01 |
| AB-02 | Mandar un cuerpo inválido | Asegurado | THR-02 | 422 | TC-01 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | THE asegurado SHALL poder encender o apagar «Siniestros», «Vencimientos» y «Pagos» y la preferencia SHALL conservarse en el servidor | STATED |
| AC-02 | WHEN una categoría está apagada, THE backend SHALL no enviar ese push y THE app SHALL no mostrar el aviso local equivalente | STATED |
| AC-03 | WHILE no hay preferencia guardada, THE sistema SHALL enviar todas las categorías | DERIVED: nadie pierde avisos por no haber entrado a ajustes |
| AC-04 | WHILE no hay señal, THE pantalla SHALL mostrar la copia local y guardar el cambio al recuperar la red | DERIVED: la billetera funciona sin conexión (HU-MOV-04) |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | ACCESSIBILITY | Pantalla de preferencias | TalkBack lee cada interruptor con su estado; objetivos de 48 dp |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | Preferencia ligada al documento |
| Sensitive data (special categories, biometrics, health, financial, minors) | NO | Tres booleanos |
| Authentication, sessions, credentials or one-time codes | YES | Endpoints con el token del asegurado |
| Authorization or access to other users' resources | YES | Solo las preferencias propias |
| Payments or money movement | NO | No aplica |
| Third-party services or integrations | NO | Firebase ya existe |
| Infrastructure, network, IAM, CI/CD or secrets | NO | Sin secretos ni infraestructura nuevos; la llave de Firebase es de HU-MOV-11 |
| Multiple tenants, partners or organizations | NO | No aplica |
| Storage or processing on a mobile device | YES | Copia local de tres booleanos |
| Web UI or data exposed in the browser | NO | Es la app |
| New or updated dependencies | NO | Sin dependencias nuevas |
| Critical paths (sdd/security/critical-paths.md) | YES | backend/app/migrations/** y backend/app/main.py |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | Las preferencias se leen y escriben solo para la cuenta del token | DATA-01, THR-01 | CTL-01 | HIGH |
| SR-02 | El cuerpo se valida y el push sigue sin datos sensibles; la categoría viaja en `data` | THR-02 | CTL-02 | MEDIUM |
| SR-03 | La copia del teléfono no guarda el correo ni el documento y se borra con la cuenta | DATA-01 | CTL-03 | MEDIUM |

## 2. Design

### Components

- **Backend:** `PushPreferencesDB` (migración `0004`), `PushPreferences`, `NotificationService.preferences` y
  `save_preferences`; `notify(category=...)` consulta la preferencia antes de enviar y agrega `category` a `data`;
  `claim_changed` usa `payments` para `PAID` y `claims` para lo demás; `renewal_due` usa `renewals`. Endpoints
  `GET` y `PUT /insured/devices/push-preferences` con la dependencia `insured_account`.
- **App:** `PushPreferences` en el dominio; `HttpPolicyActionsApi` y `SessionPolicyActions` con `pushPreferences`
  y `savePushPreferences`; `NotificationPreferenceStore` (SharedPreferences por cuenta, borrado al cerrar sesión);
  `SessionPolicySync`, en cada sincronización, envía el cambio pendiente o trae las preferencias del servidor (también tras cerrar sesión o reinstalar) y omite el aviso local de la categoría apagada; pantalla `ui/settings` con tres interruptores,
  abierta desde la billetera; sin señal (incluido el ingreso sin red, sin tokens) muestra la copia local con «Reintentar» y el cambio se envía con la siguiente sincronización. El cuerpo del PUT exige las tres categorías como booleanos estrictos y el guardado es un upsert.

### Data flows

Asegurado → `PUT /insured/devices/push-preferences` → `push_preferences` → cada envío consulta la fila antes de
llamar a Firebase. La app guarda la misma copia en el teléfono para los avisos locales.

### Data contract

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| GET /insured/devices/push-preferences | — | `{claims, renewals, payments}`; todas `true` sin preferencia |
| PUT /insured/devices/push-preferences | `{claims, renewals, payments}` | Lo guardado; 422 inválido; 401 sin sesión |
| Push `data` (cambia) | — | suma `category` |

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| El documento sale de la cuenta del token | CTL-01 | Recibirlo en el cuerpo: permitiría tocar a otros |
| Filtro en el servidor y también en la app | CTL-02 | Solo en la app: un push igual llegaría a Google y a la pantalla bloqueada |

### Architecture decisions

N/A: reutiliza la arquitectura hexagonal (ADR 0004) y los almacenes de la app (ADR 0014, 0021).

### Configuration and deployment

- Migración `0004`; sin variables nuevas.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Preferencias en el backend y filtro del envío | AC-01, AC-02, AC-03, SR-01, SR-02 | CTL-01, CTL-02 | DONE | backend/app/modules/notifications/service.py; backend/app/bff/insured/router.py |
| T-02 | Pantalla, copia local y filtro de los avisos locales en la app | AC-01, AC-02, AC-04, QR-01 | CTL-02 | DONE | movil/app/src/main/java/com/solventa/app/ui/settings/NotificationPreferencesScreen.kt |
| T-03 | Pruebas | AC-01 a AC-04 | CTL-01, CTL-02 | DONE | backend/tests/integration/test_push_preferences_api.py |
| T-04 | Correcciones de la revisión independiente: preferencias en la sincronización, ingreso sin red, cuerpo estricto, upsert, lectura de mejor esfuerzo y prueba de la migración | AC-02, AC-04, SR-02 | CTL-02 | DONE | movil/app/src/main/java/com/solventa/app/data/policies/SessionPolicySync.kt |

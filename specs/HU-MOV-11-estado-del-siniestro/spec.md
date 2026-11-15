# HU-MOV-11 · Estado del siniestro

| Field | Value |
|---|---|
| ID | HU-MOV-11 |
| Title | Estado del siniestro |
| User story | https://github.com/proyectos-miso/proyecto-final-miso/issues/77 |
| Owner | Juan Camilo Peña |
| Status | DONE |
| Created | 2026-11-09 |
| Security profile | HIGH |

## 1. Requirements

### User story

**HU-MOV-11 · Consultar el estado de mi siniestro** (Sprint 3, Must have, 2 puntos, FE-MOV-06, ESC-LAT-04; depende de HU-MOV-10).

**Como** cliente asegurado **quiero** consultar en qué va mi reclamación **para** no tener que llamar a preguntar.

Criterios de aceptación de la historia:

1. **Lista de siniestros.** *Dado* un asegurado con siniestros reportados, *cuando* entra a Siniestros, *entonces* ve cada uno con su número de radicado, la póliza, la fecha de reporte y el estado actual, del más reciente al más antiguo.
2. **Historial del siniestro.** *Dado* un siniestro, *cuando* el asegurado lo abre, *entonces* ve la línea de tiempo de estados (recibido, en evaluación, aprobado, rechazado o pagado) con fecha y una explicación en lenguaje claro.
3. **Respuesta rápida.** *Dada* la consulta, *cuando* se ejecuta, *entonces* el servidor responde en menos de 150 ms en el percentil 95.
4. **Sin conexión.** *Dado* un asegurado sin señal, *cuando* consulta sus siniestros, *entonces* ve el último estado conocido con la fecha de la última actualización.
5. **Solo lo suyo.** *Dado* un asegurado, *cuando* consulta, *entonces* solo ve sus propios siniestros, y el de otra persona responde como si no existiera.
6. **Sin siniestros.** *Dado* un asegurado sin siniestros, *cuando* entra a Siniestros, *entonces* ve "No registra siniestros en este momento" y el botón para reportar uno.

Requisitos no funcionales: HA-04 (vista de lectura), HA-15 (datos en transmisión), HA-16 (datos almacenados). Mockup: cinco pantallas en el issue (lista, detalle aprobado, detalle rechazado, sin conexión y estado vacío). Tareas: #242 endpoint, #243 lista y detalle, #244 copia local cifrada, #245 pruebas.

**Brechas de rutas críticas que absorbe** (`specs/pendientes/HU-MOV-11.md`): RC-15 avisos al asegurado, RC-16 consulta desde vista de lectura, RC-27 aviso de vencimiento y renovación, RC-40 carátula en la app.

### Problem and outcome

El asegurado ya puede tomar las fotos de su siniestro (HU-MOV-08) y operaciones ya lo dictamina y paga (HU-WEB-15), pero el asegurado no tiene cómo saber en qué va: tiene que llamar. Resultado: en la app ve sus siniestros y la línea de tiempo de cada uno en lenguaje claro, también sin señal; recibe una notificación cuando un siniestro cambia de estado o una póliza está por vencer; y descarga la carátula de su póliza desde la billetera.

### Goals and non-goals

Objetivos: endpoint del asegurado con sus siniestros e historial; pantalla «Mis siniestros» con detalle y línea de tiempo; copia local cifrada con la fecha de la última actualización; notificación push (Firebase Cloud Messaging, plan gratuito) cuando un siniestro cambia de estado y cuando una póliza está por vencer, con notificación local al sincronizar como respaldo; renovación de la póliza por un año desde la app y desde el portal (RC-24); vencimiento automático de las pólizas; descarga de la carátula desde la tarjeta de la póliza.

Fuera de alcance:
- **Reportar y enviar el siniestro:** HU-MOV-08, 09 y 10. El botón «Reportar un siniestro» abre el flujo que ya dejó HU-MOV-08.
- **Cobro de la renovación:** renovar extiende la vigencia un año con las mismas condiciones y versión nueva de la carátula; el recaudo de la prima sigue el flujo de pagos existente.
- **SMS o correo:** el aviso es push; sin Firebase configurado queda la notificación local al sincronizar.
- **Fotos y montos del dictamen:** el asegurado ve el estado, la fecha y una explicación, no el expediente del analista.

Decisiones de Juan Camilo Peña (2026-11-09): push con Firebase Cloud Messaging en vez de SNS, por ser gratuito; la renovación (RC-24) la hace esta historia porque HU-WEB-12 no la incluyó.

### Affected paths

| Path | Change |
|---|---|
| backend/app/modules/claims/insured.py | Siniestros del asegurado con su historial en lenguaje claro |
| backend/app/bff/insured/router.py | `GET /insured/claims`, carátula, renovación y registro del token push |
| backend/app/modules/notifications/** | Tokens push y envío por Firebase Cloud Messaging |
| backend/app/modules/claims/service.py | Push al asegurado después de cada cambio de estado |
| backend/app/modules/policy/** | Renovación, vencimiento y aviso de vencimiento |
| backend/app/bff/experience/operations_router.py | Renovación desde el portal |
| backend/app/main.py | Tarea diaria de vencimientos y avisos |
| backend/pyproject.toml | Dependencia `firebase-admin` |
| backend/requirements.txt | Dependencia `firebase-admin` para la imagen |
| backend/app/core/config.py | `FIREBASE_CREDENTIALS_FILE` y `FIREBASE_CREDENTIALS_JSON` |
| movil/build.gradle.kts | Plugin de Google Services declarado sin aplicar |
| movil/gradle/libs.versions.toml | Firebase Messaging y plugin de Google Services |
| movil/app/src/main/java/com/solventa/app/SolventaApplication.kt | Cliente de siniestros, acciones de la tarjeta y registro del token push |
| movil/app/src/main/java/com/solventa/app/ui/auth/AuthViewModel.kt | La espera para pedir el código se reinicia al cambiar entre ingreso y recuperación, encontrada en la prueba manual; el límite real sigue en el servidor |
| terraform/** | Variable sensible con la cuenta de servicio de Firebase para la tarea de ECS |
| .github/workflows/** | Secreto `FIREBASE_CREDENTIALS_JSON` en el despliegue |
| web/src/app/features/gestion/** | Botón «Renovar por un año» y estado Vencida |
| backend/tests/** | Pruebas del endpoint, del alcance y del tiempo de respuesta |
| movil/app/src/main/java/com/solventa/app/data/claims/** | Cliente HTTP y copia local cifrada de los siniestros |
| movil/app/src/main/java/com/solventa/app/domain/claims/** | Modelos y contratos de los siniestros del asegurado |
| movil/app/src/main/java/com/solventa/app/ui/claims/** | Lista, detalle y línea de tiempo |
| movil/app/src/main/java/com/solventa/app/data/policies/** | Sincronización de siniestros, avisos y carátula |
| movil/app/src/main/java/com/solventa/app/ui/wallet/** | Acceso a Siniestros, aviso de vencimiento y carátula |
| movil/app/src/main/java/com/solventa/app/MainActivity.kt | Rama de navegación de Siniestros |
| movil/app/src/main/AndroidManifest.xml | Permiso de notificaciones y servicio de mensajería |
| movil/app/build.gradle.kts | Firebase Messaging, opcional según `google-services.json` |
| movil/app/src/test/** | Pruebas unitarias de la app |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Asegurado | Consultar sus siniestros | 1. En la billetera pulsa «Mis siniestros». 2. Ve la lista. 3. Abre uno y ve la línea de tiempo | Sin señal: ve la copia local con la fecha de la última actualización. Sin siniestros: mensaje y botón para reportar |
| UC-02 | Asegurado | Recibir avisos | 1. Operaciones cambia el estado del siniestro. 2. Llega un push. 3. Al tocarlo abre el siniestro | Sin Firebase: la sincronización muestra la notificación local. Notificaciones negadas: el cambio se ve al abrir la app |
| UC-03 | Asegurado | Saber que su póliza vence y renovarla | 1. Faltan 30 días o menos. 2. La tarjeta lo dice y llega un push una sola vez. 3. Pulsa «Renovar» y la vigencia se extiende un año | Más de 30 días: 409. Vencida: la renovación la reactiva como pendiente de pago |
| UC-04 | Asegurado | Descargar su carátula | 1. Pulsa «Descargar carátula» en la tarjeta. 2. El PDF queda en Descargas con una notificación del sistema | Sin señal o póliza cancelada: mensaje claro |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Pedir siniestros o la carátula de otra persona | Asegurado | THR-01 | Los siniestros salen del documento de la cuenta del token; la carátula de otra póliza responde 404 | TC-06 |
| AB-02 | Leer la copia local de siniestros en un teléfono robado | Atacante con el teléfono | THR-02 | Base cifrada con llave del Keystore por cuenta | TC-12 |
| AB-03 | Usar un token del portal o sin sesión | Atacante | THR-03 | 403 o 401 | TC-07 |
| AB-04 | Registrar un token push con contenido inyectado o recibir avisos de otra persona | Asegurado | THR-04 | Formato validado y el token queda ligado al documento de la cuenta del token | TC-09 |
| AB-05 | Renovar una póliza ajena o fuera de la ventana | Asegurado | THR-01 | 404 si no es suya; 409 fuera de los 30 días | TC-08 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN el asegurado entra a Siniestros, THE app SHALL listar sus siniestros con radicado, póliza, fecha de reporte y estado, del más reciente al más antiguo | STATED |
| AC-02 | WHEN abre un siniestro, THE app SHALL mostrar la línea de tiempo de estados con fecha y una explicación en lenguaje claro | STATED |
| AC-03 | THE consulta SHALL responder en menos de 150 ms en el percentil 95 | STATED |
| AC-04 | WHILE no hay señal, THE app SHALL mostrar el último estado conocido y la fecha de la última actualización | STATED |
| AC-05 | THE consulta SHALL devolver solo los siniestros del asegurado; el de otra persona responde como si no existiera | STATED |
| AC-06 | WHEN no tiene siniestros, THE app SHALL mostrar "No registra siniestros en este momento" y el botón para reportar | STATED |
| AC-07 | WHEN un siniestro rechazado se muestra, THE app SHALL explicar el motivo en lenguaje claro y cómo pedir una revisión | DERIVED: el mockup 3 lo pide y la justificación del analista no es lenguaje para el cliente |
| AC-08 | WHEN un siniestro cambia de estado, THE backend SHALL enviar un push al teléfono del asegurado y THE app SHALL mostrar la notificación que abre ese siniestro, también al sincronizar si el push no llegó | DERIVED: RC-15, el asegurado no recibe avisos de su siniestro |
| AC-09 | WHEN faltan 30 días o menos para el fin de vigencia, THE billetera SHALL avisarlo en la tarjeta y con un push una sola vez por versión | DERIVED: RC-27, el asegurado no se entera del vencimiento |
| AC-10 | WHEN el asegurado pulsa «Descargar carátula», THE app SHALL descargar el PDF de su póliza en Descargas con un enlace de 15 minutos, sin abrirlo; el asegurado decide si lo abre | DERIVED: RC-40, HU-WEB-16 dejó fuera la descarga desde la app |
| AC-11 | THE copia local de siniestros SHALL guardarse cifrada por cuenta y borrarse al cerrar sesión | DERIVED: HA-16, como la billetera (ADR 0021) |
| AC-12 | WHEN faltan 30 días o menos o la póliza venció, THE asegurado en la app y THE analista en el portal SHALL poder renovarla por un año con versión nueva | DERIVED: RC-24, HU-WEB-12 no implementó la renovación |
| AC-13 | WHEN pasa el fin de vigencia sin renovar, THE sistema SHALL marcar la póliza como vencida en la tarea diaria | DERIVED: RC-27, una póliza sin vigencia no puede seguir como vigente |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | PERFORMANCE | Consulta de siniestros del asegurado desde vista de lectura (RC-16) | p95 por debajo de 150 ms con 10.000 siniestros, PostgreSQL local |
| QR-02 | ACCESSIBILITY | Lista, detalle y avisos | Lineamientos de accesibilidad de Android: TalkBack lee estado y fecha, objetivos de 48 dp, contraste AA |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | YES | Radicados y pólizas del asegurado |
| Sensitive data (special categories, biometrics, health, financial, minors) | YES | El estado de un siniestro de vida revela salud o muerte |
| Authentication, sessions, credentials or one-time codes | YES | Token del asegurado y refresh en la sincronización |
| Authorization or access to other users' resources | YES | Solo los siniestros y la carátula propios |
| Payments or money movement | NO | Solo muestra que se pagó |
| Third-party services or integrations | YES | Firebase Cloud Messaging entrega los push |
| Infrastructure, network, IAM, CI/CD or secrets | YES | Llave de la cuenta de servicio de Firebase: secreto de CI que llega a ECS por Terraform |
| Multiple tenants, partners or organizations | NO | El asegurado no es un socio |
| Storage or processing on a mobile device | YES | Copia local cifrada de los siniestros |
| Web UI or data exposed in the browser | NO | Es la app |
| New or updated dependencies | YES | `firebase-admin` en el backend y `firebase-messaging` en la app |
| Critical paths (sdd/security/critical-paths.md) | YES | movil/app/src/main/AndroidManifest.xml |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | Siniestros, carátula y renovación salen del documento de la cuenta del token; lo ajeno responde 404 | DATA-01, THR-01 | CTL-01 | HIGH |
| SR-02 | Solo el rol insured con sesión vigente usa `/insured/*`; sin sesión 401, otro rol 403, sesión revocada 401 | THR-03 | CTL-02 | HIGH |
| SR-03 | La copia local de siniestros va en la base cifrada por cuenta (SQLCipher con llave del Keystore) y se borra al cerrar sesión | DATA-01, THR-02 | CTL-03 | HIGH |
| SR-04 | El push no lleva datos sensibles: solo radicado o póliza y un texto general; el detalle se pide con sesión | DATA-02, THR-05 | CTL-04 | HIGH |
| SR-05 | El token push se valida y queda ligado al documento de la cuenta; un fallo de Firebase no rompe la operación | DATA-02, THR-04, THR-06 | CTL-05 | MEDIUM |
| SR-06 | La llave de Firebase nunca está en el repositorio: en local se lee por la ruta en `FIREBASE_CREDENTIALS_FILE` y en AWS llega como secreto de CI en `FIREBASE_CREDENTIALS_JSON` | DATA-03, THR-07 | CTL-06 | HIGH |
| SR-07 | La vista del asegurado no expone fotos, perito, responsable ni montos internos del dictamen | DATA-01, THR-05 | CTL-07 | MEDIUM |
| SR-08 | La renovación solo procede dentro de la ventana de 30 días o con la póliza vencida, y queda en la auditoría de la póliza | THR-01 | CTL-08 | MEDIUM |

## 2. Design

### Components

Backend (ADR 0004 y 0005):

- **`modules/claims/insured.py`:** consulta de solo lectura de los siniestros por `insured_document`, con la línea de tiempo en lenguaje claro, el motivo del rechazo y el canal de revisión (RC-16, HA-04).
- **`modules/claims/service.py`:** cada evento queda en `session.info` y, después del commit, se envía el push al asegurado.
- **`modules/notifications`:** tabla `push_tokens` y `NotificationService` con `FirebaseSender`; sin credenciales no envía y lo registra.
- **`modules/policy/operations.py`:** `renew` (un año, versión nueva, nueva carátula), `expire_due` y `remind_renewals` (una vez por versión, con evento `RENEWAL_REMINDER`).
- **`main.py`:** tarea diaria de vencimientos y avisos.
- **`bff/insured/router.py`:** `GET /insured/claims`, carátula, renovación y registro del token push.
- **`bff/experience/operations_router.py`:** renovación para el analista.

Portal: estado «Vencida» y botón «Renovar por un año» en las operaciones de la póliza.

App (ADR 0015, 0021 y 0022): cliente HTTP de siniestros, copia cifrada en la base de la billetera, pantallas de lista y detalle, servicio de Firebase Messaging opcional, notificación local al sincronizar, aviso de vencimiento, renovar y descargar carátula en la tarjeta.

### Data flows

- **Consulta:** app → `GET /insured/claims` (token del asegurado) → PostgreSQL → app → base cifrada.
- **Aviso de siniestro:** analista decide → commit → `push_tokens` del documento → Firebase → teléfono → al tocar abre el siniestro.
- **Vencimiento:** tarea diaria → pólizas a 30 días sin aviso de la versión → evento → push.
- **Renovación:** app o portal → `POST .../renewal` → nueva versión y evento `RENEWED`.

### Data contract

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| GET /insured/claims | — | `updated_at`, `claims[]` (radicado, póliza, fecha, estado, etiqueta, último cambio, `timeline[]`, motivo, canal de revisión); `no-store` |
| GET /insured/policies/{n}/caratula | — | Enlace de 15 minutos; 404 ajena, 409 cancelada, 503 en generación |
| POST /insured/policies/{n}/renewal | — | Resultado con versión; 404 ajena, 409 fuera de la ventana |
| POST /insured/devices/push-token | `token` (20 a 256, `[A-Za-z0-9_:-]`) | 204 |
| POST /experience/policies/{n}/renewal | — | Resultado con versión; 409 fuera de la ventana |
| Push `data` | — | `type` (`CLAIM_STATUS` o `RENEWAL_REMINDER`) y `claim_number` o `policy_number` |

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| El documento sale de la cuenta del token y lo ajeno responde 404 | CTL-01 | Recibir el documento del cliente: permitiría consultar a otros |
| Copia local en la base SQLCipher de la billetera | CTL-03 | Preferencias o archivos: quedarían en claro |
| Push con identificadores y texto general | CTL-04 | Mandar el motivo o el monto: Google y la pantalla bloqueada los verían |
| Push después del commit y de mejor esfuerzo | CTL-05 | Enviar dentro de la transacción: un fallo de Firebase revertiría el dictamen |
| Firebase Cloud Messaging | CTL-04 | Amazon SNS: también usa FCM en Android y tiene costo |
| Llave como secreto de GitHub que Terraform pasa a la tarea de ECS, igual que las llaves JWT y OTP | CTL-06 | Archivo dentro de la imagen: quedaría en el registro de contenedores |

### Architecture decisions

N/A: reutiliza la arquitectura hexagonal (ADR 0004), la base cifrada por cuenta (ADR 0021), WorkManager (ADR 0022) y el almacenamiento de documentos (ADR 0023). Firebase entra como adaptador de salida opcional.

### Configuration and deployment

- **Tabla nueva:** `push_tokens`; el estado `EXPIRED` no requiere columna.
- **Variables:** `FIREBASE_CREDENTIALS_FILE` (ruta al JSON, en local) o `FIREBASE_CREDENTIALS_JSON` (contenido, en AWS); sin ninguna, no hay push.
- **Terraform y CI:** variable sensible `firebase_credentials_json` (vacía por defecto) desde el secreto `FIREBASE_CREDENTIALS_JSON` de GitHub en `deploy.yml` y `destroy.yml`; hay que crear ese secreto antes de desplegar.
- **App:** `movil/app/google-services.json` (no se versiona); sin él, la app compila sin Firebase.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Endpoint de siniestros del asegurado desde vista de lectura (#242) | AC-01, AC-02, AC-03, AC-05, AC-07, QR-01, SR-01, SR-07 | CTL-01, CTL-07 | DONE | backend/app/modules/claims/insured.py; backend/tests/integration/test_insured_claims_api.py |
| T-02 | Carátula, renovación y registro del token en el BFF del asegurado | AC-10, AC-12, SR-01, SR-02 | CTL-01, CTL-02 | DONE | backend/app/bff/insured/router.py |
| T-03 | Push por Firebase después de cada cambio de estado, con la llave como secreto de CI en AWS | AC-08, SR-04, SR-05, SR-06 | CTL-04, CTL-05, CTL-06 | DONE | backend/app/modules/notifications/service.py; terraform/modules/ecs/main.tf; backend/app/modules/claims/service.py |
| T-04 | Renovación, vencimiento y aviso de vencimiento con tarea diaria; renovación en el portal | AC-09, AC-12, AC-13, SR-08 | CTL-08 | DONE | backend/app/modules/policy/operations.py; backend/app/main.py; web/src/app/features/gestion/policy-operations.component.ts |
| T-05 | Lista, detalle, copia cifrada, avisos, renovación y carátula en la app (#243, #244) | AC-01, AC-02, AC-04, AC-06, AC-08, AC-09, AC-10, AC-11, QR-02, SR-03 | CTL-03, CTL-04 | DONE | movil/app/src/main/java/com/solventa/app/ui/claims/ClaimsScreens.kt; movil/app/src/main/java/com/solventa/app/data/policies/SessionPolicySync.kt |
| T-06 | Pruebas del backend, del portal y de la app (#245) | AC-03, QR-01 | CTL-01, CTL-05 | DONE | backend/tests/integration/test_insured_claims_api.py; movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt |

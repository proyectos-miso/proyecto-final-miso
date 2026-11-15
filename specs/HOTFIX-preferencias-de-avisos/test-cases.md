# Test cases · HOTFIX

## Scenarios

| ID | From | Scenario | Disposition |
|---|---|---|---|
| SC-01 | AC-01 | Dado un asegurado, cuando guarda sus preferencias, entonces el servidor las devuelve iguales; un cuerpo inválido, sin sesión o con token del portal se rechaza | TC-01 |
| SC-02 | UC-02 | Dada una categoría apagada, cuando ocurre un cambio de esa categoría, entonces el backend no envía el push; las otras sí, cada una con su categoría | TC-02 |
| SC-03 | AC-03 | Dado un asegurado sin preferencia guardada, cuando consulta o recibe un aviso, entonces todas las categorías están encendidas | TC-01, TC-02 |
| SC-04 | AC-02 | Dada una categoría apagada en el teléfono, cuando la sincronización trae un cambio, entonces no muestra el aviso local pero sí guarda el cambio; un pago es de «Pagos» | TC-03 |
| SC-05 | UC-01 | Dado el asegurado en «Avisos», cuando apaga un interruptor, entonces se guarda al instante y ve «Guardado»; un error ofrece reintentar | TC-04 |
| SC-06 | AC-04 | Dado el teléfono sin señal, cuando cambia una preferencia, entonces ve la copia local, queda pendiente y se envía con la siguiente sincronización o al reintentar | TC-04 |
| SC-07 | UC-01 | Dado el asegurado, cuando cierra sesión, entonces la copia local de las preferencias se borra | TC-05 |
| SC-08 | AC-01 | Dada la pantalla, cuando se recorre con TalkBack, entonces cada interruptor dice su nombre, su ayuda y su estado | TC-06 |

## Scenario coverage

| Kind | Applies | Scenarios | Rationale |
|---|---|---|---|
| Main flow | YES | SC-01, SC-05 | — |
| Alternate flows | YES | SC-03, SC-06 | — |
| Invalid input and errors | YES | SC-01, SC-05 | — |
| Limits and boundaries | NO | — | Tres booleanos sin rangos |
| Related flows before and after | YES | SC-04, SC-07 | — |
| Several users, sessions, devices or roles | YES | SC-01 | — |
| State and time (expiry, retries, interruptions, going back, offline) | YES | SC-06 | — |

## Test cases

### TC-01 · Leer y guardar las preferencias propias

| Field | Value |
|---|---|
| Covers | AC-01, AC-03, SR-01, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | INTEGRATION |
| Preconditions | María registrada |
| Procedure | 1. Leer sin preferencia. 2. Guardar y volver a leer. 3. Enviar un cuerpo inválido, sin sesión y con token del portal |
| Expected result | Todas `true`; lo guardado vuelve igual; 422 con un cuerpo parcial, no booleano o con campos extra; 401 y 403 |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_push_preferences_api.py::test_sin_preferencia_guardada_se_aceptan_todas_las_categorias; backend/tests/integration/test_push_preferences_api.py::test_guarda_las_preferencias_y_las_devuelve; backend/tests/integration/test_push_preferences_api.py::test_un_cuerpo_invalido_o_sin_sesion_se_rechaza; backend/tests/integration/test_push_preferences_api.py::test_el_segundo_guardado_reemplaza_al_primero_con_upsert; backend/tests/integration/test_migrations.py::test_la_migracion_de_preferencias_crea_su_tabla_desde_0003_y_se_revierte |
| Status | PASS |

### TC-02 · El envío respeta la categoría

| Field | Value |
|---|---|
| Covers | AC-02, AC-03, SR-02 |
| Kind | SECURITY |
| Risk covered | THR-02 |
| Level | INTEGRATION |
| Preconditions | Teléfono registrado y emisor falso |
| Procedure | 1. Apagar «Vencimientos» y «Pagos». 2. Enviar un cambio de estado, un pago y un vencimiento |
| Expected result | Solo sale el cambio de estado, con `category: claims`; el pago es `payments` y el vencimiento `renewals` |
| Tool or method | pytest |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | backend/tests/integration/test_push_preferences_api.py::test_una_categoria_apagada_no_se_envia_y_las_demas_si; backend/tests/integration/test_push_preferences_api.py::test_el_push_lleva_su_categoria_y_el_pago_es_de_pagos; backend/tests/integration/test_push_preferences_api.py::test_si_falla_la_lectura_de_la_preferencia_el_aviso_no_rompe_la_operacion |
| Status | PASS |

### TC-03 · Los avisos locales respetan la preferencia

| Field | Value |
|---|---|
| Covers | AC-02 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Copia local con una categoría apagada |
| Procedure | 1. Sincronizar con un cambio de estado. 2. Con un pago. 3. Con una póliza por vencer, apagar y volver a encender |
| Expected result | No avisa pero guarda; el pago cuenta como «Pagos»; el vencimiento avisa una vez al encender |
| Tool or method | JUnit |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt::"FE-MOV-04 con Siniestros apagado un cambio de estado no avisa pero si se guarda"; movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt::"FE-MOV-04 un siniestro pagado es un aviso de Pagos, no de Siniestros"; movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt::"FE-MOV-04 con Vencimientos apagado no avisa la poliza por vencer y al encenderlo avisa"; movil/app/src/test/java/com/solventa/app/data/notifications/AndroidNotificationPosterTest.kt::"FE-MOV-04 un aviso de una categoria apagada se descarta y uno sin categoria se muestra"; movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt::"FE-MOV-04 tras cerrar sesion la sincronizacion trae las preferencias del servidor antes de avisar" |
| Status | PASS |

### TC-04 · Pantalla de preferencias

| Field | Value |
|---|---|
| Covers | AC-01, AC-04 |
| Kind | FUNCTIONAL |
| Risk covered | — |
| Level | UNIT |
| Preconditions | Servidor simulado |
| Procedure | 1. Abrir «Avisos». 2. Apagar un interruptor. 3. Fallar el servidor y reintentar. 4. Sin red, cambiar y sincronizar. 5. Abrir con el ingreso sin red |
| Expected result | Carga del servidor; guarda al instante con PUT y dice «Guardado»; el error ofrece reintentar; sin red muestra la copia local con «Reintentar» y la sincronización envía lo pendiente; sin tokens es «sin conexión», no sesión vencida |
| Tool or method | JUnit, Robolectric y Compose |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/settings/NotificationPreferencesViewModelTest.kt::"cada interruptor guarda al instante con PUT y muestra Guardado"; movil/app/src/test/java/com/solventa/app/ui/settings/NotificationPreferencesViewModelTest.kt::"sin red muestra la copia local, deja el cambio pendiente y lo envia al reabrir"; movil/app/src/test/java/com/solventa/app/ui/settings/NotificationPreferencesViewModelTest.kt::"un error del servidor muestra el aviso y Reintentar vuelve a enviar lo mismo"; movil/app/src/test/java/com/solventa/app/data/policies/HttpPolicyActionsApiTest.kt::"FE-MOV-04 las preferencias de avisos se leen con GET y se guardan con PUT y su cuerpo"; movil/app/src/test/java/com/solventa/app/SolventaMobileRootClaimsTest.kt::"FE-MOV-04 desde la billetera se abren las preferencias de avisos, se apaga una y se vuelve"; movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt::"FE-MOV-04 un cambio hecho sin red se envia en la siguiente sincronizacion"; movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt::"FE-MOV-04 con el ingreso sin red las preferencias quedan sin conexion, no con la sesion vencida"; movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt::"FE-MOV-04 si la pantalla cambia la copia durante la sincronizacion gana la pantalla"; movil/app/src/test/java/com/solventa/app/data/policies/ClaimSyncTest.kt::"FE-MOV-04 si el servidor rechaza lo pendiente se descarta y queda lo del servidor" |
| Status | PASS |

### TC-05 · La copia local se borra al cerrar sesión

| Field | Value |
|---|---|
| Covers | AC-04, SR-01, SR-03 |
| Kind | SECURITY |
| Risk covered | THR-01 |
| Level | UNIT |
| Preconditions | Copia local guardada |
| Procedure | 1. Cerrar sesión, revocar la huella o cambiar la contraseña |
| Expected result | La copia vuelve a los valores por defecto sin tocar otras cuentas |
| Tool or method | JUnit |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/auth/AuthViewModelTest.kt::"FE-MOV-04 cerrar sesion, la huella revocada o el cambio de contrasena borran las preferencias de avisos"; movil/app/src/test/java/com/solventa/app/data/settings/NotificationPreferenceStoreTest.kt::"al borrar la cuenta vuelve a los valores por defecto sin tocar las demas" |
| Status | PASS |

### TC-06 · Accesibilidad de la pantalla

| Field | Value |
|---|---|
| Covers | QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | ACCESSIBILITY |
| Preconditions | Pantalla con las tres preferencias |
| Procedure | 1. Revisar cada interruptor con su nombre, ayuda y estado. 2. Medir los objetivos. 3. Guardar y escuchar el anuncio |
| Expected result | Cada fila se lee completa; al menos 48 dp; «Guardado» se anuncia |
| Tool or method | Compose y Robolectric |
| Automation | AUTOMATED |
| Environment | LOCAL |
| Evidence | movil/app/src/test/java/com/solventa/app/ui/settings/NotificationPreferencesScreenTest.kt::"muestra tres interruptores con su nombre, su ayuda y su estado, de al menos 48 dp"; movil/app/src/test/java/com/solventa/app/ui/settings/NotificationPreferencesScreenTest.kt::"Guardado se anuncia por TalkBack al guardarse" |
| Status | PASS |

### TC-07 · Prueba en el teléfono

| Field | Value |
|---|---|
| Covers | AC-01, AC-02, QR-01 |
| Kind | QUALITY |
| Risk covered | — |
| Level | E2E |
| Preconditions | APK local por túnel y backend local |
| Procedure | 1. Abrir «Avisos» y apagar «Siniestros». 2. Rechazar un siniestro desde el portal. 3. Encender «Siniestros» y repetir. 4. Recorrer la pantalla con TalkBack |
| Expected result | Sin push ni aviso local con la categoría apagada; con ella encendida llega; TalkBack lee cada interruptor |
| Tool or method | Prueba manual |
| Automation | MANUAL |
| Environment | LOCAL |
| Evidence | PENDING |
| Status | PENDING |

## Security test coverage

| Category | Applies | Test cases | Rationale |
|---|---|---|---|
| Unauthenticated user | YES | TC-01 | — |
| User without permissions | YES | TC-01 | — |
| Access to another user's resources | YES | TC-01 | El documento sale del token |
| Cross-tenant access | NO | — | Sin socios |
| Malicious input | YES | TC-01 | — |
| Expired tokens | YES | TC-04 | La app vuelve a pedir el ingreso ante 401 |
| Revoked sessions | YES | TC-05 | — |
| Tampered data | NO | — | Tres booleanos sin integridad que proteger |
| Wrong keys | NO | — | Sin cifrado propio |
| External service failures | YES | TC-04 | Servidor caído y sin red |
| Race conditions | NO | — | Última escritura gana, aceptado en RISK-01 |
| Information exposure | YES | TC-02 | El push solo suma la categoría |
| Secrets in code | NO | — | Sin secretos nuevos |
| Insecure configuration | NO | — | Sin configuración nueva |
| Vulnerable dependencies | NO | — | Sin dependencias nuevas |

# HU-MOV-06 · Ultima sincronizacion

| Field | Value |
|---|---|
| ID | HU-MOV-06 |
| Title | Ultima sincronizacion |
| User story | https://github.com/proyectos-miso/proyecto-final-miso/issues/72 |
| Owner | Juan Camilo Peña |
| Status | DONE |
| Created | 2026-10-24 |
| Security profile | HIGH |

## 1. Requirements

### User story

**HU-MOV-06 · Saber de cuándo es la información que estoy viendo** (Sprint 2, Must have, 1 punto, FE-MOV-03, ESC-LAT-05; depende de HU-MOV-04).

**Como** cliente asegurado **quiero** ver la fecha y hora de la última sincronización **para** saber si la información que estoy mostrando está vigente.

Criterios de aceptación de la historia:

1. **Fecha visible.** *Dada* la billetera de pólizas, *cuando* se muestra, *entonces* indica "Última sincronización" con fecha y hora locales.
2. **Información desactualizada.** *Dada* una última sincronización de hace más de 24 horas, *cuando* se ve la billetera, *entonces* se muestra un aviso de que la información puede no estar actualizada.
3. **Actualización de la fecha.** *Dada* una sincronización exitosa, *cuando* termina, *entonces* la fecha mostrada se actualiza.

Requisitos no funcionales: HA-04 (servir la consulta desde una vista de lectura), HA-08 (absorber picos con contrapresión). Mockup: https://solventa-movil.vercel.app/?vista=billetera, con el texto "Última sincronización". Tareas: #195 guardar y exponer la fecha, #196 indicador con aviso de desactualización, #197 pruebas unitarias.

### Problem and outcome

Sin red, la billetera muestra la copia guardada pero no dice de cuándo es, y el asegurado no sabe si puede confiar en ella. Resultado: la billetera muestra la fecha y hora local de la última sincronización exitosa y avisa cuando pasaron más de 24 horas.

### Goals and non-goals

Objetivos: guardar la hora de cada sincronización exitosa con la copia de la cuenta, mostrarla en la billetera y avisar cuando la copia tenga más de 24 horas.

Fuera de alcance: sincronizar sola al volver la red y los reintentos (HU-MOV-07); un botón para sincronizar a mano. HA-04 y HA-08 ya se cumplen: la billetera lee la copia local y esta historia no agrega llamadas al backend.

### Affected paths

| Path | Change |
|---|---|
| movil/app/src/main/java/com/solventa/app/domain/policies/PolicyCard.kt | La copia trae la hora de la sincronización |
| movil/app/src/main/java/com/solventa/app/data/policies/EncryptedPolicyStore.kt | Guardar y leer la hora con la copia cifrada |
| movil/app/src/main/java/com/solventa/app/ui/wallet/WalletViewModel.kt | Hora de la última sincronización y cálculo de desactualización |
| movil/app/src/main/java/com/solventa/app/MainActivity.kt | Recalcula la billetera cuando la app vuelve al primer plano |
| movil/app/src/main/java/com/solventa/app/ui/wallet/WalletScreen.kt | Texto de la última sincronización y aviso |
| movil/app/src/main/java/com/solventa/app/ui/wallet/Formats.kt | Fecha y hora local |
| movil/app/src/main/java/com/solventa/app/ui/theme/Components.kt | El aviso acepta el ícono del reloj |
| movil/app/src/main/res/values/strings.xml | Textos de la fecha y del aviso |
| movil/app/src/main/res/drawable/ic_clock.xml | Ícono del reloj del mockup |
| movil/app/src/test/java/com/solventa/app/data/policies/PolicyTestDoubles.kt | Almacén en memoria con la hora |
| movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt | Pruebas de la fecha y del aviso |
| movil/app/src/test/java/com/solventa/app/ui/wallet/WalletScreenTest.kt | Pruebas de la pantalla |
| movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt | Prueba instrumentada de la hora y de la base alterada |

### Use cases

| ID | Actor | Use case | Main flow | Alternate flows |
|---|---|---|---|---|
| UC-01 | Asegurado | Ver de cuándo es la información | 1. Abre la billetera. 2. La app muestra "Última sincronización" con la fecha y hora local de la copia | Sin copia: no hay fecha y se pide conexión (HU-MOV-04) |
| UC-02 | Asegurado | Saber que la copia está vieja | 1. Abre la billetera sin red con una copia de hace más de 24 horas. 2. La app muestra la fecha y un aviso de que la información puede no estar actualizada | La copia tiene menos de 24 horas: solo la fecha |
| UC-03 | Asegurado | Ver la fecha actualizada | 1. Abre la billetera con red. 2. La sincronización termina bien. 3. La fecha pasa a la de ahora y el aviso desaparece | El servidor falla: se conserva la fecha anterior |

### Abuse cases

| ID | Abuse case | Actor | Threat | Expected response | Test case |
|---|---|---|---|---|---|
| AB-01 | Atrasar el reloj del teléfono para que una copia vieja no muestre el aviso de desactualización | Quien muestra la billetera | THR-01 | Si la hora guardada queda en el futuro respecto al reloj del teléfono, la billetera muestra el aviso | TC-03 |
| AB-02 | Alterar la hora guardada en el teléfono para que una copia vieja parezca reciente | Quien tenga el teléfono | THR-02 | La hora vive dentro de la base cifrada de HU-MOV-04; alterada, la base no abre y se pide conexión | TC-05 |

### Acceptance criteria

| ID | Criterion | Source |
|---|---|---|
| AC-01 | WHEN la billetera muestra pólizas o el aviso de que no hay vigentes, THE app SHALL indicar "Última sincronización" con fecha y hora en la zona horaria del teléfono | STATED |
| AC-02 | WHEN la última sincronización tiene más de 24 horas, THE billetera SHALL mostrar un aviso de que la información puede no estar actualizada | STATED |
| AC-03 | WHEN una sincronización termina bien, THE billetera SHALL mostrar la nueva fecha y quitar el aviso de desactualización | STATED |
| AC-04 | THE hora de la última sincronización SHALL guardarse por cuenta, junto con la copia cifrada, y borrarse con ella | DERIVED: la historia no dice dónde se guarda; separada de la copia podría quedar con otra cuenta o sin la copia |
| AC-05 | WHEN el servidor falla o no hay red, THE billetera SHALL conservar la hora de la última sincronización exitosa | DERIVED: un intento fallido no es una sincronización |
| AC-06 | IF la hora guardada queda después de la hora del teléfono, THE billetera SHALL tratar la copia como desactualizada | DERIVED: con el reloj atrasado el aviso no debe esconderse (AB-01) |
| AC-07 | WHEN han pasado exactamente 24 horas, THE billetera SHALL no mostrar todavía el aviso | DERIVED: la historia dice "más de 24 horas"; se fija el borde |
| AC-08 | WHEN la app vuelve al primer plano, THE billetera SHALL recalcular el aviso de desactualización y las pólizas vencidas con la hora actual | DERIVED: en la prueba manual del 2026-10-25 el aviso no aparecía al volver de cambiar la fecha, porque solo se calculaba al abrir la billetera |

### Quality requirements

| ID | Category | Requirement | Target and conditions |
|---|---|---|---|
| QR-01 | ACCESSIBILITY | La fecha y el aviso se leen con TalkBack y crecen con la fuente | Guías de accesibilidad de Android: el aviso se anuncia al aparecer y nada se corta con la fuente al máximo |

### Security triggers

| Trigger | Applies | Rationale |
|---|---|---|
| Personal data (identifies or can identify a person) | NO | La hora de sincronización no identifica a nadie; las pólizas ya se clasificaron en HU-MOV-04 |
| Sensitive data (special categories, biometrics, health, financial, minors) | NO | Solo una marca de tiempo |
| Authentication, sessions, credentials or one-time codes | NO | No cambia el ingreso ni la sesión |
| Authorization or access to other users' resources | NO | La hora se guarda en la copia de cada cuenta, ya aislada en HU-MOV-04 |
| Payments or money movement | NO | No aplica |
| Third-party services or integrations | NO | No aplica |
| Infrastructure, network, IAM, CI/CD or secrets | NO | No aplica |
| Multiple tenants, partners or organizations | NO | Canal del asegurado |
| Storage or processing on a mobile device | YES | La hora se guarda en la base cifrada del teléfono |
| Web UI or data exposed in the browser | NO | Solo app móvil |
| New or updated dependencies | NO | Sin dependencias nuevas |
| Critical paths (sdd/security/critical-paths.md) | YES | MainActivity (perfil mínimo MEDIUM) observa cuándo la app vuelve al primer plano; no cambia el manifiesto, la sesión ni la composición |

### Security requirements

| ID | Requirement | Source | Controls | Priority |
|---|---|---|---|---|
| SR-01 | La hora de la sincronización vive dentro de la base cifrada de la cuenta y se borra con ella | DATA-01, THR-02 | CTL-01 | MEDIUM |
| SR-02 | Una hora guardada posterior al reloj del teléfono cuenta como copia desactualizada | DATA-01, THR-01 | CTL-02 | MEDIUM |

## 2. Design

### Components

- `domain/policies`: `WalletCopy(policies, syncedAt)`; `PolicyStore.read` devuelve la copia con su hora y `save` recibe la hora de la sincronización.
- `data/policies/EncryptedPolicyStore`: guarda la hora en `wallet_meta.savedAtEpochMillis`, que ya existe en la base cifrada de HU-MOV-04, y la devuelve al leer. Sin cambio de esquema.
- `ui/wallet/WalletViewModel`: `WalletUiState` suma `lastSync` y `stale`. Con red, la hora es la del reloj al terminar; sin red o con error, la de la copia. `stale` es verdadero si pasaron más de 24 horas o si la hora guardada está en el futuro.
- `ui/wallet/WalletScreen`: texto "Última sincronización: <fecha y hora>" con el ícono del reloj, como en el mockup, y el aviso de desactualización con el componente de aviso.
- `ui/wallet/Formats`: fecha y hora local del teléfono.
- `MainActivity`: al volver la app al primer plano (`ON_RESUME`) llama a `WalletViewModel.refreshClock`, que recalcula el aviso y las pólizas vencidas sin volver a leer la copia.

### Data flows

- **Con red:** la billetera obtiene las pólizas → guarda la copia con la hora del reloj → muestra esa hora.
- **Sin red o con error:** la billetera lee la copia y su hora de la base cifrada → calcula si está desactualizada con el reloj del teléfono.
- No hay límites de confianza nuevos; la hora no sale del teléfono.

### Data contract

N/A: no cambia ninguna API; la hora vive en la tabla `wallet_meta` de la base local, que ya existía.

### Security design decisions

| Decision | Implements | Rejected alternative and why |
|---|---|---|
| Guardar la hora dentro de la base cifrada de la cuenta | CTL-01 | Preferencias en claro: se podría alterar sin tocar la copia y quedaría fuera del borrado por cuenta |
| Una hora en el futuro cuenta como desactualizada | CTL-02 | Confiar en el reloj sin más: atrasarlo escondería el aviso |

### Architecture decisions

N/A: reutiliza la base cifrada de la ADR 0021 y la pantalla única de la billetera (ADR 0015).

### Configuration and deployment

- **Umbral:** 24 horas, constante en el ViewModel.
- **Variables:** ninguna.
- **Base local:** sin cambio de versión; la columna ya existía.

## 3. Tasks

| ID | Task | Traces to | Controls | Status | Evidence |
|---|---|---|---|---|---|
| T-01 | Guardar y exponer la hora de la última sincronización con la copia cifrada (#195) | AC-04, AC-05, SR-01 | CTL-01 | DONE | movil/app/src/main/java/com/solventa/app/data/policies/EncryptedPolicyStore.kt |
| T-02 | Texto de la última sincronización y aviso de desactualización en la billetera (#196) | AC-01, AC-02, AC-03, AC-06, AC-07, QR-01, SR-02 | CTL-02 | DONE | movil/app/src/main/java/com/solventa/app/ui/wallet/WalletViewModel.kt; movil/app/src/main/java/com/solventa/app/ui/wallet/WalletScreen.kt |
| T-04 | Recalcular el aviso y las pólizas vencidas al volver al primer plano | AC-08 | CTL-02 | DONE | movil/app/src/main/java/com/solventa/app/MainActivity.kt; movil/app/src/main/java/com/solventa/app/ui/wallet/WalletViewModel.kt |
| T-03 | Pruebas unitarias del ViewModel, de la pantalla y del almacén (#197) | AC-01, AC-02, AC-03, AC-05, AC-06, AC-07 | CTL-01, CTL-02 | DONE | movil/app/src/test/java/com/solventa/app/ui/wallet/WalletViewModelTest.kt; movil/app/src/androidTest/java/com/solventa/app/data/policies/EncryptedPolicyStoreTest.kt |

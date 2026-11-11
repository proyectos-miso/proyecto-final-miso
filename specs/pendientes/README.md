# Pendientes de rutas críticas

Los seis recorridos críticos de Solventa (J1–J6) y sus features son compromisos del proyecto. Al cierre del Sprint 2 se revisó qué les falta. No se crean historias nuevas: cada brecha se asigna a una historia del tablero que ya va a tocar ese código o esa pantalla, y entra en su spec.

Cada archivo de esta carpeta es la nota de pendientes de una historia o de un hotfix. Quien cree el spec de esa historia (skill `spec-create`) lee su nota y lleva cada brecha a los criterios de aceptación como `DERIVED: RC-NN, <por qué>` (el validador pide el motivo), con sus pruebas. Desde ese momento el spec es la fuente de verdad y el gate sigue la brecha como a cualquier otro criterio.

`RC-NN` es el código de cada brecha («ruta crítica»). Sirve para nombrarla y para buscar después en qué spec quedó: `grep -r "RC-15" specs/`.

Cuando un tercero que no tenemos es necesario (pasarela bancaria, oráculo de vuelos o clima, regulador), se construye un adaptador simulado detrás de una interfaz, como el buró y la pasarela de pagos (HA-22, HA-24).

## Notas por historia

| Nota | Historia | Responsable | Brechas |
|---|---|---|---|
| [HU-WEB-12.md](HU-WEB-12.md) | Cuotas por socio (#66) | Oscar | RC-02, RC-03, RC-04, RC-06, RC-07, RC-08, RC-09, RC-10, RC-23, RC-24, RC-25 (completadas en `specs/HU-WEB-12-recibir-un-rechazo-claro-al-superar-la-cuota-con/`) |
| [HU-MOV-08.md](HU-MOV-08.md) | Fotos con ubicación y hora (#74) | David | RC-11 |
| [HU-MOV-09.md](HU-MOV-09.md) | Envío sin señal (#75) | David | RC-12 |
| [HU-MOV-10.md](HU-MOV-10.md) | Radicado (#76) | David | RC-13, RC-14, RC-26 |
| [HU-MOV-11.md](HU-MOV-11.md) | Estado del siniestro (#77) | Juan Camilo | RC-15, RC-16, RC-27, RC-40 |
| En `specs/HU-WEB-15-dictamen-y-liquidacion/` | Dictamen y liquidación (#115) | Juan Camilo | RC-01, RC-05, RC-17, RC-18, RC-19, RC-20, RC-21, RC-22, RC-28, RC-29 (ya en el spec) |
| [HOTFIX-produccion.md](HOTFIX-produccion.md) | Hotfix de producción, seguridad y CI/CD | Oscar | RC-30, RC-31, RC-33, RC-35, RC-36 |

## Recorridos y dónde se resuelven

| Recorrido | Historias que lo completan |
|---|---|
| J1 · Cotización embebida | HU-WEB-12 (cuotas y alta de socios) |
| J2 · Suscripción y emisión | HU-WEB-12 (firma), HU-WEB-15 (beneficiarios) |
| J3 · Siniestro asistido | HU-MOV-08, 09, 10 y 11; HU-WEB-15 (perito y pago de la indemnización) |
| J4 · Siniestro paramétrico | HU-MOV-10 (evento que crea el siniestro), HU-WEB-15 (liquidación automática), HU-MOV-11 (aviso) |
| J5 · Perfilamiento vida hipotecario | HU-WEB-12 (consentimiento obligatorio, edad, datos del crédito, Open Data) |
| J6 · Ciclo de vida | HU-WEB-15 (cancelación, endosos, reportes), HU-WEB-12 y HU-MOV-11 (vencimiento y renovación) |

## Fuera de las notas

- **RC-32:** tokens sin `type` y TOTP reutilizable. Se resuelve en `HOTFIX-tokens-tipo-y-totp`.
- **RC-34:** SMS real en AWS. Se deja simulado por ahora; el adaptador de Twilio ya existe.
- **Tareas de proceso**, que se hacen en el tablero y no en un spec:
  - **RC-37:** enlazar cada historia de arquitectura a su brecha o historia, cerrar las duplicadas (HA-01, HA-06) y reasignar los responsables que ya no están en el equipo.
  - **RC-39:** cerrar el issue y sus tareas al integrar cada PR a `dev`.
- **RC-38:** el reparto del Sprint 3 ya está aplicado. HU-WEB-15 pasó a Juan Camilo.

## Al usar una nota

1. Crea el spec con `spec-create`. En Requirements, cada RC de la nota entra como criterio `DERIVED: RC-NN, <por qué>`.
2. Si una brecha no cabe en la historia, déjalo escrito en «Goals and non-goals» del spec con el motivo y avísale al equipo; no la descartes en silencio.
3. Cuando el spec tenga todas las brechas, la nota se puede borrar en el mismo PR.

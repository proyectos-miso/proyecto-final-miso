# Mockups · HU-WEB-12 · Recibir un rechazo claro al superar la cuota contratada

Colección de mockups en formato SVG con fidelidad media acordes al diseño del portal y canal B2B de Solventa (paleta corporativa `#0f172a`, `#0b5474`, `#38bdf8`, estándar RFC 6585 de cabeceras de cuota y arquitectura de aislamiento HA-07).

## Flujo de Pantallas

| Paso | Archivo | Descripción | Criterio de Aceptación Asociado |
|---|---|---|---|
| 1 | [01-canal-socio-oferta-y-cuota.svg](./01-canal-socio-oferta-y-cuota.svg) | Canal del socio con la oferta cotizada (200 OK) y cuota visible: límite, solicitudes restantes y hora de renovación en cabeceras. | CA-04: Cuota restante visible |
| 2 | [02-rechazo-cuota-429-retry-after.svg](./02-rechazo-cuota-429-retry-after.svg) | Rechazo explícito (429 Too Many Requests) con mensaje claro, cabecera `Retry-After` y degradación elegante sin errores técnicos. | CA-01: Rechazo explícito<br>CA-02: Cuándo reintentar |
| 3 | [03-aislamiento-entre-socios.svg](./03-aislamiento-entre-socios.svg) | Demostración de aislamiento concurrente: Banco Aliado recibe 429 mientras otro socio cotiza con normalidad en 200 OK (HA-07 / HA-14). | CA-03: Sin afectar a otros socios |


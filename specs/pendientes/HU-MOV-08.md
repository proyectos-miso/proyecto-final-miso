# Pendientes de rutas críticas · HU-MOV-08

**Historia:** HU-MOV-08 · Capturar evidencia fotográfica con ubicación y hora ([#74](https://github.com/proyectos-miso/proyecto-final-miso/issues/74)) · **Responsable:** David · **Sprint 3**

**Por qué aquí:** la historia crea las fotos del siniestro (J3), y su protección en tránsito es parte de la misma entrega.

| RC | Recorrido | Brecha | Cómo implementarlo | Criterio sugerido |
|---|---|---|---|---|
| RC-11 | J3 | Las fotos del siniestro no están protegidas en tránsito (HA-15) | HTTPS de punta a punta (depende del hotfix RC-31), un hash por foto calculado en el teléfono y verificado al recibirla | WHEN el backend recibe una foto, THE sistema SHALL verificar su hash y rechazar la que no coincida |

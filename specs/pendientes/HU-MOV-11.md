# Pendientes de rutas críticas · HU-MOV-11

**Historia:** HU-MOV-11 · Consultar el estado de mi siniestro ([#77](https://github.com/proyectos-miso/proyecto-final-miso/issues/77)) · **Responsable:** Juan Camilo · **Sprint 3**

**Por qué aquí:** la historia es la pantalla del asegurado sobre su siniestro (J3). Los avisos al asegurado (J3, J4 y J6) y la carátula en la app se apoyan en la misma sincronización.

| RC | Recorrido | Brecha | Cómo implementarlo | Criterio sugerido |
|---|---|---|---|---|
| RC-15 | J3, J4 | El asegurado no recibe avisos de su póliza ni de su siniestro (FE-MOV-04). El aviso del pago paramétrico debe llegar en menos de 60 s | Notificaciones en el teléfono con WorkManager a partir de los eventos que publica HU-MOV-10 (RC-14), y línea de tiempo del siniestro en la app | WHEN cambia el estado de un siniestro del asegurado, THE app SHALL avisarle con una notificación |
| RC-16 | J3 | La consulta del estado del siniestro no sale de una vista de lectura (HA-04) | Endpoint del asegurado que lee solo el resumen del siniestro, como el detalle de HU-WEB-09 | THE consulta de siniestros del asegurado SHALL leer solo el resumen y responder en p95 bajo 150 ms |
| RC-27 | J6 | El asegurado no recibe el aviso de vencimiento y renovación (FE-MOV-04, criterio 2) | Aviso 30 días antes en la billetera y como notificación, con el botón para renovar (HU-WEB-12, RC-24) | WHEN faltan 30 días para el fin de vigencia, THE app SHALL avisar al asegurado y ofrecer renovar |
| RC-40 | J2, J6 | El asegurado no puede descargar su carátula desde la app (HU-WEB-16 lo dejó fuera de alcance) | Botón en la tarjeta de la billetera que pide el enlace prefirmado de HU-WEB-16 con el alcance del asegurado | WHEN el asegurado abre una póliza en la billetera, THE app SHALL permitirle descargar su carátula |

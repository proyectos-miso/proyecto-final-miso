# Pendientes de rutas críticas · HU-WEB-15

**Historia:** HU-WEB-15 · Dictamen y liquidación de siniestros ([#115](https://github.com/proyectos-miso/proyecto-final-miso/issues/115)) · **Responsable:** Juan Camilo · **Sprint 3**

**Por qué aquí:** la historia es el back office del analista sobre siniestros y pólizas (J3) y sigue a la búsqueda y el detalle de HU-WEB-08 y 09. Ahí entran la liquidación, el pago y las operaciones sobre la póliza (J2, J4 y J6).

**Nota:** el cuerpo de la historia cita «FE-WEB-04 · Administración de siniestros», pero FE-WEB-04 es Cancelación de póliza (#4). RC-17 cubre esa feature.

| RC | Recorrido | Brecha | Cómo implementarlo | Criterio sugerido |
|---|---|---|---|---|
| RC-01 | J2 | Las pólizas de vida se emiten sin beneficiarios (`beneficiaries=[]`) | Paso de beneficiarios en el portal (nombre, documento, parentesco y porcentaje, que deben sumar 100 %), validado en `PolicyIssueRequest`, guardado en la póliza y mostrado en el detalle. Son a quienes se les paga la indemnización (RC-29) | WHEN se emite una póliza de vida, THE sistema SHALL exigir beneficiarios que sumen 100 % |
| RC-05 | J2 | `payment.service` y `policy.service` del portal no tienen pruebas | Specs con `HttpTestingController` | THE servicios web de pago y póliza SHALL tener pruebas unitarias |
| RC-17 | J6 | No se puede cancelar ni terminar una póliza (FE-WEB-04) | Cancelación a pedido del cliente con motivo y devolución de prima proporcional; al liquidar un siniestro de muerte, la póliza pasa a terminada | WHEN el analista cancela una póliza o liquida un siniestro de muerte, THE póliza SHALL quedar cancelada o terminada con su motivo |
| RC-18 | J3 | El caso no se asigna a un analista ni a un perito (FE-MOV-07) | Estado `Asignado` con el analista responsable y un catálogo de peritos por ciudad para la inspección en sitio | WHEN el analista toma un siniestro, THE sistema SHALL registrar el responsable y, si aplica, el perito asignado |
| RC-19 | J5, J6 | Las decisiones no se pueden reconstruir (HA-18, FE-WEB-08) | El dictamen guarda la regla, la evidencia y el analista en un registro inmutable, como los rechazos de HU-WEB-07; vista de auditoría por póliza | THE dictamen SHALL quedar en un registro inmutable con regla, evidencia y analista |
| RC-20 | — | El portal cierra sesión solo en el navegador, y los refresh concurrentes no se deduplican | Endpoint de cierre de sesión del asesor y del analista que revoque el refresh, e interceptor con un solo refresh en vuelo | WHEN el usuario del portal cierra sesión, THE backend SHALL revocar su token de renovación |
| RC-21 | J6 | No hay endosos ni modificaciones de una póliza vigente | Endoso de beneficiarios y de suma asegurada desde el detalle de HU-WEB-09; cada cambio queda registrado y se emite una carátula nueva con el servicio de HU-WEB-16 | WHEN el analista registra un endoso, THE sistema SHALL guardar la versión y emitir una carátula nueva |
| RC-22 | J6 | No hay reportes al regulador ni al reasegurador | Reporte mensual en PDF y CSV (pólizas emitidas, primas, siniestros y cesión de riesgo) con el servicio de documentos de HU-WEB-16, guardado en S3 | WHEN cierra un mes, THE sistema SHALL generar los reportes al regulador y al reasegurador |
| RC-28 | J4 | Ningún siniestro se liquida sin intervención humana | Regla de liquidación automática para los siniestros `PARAMETRIC` que crea HU-MOV-10 (RC-26): aprobación por la regla, registro inmutable y orden de pago sin analista | WHEN se crea un siniestro paramétrico que cumple la regla, THE sistema SHALL liquidarlo y ordenar el pago sin intervención humana |
| RC-29 | J3, J4 | La indemnización no se paga al beneficiario | Orden de pago al beneficiario por la pasarela simulada del módulo de pagos, con idempotencia y recibo, como el cobro de HU-WEB-06 | WHEN se aprueba una liquidación, THE sistema SHALL ordenar el pago a los beneficiarios una sola vez |

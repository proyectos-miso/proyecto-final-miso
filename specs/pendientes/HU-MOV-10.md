# Pendientes de rutas críticas · HU-MOV-10

**Historia:** HU-MOV-10 · Obtener un número de radicado por cada siniestro ([#76](https://github.com/proyectos-miso/proyecto-final-miso/issues/76)) · **Responsable:** David · **Sprint 3**

**Por qué aquí:** la historia crea las tablas de siniestros y el evento `ClaimFiled`. Es el lugar para las migraciones, el publicador de eventos y el siniestro paramétrico (J3 y J4).

**Base que ya existe:** la tabla mínima `claims` de HU-WEB-09 (`backend/app/modules/claims/models.py`), que se puede ampliar, y `boto3`, que trajo HU-WEB-16.

| RC | Recorrido | Brecha | Cómo implementarlo | Criterio sugerido |
|---|---|---|---|---|
| RC-13 | J3 | No hay migraciones: las columnas nuevas se agregan con `ALTER ... IF NOT EXISTS` al arrancar | Alembic con una migración inicial desde el esquema actual; las tablas de siniestros en la primera migración real | THE esquema de la base SHALL cambiar solo por migraciones versionadas |
| RC-14 | J2, J3 | El outbox no tiene publicador: `PolicyIssued` se queda en la tabla | Publicador hacia SQS para `PolicyIssued` y `ClaimFiled`. Es la base de las notificaciones de HU-MOV-11 (RC-15) | WHEN se guarda un evento en el outbox, THE publicador SHALL enviarlo a SQS al menos una vez |
| RC-26 | J4 | No hay siniestros paramétricos | Endpoint de eventos externos (retraso de vuelo o evento climático) firmado por el oráculo, con un oráculo simulado. Si un evento cumple la condición de una póliza, se crea el siniestro con su radicado y origen `PARAMETRIC` | WHEN llega un evento externo válido que cumple la condición de una póliza, THE sistema SHALL crear el siniestro con radicado y origen paramétrico |

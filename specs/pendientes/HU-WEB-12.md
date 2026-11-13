# Pendientes de rutas críticas · HU-WEB-12

**Historia:** HU-WEB-12 · Recibir un rechazo claro al superar la cuota contratada ([#66](https://github.com/proyectos-miso/proyecto-final-miso/issues/66)) · **Responsable:** Oscar · **Sprint 3**

**Estado:** COMPLETADO · Las 11 brechas funcionales asignadas han sido especificadas, implementadas y verificadas en `specs/HU-WEB-12-recibir-un-rechazo-claro-al-superar-la-cuota-con/` con gate `SECURITY_VERIFIED`.

**Por qué aquí:** la historia toca el camino de la cotización y la emisión (rating, socios y portal). Estas brechas de J1, J2, J5 y J6 están en ese mismo código.

| RC | Recorrido | Brecha | Cómo se implementó | Estado |
|---|---|---|---|---|
| RC-02 | J2 | La póliza no se firma (FE-WEB-02, criterio 4) | Al aceptar, el cliente confirma con código OTP verificado; la carátula lleva el hash SHA-256 del PDF y la tabla `policy_signatures` es inmutable vía disparador PostgreSQL `make_immutable` | IMPLEMENTADO |
| RC-03 | J2 | Los errores de pago y emisión dicen «Reintente» aunque el fallo sea definitivo; solo se distingue el 402 | Un mensaje específico por código (402, 404, 409, 422, 502) en `payment-step` bloqueando reintentos en fallos definitivos | IMPLEMENTADO |
| RC-04 | J2 | El cotizador trae una cédula de prueba por defecto y un medio de pago «sin fondos (prueba)» | Eliminados datos de prueba quemados en formularios de consentimiento, cotizador y pagos en producción | IMPLEMENTADO |
| RC-06 | J5 | El consentimiento no se exige para cotizar, vive en memoria y acepta cualquier código | Tabla relacional `consents` en PostgreSQL; `consent_id` obligatorio en `QuoteRequest` verificado en `create_quote`; validación estricta de OTP | IMPLEMENTADO |
| RC-07 | J5 | `birth_date` es texto, y el motor tarifa menores y fechas futuras | Tipo `birth_date: date` y regla estricta de 18 a 75 años dentro de `create_quote`, retornando HTTP 422 si incumple | IMPLEMENTADO |
| RC-08 | J5 | La entidad financiera y el plazo se capturan pero no llegan a la cotización | Campos `financial_entity` y `term_months` en contrato Pydantic `QuoteRequest` y persistidos en tabla `quotes` | IMPLEMENTADO |
| RC-09 | J5 | `simulated_delay` es un parámetro público de la cotización | Eliminación total del parámetro en endpoints de producción de Quote Edge y BFF Experience | IMPLEMENTADO |
| RC-10 | J1 | Los pagos se guardan con `partner_id="default"` | Extracción estricta del socio desde los claims del token JWT verificado en el servidor para cobros | IMPLEMENTADO |
| RC-23 | J1 | No hay alta de socios por autoservicio (FE-WEB-05, HA-23) | Endpoint `POST /quote-edge/partners/register` para autoservicio con credenciales sandbox y cuota inicial, y `POST /experience/operations/partners/{id}/approve` para promoción a producción | IMPLEMENTADO |
| RC-24 | J6 | Las pólizas no vencen: no existe el estado `EXPIRED` ni la renovación | Estado `EXPIRED` en enum `PolicyStatus`, mantenimiento transaccional periódico de vencimientos y endpoint de renovación `POST /experience/policies/{number}/renewal` y `/insured/policies/{number}/renewal` | IMPLEMENTADO |
| RC-25 | J5 | El perfilamiento usa un buró simulado y no consulta Open Data ni Open Finance | Adaptador a Open Data real (`datos.gov.co`) y simulación Open Finance detrás de interfaz común con timeout de 400 ms y degradación a score base | IMPLEMENTADO |

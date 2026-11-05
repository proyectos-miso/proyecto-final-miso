# Pendientes de rutas críticas · HU-WEB-12

**Historia:** HU-WEB-12 · Recibir un rechazo claro al superar la cuota contratada ([#66](https://github.com/proyectos-miso/proyecto-final-miso/issues/66)) · **Responsable:** Oscar · **Sprint 3**

**Por qué aquí:** la historia toca el camino de la cotización y la emisión (rating, socios y portal). Estas brechas de J1, J2, J5 y J6 están en ese mismo código.

| RC | Recorrido | Brecha | Cómo implementarlo | Criterio sugerido |
|---|---|---|---|---|
| RC-02 | J2 | La póliza no se firma (FE-WEB-02, criterio 4) | Al aceptar, el cliente confirma con un código de un solo uso; se reutiliza el OTP del módulo de identidad. La carátula de HU-WEB-16 lleva el hash SHA-256 del PDF, la fecha y la constancia del consentimiento, y el registro de firma es inmutable | WHEN el cliente acepta la cotización, THE sistema SHALL exigir su código de un solo uso y guardar una constancia de firma inmutable con el hash del PDF |
| RC-03 | J2 | Los errores de pago y emisión dicen «Reintente» aunque el fallo sea definitivo; solo se distingue el 402 | Un mensaje por código (409, 422, 404, 502) en `payment-step` | IF el cobro o la emisión fallan, THE portal SHALL mostrar un mensaje según el código y no ofrecer reintento en los fallos definitivos |
| RC-04 | J2 | El cotizador trae una cédula de prueba por defecto y un medio de pago «sin fondos (prueba)» | Quitarlos, o mostrarlos solo en desarrollo | THE portal en producción SHALL no traer datos ni medios de pago de prueba |
| RC-06 | J5 | El consentimiento no se exige para cotizar, vive en memoria y acepta cualquier código | Tabla `consents` en PostgreSQL; `consent_id` obligatorio en `QuoteRequest` y verificado en `create_quote`; OTP real del módulo de identidad | IF no hay un consentimiento vigente del cliente, THE cotización SHALL rechazarse |
| RC-07 | J5 | `birth_date` es texto, y el motor tarifa menores y fechas futuras | `birth_date: date` y la regla de 18 a 75 años dentro de `create_quote`, con un 422 claro | IF la edad está fuera de 18 a 75 años, THE cotización SHALL responder 422 |
| RC-08 | J5 | La entidad financiera y el plazo se capturan pero no llegan a la cotización | `financial_entity` y `term_months` en el contrato, guardados con la cotización | THE cotización SHALL guardar la entidad financiera y el plazo del crédito |
| RC-09 | J5 | `simulated_delay` es un parámetro público de la cotización | Quitarlo del endpoint; dejarlo solo en las pruebas | THE endpoint de cotización SHALL no aceptar parámetros de simulación |
| RC-10 | J1 | Los pagos se guardan con `partner_id="default"` | Tomar el socio del token, como en la cotización | THE cobro SHALL guardarse con el socio del token |
| RC-23 | J1 | No hay alta de socios por autoservicio (FE-WEB-05, HA-23) | Registro del socio con credenciales de sandbox, cuota inicial y enlace al contrato OpenAPI; al aprobarlo pasa a producción con su cuota | WHEN un socio se registra, THE sistema SHALL entregarle credenciales de sandbox y una cuota inicial |
| RC-24 | J6 | Las pólizas no vencen: no existe el estado `EXPIRED` ni la renovación | Tarea programada diaria, la misma que reinicia las cuotas, que marca las vencidas; renovar emite un periodo nuevo con la tarifa vigente | WHEN una póliza llega a su fin de vigencia, THE sistema SHALL marcarla vencida y permitir renovarla |
| RC-25 | J5 | El perfilamiento usa un buró simulado y no consulta Open Data ni Open Finance | Adaptador a una fuente de Open Data real (datos abiertos de Colombia) y adaptador de Open Finance simulado detrás de la misma interfaz (HA-24), con el timeout de 400 ms que ya existe | THE perfilamiento SHALL consultar una fuente de Open Data real detrás de una interfaz, con degradación si no responde |

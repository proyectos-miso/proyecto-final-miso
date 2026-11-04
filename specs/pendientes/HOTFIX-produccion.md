# Pendientes de rutas críticas · Hotfix de producción, seguridad y CI/CD

**Responsable:** Oscar · **Sprint 3**

**Por qué aquí:** estas brechas no son de una historia; afectan a producción y al proceso de entrega. Cada una va como hotfix con su paquete `HOTFIX` (`spec new HOTFIX "<título>"`), o continúa el paquete indicado.

| RC | Brecha | Cómo resolverlo | Paquete |
|---|---|---|---|
| RC-30 | Producción corre como `development`: la tarea de ECS no define `ENVIRONMENT`. Se siembran las cuentas de prueba, la API devuelve el código de un solo uso y la documentación queda abierta | Inyectar `ENVIRONMENT=production` en `terraform/modules/ecs` | Continúa `HOTFIX-configuracion-de-produccion` |
| RC-31 | CloudFront → ALB va por HTTP; el ALB ya es interno, pero no tiene ACM ni listener 443 | Certificado ACM, listener 443 y `origin_protocol_policy = "https-only"` | Continúa `HOTFIX-transporte-y-borde` |
| RC-33 | La contraseña de RDS va en claro en la tarea de ECS y en el tfstate | Secrets Manager con `random_password` y `secrets` en la definición de la tarea | Continúa `HOTFIX-configuracion-de-produccion` |
| RC-35 | El ruleset de `main` y `dev` pide una aprobación, pero ningún check del CI | Marcar como obligatorios Backend, Web, Mobile, Terraform, SDD gate y SonarCloud | Configuración del repositorio, sin código |
| RC-36 | El CD es frágil | Imagen etiquetada con el SHA (hoy usa `:latest`, que expira a los 30 días); `deployment_circuit_breaker`; RDS con backups, snapshot final y `deletion_protection`; tfstate con bloqueo; alarmas de 5xx y de hosts no sanos; `--cov-fail-under` y `sonarqube-scan-action@v6`; borrar `deploy-local.yml` | Hotfix nuevo de infraestructura |

mock_provider "aws" {}

variables {
  db_password    = "valor-de-prueba-123456"
  jwt_secret_key = "valor-de-prueba-jwt-0123456789abcdef0123456789"
  otp_secret_key = "valor-de-prueba-otp-0123456789abcdef0123456789"
}

run "plan_sin_infraestructura" {
  command = plan
}

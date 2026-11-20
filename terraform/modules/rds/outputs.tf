output "endpoint" {
  value = aws_db_instance.main.endpoint
}

output "address" {
  value = aws_db_instance.main.address
}

output "port" {
  value = aws_db_instance.main.port
}

output "db_name" {
  value = aws_db_instance.main.db_name
}

output "db_password_secret_arn" {
  description = "ARN del secreto en Secrets Manager con la contraseña de RDS"
  value       = aws_secretsmanager_secret.db_password.arn
}

output "kms_key_arn" {
  description = "ARN de la llave KMS de RDS"
  value       = aws_kms_key.rds.arn
}

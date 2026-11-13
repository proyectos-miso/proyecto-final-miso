variable "aws_region" {
  description = "Región de AWS para el despliegue Multi-AZ"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Nombre del ambiente (production, staging, dev)"
  type        = string
  default     = "production"
}

variable "project_name" {
  description = "Prefijo para los nombres de recursos de infraestructura"
  type        = string
  default     = "solventa"
}

variable "db_username" {
  description = "Usuario administrador de PostgreSQL en RDS"
  type        = string
  default     = "solventa_admin"
}

variable "db_password" {
  description = "Contraseña de PostgreSQL en RDS. Si no se suministra externamente, se genera con random_password y se guarda en Secrets Manager (RC-33)"
  type        = string
  sensitive   = true
  default     = null

  validation {
    condition     = var.db_password == null || (length(var.db_password) >= 12 && length(var.db_password) <= 41 && !can(regex("[/@\" ]", var.db_password)))
    error_message = "db_password debe tener entre 12 y 41 caracteres, sin /, @, comillas dobles ni espacios."
  }
}

variable "db_deletion_protection" {
  description = "Habilitar protección contra eliminación accidental de la base de datos RDS"
  type        = bool
  default     = true
}

variable "jwt_secret_key" {
  description = "Llave de firma de los tokens del backend. Llega como TF_VAR_jwt_secret_key desde los secretos de CI"
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.jwt_secret_key) >= 32
    error_message = "jwt_secret_key debe tener al menos 32 caracteres."
  }
}

variable "firebase_credentials_json" {
  description = "Cuenta de servicio de Firebase (JSON) para los push de HU-MOV-11. Llega como TF_VAR_firebase_credentials_json desde los secretos de CI; vacía, no hay push"
  type        = string
  sensitive   = true
  default     = ""
}

variable "otp_secret_key" {
  description = "Llave del MAC de los códigos de un solo uso, distinta de la de los tokens. Llega como TF_VAR_otp_secret_key"
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.otp_secret_key) >= 32 && var.otp_secret_key != var.jwt_secret_key
    error_message = "otp_secret_key debe tener al menos 32 caracteres y ser distinta de jwt_secret_key."
  }
}

variable "use_local_emulator" {
  description = "Habilitar para redirigir endpoints al emulador local Floci (http://localhost:4566)"
  type        = bool
  default     = false
}

variable "local_emulator_endpoint" {
  description = "URL del servicio de emulación local Floci"
  type        = string
  default     = "http://localhost:4566"
}


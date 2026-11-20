variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "private_subnet_ids" {
  type = list(string)
}

variable "alb_security_group_id" {
  type = string
}

variable "target_group_arn" {
  type = string
}

variable "ecr_image_url" {
  type = string
}

variable "db_host" {
  type = string
}

variable "db_name" {
  type = string
}

variable "db_user" {
  type = string
}

variable "db_password" {
  type      = string
  sensitive = true
  default   = null
}

variable "db_password_secret_arn" {
  description = "ARN del secreto en Secrets Manager con la contraseña de RDS (RC-33)"
  type        = string
}

variable "db_kms_key_arn" {
  description = "ARN de la llave KMS que cifra el secreto de RDS y los secretos del backend en Secrets Manager (RC-33)"
  type        = string
}

variable "jwt_secret_key" {
  description = "Llave de firma de los tokens del backend"
  type        = string
  sensitive   = true
}

variable "otp_secret_key" {
  description = "Llave del MAC de los códigos de un solo uso"
  type        = string
  sensitive   = true
}

variable "firebase_credentials_json" {
  description = "Cuenta de servicio de Firebase para los push (HU-MOV-11); vacía, el backend no envía push"
  type        = string
  sensitive   = true
  default     = ""
}

variable "sqs_queue_url" {
  type = string
}

variable "sqs_queue_arn" {
  description = "Cola de eventos a la que el publicador del outbox envía (HU-MOV-10, RC-14)"
  type        = string
}

variable "oracle_secret" {
  description = "Llave compartida con el oráculo de eventos para firmar los eventos paramétricos (HU-MOV-10, RC-26); vacía, el canal queda cerrado"
  type        = string
  sensitive   = true
  default     = ""

  validation {
    condition     = var.oracle_secret == "" || length(var.oracle_secret) >= 32
    error_message = "oracle_secret debe estar vacía o tener al menos 32 caracteres."
  }
}

variable "s3_bucket_name" {
  type = string
}

variable "s3_bucket_arn" {
  type = string
}

variable "storage_kms_key_arn" {
  type = string
}

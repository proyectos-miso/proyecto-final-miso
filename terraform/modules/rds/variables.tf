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

variable "ecs_security_group_id" {
  type = string
}

variable "db_username" {
  type = string
}

variable "db_password" {
  type      = string
  sensitive = true
  default   = null
}

variable "db_deletion_protection" {
  description = "Habilitar protección contra eliminación accidental de la base de datos RDS"
  type        = bool
  default     = true
}

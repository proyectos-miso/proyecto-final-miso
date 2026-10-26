variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "api_origin_enabled" {
  type        = bool
  description = "Crea el VPC origin y las rutas /api/* y /health. Debe conocerse en el plan, aunque el balanceador aún no exista"
  default     = false
}

variable "alb_arn" {
  type        = string
  description = "ARN del balanceador interno al que CloudFront llega mediante un VPC origin"
  default     = ""
}

variable "alb_dns_name" {
  type        = string
  description = "DNS del Application Load Balancer para enrutar llamadas /api/* a través de CloudFront"
  default     = ""
}


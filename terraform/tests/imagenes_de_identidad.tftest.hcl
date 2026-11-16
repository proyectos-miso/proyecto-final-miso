# Permisos del rol de la tarea sobre las imágenes de identidad del registro (mejora FE-MOV-01), con el proveedor simulado.
mock_provider "aws" {}

variables {
  project_name              = "solventa"
  environment               = "test"
  vpc_id                    = "vpc-00000000"
  private_subnet_ids        = ["subnet-00000001", "subnet-00000002"]
  alb_security_group_id     = "sg-00000000"
  target_group_arn          = "arn:aws:elasticloadbalancing:us-east-1:000000000000:targetgroup/prueba/0000000000000000"
  ecr_image_url             = "000000000000.dkr.ecr.us-east-1.amazonaws.com/solventa:latest"
  db_host                   = "db.example.test"
  db_name                   = "solventa"
  db_user                   = "solventa"
  db_password               = "valor-de-prueba-123456"
  db_password_secret_arn    = "arn:aws:secretsmanager:us-east-1:000000000000:secret:prueba"
  db_kms_key_arn            = "arn:aws:kms:us-east-1:000000000000:key/base-de-datos"
  jwt_secret_key            = "valor-de-prueba-jwt-0123456789abcdef0123456789"
  otp_secret_key            = "valor-de-prueba-otp-0123456789abcdef0123456789"
  firebase_credentials_json = ""
  oracle_secret             = ""
  sqs_queue_url             = "https://sqs.us-east-1.amazonaws.com/000000000000/solventa-events-test"
  sqs_queue_arn             = "arn:aws:sqs:us-east-1:000000000000:solventa-events-test"
  s3_bucket_name            = "solventa-documents-test"
  s3_bucket_arn             = "arn:aws:s3:::solventa-documents-test"
  storage_kms_key_arn       = "arn:aws:kms:us-east-1:000000000000:key/almacenamiento"
}

run "el_rol_de_la_tarea_borra_solo_las_imagenes_de_identidad" {
  command = plan

  module {
    source = "./modules/ecs"
  }

  assert {
    condition = one([
      for s in jsondecode(aws_iam_role_policy.task_documents.policy).Statement : s if s.Sid == "ImagenesDeIdentidad"
    ]).Resource == "${var.s3_bucket_arn}/identidad/*"
    error_message = "Las imágenes de identidad deben limitarse al prefijo identidad/."
  }

  assert {
    condition = alltrue([
      for s in jsondecode(aws_iam_role_policy.task_documents.policy).Statement :
      s.Sid == "ImagenesDeIdentidad" if contains(s.Action, "s3:DeleteObject")
    ])
    error_message = "Solo las imágenes de identidad se pueden borrar: las carátulas y las fotos de siniestros no."
  }
}

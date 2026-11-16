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

run "el_rol_borra_las_versiones_y_lista_solo_bajo_identidad" {
  command = plan

  module {
    source = "./modules/ecs"
  }

  assert {
    condition = contains(one([
      for s in jsondecode(aws_iam_role_policy.task_documents.policy).Statement : s if s.Sid == "ImagenesDeIdentidad"
    ]).Action, "s3:DeleteObjectVersion")
    error_message = "Sin s3:DeleteObjectVersion el borrado solo agrega un marcador y la imagen sigue en el bucket versionado."
  }

  assert {
    condition = alltrue([
      for s in jsondecode(aws_iam_role_policy.task_documents.policy).Statement :
      s.Sid == "ImagenesDeIdentidad" if contains(s.Action, "s3:DeleteObjectVersion")
    ])
    error_message = "Solo las imágenes de identidad pueden perder versiones."
  }

  assert {
    condition = one([
      for s in jsondecode(aws_iam_role_policy.task_documents.policy).Statement : s if s.Sid == "VersionesDeIdentidad"
      ]).Resource == var.s3_bucket_arn && one([
      for s in jsondecode(aws_iam_role_policy.task_documents.policy).Statement : s if s.Sid == "VersionesDeIdentidad"
    ]).Condition.StringLike["s3:prefix"] == ["identidad/", "identidad/*"]
    error_message = "Listar versiones debe limitarse con s3:prefix a identidad/."
  }

  assert {
    condition = alltrue([
      for s in jsondecode(aws_iam_role_policy.task_documents.policy).Statement :
      s.Sid == "VersionesDeIdentidad" if contains(s.Action, "s3:ListBucketVersions")
    ])
    error_message = "Ninguna otra sentencia puede listar versiones del bucket."
  }
}

run "el_bucket_vence_las_imagenes_de_identidad_y_sus_versiones" {
  command = plan

  module {
    source = "./modules/storage_events"
  }

  assert {
    condition = alltrue([
      for r in aws_s3_bucket_lifecycle_configuration.documents.rule : one(r.filter).prefix == "identidad/"
    ])
    error_message = "Las reglas de ciclo de vida deben limitarse a identidad/."
  }

  assert {
    condition = one([
      for r in aws_s3_bucket_lifecycle_configuration.documents.rule : r if r.id == "identidad-vence"
    ]).noncurrent_version_expiration[0].noncurrent_days == 1
    error_message = "Las versiones no actuales de las imágenes deben vencer al día siguiente."
  }

  assert {
    condition = one([
      for r in aws_s3_bucket_lifecycle_configuration.documents.rule : r if r.id == "identidad-vence"
    ]).expiration[0].days == 30
    error_message = "Una imagen actual no puede durar más que una verificación pendiente (30 días)."
  }

  assert {
    condition = one([
      for r in aws_s3_bucket_lifecycle_configuration.documents.rule : r if r.id == "identidad-marcadores"
    ]).expiration[0].expired_object_delete_marker == true
    error_message = "Los marcadores de borrado vencidos deben limpiarse."
  }
}

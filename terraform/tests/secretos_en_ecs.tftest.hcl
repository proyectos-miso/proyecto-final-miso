mock_provider "aws" {
  mock_resource "aws_iam_role" {
    defaults = {
      arn = "arn:aws:iam::000000000000:role/rol-de-prueba"
    }
  }

  mock_data "aws_region" {
    defaults = {
      name = "us-east-1"
    }
  }
}

variables {
  project_name           = "solventa"
  environment            = "production"
  vpc_id                 = "vpc-prueba"
  private_subnet_ids     = ["subnet-prueba-a", "subnet-prueba-b"]
  alb_security_group_id  = "sg-prueba"
  target_group_arn       = "arn:aws:elasticloadbalancing:us-east-1:000000000000:targetgroup/prueba/0000"
  ecr_image_url          = "000000000000.dkr.ecr.us-east-1.amazonaws.com/solventa-backend:latest"
  db_host                = "db.prueba.internal"
  db_name                = "solventa_db"
  db_user                = "solventa_admin"
  db_password_secret_arn = "arn:aws:secretsmanager:us-east-1:000000000000:secret:solventa-rds-password-production-AbCdEf"
  db_kms_key_arn         = "arn:aws:kms:us-east-1:000000000000:key/00000000-0000-0000-0000-000000000000"
  jwt_secret_key         = "valor-de-prueba-jwt-0123456789abcdef0123456789"
  otp_secret_key         = "valor-de-prueba-otp-0123456789abcdef0123456789"
  sqs_queue_url          = "https://sqs.us-east-1.amazonaws.com/000000000000/solventa-events"
  sqs_queue_arn          = "arn:aws:sqs:us-east-1:000000000000:solventa-events"
  s3_bucket_name         = "solventa-documentos-prueba"
  s3_bucket_arn          = "arn:aws:s3:::solventa-documentos-prueba"
  storage_kms_key_arn    = "arn:aws:kms:us-east-1:000000000000:key/11111111-1111-1111-1111-111111111111"
}

run "llaves_obligatorias_como_secretos" {
  command = apply

  module {
    source = "./modules/ecs"
  }

  assert {
    condition = length([
      for variable in jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].environment : variable
      if contains(["JWT_SECRET_KEY", "OTP_SECRET_KEY", "FIREBASE_CREDENTIALS_JSON", "ORACLE_SECRET", "DB_PASSWORD"], variable.name)
    ]) == 0
    error_message = "Ningún secreto debe ir en environment de la tarea."
  }

  assert {
    condition     = sort([for secret in jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].secrets : secret.name]) == tolist(["DB_PASSWORD", "JWT_SECRET_KEY", "OTP_SECRET_KEY"])
    error_message = "Sin Firebase ni oráculo, la tarea recibe solo DB_PASSWORD, JWT_SECRET_KEY y OTP_SECRET_KEY por secrets."
  }

  assert {
    condition = alltrue([
      for secret in jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].secrets :
      secret.valueFrom == (secret.name == "DB_PASSWORD" ? var.db_password_secret_arn : aws_secretsmanager_secret.backend[secret.name].arn)
    ])
    error_message = "Cada secreto se inyecta con el ARN de su secreto en Secrets Manager."
  }

  assert {
    condition     = alltrue([for secret in aws_secretsmanager_secret.backend : secret.kms_key_id == var.db_kms_key_arn])
    error_message = "Los secretos del backend se cifran con la llave KMS del proyecto."
  }

  assert {
    condition     = sort(jsondecode(aws_iam_role_policy.execution_secrets.policy).Statement[0].Resource) == sort(concat([var.db_password_secret_arn], [for secret in aws_secretsmanager_secret.backend : secret.arn]))
    error_message = "El rol de ejecución solo lee los ARN de los secretos de la tarea."
  }

  assert {
    condition     = jsondecode(aws_iam_role_policy.execution_secrets.policy).Statement[1].Resource == [var.db_kms_key_arn]
    error_message = "El rol de ejecución solo descifra con la llave de los secretos."
  }

  assert {
    condition     = jsondecode(aws_iam_role_policy.execution_secrets.policy).Statement[1].Condition.StringEquals["kms:ViaService"] == "secretsmanager.us-east-1.amazonaws.com"
    error_message = "El rol de ejecución solo descifra a través de Secrets Manager, incluido el secreto de RDS."
  }

  assert {
    condition     = contains(jsondecode(aws_iam_role_policy.execution_secrets.policy).Statement[0].Resource, var.db_password_secret_arn) && startswith(var.db_password_secret_arn, "arn:aws:secretsmanager:")
    error_message = "DB_PASSWORD sigue llegando por Secrets Manager, así que la condición kms:ViaService no le impide descifrarse."
  }

  assert {
    condition     = !strcontains(aws_iam_role_policy.task_documents.policy, "secretsmanager")
    error_message = "El rol de la tarea no lee Secrets Manager."
  }

  assert {
    condition = alltrue([
      for variable in jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].environment :
      !contains([var.jwt_secret_key, var.otp_secret_key], variable.value)
    ])
    error_message = "Ningún valor de llave aparece en environment."
  }
}

run "secretos_opcionales_cuando_existen" {
  command = apply

  module {
    source = "./modules/ecs"
  }

  variables {
    firebase_credentials_json = "{\"tipo\":\"cuenta-de-prueba\"}"
    oracle_secret             = "valor-de-prueba-oraculo-0123456789abcdef0123"
  }

  assert {
    condition     = sort([for secret in jsondecode(aws_ecs_task_definition.backend.container_definitions)[0].secrets : secret.name]) == tolist(["DB_PASSWORD", "FIREBASE_CREDENTIALS_JSON", "JWT_SECRET_KEY", "ORACLE_SECRET", "OTP_SECRET_KEY"])
    error_message = "Con Firebase y oráculo, la tarea recibe los cinco secretos por secrets."
  }

  assert {
    condition     = length(jsondecode(aws_iam_role_policy.execution_secrets.policy).Statement[0].Resource) == 5
    error_message = "El rol de ejecución lee exactamente los cinco secretos."
  }

  assert {
    condition     = aws_secretsmanager_secret.backend["ORACLE_SECRET"].name == "solventa-oracle-secret-production"
    error_message = "El secreto del oráculo lleva el nombre del proyecto y del ambiente."
  }
}

run "llave_del_oraculo_corta_se_rechaza" {
  command = plan

  module {
    source = "./modules/ecs"
  }

  variables {
    oracle_secret = "llave-corta-de-prueba"
  }

  expect_failures = [var.oracle_secret]
}

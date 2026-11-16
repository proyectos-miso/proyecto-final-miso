# Permisos del rol de la tarea sobre la cola de eventos (ADR 0026 y 0030), con el proveedor simulado.
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

run "el_rol_de_la_tarea_consume_solo_la_cola_de_eventos" {
  command = plan

  module {
    source = "./modules/ecs"
  }

  assert {
    condition = one([
      for s in jsondecode(aws_iam_role_policy.task_documents.policy).Statement : s if s.Sid == "ConsumirEventos"
      ]).Action == [
      "sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility",
    ]
    error_message = "El consumidor necesita leer, aplazar y borrar mensajes, y nada más."
  }

  assert {
    condition = alltrue([
      for s in jsondecode(aws_iam_role_policy.task_documents.policy).Statement :
      s.Resource == var.sqs_queue_arn if anytrue([for a in s.Action : startswith(a, "sqs:")])
    ])
    error_message = "Los permisos de SQS deben limitarse a la cola de eventos."
  }

  assert {
    condition = length(distinct(flatten([
      for s in jsondecode(aws_iam_role_policy.task_documents.policy).Statement :
      s.Action if anytrue([for a in s.Action : startswith(a, "sqs:")])
    ]))) == 5
    error_message = "El rol no debe tener permisos de SQS además de publicar y consumir."
  }

  assert {
    condition = one([
      for s in jsondecode(aws_iam_role_policy.task_documents.policy).Statement : s if s.Sid == "LlaveDelBucket"
    ]).Resource == var.storage_kms_key_arn
    error_message = "La cola está cifrada con la llave de almacenamiento: el rol debe poder descifrar con ella."
  }
}

run "la_cola_de_eventos_tolera_reintentos_crecientes" {
  command = plan

  module {
    source = "./modules/storage_events"
  }

  variables {
    project_name = "solventa"
    environment  = "test"
  }

  override_resource {
    target          = aws_sqs_queue.dlq
    override_during = plan
    values = {
      arn = "arn:aws:sqs:us-east-1:000000000000:solventa-events-dlq-test"
    }
  }

  assert {
    condition     = aws_sqs_queue.events.visibility_timeout_seconds == 120
    error_message = "La visibilidad de la cola debe dejar procesar un lote antes de que el mensaje vuelva."
  }

  assert {
    condition     = jsondecode(aws_sqs_queue.events.redrive_policy).maxReceiveCount == 8
    error_message = "Con la espera creciente del consumidor, ocho recepciones cubren cerca de una hora de caída."
  }

  assert {
    condition     = jsondecode(aws_sqs_queue.events.redrive_policy).deadLetterTargetArn == "arn:aws:sqs:us-east-1:000000000000:solventa-events-dlq-test"
    error_message = "Tras las ocho recepciones, el mensaje debe ir a la DLQ de la cola de eventos."
  }
}

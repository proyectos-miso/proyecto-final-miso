# Clave KMS para cifrado de almacenamiento de documentos y mensajes SQS (HA-15)
resource "aws_kms_key" "storage" {
  description             = "KMS Key para S3 y SQS de Solventa Seguros"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = {
    Name = "${var.project_name}-storage-kms-${var.environment}"
  }
}

# Bucket S3 para pólizas, certificados y documentos (HA-08: versionado)
resource "aws_s3_bucket" "documents" {
  bucket        = "${var.project_name}-documents-${var.environment}"
  force_destroy = true

  tags = {
    Name = "${var.project_name}-documents-${var.environment}"
  }
}

resource "aws_s3_bucket_versioning" "documents" {
  bucket = aws_s3_bucket.documents.id

  versioning_configuration {
    status = "Enabled"
  }
}

# Imágenes de identidad del registro (mejora FE-MOV-01): el backend borra todas sus versiones al decidir, revocar o
# vencer; esta regla es la defensa si un borrado falla. Una verificación pendiente dura a lo sumo 30 días.
# Una sola regla: S3 rechaza dos reglas con prefijos que se solapan y la misma acción Expiration, y no admite
# `expired_object_delete_marker` junto a `days`. Con `days`, S3 también quita el marcador de borrado que queda solo
# cuando vencen las versiones no actuales. Comprobar en el primer `apply` de DEV que S3 acepta la configuración.
resource "aws_s3_bucket_lifecycle_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id

  rule {
    id     = "identidad-vence"
    status = "Enabled"

    filter {
      prefix = "identidad/"
    }

    expiration {
      days = 30
    }

    noncurrent_version_expiration {
      noncurrent_days = 1
    }
  }

  depends_on = [aws_s3_bucket_versioning.documents]
}

resource "aws_s3_bucket_server_side_encryption_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id

  rule {
    apply_server_side_encryption_by_default {
      kms_master_key_id = aws_kms_key.storage.arn
      sse_algorithm     = "aws:kms"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "documents" {
  bucket = aws_s3_bucket.documents.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Dead Letter Queue (DLQ) para eventos fallidos
resource "aws_sqs_queue" "dlq" {
  name                      = "${var.project_name}-events-dlq-${var.environment}"
  message_retention_seconds = 1209600 # 14 días
  kms_master_key_id         = aws_kms_key.storage.id

  tags = {
    Name = "${var.project_name}-events-dlq-${var.environment}"
  }
}

# Cola principal SQS para eventos (HA-08). El consumidor aplaza cada reintento 30 s × 2^(n-1), hasta 15 min (ADR 0030):
# con 8 recepciones, un mensaje aguanta una caída de Firebase de cerca de una hora antes de pasar a la DLQ.
resource "aws_sqs_queue" "events" {
  name                       = "${var.project_name}-events-${var.environment}"
  delay_seconds              = 0
  max_message_size           = 262144
  message_retention_seconds  = 345600 # 4 días
  receive_wait_time_seconds  = 20     # Long polling
  visibility_timeout_seconds = 120    # Un lote de 10 avisos cabe holgado; si el consumidor cae, el mensaje vuelve
  kms_master_key_id          = aws_kms_key.storage.id

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq.arn
    maxReceiveCount     = 8
  })

  tags = {
    Name = "${var.project_name}-events-${var.environment}"
  }
}

output "s3_bucket_name" {
  value = aws_s3_bucket.documents.id
}

output "s3_bucket_arn" {
  value = aws_s3_bucket.documents.arn
}

output "kms_key_arn" {
  value = aws_kms_key.storage.arn
}

output "sqs_queue_url" {
  value = aws_sqs_queue.events.url
}

output "sqs_queue_arn" {
  value = aws_sqs_queue.events.arn
}

output "dlq_queue_url" {
  value = aws_sqs_queue.dlq.url
}

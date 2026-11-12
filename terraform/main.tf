module "vpc" {
  source = "./modules/vpc"

  project_name = var.project_name
  environment  = var.environment
}

module "alb" {
  source = "./modules/alb"

  project_name       = var.project_name
  environment        = var.environment
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
}

module "ecr" {
  source = "./modules/ecr"

  project_name = var.project_name
  environment  = var.environment
}

module "rds" {
  source = "./modules/rds"

  project_name           = var.project_name
  environment            = var.environment
  vpc_id                 = module.vpc.vpc_id
  private_subnet_ids     = module.vpc.private_subnet_ids
  ecs_security_group_id  = module.ecs.ecs_security_group_id
  db_username            = var.db_username
  db_password            = var.db_password
  db_deletion_protection = var.db_deletion_protection
}

module "storage_events" {
  source = "./modules/storage_events"

  project_name = var.project_name
  environment  = var.environment
}

module "ecs" {
  source = "./modules/ecs"

  project_name           = var.project_name
  environment            = var.environment
  vpc_id                 = module.vpc.vpc_id
  private_subnet_ids     = module.vpc.private_subnet_ids
  alb_security_group_id  = module.alb.alb_security_group_id
  target_group_arn       = module.alb.target_group_arn
  ecr_image_url          = "${module.ecr.repository_url}:latest"
  db_host                = module.rds.address
  db_name                = module.rds.db_name
  db_user                = var.db_username
  db_password            = var.db_password
  db_password_secret_arn = module.rds.db_password_secret_arn
  db_kms_key_arn         = module.rds.kms_key_arn
  jwt_secret_key         = var.jwt_secret_key
  otp_secret_key         = var.otp_secret_key
  sqs_queue_url          = module.storage_events.sqs_queue_url
  s3_bucket_name         = module.storage_events.s3_bucket_name
  s3_bucket_arn          = module.storage_events.s3_bucket_arn
  storage_kms_key_arn    = module.storage_events.kms_key_arn
}

module "web_hosting" {
  source = "./modules/web_hosting"

  project_name       = var.project_name
  environment        = var.environment
  api_origin_enabled = true
  alb_dns_name       = module.alb.alb_dns_name
  alb_arn            = module.alb.alb_arn
}


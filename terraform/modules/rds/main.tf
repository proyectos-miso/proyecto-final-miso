# Clave KMS dedicada para cifrado en reposo de la base de datos (HA-15)
resource "aws_kms_key" "rds" {
  description             = "KMS Key para cifrado de almacenamiento de RDS PostgreSQL"
  deletion_window_in_days = 7
  enable_key_rotation     = true

  tags = {
    Name = "${var.project_name}-rds-kms-${var.environment}"
  }
}

# Generación de contraseña aleatoria cuando no se suministra externamente (RC-33)
resource "random_password" "db_password" {
  count   = var.db_password == null || var.db_password == "" ? 1 : 0
  length  = 24
  special = false
}

locals {
  db_password = var.db_password != null && var.db_password != "" ? var.db_password : random_password.db_password[0].result
}

# Secreto en AWS Secrets Manager para almacenar la contraseña de RDS cifrada con KMS (RC-33)
resource "aws_secretsmanager_secret" "db_password" {
  name                    = "${var.project_name}-rds-password-${var.environment}"
  description             = "Contraseña de la base de datos RDS PostgreSQL"
  kms_key_id              = aws_kms_key.rds.arn
  recovery_window_in_days = 0

  tags = {
    Name = "${var.project_name}-rds-password-${var.environment}"
  }
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = local.db_password
}

# Subnet Group en subredes privadas aisladas
resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet-group-${var.environment}"
  subnet_ids = var.private_subnet_ids

  tags = {
    Name = "${var.project_name}-db-subnet-group-${var.environment}"
  }
}

# Security Group para PostgreSQL: solo permite tráfico en 5432 desde ECS
resource "aws_security_group" "rds" {
  name        = "${var.project_name}-rds-sg-${var.environment}"
  description = "Permitir acceso a PostgreSQL unicamente desde las tareas ECS"
  vpc_id      = var.vpc_id

  ingress {
    description     = "PostgreSQL desde ECS Tasks"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.ecs_security_group_id]
  }

  egress {
    description = "Salida restringida"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-rds-sg-${var.environment}"
  }
}

# Instancia RDS PostgreSQL Multi-AZ (HA-15 y HA-06)
resource "aws_db_instance" "main" {
  identifier             = "${var.project_name}-db-${var.environment}"
  engine                 = "postgres"
  engine_version         = "16"
  instance_class         = "db.t4g.micro"
  allocated_storage      = 20
  max_allocated_storage  = 100
  storage_type           = "gp3"
  multi_az               = true
  storage_encrypted      = true
  kms_key_id             = aws_kms_key.rds.arn

  db_name  = "solventa_db"
  username = var.db_username
  password = local.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  backup_retention_period   = 7
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.project_name}-db-final-snapshot-${var.environment}"
  deletion_protection       = var.db_deletion_protection
  publicly_accessible       = false

  tags = {
    Name = "${var.project_name}-postgres-${var.environment}"
  }
}

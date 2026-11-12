# Lista administrada de las direcciones con las que CloudFront llega a sus orígenes
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

# Security Group para el Application Load Balancer
resource "aws_security_group" "alb" {
  name        = "${var.project_name}-alb-sg-${var.environment}"
  description = "Permitir trafico HTTP/HTTPS entrante hacia el ALB"
  vpc_id      = var.vpc_id

  ingress {
    description     = "HTTP solo desde CloudFront (VPC origin)"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.cloudfront.id]
  }

  ingress {
    description     = "HTTPS solo desde CloudFront (VPC origin)"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.cloudfront.id]
  }

  egress {
    description = "Salida hacia contenedores ECS"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-alb-sg-${var.environment}"
  }
}

# Application Load Balancer interno Multi-AZ: solo CloudFront llega a él por la red privada de AWS
resource "aws_lb" "main" {
  name               = "${var.project_name}-alb-${var.environment}"
  internal           = true
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.private_subnet_ids

  enable_deletion_protection = false

  tags = {
    Name = "${var.project_name}-alb-${var.environment}"
  }
}

# Target Group para el backend FastAPI (HA-09: Healthcheck cada 5s, umbral 2)
resource "aws_lb_target_group" "backend" {
  name        = "${var.project_name}-tg-backend-${var.environment}"
  port        = 8000
  protocol    = "HTTP"
  vpc_id      = var.vpc_id
  target_type = "ip"

  health_check {
    enabled             = true
    path                = "/health"
    port                = "8000"
    protocol            = "HTTP"
    interval            = 5
    timeout             = 2
    healthy_threshold   = 2
    unhealthy_threshold = 2
    matcher             = "200"
  }

  tags = {
    Name = "${var.project_name}-tg-backend-${var.environment}"
  }
}

# Certificado ACM para el listener HTTPS del balanceador
resource "aws_acm_certificate" "alb" {
  domain_name       = "${var.project_name}-alb.${var.environment}.internal"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "${var.project_name}-alb-cert-${var.environment}"
  }
}

# Listener HTTPS con TLS 1.2+ que reenvía hacia el Target Group del backend
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate.alb.arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }
}

# Listener HTTP que redirige hacia HTTPS
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

# Alarma de CloudWatch para errores 5XX en el Application Load Balancer (RC-36)
resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  alarm_name          = "${var.project_name}-alb-5xx-errors-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 5
  alarm_description   = "Alarma cuando los errores 5XX superan el umbral en el ALB"
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = aws_lb_target_group.backend.arn_suffix
  }

  tags = {
    Name = "${var.project_name}-alarm-alb-5xx-${var.environment}"
  }
}

# Alarma de CloudWatch para hosts no sanos en el Target Group (RC-36)
resource "aws_cloudwatch_metric_alarm" "unhealthy_hosts" {
  alarm_name          = "${var.project_name}-alb-unhealthy-hosts-${var.environment}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "UnHealthyHostCount"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Maximum"
  threshold           = 0
  alarm_description   = "Alarma cuando hay contenedores ECS backend no sanos en el ALB"
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = aws_lb_target_group.backend.arn_suffix
  }

  tags = {
    Name = "${var.project_name}-alarm-unhealthy-hosts-${var.environment}"
  }
}

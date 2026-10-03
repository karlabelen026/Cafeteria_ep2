# Alarmas de "auto-recovery" (ver docs/EP2_PLAN.md sección 8.1: sin esto, si
# alguna de las dos EC2 falla un chequeo de AWS nadie se entera hasta la
# próxima demo). count = 0 si enable_recovery_alarms = false, por si el
# Learner Lab no deja usar acciones de alarma "aws:recover"/"aws:reboot".

locals {
  # Una entrada por instancia a monitorear: backend (MySQL/RabbitMQ/ms/bff) y
  # frontend (nginx). Mismo par de alarmas para cada una.
  monitored_instances = {
    backend  = aws_instance.backend.id
    frontend = aws_instance.frontend.id
  }
}

resource "aws_cloudwatch_metric_alarm" "status_check_system" {
  for_each = var.enable_recovery_alarms ? local.monitored_instances : {}

  alarm_name          = "cafeteria360-${each.key}-status-check-system"
  alarm_description   = "Recupera la instancia ${each.key} (nueva EC2, misma EIP/EBS) si falla el chequeo de la infraestructura subyacente de AWS."
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed_System"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"

  dimensions = {
    InstanceId = each.value
  }

  alarm_actions = ["arn:aws:automate:${var.region}:ec2:recover"]
}

resource "aws_cloudwatch_metric_alarm" "status_check_instance" {
  for_each = var.enable_recovery_alarms ? local.monitored_instances : {}

  alarm_name          = "cafeteria360-${each.key}-status-check-instance"
  alarm_description   = "Reinicia la instancia ${each.key} si falla su propio chequeo de estado (SO colgado, OOM del kernel, etc.)."
  namespace           = "AWS/EC2"
  metric_name         = "StatusCheckFailed_Instance"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"

  dimensions = {
    InstanceId = each.value
  }

  alarm_actions = ["arn:aws:automate:${var.region}:ec2:reboot"]
}

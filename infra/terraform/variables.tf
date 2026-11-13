# Todas las variables sensibles solo se completan en terraform.tfvars (NUNCA
# subido a git, ver .gitignore de este directorio). terraform.tfvars.example
# trae placeholders.

variable "region" {
  description = "Región de AWS donde se crea todo (AWS Academy Learner Lab solo habilita us-east-1)."
  type        = string
  default     = "us-east-1"
}

variable "backend_instance_type" {
  description = "Tipo de instancia EC2 que corre MySQL + los 13 servicios del backend (Kafka y RabbitMQ tienen sus propias EC2, ver main.tf). La politica Pvoclabs2 del Learner Lab deniega RunInstances/StartInstances para cualquier tamano mayor a large (confirmado con --dry-run)."
  type        = string
  default     = "t3.large"
}

variable "kafka_instance_type" {
  description = "Tipo de instancia EC2 del cluster Kafka (3 Zookeeper + 3 brokers + Kafka UI, ver deploy/aws/docker-compose.kafka.yml)."
  type        = string
  default     = "t3.medium"
}

variable "rabbitmq_instance_type" {
  description = "Tipo de instancia EC2 del cluster RabbitMQ de 3 nodos (ver deploy/aws/docker-compose.rabbitmq.yml)."
  type        = string
  default     = "t3.medium"
}

variable "backend_image" {
  description = "Imagen unica de los 13 servicios del backend, publicada en GHCR por .github/workflows/build-images.yml. Ej: ghcr.io/<owner>/cafeteria-backend:deploy"
  type        = string
}

variable "ghcr_token" {
  description = "Opcional: token de GitHub con permiso read:packages, solo si el package de GHCR es privado. Vacio = el package es publico y la EC2 hace pull sin login."
  type        = string
  default     = ""
  sensitive   = true
}

variable "frontend_instance_type" {
  description = "Tipo de instancia EC2 que solo corre nginx sirviendo el build estatico de React (ver main.tf). Mucho mas liviana que la de backend: no corre ninguna JVM."
  type        = string
  default     = "t3.micro"
}

variable "backend_availability_zone" {
  description = "AZ fija para la instancia de backend y para el volumen EBS de datos de MySQL (aws_ebs_volume.mysql_data). Tiene que ser la misma para ambos (un volumen EBS solo se adjunta a instancias de su propia AZ); fijarla evita que un reemplazo de la instancia (terraform apply -replace=aws_instance.backend) la mande a otra AZ y deje el volumen con datos sin poder re-adjuntarse."
  type        = string
  default     = "us-east-1a"
}

variable "key_name" {
  description = "Nombre del key pair ya existente en el Learner Lab (siempre se llama \"vockey\")."
  type        = string
  default     = "vockey"
}

variable "iam_instance_profile" {
  description = "Instance profile ya creado por AWS Academy (siempre \"LabInstanceProfile\"). Este módulo NO crea roles ni políticas IAM: el Learner Lab no lo permite."
  type        = string
  default     = "LabInstanceProfile"
}

variable "my_ip_cidr" {
  description = "Tu IP pública en formato CIDR (p.ej. \"190.123.45.67/32\"), para restringir SSH (22) solo a ti."
  type        = string
}

variable "ui_allowed_cidrs" {
  description = "Desde donde se puede abrir el panel de RabbitMQ (15672) y Kafka UI (8095). Por defecto cualquier IP, para poder mostrarlos desde otro computador o red (ej. la del Duoc): los dos piden usuario y clave. Para restringirlos, pasa una lista como [\"190.123.45.67/32\"]."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "repo_url" {
  description = "URL del repositorio Git que el user_data clona en la EC2."
  type        = string
}

variable "repo_branch" {
  description = "Rama del repositorio a clonar (y a la que apunta \"git pull\" en cada actualización manual, ver infra/terraform/README.md)."
  type        = string
  default     = "main"
}

variable "azure_tenant_id" {
  description = "Directory (tenant) ID del tenant de Azure Entra ID (sección 7)."
  type        = string
}

variable "azure_backend_client_id" {
  description = "Application (client) ID del App Registration \"cafeteria-backend\"."
  type        = string
}

variable "azure_frontend_client_id" {
  description = "Application (client) ID del App Registration SPA \"cafeteria-frontend\"."
  type        = string
}

variable "azure_issuer_uri" {
  description = "Issuer real del tenant (ver docs/EP2_PLAN.md sección 7, paso 1). Ej: https://<subdominio>.ciamlogin.com/<TENANT_ID>/v2.0"
  type        = string
}

variable "azure_jwk_set_uri" {
  description = "Solo necesario en tenants Microsoft Entra External ID (ciamlogin); vacío en tenants estándar."
  type        = string
  default     = ""
}

variable "azure_authority" {
  description = "Authority del frontend. En tenants External ID: https://<subdominio>.ciamlogin.com/"
  type        = string
  default     = ""
}

variable "db_password" {
  description = "Password de MySQL (usuario fijo \"cafeteria\")."
  type        = string
  sensitive   = true
}

variable "rabbitmq_password" {
  description = "Password de los 3 nodos RabbitMQ y de las apps que publican/consumen mensajes."
  type        = string
  sensitive   = true
}

variable "rabbitmq_erlang_cookie" {
  description = "Cookie compartida por los 3 nodos RabbitMQ para formar el cluster (debe ser idéntica en los tres, ver docs/EP2_PLAN.md sección 3 / Fase 2)."
  type        = string
  sensitive   = true
}

variable "duckdns_domain" {
  description = "Dominio completo de DuckDNS (ej. \"cafegestion360.duckdns.org\", cuenta gratuita en duckdns.org) para que la URL del frontend, el redirect de Azure y el CORS del backend no tengan que cambiar cada vez que el AWS Academy Learner Lab da una Elastic IP (o una cuenta) nueva. Vacio = se usa la Elastic IP directamente (comportamiento anterior). El token de DuckDNS NO es una variable de Terraform: el workflow terraform-deploy.yml lo usa directo (secret DUCKDNS_TOKEN) para actualizar el registro DNS despues de cada apply, ver infra/terraform/README.md."
  type        = string
  default     = ""
}

variable "enable_recovery_alarms" {
  description = "Crea las alarmas CloudWatch de recuperación/reinicio automático (monitoring.tf). Desactívalo si el Learner Lab no permite acciones de alarma \"aws:recover\"/\"aws:reboot\"."
  type        = bool
  default     = true
}

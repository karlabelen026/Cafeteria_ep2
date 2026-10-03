# AMI Ubuntu 22.04 LTS oficial de Canonical (data source: no se hardcodea el
# ID, que cambia por región y con cada parche).
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd/ubuntu-jammy-22.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# Secreto que el BFF exige en el header "X-Origin-Verify" (ver
# OriginVerifyGlobalFilter en bff-gateway) para rechazar quien le pegue
# directo al puerto 8080 saltándose el API Gateway (prueba S8).
resource "random_password" "origin_verify_secret" {
  length  = 32
  special = false
}

resource "aws_security_group" "app" {
  name        = "cafeteria360-sg"
  description = "CafeGestion360: SSH y UI de RabbitMQ solo desde mi IP; 80/443 publicos; 8080 para el API Gateway"

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.my_ip_cidr]
  }

  ingress {
    description = "RabbitMQ management UI (solo nodo 1)"
    from_port   = 15672
    to_port     = 15672
    protocol    = "tcp"
    cidr_blocks = [var.my_ip_cidr]
  }

  ingress {
    description = "HTTP del frontend (nginx redirige a 443)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS del frontend (certificado autofirmado)"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "BFF: solo deberia llegarle trafico del API Gateway (OriginVerifyGlobalFilter rechaza el resto, ver bff-gateway)"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Salida libre (pull de imagenes, apt, npm, Azure, etc.)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cafeteria360-sg"
  }
}

# Se reserva antes que la instancia para poder inyectar la IP en su propio
# user_data sin crear una dependencia circular; la asociación se hace aparte
# con aws_eip_association una vez que la instancia ya existe.
resource "aws_eip" "app" {
  domain = "vpc"

  tags = {
    Name = "cafeteria360-eip"
  }
}

resource "aws_instance" "app" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  key_name               = var.key_name
  iam_instance_profile   = var.iam_instance_profile
  vpc_security_group_ids = [aws_security_group.app.id]

  root_block_device {
    volume_type = "gp3"
    # 50 GB (antes 30): con 30 GB "docker compose build" de los 11
    # contenedores (8 ms + bff + notificaciones + admin, cada uno con su
    # propia imagen Maven) + las imagenes de MySQL y los 3 nodos RabbitMQ
    # dejaba el disco muy justo.
    volume_size = 50
  }

  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    repo_url                 = var.repo_url
    repo_branch              = var.repo_branch
    eip                      = aws_eip.app.public_ip
    db_password              = var.db_password
    rabbitmq_password        = var.rabbitmq_password
    rabbitmq_erlang_cookie   = var.rabbitmq_erlang_cookie
    azure_tenant_id          = var.azure_tenant_id
    azure_backend_client_id  = var.azure_backend_client_id
    azure_frontend_client_id = var.azure_frontend_client_id
    azure_issuer_uri         = var.azure_issuer_uri
    azure_jwk_set_uri        = var.azure_jwk_set_uri
    azure_authority          = var.azure_authority
    origin_verify_secret     = random_password.origin_verify_secret.result
    api_gateway_url          = "${aws_apigatewayv2_api.app.api_endpoint}/api"
  })

  tags = {
    Name = "cafeteria360"
  }
}

resource "aws_eip_association" "app" {
  instance_id   = aws_instance.app.id
  allocation_id = aws_eip.app.id
}

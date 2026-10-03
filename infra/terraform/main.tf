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

resource "aws_security_group" "backend" {
  name        = "cafeteria360-backend-sg"
  description = "CafeGestion360 backend (MySQL, RabbitMQ, microservicios, bff-gateway): SSH y UI de RabbitMQ solo desde mi IP; 8080 publico para el API Gateway"

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
    description = "BFF: solo deberia llegarle trafico del API Gateway (OriginVerifyGlobalFilter rechaza el resto, ver bff-gateway)"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Salida libre (pull de imagenes, apt, Maven, Azure, etc.)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cafeteria360-backend-sg"
  }
}

resource "aws_security_group" "frontend" {
  name        = "cafeteria360-frontend-sg"
  description = "CafeGestion360 frontend (nginx): SSH solo desde mi IP; 80/443 publicos"

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
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

  egress {
    description = "Salida libre (pull de imagenes, apt, npm, etc.)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cafeteria360-frontend-sg"
  }
}

# Se reservan antes que las instancias para poder inyectar las IPs en los
# user_data (el back necesita la IP del front para CORS/FRONTEND_ORIGIN, el
# front necesita la suya propia) sin crear una dependencia circular; la
# asociación se hace aparte con aws_eip_association una vez que la instancia
# ya existe.
resource "aws_eip" "backend" {
  domain = "vpc"

  tags = {
    Name = "cafeteria360-backend-eip"
  }
}

resource "aws_eip" "frontend" {
  domain = "vpc"

  tags = {
    Name = "cafeteria360-frontend-eip"
  }
}

resource "aws_instance" "backend" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.backend_instance_type
  key_name               = var.key_name
  iam_instance_profile   = var.iam_instance_profile
  vpc_security_group_ids = [aws_security_group.backend.id]

  root_block_device {
    volume_type = "gp3"
    # 50 GB: "docker compose build" de los 10 contenedores (8 ms + bff +
    # notificaciones+admin, cada uno con su propia imagen Maven) + la imagen
    # de MySQL y los 3 nodos RabbitMQ dejaba el disco muy justo con menos.
    volume_size = 50
  }

  user_data = templatefile("${path.module}/user_data_backend.sh.tftpl", {
    repo_url                = var.repo_url
    repo_branch             = var.repo_branch
    frontend_eip            = aws_eip.frontend.public_ip
    db_password             = var.db_password
    rabbitmq_password       = var.rabbitmq_password
    rabbitmq_erlang_cookie  = var.rabbitmq_erlang_cookie
    azure_issuer_uri        = var.azure_issuer_uri
    azure_backend_client_id = var.azure_backend_client_id
    azure_jwk_set_uri       = var.azure_jwk_set_uri
    origin_verify_secret    = random_password.origin_verify_secret.result
  })

  tags = {
    Name = "cafeteria360-backend"
  }
}

resource "aws_instance" "frontend" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.frontend_instance_type
  key_name               = var.key_name
  iam_instance_profile   = var.iam_instance_profile
  vpc_security_group_ids = [aws_security_group.frontend.id]

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
  }

  user_data = templatefile("${path.module}/user_data_frontend.sh.tftpl", {
    repo_url                 = var.repo_url
    repo_branch              = var.repo_branch
    eip                      = aws_eip.frontend.public_ip
    azure_tenant_id          = var.azure_tenant_id
    azure_frontend_client_id = var.azure_frontend_client_id
    azure_backend_client_id  = var.azure_backend_client_id
    azure_authority          = var.azure_authority
    api_gateway_url          = "${aws_apigatewayv2_api.app.api_endpoint}/api"
  })

  tags = {
    Name = "cafeteria360-frontend"
  }
}

resource "aws_eip_association" "backend" {
  instance_id   = aws_instance.backend.id
  allocation_id = aws_eip.backend.id
}

resource "aws_eip_association" "frontend" {
  instance_id   = aws_instance.frontend.id
  allocation_id = aws_eip.frontend.id
}

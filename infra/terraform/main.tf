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

# Nombre publico del frontend: el dominio de DuckDNS si esta configurado
# (ver variables.tf, duckdns_domain), o la propia Elastic IP si no. Se usa en
# el certificado autofirmado, en VITE_AZURE_REDIRECT_URI y en el
# FRONTEND_ORIGIN (CORS) del backend, para que ninguno de los tres tenga que
# tocarse a mano cuando la IP cambia entre sesiones del Learner Lab (ver
# terraform-deploy.yml, paso "Actualizar DuckDNS").
locals {
  frontend_hostname = var.duckdns_domain != "" ? var.duckdns_domain : aws_eip.frontend.public_ip
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
  availability_zone      = var.backend_availability_zone
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
    frontend_hostname       = local.frontend_hostname
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

# Volumen EBS separado del disco raiz para /var/lib/mysql (ver
# user_data_backend.sh.tftpl: se monta en $APP_DIR/mysql-data, que
# docker-compose.yml bind-mountea en el contenedor de MySQL). Al vivir en un
# resource aparte de aws_instance.backend, un reemplazo de la instancia
# (ej. "terraform apply -replace=aws_instance.backend" para probar un boot
# limpio) no lo destruye: los datos de MySQL sobreviven. Debe estar en la
# misma AZ que la instancia, por eso esta fija en backend_availability_zone.
resource "aws_ebs_volume" "mysql_data" {
  availability_zone = var.backend_availability_zone
  size              = 20
  type              = "gp3"

  tags = {
    Name = "cafeteria360-mysql-data"
  }
}

resource "aws_volume_attachment" "mysql_data" {
  device_name = "/dev/sdf"
  volume_id   = aws_ebs_volume.mysql_data.id
  instance_id = aws_instance.backend.id
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
    frontend_hostname        = local.frontend_hostname
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

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

# Secreto compartido para las llamadas servicio-a-servicio (header
# X-Internal-Token, ver ms-pedidos InternalTokenFilter).
resource "random_password" "internal_api_token" {
  length  = 32
  special = false
}

resource "aws_security_group" "backend" {
  name        = "cafeteria360-backend-sg"
  description = "CafeGestion360 backend (MySQL, microservicios, bff-gateway): SSH solo desde mi IP; 8080 publico para el API Gateway"

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
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

# Los brokers de Kafka y RabbitMQ solo aceptan trafico de los microservicios
# (SG de backend), nunca de internet. Las UIs de administracion se abren a
# ui_allowed_cidrs (por defecto cualquier IP) y piden usuario y clave.
resource "aws_security_group" "kafka" {
  name        = "cafeteria360-kafka-sg"
  description = "CafeGestion360 Kafka (3 brokers + Kafka UI): brokers solo desde el backend; Kafka UI con login; SSH solo desde mi IP"

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.my_ip_cidr]
  }

  ingress {
    description = "Kafka UI (pide usuario y clave)"
    from_port   = 8095
    to_port     = 8095
    protocol    = "tcp"
    cidr_blocks = var.ui_allowed_cidrs
  }

  ingress {
    description     = "Brokers kafka-1/2/3 (listener PLAINTEXT_HOST): solo los microservicios"
    from_port       = 9092
    to_port         = 9094
    protocol        = "tcp"
    security_groups = [aws_security_group.backend.id]
  }

  egress {
    description = "Salida libre (pull de imagenes, apt, git)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cafeteria360-kafka-sg"
  }
}

resource "aws_security_group" "rabbitmq" {
  name        = "cafeteria360-rabbitmq-sg"
  description = "CafeGestion360 RabbitMQ (3 nodos): AMQP solo desde el backend; management con login; SSH solo desde mi IP"

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.my_ip_cidr]
  }

  ingress {
    description = "RabbitMQ management UI (nodo 1, pide usuario y clave)"
    from_port   = 15672
    to_port     = 15672
    protocol    = "tcp"
    cidr_blocks = var.ui_allowed_cidrs
  }

  ingress {
    description     = "AMQP de rabbitmq-1/2/3: solo los microservicios"
    from_port       = 5672
    to_port         = 5674
    protocol        = "tcp"
    security_groups = [aws_security_group.backend.id]
  }

  ingress {
    description     = "API de management para ms-rabbitmq-admin"
    from_port       = 15672
    to_port         = 15672
    protocol        = "tcp"
    security_groups = [aws_security_group.backend.id]
  }

  egress {
    description = "Salida libre (pull de imagenes, apt, git)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "cafeteria360-rabbitmq-sg"
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

# 4 Elastic IPs en total (el Learner Lab permite hasta 5): las de Kafka y
# RabbitMQ solo sirven para entrar a sus UIs y por SSH desde mi IP; los
# microservicios les hablan por IP privada.
resource "aws_eip" "kafka" {
  domain = "vpc"

  tags = {
    Name = "cafeteria360-kafka-eip"
  }
}

resource "aws_eip" "rabbitmq" {
  domain = "vpc"

  tags = {
    Name = "cafeteria360-rabbitmq-eip"
  }
}

# Kafka y RabbitMQ van en EC2 propias (separadas del backend). Sus IP privadas
# no cambian al detener/iniciar la instancia, por eso el backend las recibe
# directo en su user_data.
resource "aws_instance" "kafka" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.kafka_instance_type
  key_name               = var.key_name
  iam_instance_profile   = var.iam_instance_profile
  vpc_security_group_ids = [aws_security_group.kafka.id]

  # Ver el comentario en aws_instance.backend.
  user_data_replace_on_change = true

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
  }

  user_data = templatefile("${path.module}/user_data_kafka.sh.tftpl", {
    repo_url          = var.repo_url
    repo_branch       = var.repo_branch
    kafka_ui_password = var.rabbitmq_password
  })

  tags = {
    Name = "cafeteria360-kafka"
  }
}

resource "aws_instance" "rabbitmq" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.rabbitmq_instance_type
  key_name               = var.key_name
  iam_instance_profile   = var.iam_instance_profile
  vpc_security_group_ids = [aws_security_group.rabbitmq.id]

  # Ver el comentario en aws_instance.backend.
  user_data_replace_on_change = true

  root_block_device {
    volume_type = "gp3"
    volume_size = 20
  }

  user_data = templatefile("${path.module}/user_data_rabbitmq.sh.tftpl", {
    repo_url               = var.repo_url
    repo_branch            = var.repo_branch
    rabbitmq_password      = var.rabbitmq_password
    rabbitmq_erlang_cookie = var.rabbitmq_erlang_cookie
  })

  tags = {
    Name = "cafeteria360-rabbitmq"
  }
}

resource "aws_instance" "backend" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.backend_instance_type
  availability_zone      = var.backend_availability_zone
  key_name               = var.key_name
  iam_instance_profile   = var.iam_instance_profile
  vpc_security_group_ids = [aws_security_group.backend.id]

  # Sin esto, un cambio de user_data NO reemplaza la instancia: el provider
  # para la instancia, le actualiza el atributo user-data (API de AWS exige
  # pararla para eso) y la vuelve a prender con el MISMO instance-id. Pero
  # cloud-init solo corre el user_data una vez por instance-id (lo rastrea el
  # el propio SO) — al reiniciar NO lo vuelve a ejecutar, asi que el .env y
  # el certificado se quedan con el contenido del primer boot. Con esto en
  # true, un cambio de user_data siempre destruye y crea la instancia de
  # nuevo, garantizando que cloud-init corre con el contenido actualizado.
  user_data_replace_on_change = true

  root_block_device {
    volume_type = "gp3"
    # Ya no compila nada (solo pull de la imagen de GHCR, que pesa cerca de
    # 2 GB); se mantiene el tamano para no reemplazar el disco sin necesidad.
    volume_size = 50
  }

  user_data = templatefile("${path.module}/user_data_backend.sh.tftpl", {
    repo_url                = var.repo_url
    repo_branch             = var.repo_branch
    frontend_hostname       = local.frontend_hostname
    db_password             = var.db_password
    rabbitmq_password       = var.rabbitmq_password
    azure_issuer_uri        = var.azure_issuer_uri
    azure_backend_client_id = var.azure_backend_client_id
    azure_jwk_set_uri       = var.azure_jwk_set_uri
    origin_verify_secret    = random_password.origin_verify_secret.result
    internal_api_token      = random_password.internal_api_token.result
    backend_image           = var.backend_image
    ghcr_token              = var.ghcr_token
    kafka_private_ip        = aws_instance.kafka.private_ip
    rabbitmq_private_ip     = aws_instance.rabbitmq.private_ip
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

  # Ver el comentario identico en aws_instance.backend.
  user_data_replace_on_change = true

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
    rabbitmq_dashboard_url   = "http://${aws_eip.rabbitmq.public_ip}:15672"
    kafka_ui_url             = "http://${aws_eip.kafka.public_ip}:8095"
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

resource "aws_eip_association" "kafka" {
  instance_id   = aws_instance.kafka.id
  allocation_id = aws_eip.kafka.id
}

resource "aws_eip_association" "rabbitmq" {
  instance_id   = aws_instance.rabbitmq.id
  allocation_id = aws_eip.rabbitmq.id
}

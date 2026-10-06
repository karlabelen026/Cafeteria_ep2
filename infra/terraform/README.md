# Terraform — CafeGestión360 en AWS Academy Learner Lab

Crea **dos** EC2 (Ubuntu 22.04) con Docker Compose: una de **backend**
(MySQL + RabbitMQ cluster + los 8 microservicios + bff-gateway) y otra de
**frontend** (solo nginx sirviendo el build de React), más un API Gateway
(HTTP API) delante del BFF de la instancia de backend. El frontend nunca le
habla a la instancia de backend directamente: siempre pasa por el API
Gateway (`VITE_API_BASE_URL`). Pensado para el Learner Lab: usa el key pair
`vockey` y el `LabInstanceProfile` ya existentes, y no crea ningún rol ni
política IAM (el Lab no lo permite).

Ver el diseño completo en [`docs/EP2_PLAN.md`](../../docs/EP2_PLAN.md),
sección 8.

## Qué crea

| Archivo | Recursos |
|---|---|
| `versions.tf` | providers `aws` y `random` |
| `variables.tf` | todas las variables de entrada |
| `main.tf` | AMI Ubuntu 22.04, 2 Security Groups, 2 Elastic IP, las 2 instancias EC2 (backend: disco gp3 50 GB; frontend: 20 GB) |
| `monitoring.tf` | alarmas CloudWatch de auto-recuperación/reinicio, una por cada instancia |
| `apigateway.tf` | API Gateway HTTP API → proxy hacia el BFF de la instancia de backend en el puerto 8080, inyectando `X-Origin-Verify` |
| `user_data_backend.sh.tftpl` | script de primer arranque de la instancia de backend: Docker, swap 4 GB, clona el repo, genera `.env`, crea `cafeteria.service` (levanta todo menos `frontend`) |
| `user_data_frontend.sh.tftpl` | script de primer arranque de la instancia de frontend: Docker, swap 2 GB, clona el repo, genera `.env`, certificado autofirmado, crea `cafeteria.service` (levanta solo `frontend`, con `--no-deps`) |
| `outputs.tf` | IPs elásticas, URLs, comandos SSH, secreto de `X-Origin-Verify` |
| `backend.tf` | backend remoto S3 + bloqueo DynamoDB (vacío: se configura en `init-backend.sh`) |
| `init-backend.sh` | crea (si no existen) el bucket S3 y la tabla DynamoDB del state remoto, y corre `terraform init` apuntando a ellos — lo usan tanto GitHub Actions como vos en local |

## Requisitos

- Terraform >= 1.5 y AWS CLI v2.
- Una sesión activa de **AWS Academy Learner Lab**: copia `aws_access_key_id`,
  `aws_secret_access_key` y `aws_session_token` desde "AWS Details" a
  `~/.aws/credentials` (caducan cada pocas horas, hay que renovarlas).
- Tu IP pública (para `my_ip_cidr`).
- Los datos de tus App Registrations de Azure (sección 7 / 7-bis del plan).

## Por qué hay un backend remoto (S3 + DynamoDB)

El state de Terraform **no** vive solo en tu disco: `init-backend.sh` crea (la
primera vez) un bucket S3 (`cafegestion360-tfstate-<tu-account-id>`) y una
tabla DynamoDB (`cafegestion360-tfstate-lock`) y configura `terraform init`
para usarlos. Es imprescindible para que GitHub Actions funcione (cada
workflow corre en una máquina nueva y vacía: sin backend remoto, cada
`apply` no encontraría el state anterior y volvería a crear una EC2 nueva,
dejando la vieja huérfana) y también sirve en local para no perder el state
si reinstalás tu máquina.

## Uso (local, desde tu terminal)

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # completa tus valores reales
./init-backend.sh                               # crea el backend remoto (si no existe) + terraform init
terraform plan
terraform apply                                 # escribe "yes"
terraform output                                # IP, URLs
```

Después del `apply`:

1. Agrega `https://<frontend_elastic_ip>` a los Redirect URIs del SPA en
   Azure (sección 7, paso 8).
2. Abre `https://<frontend_elastic_ip>` y acepta la advertencia del
   certificado autofirmado (es autofirmado a propósito, no hay dominio
   propio).
3. UI de RabbitMQ: `http://<backend_elastic_ip>:15672` (solo visible desde
   tu IP).
4. El frontend y el `.env` del backend ya quedan apuntando al API Gateway
   (`terraform output api_gateway_url`): no hace falta tocar nada a mano.

### Ver el arranque (tarda 10–15 min la primera vez el backend; unos pocos
### minutos el frontend)

```bash
ssh -i labsuser.pem ubuntu@<backend_elastic_ip> 'sudo tail -f /var/log/cloud-init-output.log'
ssh -i labsuser.pem ubuntu@<backend_elastic_ip> 'cd /opt/cafeteria && sudo docker compose ps'

ssh -i labsuser.pem ubuntu@<frontend_elastic_ip> 'sudo tail -f /var/log/cloud-init-output.log'
ssh -i labsuser.pem ubuntu@<frontend_elastic_ip> 'cd /opt/cafeteria && sudo docker compose ps'
```

### Actualizar después de un `git push`

```bash
ssh -i labsuser.pem ubuntu@<backend_elastic_ip> \
  'cd /opt/cafeteria && sudo git pull && sudo docker compose up -d --build mysql rabbitmq-1 rabbitmq-2 rabbitmq-3 ms-productos ms-inventario ms-pedidos ms-clientes ms-pagos ms-empleados ms-proveedores ms-reportes ms-notificaciones ms-rabbitmq-admin bff-gateway'

ssh -i labsuser.pem ubuntu@<frontend_elastic_ip> \
  'cd /opt/cafeteria && sudo git pull && sudo docker compose up -d --build --no-deps frontend'
```

### Validar sin credenciales de AWS (CI o antes de pedirlas)

```bash
terraform init -backend=false
terraform validate
terraform fmt -check
```

### Al terminar el semestre (o cada vez que cierres la sesión del Lab)

```bash
./init-backend.sh
terraform destroy
```

## CI/CD con GitHub Actions

**CI (Integración Continua)**: en cada push y cada pull request, a cualquier
rama, [`ci.yml`](../../.github/workflows/ci.yml) compila el backend
(`mvnw clean verify`) y el frontend (`npm run build`) y corre las pruebas —
detecta errores de inmediato, sin necesitar ningún secret ni tocar AWS.

**CD (Entrega/Despliegue Continuo)**: una vez que el código pasó CI, estos
dos workflows son los que preparan o aplican la infraestructura real en AWS:

| Workflow | Qué hace |
|---|---|
| `terraform-deploy.yml` | **Etapa 1** — `terraform plan` + `apply` contra AWS: crea/actualiza las 2 EC2 (backend y frontend), el volumen EBS de MySQL y el API Gateway. **Etapa 2** (automática, no es un paso de este workflow) — cada EC2, al arrancar, corre sola `docker compose up --build`: la de backend levanta MySQL + RabbitMQ + los 9 microservicios + bff-gateway; la de frontend levanta nginx. Pide confirmar la rama que la EC2 va a clonar (`repo_branch`, por defecto `deploy`) y, opcionalmente, `replace_target` (ej. `aws_instance.backend`) para forzar el reemplazo de un recurso puntual sin tocar el resto. Publica los outputs (IPs, URLs) en el resumen del job. |
| `terraform-destroy.yml` | `terraform destroy`. Pide escribir literalmente `destruir` en el input `confirmar` para evitar un click accidental. |

Los dos son **solo con disparo manual** (`workflow_dispatch`), no en cada
push: eso técnicamente lo hace **Entrega Continua** (el código queda listo
para desplegar, pero alguien aprueba el lanzamiento con "Run workflow"), no
**Despliegue Continuo** puro (que aplicaría sin que nadie lo apruete). La
razón es concreta: las credenciales de AWS Academy Learner Lab expiran cada
pocas horas, así que automatizarlo en cada push fallaría la mayoría de las
veces y además re-aplicaría infraestructura real sin que nadie lo pidiera —
no es una limitación del pipeline, es una decisión deliberada por las
restricciones del Learner Lab.

| Workflow | Qué hace |
|---|---|
| `terraform-deploy.yml` | `terraform plan` + `apply` contra AWS. Pide confirmar la rama que la EC2 va a clonar (`repo_branch`, por defecto `deploy`) y, opcionalmente, `replace_target` (ej. `aws_instance.backend`) para forzar el reemplazo de un recurso puntual sin tocar el resto. Publica los outputs (IP, URLs) en el resumen del job. |
| `terraform-destroy.yml` | `terraform destroy`. Pide escribir literalmente `destruir` en el input `confirmar` para evitar un click accidental. |

### Cómo correrlos

1. **Cada vez que arranques el AWS Academy Learner Lab**: "AWS Details" →
   copia `aws_access_key_id`, `aws_secret_access_key` y `aws_session_token` →
   actualiza los secrets `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` y
   `AWS_SESSION_TOKEN` del repo (Settings → Secrets and variables → Actions).
   Son temporales: si el workflow falla con un error de autenticación, es
   casi siempre porque el token ya venció.
2. Pestaña **Actions** → elige "Terraform - Deploy a AWS (Academy Lab)" →
   **Run workflow** → elige la rama del propio workflow (da igual, el código
   que se despliega en la EC2 es el de `repo_branch`) y el valor de
   `repo_branch` (por defecto `deploy`; asegurate de haber pusheado esa rama
   antes).
3. Para apagar todo: "Terraform - Destruir infraestructura AWS" → **Run
   workflow** → escribe `destruir` en el campo de confirmación.

### Secrets necesarios en el repo

| Secret | Para qué |
|---|---|
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` | credenciales temporales de AWS Academy |
| `AWS_REGION` | opcional, default `us-east-1` |
| `MY_IP_CIDR` | tu IP pública en formato CIDR, para `variables.tf` |
| `AZURE_TENANT_ID`, `AZURE_BACKEND_CLIENT_ID`, `AZURE_FRONTEND_CLIENT_ID`, `AZURE_ISSUER_URI` | datos de tus App Registrations (sección 7 del plan) |
| `AZURE_JWK_SET_URI`, `AZURE_AUTHORITY` | solo tenants External ID (ciamlogin); dejalos vacíos si no aplica |
| `DB_PASSWORD`, `RABBITMQ_PASSWORD`, `RABBITMQ_ERLANG_COOKIE` | passwords de MySQL y RabbitMQ |
| `ENABLE_RECOVERY_ALARMS` | opcional, default `true` |
| `DUCKDNS_DOMAIN`, `DUCKDNS_TOKEN` | opcionales — dominio completo (ej. `cafegestion360.duckdns.org`) y token de una cuenta gratuita en [duckdns.org](https://www.duckdns.org). Si están, la URL del frontend (y el redirect de Azure, y el CORS del backend) queda fija en ese dominio en vez de la Elastic IP — necesario porque el Learner Lab puede darte una Elastic IP (o hasta una cuenta de AWS) nueva en cada sesión. El workflow actualiza el registro DNS a la IP actual después de cada `apply`. Vacíos = se sigue usando la Elastic IP directamente. |

`repo_url` **no** es un secret: el workflow lo arma solo con
`https://github.com/<owner>/<repo>.git` (`github.repository`), así que la
EC2 siempre clona el mismo repo donde corre el workflow.

No hace falta configurar nada de backend remoto en los secrets: el workflow
corre `init-backend.sh`, que crea/reutiliza el bucket S3 y la tabla DynamoDB
automáticamente a partir del Account ID de las credenciales que le pasaste.

## Notas de diseño

- **Volumen EBS separado para los datos de MySQL**: `aws_ebs_volume.mysql_data`
  (20 GB, misma AZ fija que la instancia via `backend_availability_zone`) vive
  aparte del disco raíz de la instancia de backend y se monta en
  `$APP_DIR/mysql-data` (`user_data_backend.sh.tftpl`), que `docker-compose.yml`
  bind-mountea en `/var/lib/mysql`. Un `terraform apply -replace=aws_instance.backend`
  destruye y recrea la instancia (y el volumen de datos de **Docker**, que
  vivía en el disco raíz), pero el volumen EBS no se toca: el script lo
  detecta (ya tiene filesystem, no lo reformatea) y lo vuelve a montar con
  los datos intactos. Sin esto, cada reemplazo de instancia dejaba la base de
  datos vacía.
- **Dos instancias, sin Elastic Load Balancer ni Auto Scaling**: backend y
  frontend separados (frontend liviano en `t3.micro`, backend con todo el
  peso de las JVM en `t3.large`) alcanza para la demo y evita costos/roles
  IAM extra que el Learner Lab no permite crear. El frontend no depende de
  tener al backend arrancado para construirse ni para arrancar (le habla por
  HTTP al API Gateway en tiempo de ejecución, nunca en build time), así que
  las dos instancias se pueden crear/reemplazar de forma independiente.
- **`X-Origin-Verify`**: el secreto lo genera Terraform (`random_password`) y
  lo inyecta el API Gateway en cada petición (parameter mapping de la
  integración HTTP_PROXY, solo soportado con `payload_format_version = "1.0"`)
  y en el `.env` de la EC2. El BFF (`OriginVerifyGlobalFilter`) lo compara y
  devuelve 403 si alguien le pega directo al puerto 8080 (prueba S8 de
  `docs/EP2_PLAN.md` sección 11).
  **Ojo con la sintaxis del valor estático en `request_parameters`**: va
  **sin** comillas ni llaves (`"overwrite:header.x-origin-verify" = random_password.origin_verify_secret.result`,
  valor literal tal cual). La documentación de AWS muestra la columna
  "Static value" como `{{string}}`, pero eso es notación de marcador de
  posición, no sintaxis real — tanto `'valor'` (comillas simples) como
  `{{valor}}` (llaves literales) fallan: el primero no da error pero el
  mapping nunca se aplica en runtime (todo responde 403 a través del
  Gateway, sin pista de por qué); el segundo sí da un 400 explícito
  ("Invalid mapping expression specified") al aplicar.
- **Elastic IP antes que la instancia**: cada una se reserva con `aws_eip`
  (sin asociar) para poder pasar su IP al `user_data` de la propia instancia
  (y la del frontend también al `user_data` del backend, para
  `FRONTEND_ORIGIN`/CORS) sin crear una dependencia circular;
  `aws_eip_association` las asocia después.
- **`enable_recovery_alarms`**: el Learner Lab a veces no permite acciones de
  alarma `aws:recover`/`aws:reboot`; si `terraform apply` falla en
  `monitoring.tf`, pon esta variable en `false` y vuelve a aplicar.
- **systemd en vez de `restart` de Docker a secas**: el Learner Lab apaga la
  instancia al cerrar la sesión del lab. `cafeteria.service` (habilitado con
  `systemctl enable`) hace `docker compose up -d --build` en cada arranque,
  así que al reabrir el lab todo vuelve a subir solo, con la misma Elastic IP.
- **Backend S3 + DynamoDB en vez de state local**: necesario para que
  GitHub Actions (runners efímeros, sin disco persistente entre corridas) y
  tu máquina local compartan el mismo state. El nombre del bucket incluye el
  Account ID (`cafegestion360-tfstate-<account-id>`) para que sea único sin
  tener que inventarlo ni guardarlo como secret aparte. `init-backend.sh` no
  borra el bucket/tabla en ningún flujo (ni siquiera el de destroy): son
  baratos de dejar y evitan tener que recrear el backend en cada sesión del
  Lab.

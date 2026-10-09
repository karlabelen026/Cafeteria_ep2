# CafeGestión360 — DSY1107 Desarrollo Cloud Native I — Evaluación Parcial 2

Sistema de gestión para una cafetería: tienda pública con carrito y pago, y un portal interno por
roles (pedidos, inventario, recetas, clientes, pagos, empleados, proveedores, reportes, alertas y chat).
Los microservicios se comunican de forma asíncrona con **RabbitMQ** (colas, exchanges, bindings, DLX/DLQ
y ACK manual) sobre un **cluster de 3 nodos**.

- **Backend:** Java 17, Spring Boot 3.3, Maven multi-módulo — `cafeteria-backend/cafeteria-backend`
- **Frontend:** React 18 + Vite, servido con nginx — `cafeteria-frontend/cafeteria-frontend`
- **Seguridad:** Azure Entra ID (MSAL en el frontend, validación de JWT en el BFF y en cada microservicio)
- **Datos:** MySQL 8.4, una base por microservicio
- **Infraestructura:** Docker Compose en local; Terraform + GitHub Actions para AWS (2 EC2 + API Gateway)

---

## 1. Arquitectura

```
Navegador ──> Frontend (nginx)
     │
     └──> API Gateway (AWS) ──> BFF Gateway :8080 ──> microservicios ──> MySQL (una base por servicio)
                                                          │
                                                          └──> RabbitMQ (cluster: rabbitmq-1, -2, -3)
```

| Servicio | Puerto | Qué hace |
|---|---|---|
| `bff-gateway` | 8080 | Única puerta de entrada (`/api/**`). Valida el JWT, aplica CORS y enruta |
| `ms-productos` | 8081 | Carta de productos (pública para lectura) |
| `ms-inventario` | 8082 | Insumos, recetas y movimientos de stock |
| `ms-pedidos` | 8083 | Pedidos, checkout público y seguimiento |
| `ms-clientes` | 8084 | Clientes y puntos de fidelización |
| `ms-pagos` | 8085 | Pasarela de pago simulada |
| `ms-empleados` | 8086 | Empleados |
| `ms-proveedores` | 8087 | Proveedores |
| `ms-reportes` | 8088 | Ventas diarias y KPIs del dashboard |
| `ms-notificaciones` | 8089 | Boletas, alertas y chat del equipo |
| `ms-rabbitmq-admin` | 8090 | Administración de RabbitMQ (colas, exchanges, bindings, DLQ) |
| `frontend` | 4200 | Tienda pública y portal del equipo |
| `mysql` | 3306 | Base de datos |
| `rabbitmq-1 / 2 / 3` | 15672 / 15673 / 15674 | Panel de administración de cada nodo (AMQP 5672 solo en el nodo 1) |

`cafeteria-common` es una librería compartida con los eventos de dominio y el manejo de ACK.

---

## 2. Levantarlo en local

Requisito: Docker Desktop corriendo.

```bash
cp .env.example .env        # y completa los datos de Azure (sección 6)
docker compose up -d --build
```

La primera vez tarda varios minutos. Cuando termine:

- Tienda y portal: http://localhost:4200
- API: http://localhost:8080/api/productos
- Panel de RabbitMQ: http://localhost:15672

En una base vacía se cargan solos la carta (25 productos con foto), 6 insumos y sus recetas.
El servicio `mysql-init` crea las 9 bases de datos y sus permisos en cada arranque.

```bash
docker compose ps                    # estado de cada contenedor
docker compose logs -f ms-pagos      # logs de un servicio
docker compose down                  # apagar (los datos se conservan)
```

Si reconstruyes un solo microservicio (`docker compose up -d --build ms-pedidos`), reinicia también el
BFF (`docker compose restart bff-gateway`): el gateway conserva la IP anterior del contenedor.

### Sin Azure (solo para desarrollo)

Con `SPRING_PROFILES_ACTIVE=noauth` y `VITE_AUTH_DISABLED=true` en el `.env`, el backend no valida JWT
y usa H2 embebida; el perfil se elige con el selector "Perfil" de la barra superior. No debe usarse en
la entrega.

---

## 3. Roles y permisos

Roles (App Roles de Azure, en mayúsculas): `ADMIN`, `GERENTE`, `BARISTA`, `CAJERO`, `BODEGUERO`.
El rol lo entrega el backend (`GET /api/me`, leído del JWT ya validado); el frontend nunca lo decide.

| Módulo | ADMIN | GERENTE | BARISTA | CAJERO | BODEGUERO |
|---|---|---|---|---|---|
| Resumen | KPIs + RabbitMQ | KPIs | cola de preparación | caja del día | insumos críticos |
| Menú (productos) | CRUD | ver | ver | ver | ver |
| Pedidos | cambiar estado, cancelar | ver | ver, cambiar estado | ver | — |
| Clientes | CRUD | ver | ver | ver, crear, editar | — |
| Pagos | ver, anular | ver | — | ver | — |
| Inventario y recetas | CRUD | ver | ver inventario | — | crear, editar, movimientos |
| Proveedores | CRUD | ver | — | — | crear, editar |
| Empleados | CRUD | ver | — | — | — |
| Reportes | sí | sí | — | sí | — |
| Alertas y chat del equipo | sí | sí | sí | sí | sí |
| Mensajería y panel de RabbitMQ | sí | — | — | — | — |

Eliminar es exclusivo de ADMIN en todos los módulos. Todas las vistas se refrescan solas cada 8 segundos,
así que lo que un rol crea o modifica lo ven los demás sin recargar.

### Estados de un pedido

El equipo y el cliente ven **Pendiente → En preparación → Listo → Entregado** (más Cancelado). Cada
cambio avanza un paso. La confirmación del pago ocurre antes, por mensajería, y se revisa en "Pagos":
los pedidos con pago sin confirmar o rechazado no aparecen en "Pedidos".

---

## 4. Mensajería con RabbitMQ

### Flujo de una compra

1. `ms-pedidos` crea el pedido y publica `pedido.creado`.
2. `ms-pagos` lo consume, simula la pasarela y publica `pago.aprobado` o `pago.rechazado`.
3. `pago.aprobado` lo consumen cinco servicios, cada uno desde su propia cola:
   `ms-pedidos` (marca el pedido), `ms-inventario` (descuenta stock según la receta),
   `ms-clientes` (suma puntos), `ms-reportes` (suma la venta) y `ms-notificaciones` (genera la boleta).
4. Si un insumo queda bajo su mínimo, `ms-inventario` publica `stock.bajo` y `ms-notificaciones`
   crea la alerta. Lo mismo cuando un pedido pasa a "Listo".

### Exchanges (5)

| Exchange | Tipo | Productor |
|---|---|---|
| `cafeteria.pedidos.exchange` | topic | ms-pedidos |
| `cafeteria.pagos.exchange` | direct | ms-pagos |
| `cafeteria.inventario.exchange` | direct | ms-inventario |
| `cafeteria.chat.exchange` | fanout | ms-notificaciones |
| `cafeteria.dlx` | direct | RabbitMQ (mensajes muertos) |

### Colas (9 principales, cada una con su DLQ = 18)

| Cola | Binding | Consumidor |
|---|---|---|
| `pagos.pedido-creado.queue` | pedidos → `pedido.creado` | ms-pagos |
| `pedidos.pago-resultado.queue` | pagos → `pago.aprobado`, `pago.rechazado` | ms-pedidos |
| `inventario.pago-aprobado.queue` | pagos → `pago.aprobado` | ms-inventario |
| `clientes.pago-aprobado.queue` | pagos → `pago.aprobado` | ms-clientes |
| `reportes.pago-aprobado.queue` | pagos → `pago.aprobado` | ms-reportes |
| `reportes.pedido-eventos.queue` | pedidos → `pedido.#` | ms-reportes |
| `notificaciones.ticket.queue` | pagos → `pago.aprobado` | ms-notificaciones |
| `notificaciones.alertas.queue` | inventario → `stock.bajo`; pedidos → `pedido.estado.actualizado` | ms-notificaciones |
| `notificaciones.chat.queue` | chat (fanout) | ms-notificaciones |

Todas son colas **quorum** y durables, con TTL de 10 minutos, largo máximo de 10.000 mensajes y límite
de 3 entregas. Cada DLQ (`<cola>.dlq`) está enlazada a `cafeteria.dlx` y conserva los mensajes 24 horas.

### Cómo está organizado el código (pauta de evaluación)

| Indicador | Dónde está |
|---|---|
| Nombres centralizados | Bloque `app.rabbitmq` de cada `application.yml`, mapeado por `messaging/config/RabbitProperties` |
| Beans de Queue, Exchange y Binding | `messaging/config/RabbitMQConfig` de cada microservicio, con la ruta documentada |
| Configuración separada del negocio | Los services usan interfaces `*EventPublisher`; nunca `RabbitTemplate` |
| Consumidores por dominio | `messaging/consumer/<dominio>/` con `@RabbitListener` |
| ACK manual y errores | `cafeteria-common/.../AckHandler`: `ack`, `nackSinReintento` (directo a DLQ) y `nackConReintento` |
| API REST del administrador | `ms-rabbitmq-admin/controller/RabbitAdminController` |
| Lógica encapsulada | `ms-rabbitmq-admin/service/RabbitAdminService` |
| Validación de entrada | DTO con Bean Validation (`CreateQueueRequest`, `CreateExchangeRequest`, `BindingRequest`) |

Política de errores en los consumidores:

- **Procesado bien o evento repetido** (tabla `eventos_procesados`, idempotencia): ACK.
- **Error de negocio o datos inválidos**: NACK sin reencolar → va directo a la DLQ.
- **Error transitorio** (base caída, otro servicio no responde): NACK con reencolado, hasta 3 entregas;
  después RabbitMQ lo envía a la DLQ.
- Cada DLQ tiene un `*DlqListener` que registra el mensaje y su cabecera `x-death` en el log.

### Microservicio administrador (`/api/rabbitmq/**`, solo ADMIN)

| Verbo | Ruta | Qué hace |
|---|---|---|
| GET | `/queues`, `/queues/{name}` | Lista o detalla colas |
| POST | `/queues` | Crea una cola |
| DELETE | `/queues/{name}` | Elimina una cola (409 si es del sistema) |
| POST | `/queues/{name}/purge` | Vacía una cola |
| GET / POST | `/exchanges` | Lista o crea exchanges |
| DELETE | `/exchanges/{name}` | Elimina un exchange (409 si es del sistema) |
| GET / POST / DELETE | `/bindings` | Lista, crea o elimina bindings |
| GET | `/dlq` | Mensajes pendientes en cada DLQ |
| POST | `/dlq/{name}/reprocess?max=10` | Reenvía mensajes de una DLQ a su exchange de origen |
| GET | `/cluster` | Estado de los nodos |

Los exchanges y colas del sistema están protegidos (`app.rabbitmq.protegidos`). Documentación
interactiva: http://localhost:8090/swagger-ui.html

### Panel de RabbitMQ y cluster

El usuario y la clave del broker se definen en `docker-compose.yml` (`RABBITMQ_DEFAULT_USER` /
`RABBITMQ_DEFAULT_PASS`, tomados de `RABBITMQ_USER` / `RABBITMQ_PASSWORD` del `.env`).

El rol ADMIN tiene el botón **"Dashboard RabbitMQ"** (barra lateral, Resumen y Mensajería) que abre el
panel propio de RabbitMQ (`VITE_RABBITMQ_DASHBOARD_URL`). La página "Mensajería" es el front de
`ms-rabbitmq-admin`.

Para demostrar primero un nodo y después el cluster:

```bash
./scripts/rabbitmq-cluster.sh un-nodo    # MySQL + rabbitmq-1 + toda la aplicación
./scripts/rabbitmq-cluster.sh agregar    # suma rabbitmq-2 y rabbitmq-3 y replica las colas
./scripts/rabbitmq-cluster.sh estado     # nodos activos y miembros de cada cola
```

`docker compose up -d --build` levanta los 3 nodos de una vez.

### Cómo generar actividad visible en el panel

| Acción en la aplicación | Qué se mueve |
|---|---|
| Comprar en la tienda con cualquier tarjeta | `pagos.pedido-creado.queue` y las colas de `pago-aprobado` y `ticket` |
| Pasar un pedido a "Listo" | `notificaciones.alertas.queue` y `reportes.pedido-eventos.queue` |
| Escribir en el chat del equipo | `notificaciones.chat.queue` |
| Tarjeta terminada en `9999` | 3 reintentos y el mensaje cae a `pagos.pedido-creado.queue.dlq` |
| Tarjeta terminada en `8888` | Directo a la DLQ, sin reintentos |
| Tarjeta terminada en `0000` | Pago rechazado (`pago.rechazado`) |

Los mensajes se consumen en milisegundos: el movimiento se ve en los gráficos "Message rates" de
Overview y de cada cola, con el refresco en 5 segundos.

---

## 5. Seguridad

- **Frontend:** MSAL hace el login con Azure Entra ID y adjunta el token como `Authorization: Bearer`
  en cada llamada (`src/services/apiClient.js`).
- **BFF y microservicios:** validan firma, emisor y audiencia del JWT, y autorizan por rol con
  `@PreAuthorize`.
- **Rutas públicas:** `GET /api/productos` y `/api/public/**` (checkout, seguimiento y boleta).
- **Llamadas entre servicios:** los consumidores de RabbitMQ no tienen un usuario logueado, así que
  consultan `GET /internal/pedidos/{id}` de `ms-pedidos` con el secreto compartido `X-Internal-Token`
  (`INTERNAL_API_TOKEN`). Esa ruta no está bajo `/api`, por lo que el BFF nunca la expone.
- **En AWS:** el BFF solo acepta peticiones que traigan el secreto `X-Origin-Verify` que inyecta el
  API Gateway.
- Nunca se guarda el número completo de la tarjeta, solo los últimos 4 dígitos.

---

## 6. Variables de entorno

Todas están documentadas en [`.env.example`](.env.example). Las principales:

| Variable | Para qué |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Vacío = Azure + MySQL. `noauth` = sin Azure, H2 |
| `AZURE_ISSUER_URI`, `AZURE_AUDIENCES`, `AZURE_JWK_SET_URI` | Validación del JWT en el backend |
| `VITE_AZURE_CLIENT_ID`, `VITE_AZURE_TENANT_ID`, `VITE_AZURE_AUTHORITY`, `VITE_API_SCOPES` | Login en el frontend |
| `VITE_API_BASE_URL` | URL del BFF |
| `VITE_RABBITMQ_DASHBOARD_URL` | A dónde lleva el botón "Dashboard RabbitMQ" |
| `FRONTEND_ORIGIN` | Origen permitido en CORS |
| `DB_PASSWORD` | Clave de MySQL |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `RABBITMQ_ERLANG_COOKIE` | Broker y cluster |
| `INTERNAL_API_TOKEN` | Secreto para llamadas entre microservicios |

El archivo `.env` nunca se sube al repositorio.

---

## 7. Despliegue en AWS

Terraform (`infra/terraform`) crea dos EC2 —backend y frontend—, sus Elastic IP, un volumen para MySQL
y un API Gateway delante del BFF. Se ejecuta con GitHub Actions; el detalle está en
[`infra/terraform/README.md`](infra/terraform/README.md).

```bash
R=<usuario>/<repositorio>

# Credenciales de la sesión de AWS Academy (caducan en pocas horas)
gh secret set AWS_ACCESS_KEY_ID -R $R
gh secret set AWS_SECRET_ACCESS_KEY -R $R
gh secret set AWS_SESSION_TOKEN -R $R
gh secret set MY_IP_CIDR -R $R --body "<tu-ip>/32"    # SSH y panel de RabbitMQ solo desde tu IP

gh workflow run terraform-destroy.yml -R $R --ref ep2 -f confirmar=destruir   # borra todo, incluida la base
gh workflow run terraform-deploy.yml  -R $R --ref ep2 -f repo_branch=ep2
```

Después del deploy el backend tarda unos 10–15 minutos en compilar. Las URL salen en el resumen del
workflow. Para actualizar el código sin recrear la instancia: `git pull` en `/opt/cafeteria` y
`sudo systemctl restart cafeteria.service`.

---

## 8. Pruebas

```bash
cd cafeteria-backend/cafeteria-backend && ./mvnw -q clean verify
cd cafeteria-frontend/cafeteria-frontend && npm run build
./scripts/smoke-test.sh http://localhost:8080
```

Compra de prueba por la API:

```bash
curl -X POST http://localhost:8080/api/public/checkout -H "Content-Type: application/json" \
  -d '{"cliente":{"nombre":"Ana","email":"ana@correo.cl"},"items":[{"productoId":1,"cantidad":1}],"pago":{"metodo":"DEBITO","numeroTarjeta":"4111111111111111"}}'
# Con el codigoSeguimiento que devuelve:
#   GET /api/public/pedidos/{codigo}   estado del pedido
#   GET /api/public/tickets/{codigo}   boleta
```

Colección de Postman: [`docs/postman`](docs/postman).

---

## 9. Estructura del repositorio

```
cafeteria-backend/cafeteria-backend/   microservicios, BFF y cafeteria-common
cafeteria-frontend/cafeteria-frontend/ aplicación React
infra/terraform/                       infraestructura de AWS
rabbitmq/rabbitmq.conf                 formación del cluster
scripts/                               smoke test y arranque del cluster por etapas
docs/                                  plan de la EP2 y colección de Postman
docker-compose.yml                     stack completo
```

---

## 10. Limitaciones conocidas

- La pauta pide el frontend en Angular; este proyecto usa React + Vite, decisión heredada de la EP1.
- La base `cafeteria` de MySQL de versiones anteriores queda sin uso; no se borra automáticamente.
- El pago es una simulación con fines académicos.

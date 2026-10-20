# CafeGestión360 — Plan EP2 (RabbitMQ) + correcciones EP1

> Documento guía para el agente (Claude Code / Codex) y para el equipo.
> Guárdalo en el repo como `docs/EP2_PLAN.md`. Los prompts de la **sección 10**
> le dicen al agente qué fase de este documento ejecutar.

---

## 0. Cómo usar este documento

1. Crea una rama: `git checkout -b ep2`.
2. Copia este archivo a `docs/EP2_PLAN.md`.
3. Copia las 3 imágenes de referencia del dashboard a `docs/referencias-dashboard/`
   con estos nombres: `ref1-coffee-crush.jpg`, `ref2-salon.jpg`, `ref3-nucaffe.jpg`.
4. Copia el bloque de la **sección 9 (Reglas para el agente)** a un archivo
   `AGENTS.md` en la raíz (Codex lo lee solo) y también a `CLAUDE.md` (Claude Code lo lee solo).
5. Ejecuta **un prompt por fase** (sección 10). Al terminar cada fase: revisa,
   prueba, y haz commit. No pegues todas las fases juntas: si algo se rompe no
   sabrás en qué paso fue.
6. Lo de Azure (sección 7) y AWS (sección 8) lo haces tú en los portales; el
   agente solo genera el código y los archivos Terraform.

---

## 1. Diagnóstico: lo que encontré en el código actual

| # | Problema | Dónde | Efecto |
|---|---|---|---|
| 1 | **El rol no se lee bien** (entras como BARISTA aunque seas ADMIN). Hay 4 causas posibles, en orden de probabilidad: **(a)** el `docker-compose.yml` hornea el frontend con `VITE_AUTH_DISABLED=true` por defecto. En ese modo el rol **no viene de Azure**, viene del selector "Ver como" guardado en `localStorage` (`cg360_demo_role`). Si alguna vez elegiste BARISTA, quedas como BARISTA para siempre. **(b)** El token es v1 o v2 y no calza con la config: con v1 el `iss` es `https://sts.windows.net/<tenant>/` (el backend espera `.../v2.0`) y con v2 el `aud` es el **GUID** del backend, no `api://cafeteria-backend`. Resultado: 401 o roles ignorados. **(c)** El rol se asignó en la Enterprise App del **frontend** y no en la del **backend**, o el token quedó cacheado de antes de asignar el rol. **(d)** El frontend calcula el rol por su cuenta; no existe un endpoint que diga qué rol leyó **el backend**. | `docker-compose.yml`, `useUserRole.js`, `application.yml` del BFF | Siempre entras con el rol equivocado |
| 2 | `@PreAuthorize("hasAuthority('ADMIN') or hasAuthority('ADMIN')")` copiado en 6 controllers (productos, inventario, clientes, empleados, proveedores, reportes) | controllers | BARISTA/CAJERO no pueden crear ni editar nada aunque deberían |
| 3 | El **checkout público** (cliente sin login) llama a `GET /clientes`, `POST /pedidos`, `POST /pagos`, y todos exigen JWT | `Checkout.jsx` | Con Azure activo la compra falla con 401. Además, abrir `GET /clientes` expondría datos personales |
| 4 | Los controllers hablan directo con el repositorio: no hay capa de servicio ni reglas de negocio. El total lo manda el navegador, los estados son texto libre y no se valida stock | todos los `ms-*` | Para la pauta EP2 hay que separar negocio de mensajería, y sin capa de servicio no hay dónde ponerla |
| 5 | Las entidades no tienen validaciones (`@NotBlank`, `@Positive`, `@Email`…) aunque los controllers usan `@Valid` | `model/*.java` | Se pueden guardar registros vacíos |
| 6 | `apiClient.js` imprime en consola el access token, su payload y los headers | `services/apiClient.js` | Mala práctica de seguridad, y el docente lo va a ver en la defensa |
| 7 | `OrderStatus` usa el id secuencial (`/pedido/5`) | `OrderStatus.jsx` | Cualquiera puede ver los pedidos de otros cambiando el número |
| 8 | No hay RabbitMQ | — | Es el 100% de la pauta EP2 |
| 9 | EC2: 9 JVM sin límite de memoria, sin `restart:` y sin Elastic IP | `docker-compose.yml` | Se cae por falta de memoria, no se levanta solo y cambia de IP |
| 10 | El enunciado EP2 dice "el frontend debe ser un componente **Angular**". El README dice que el docente autorizó React | — | Ten a mano el correo o mensaje de esa autorización para la defensa |

**Ajustes según la retroalimentación del EP1:** pega aquí los comentarios que te dio el docente,
para que el agente también los corrija en la Fase 1:

```
(pegar retroalimentación EP1 aquí)
```

---

## 2. Arquitectura objetivo EP2

```
                         Azure Entra ID (IDaaS: login + JWT con claim "roles")
                                   │
 Navegador ──HTTPS──► Frontend React (público: tienda / checkout / seguimiento;
                                      privado: /dashboard/* con MSAL + rol)
      │
      └──HTTPS──► AWS API Gateway (HTTP API) ──► EC2 :8080 bff-gateway (valida JWT, /api/me)
                                                     │  REST
     ┌───────────┬───────────┬──────────┬───────────┼───────────┬────────────┬────────────┬──────────────┐
 ms-productos ms-inventario ms-pedidos ms-clientes ms-pagos ms-empleados ms-proveedores ms-reportes
   8081         8082          8083       8084        8085       8086         8087          8088
                                                     │
                       ms-notificaciones 8089 (NUEVO: tickets/boletas + alertas)
                       ms-rabbitmq-admin 8090 (NUEVO: API REST para administrar colas/exchanges/bindings)
                                                     │ AMQP
                     RabbitMQ Cluster (rabbitmq-1, rabbitmq-2, rabbitmq-3)  — UI :15672
                                                     │
                                         PostgreSQL (un esquema por microservicio)
```

Todo corre en Docker Compose dentro de una EC2 creada con **Terraform**.

---

## 3. Diseño de mensajería (cantidad de colas, exchanges y bindings)

### 3.1 Flujo de negocio

```
Cliente paga en la tienda
  └─► ms-pedidos  crea Pedido (PENDIENTE_PAGO) ──publica──► pedido.creado
        └─► ms-pagos  procesa el pago (simulado) ──publica──► pago.aprobado | pago.rechazado
              ├─► ms-pedidos         estado PAGADO / PAGO_RECHAZADO
              ├─► ms-inventario      descuenta insumos según receta ──► stock.bajo (si queda bajo el mínimo)
              ├─► ms-clientes        crea/actualiza cliente y suma puntos
              ├─► ms-reportes        actualiza ventas del día, top productos, franjas horarias
              └─► ms-notificaciones  genera ticket/boleta
Barista cambia estado (EN_PREPARACION → LISTO → ENTREGADO) ──► pedido.estado.actualizado
  ├─► ms-notificaciones  alerta "pedido listo" (log simulando email)
  └─► ms-reportes        conteo de pedidos por estado
```

### 3.2 Exchanges (4)

| Exchange | Tipo | Productor | Routing keys |
|---|---|---|---|
| `cafeteria.pedidos.exchange` | topic | ms-pedidos | `pedido.creado`, `pedido.estado.actualizado` |
| `cafeteria.pagos.exchange` | direct | ms-pagos | `pago.aprobado`, `pago.rechazado` |
| `cafeteria.inventario.exchange` | direct | ms-inventario | `stock.bajo` |
| `cafeteria.dlx` | direct | RabbitMQ (automático al rechazar o expirar) | `<nombre-cola>.dlq` |

### 3.3 Colas principales (8) y sus DLQ (8) = 16 colas

| Cola | Exchange → binding key | Consumidor | Qué hace |
|---|---|---|---|
| `pagos.pedido-creado.queue` | pedidos → `pedido.creado` | ms-pagos | Procesa el pago simulado y publica el resultado |
| `pedidos.pago-resultado.queue` | pagos → `pago.aprobado` **y** `pago.rechazado` (2 bindings) | ms-pedidos | Cambia el estado del pedido |
| `inventario.pago-aprobado.queue` | pagos → `pago.aprobado` | ms-inventario | Descuenta insumos y publica `stock.bajo` |
| `clientes.pago-aprobado.queue` | pagos → `pago.aprobado` | ms-clientes | Upsert del cliente por email + puntos (1 punto cada $1.000) |
| `reportes.pago-aprobado.queue` | pagos → `pago.aprobado` | ms-reportes | Ventas diarias, top productos, franjas |
| `reportes.pedido-eventos.queue` | pedidos → `pedido.#` | ms-reportes | Conteo por estado / hora |
| `notificaciones.ticket.queue` | pagos → `pago.aprobado` | ms-notificaciones | Genera ticket con número correlativo |
| `notificaciones.alertas.queue` | inventario → `stock.bajo`; pedidos → `pedido.estado.actualizado` | ms-notificaciones | Guarda alertas para el dashboard |

Cada cola principal tiene su DLQ `<cola>.dlq` enlazada a `cafeteria.dlx` con routing key `<cola>.dlq`.

### 3.4 Políticas de retención (argumentos de cola)

Colas principales (tipo **quorum**, para que sobrevivan a la caída de un nodo del cluster):

| Argumento | Valor | Por qué |
|---|---|---|
| `x-queue-type` | `quorum` | Replicada en los 3 nodos |
| `x-dead-letter-exchange` | `cafeteria.dlx` | Todo lo rechazado o expirado va al DLX |
| `x-dead-letter-routing-key` | `<cola>.dlq` | Cada cola tiene su propia DLQ |
| `x-delivery-limit` | `3` | Después de 3 entregas fallidas RabbitMQ la manda solo a la DLQ |
| `x-message-ttl` | `600000` (10 min) | Si un consumidor está caído más de 10 min, el mensaje va a la DLQ y queda registrado |
| `x-max-length` | `10000` | Evita que una cola crezca sin límite |

DLQ: `x-queue-type=quorum`, `x-message-ttl=86400000` (24 h, como la guía 2.2.3), `x-max-length=10000`.

### 3.5 Política de ACK y errores (lo que más pesa: 20%)

- `spring.rabbitmq.listener.simple.acknowledge-mode: manual`, `prefetch: 5`,
  `default-requeue-rejected: false`.
- **NO** activar `listener.simple.retry` de Spring: no se combina bien con ACK manual.
  Los reintentos se controlan de forma explícita así:

| Situación | Excepción | Acción | Resultado |
|---|---|---|---|
| Procesado OK | — | `basicAck` | Sale de la cola |
| Mensaje ya procesado (mismo `eventId`) | — | `basicAck` sin reprocesar (idempotencia) | No se duplica |
| Error de negocio o datos inválidos (payload malo, pedido no existe, validación) | `NonRecoverableMessageException` | `basicNack(requeue=false)` + `log.error` | Va **directo** a la DLQ |
| Error transitorio (BD caída, timeout, servicio externo) | `RecoverableMessageException` / `DataAccessException` | `basicNack(requeue=true)` + `log.warn` con `x-delivery-count` | Se reintenta. Al pasar `x-delivery-limit=3`, RabbitMQ lo manda a la DLQ |

- **Listener de DLQ** en cada microservicio: `@RabbitListener` sobre sus DLQ. Registra en
  `log.error` el payload, la cola de origen y el motivo (header `x-death`: `reason`, `queue`, `count`)
  y luego hace `basicAck`.
- **Productor confiable:** `publisher-confirm-type: correlated`, `publisher-returns: true`,
  `template.mandatory: true`. Si un mensaje no se puede enrutar o RabbitMQ no confirma, se registra en el log.
- Todos los eventos llevan `eventId` (UUID), `ocurridoEn` y `version`. Se serializan como JSON
  (`Jackson2JsonMessageConverter`).
- **Escenarios de demo para la defensa** (en ms-pagos):
  - tarjeta terminada en `0000` → pago **rechazado** (regla de negocio, no es un error)
  - tarjeta terminada en `9999` → lanza `RecoverableMessageException`: 3 reintentos y luego **DLQ**
  - tarjeta terminada en `8888` → lanza `NonRecoverableMessageException`: **DLQ inmediato**

### 3.6 Configuración centralizada (pauta 12%)

Cada microservicio pasa de `application.properties` a **`application.yml`**, con un bloque
`app.rabbitmq` que solo trae lo que ese servicio usa. Ejemplo para ms-pedidos:

```yaml
spring:
  rabbitmq:
    addresses: ${RABBITMQ_ADDRESSES:localhost:5672}
    username: ${RABBITMQ_USER:cafeteria}
    password: ${RABBITMQ_PASSWORD:cafeteria}
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      mandatory: true
    listener:
      simple:
        acknowledge-mode: manual
        prefetch: 5
        default-requeue-rejected: false

app:
  rabbitmq:
    exchanges:
      pedidos: cafeteria.pedidos.exchange
      pagos: cafeteria.pagos.exchange
      dlx: cafeteria.dlx
    routing-keys:
      pedido-creado: pedido.creado
      pedido-estado-actualizado: pedido.estado.actualizado
      pago-aprobado: pago.aprobado
      pago-rechazado: pago.rechazado
    queues:
      pago-resultado: pedidos.pago-resultado.queue
    policies:
      delivery-limit: 3
      message-ttl-ms: 600000
      max-length: 10000
      dlq-ttl-ms: 86400000
```

- Clase `RabbitProperties` (`@ConfigurationProperties(prefix = "app.rabbitmq")`) que mapea ese bloque.
- Clase `RabbitMQConfig` (`@Configuration`) que declara los `@Bean` de `Queue`, `Exchange`, `Binding`
  (incluyendo DLQ y sus bindings), el `MessageConverter` y el `RabbitTemplate` con callbacks de confirmación.
- **Cero strings de nombres de colas en servicios o listeners.** Los listeners usan
  `@RabbitListener(queues = "${app.rabbitmq.queues.pago-resultado}")`.

### 3.7 Estructura de paquetes en cada microservicio (pautas 10% y 15%)

```
cl.duoc.cafeteria.<dominio>
 ├─ controller/             solo HTTP: recibe DTO y llama al service
 ├─ service/                reglas de negocio (no conoce RabbitMQ)
 ├─ dto/                    requests/responses con validaciones
 ├─ model/  repository/
 ├─ exception/              GlobalExceptionHandler (@RestControllerAdvice)
 └─ messaging/
     ├─ config/             RabbitProperties, RabbitMQConfig
     ├─ producer/           <Dominio>EventPublisher (interfaz) + Rabbit<Dominio>EventPublisher
     └─ consumer/
         ├─ <subdominio>/   p.ej. consumer/pago/PagoResultadoListener.java
         └─ dlq/            <Dominio>DlqListener.java
```

Los services dependen de la **interfaz** `...EventPublisher`, nunca de `RabbitTemplate`.
Los listeners solo traducen mensaje → llamada al service + ACK/NACK.

Módulo Maven nuevo **`cafeteria-common`** (librería, no app) con:
- los records de eventos (`PedidoCreadoEvent`, `PagoProcesadoEvent`, `StockBajoEvent`,
  `PedidoEstadoActualizadoEvent`, `ItemEvent`)
- `NonRecoverableMessageException`, `RecoverableMessageException`
- un helper `AckHandler` para no repetir el try/catch de ACK en cada listener.

### 3.8 Microservicio administrador `ms-rabbitmq-admin` (pautas 13% + 10% + 7%)

- Puerto 8090. Ruta en el BFF: `/api/rabbitmq/**`. Solo rol **ADMIN**.
- `RabbitAdminService` encapsula **toda** la lógica (`RabbitAdmin` de Spring AMQP para crear y eliminar;
  la API HTTP de management `http://rabbitmq-1:15672/api/...` para listar y ver el cluster).
  El controller **no** importa nada de `org.springframework.amqp`.

| Verbo | Ruta | Body / params | Respuesta |
|---|---|---|---|
| GET | `/api/rabbitmq/queues` | — | 200 lista (nombre, tipo, mensajes listos/no confirmados, consumidores) |
| GET | `/api/rabbitmq/queues/{name}` | — | 200 / 404 |
| POST | `/api/rabbitmq/queues` | `CreateQueueRequest` | 201 / 400 / 409 si ya existe |
| DELETE | `/api/rabbitmq/queues/{name}` | `?ifUnused=&ifEmpty=` | 204 / 404 / 409 si está protegida |
| POST | `/api/rabbitmq/queues/{name}/purge` | — | 200 con la cantidad purgada |
| GET | `/api/rabbitmq/exchanges` | — | 200 |
| POST | `/api/rabbitmq/exchanges` | `CreateExchangeRequest` | 201 / 400 / 409 |
| DELETE | `/api/rabbitmq/exchanges/{name}` | — | 204 / 404 / 409 |
| GET | `/api/rabbitmq/bindings` | — | 200 |
| POST | `/api/rabbitmq/bindings` | `BindingRequest` | 201 / 400 / 404 si no existe la cola o el exchange |
| DELETE | `/api/rabbitmq/bindings` | `BindingRequest` | 204 |
| GET | `/api/rabbitmq/dlq` | — | 200 resumen de todas las `*.dlq` con su conteo (para alertas del dashboard) |
| POST | `/api/rabbitmq/dlq/{name}/reprocess` | `?max=10` | 200 mueve mensajes de la DLQ a su exchange de origen |
| GET | `/api/rabbitmq/cluster` | — | 200 nodos y su estado |

Validaciones (DTO + `@Valid` + `GlobalExceptionHandler` con respuesta `{timestamp, status, error, message, fieldErrors[]}`):
- `name`: `@NotBlank @Size(min=3,max=120) @Pattern("^[a-z0-9]+([._-][a-z0-9]+)*$")`
- `type` (exchange): `@Pattern("direct|topic|fanout|headers")`
- `ttlMs`: `@Min(1000) @Max(604800000)`; `maxLength`: `@Min(1) @Max(1000000)`; `deliveryLimit`: `@Min(1) @Max(20)`
- `routingKey`: `@Size(max=255)` y patrón que permita `* # .`
- Prohibido crear con prefijo `amq.` (reservado) → 400
- Prohibido eliminar o purgar recursos de `app.rabbitmq.protected` (las 16 colas y 4 exchanges del sistema) → 409 con mensaje claro
- Documentación con **springdoc-openapi** (`/swagger-ui.html`) + javadoc en cada endpoint.

---

## 4. Roles y permisos (más perfiles)

Roles (App Roles en Azure, valor **exactamente en MAYÚSCULAS**): `ADMIN`, `GERENTE`, `BARISTA`, `CAJERO`, `BODEGUERO`.

| Módulo / acción | ADMIN | GERENTE | BARISTA | CAJERO | BODEGUERO |
|---|---|---|---|---|---|
| Dashboard | completo + RabbitMQ | completo (solo lectura) | cola de preparación | caja del día | stock crítico |
| Menú (productos) | CRUD | ver | ver | ver | ver |
| Pedidos | CRUD + cancelar | ver | ver, crear, cambiar estado | ver, crear | — |
| Clientes | CRUD | ver | ver | ver, crear, editar | — |
| Pagos | CRUD + anular | ver | — | ver, registrar | — |
| Inventario (insumos, recetas, movimientos) | CRUD | ver | ver | — | crear, editar, movimientos |
| Proveedores | CRUD | ver | — | — | crear, editar |
| Empleados | CRUD | ver | — | — | — |
| Reportes | sí | sí | — | solo el día | — |
| RabbitMQ admin | sí | — | — | — | — |

Reglas: **eliminar = solo ADMIN** en todos los módulos. Un usuario puede tener varios roles;
el frontend muestra la unión de permisos y permite elegir el "perfil activo" si tiene más de uno.

**Fuente de verdad del rol = el backend.** El BFF expone `GET /api/me` → `{ nombre, email, roles[] }`
leído del JWT ya validado. El frontend **solo** usa eso para pintar el menú y los permisos. El selector
"Ver como" solo existe si `VITE_AUTH_DISABLED=true` y debe mostrar un banner "MODO DEMO".

---

## 5. Reglas de negocio por microservicio (que todo el CRUD funcione)

Para **todos**: capa `service`, DTOs con validación, `GlobalExceptionHandler` (400 validación,
404 no existe, 409 conflicto/duplicado/transición inválida), paginación y filtro opcional en los GET
de lista (`?q=&page=&size=`), datos semilla (perfil `seed`, activo por defecto en Docker) para que
la demo no parta vacía, y tests.

| Servicio | Reglas |
|---|---|
| **ms-productos** | nombre único; `precio > 0`; `categoria` ∈ {Bebidas calientes, Bebidas frías, Pastelería, Galletas}; campo `disponible` (boolean) e `imagenUrl`; GET público con filtro por categoría y disponibles; semilla con los 12 productos que ya tienen foto en `public/productos/`. |
| **ms-inventario** | `Insumo` (nombre único, `unidadMedida` ∈ {g, ml, unidad}, `stockActual ≥ 0`, `stockMinimo ≥ 0`); **nuevo** `RecetaItem(productoId, insumoId, cantidad>0)` con CRUD; **nuevo** `MovimientoStock(tipo ENTRADA/SALIDA/AJUSTE, cantidad, motivo, fecha, usuario)` con `POST /api/inventario/{id}/movimientos`; `GET /api/inventario/alertas` (stock ≤ mínimo). El consumidor de `pago.aprobado` descuenta según receta (crea movimientos SALIDA). Si el stock no alcanza, lo deja en 0, registra un AJUSTE y publica `stock.bajo`. |
| **ms-pedidos** | `Pedido` con `codigoSeguimiento` (UUID), snapshot del cliente (nombre, email), `canal` (WEB/MOSTRADOR), items con `nombreProducto` y `precioUnitario` **tomados de ms-productos** (llamada REST interna; nunca se confía en el precio del navegador). Estados (enum) con máquina de estados: `PENDIENTE_PAGO → PAGADO → EN_PREPARACION → LISTO → ENTREGADO`; `PENDIENTE_PAGO → PAGO_RECHAZADO`; `PAGADO/EN_PREPARACION → CANCELADO` (solo ADMIN). Una transición inválida responde 409. Endpoints: `POST /api/public/checkout` (público), `GET /api/public/pedidos/{codigo}` (público, sin datos sensibles), `POST /api/pedidos` (venta en mostrador, staff), `PATCH /api/pedidos/{id}/estado`, CRUD staff. |
| **ms-pagos** | `Pago(pedidoId, monto, metodo ∈ {EFECTIVO, DEBITO, CREDITO, TRANSFERENCIA}, estado ∈ {APROBADO, RECHAZADO, ANULADO}, ultimos4, fecha)`. **Nunca** se guarda ni se publica en un mensaje el número completo de tarjeta ni el CVV: ms-pedidos solo pone `metodo` y `ultimos4` en el evento `pedido.creado`, y la simulación decide con esos 4 dígitos. El consumidor de `pedido.creado` simula la pasarela (sección 3.5). `POST /api/pagos` (cajero, efectivo en mostrador) también publica `pago.aprobado`. `PATCH /api/pagos/{id}/anular` solo ADMIN. |
| **ms-clientes** | email único y válido; teléfono `^\+?56 ?9\d{8}$`; `puntosFidelizacion ≥ 0`; upsert por email desde el evento; `POST /api/clientes/{id}/canje` (descuenta puntos, 409 si no alcanza). La lista de clientes ya **no** es pública. |
| **ms-empleados** | email único; `rol` ∈ los 5 roles; `activo`; `fechaIngreso`. (Azure decide quién entra; este módulo es la ficha de RR.HH.). Desactivar en vez de borrar, salvo ADMIN. |
| **ms-proveedores** | RUT chileno válido (dígito verificador módulo 11, validador propio `@Rut`); email; teléfono; `insumosQueProvee` (texto o lista). |
| **ms-reportes** | Modelo de lectura construido **solo desde eventos**: `VentaDiaria(fecha única, totalVentas, cantidadPedidos)`, `VentaProducto(fecha, productoId, nombre, cantidad, monto)`, `PedidosPorHora(fecha, hora, cantidad)`. `GET /api/reportes/dashboard?desde=&hasta=` devuelve: ventas hoy, ventas semana actual vs anterior (%), pedidos hoy, ticket promedio, clientes nuevos, ventas por día (7 días actual vs anterior), pedidos por franja (Mañana 7–12, Tarde 12–18, Noche 18–22), top 5 productos y pedidos por estado. |
| **ms-notificaciones** (nuevo, 8089) | `Ticket(numero correlativo, pedidoId, codigoSeguimiento, cliente, items, total, metodo, fecha)` + `GET /api/notificaciones/tickets`, `GET /api/public/tickets/{codigoSeguimiento}` (la boleta que ve el cliente). `Alerta(tipo STOCK_BAJO/PEDIDO_LISTO/DLQ, mensaje, leida, fecha)` + `GET /api/notificaciones/alertas`, `PATCH /{id}/leida`. El "email" al cliente se simula con un `log.info` estructurado. |

BFF: rutas nuevas `/api/public/**` (permitAll), `/api/notificaciones/**`, `/api/rabbitmq/**`,
`GET /api/me`. Se mantiene `GET /api/productos/**` público.

---

## 6. Frontend: público vs privado y nuevo dashboard

### 6.1 Rutas

| Ruta | Acceso |
|---|---|
| `/` tienda, `/checkout`, `/seguimiento/:codigo` (estado + boleta) | **Pública**, sin login |
| `/login` | Pública (botón "Iniciar sesión con Microsoft") |
| `/dashboard` y `/dashboard/*` (pedidos, menú, inventario, recetas, clientes, pagos, empleados, proveedores, reportes, alertas, mensajería) | **Requiere login** (MSAL) + rol según la sección 4 |

- `ProtectedRoute`: si no hay sesión → `/login?redirect=...`. Mientras MSAL resuelve el redirect (`inProgress !== "none"`) muestra un loader, no redirige.
- El checkout llama a `POST /api/public/checkout` con `{cliente, items[{productoId,cantidad}], pago{metodo, numeroTarjeta}}`. Al terminar navega a `/seguimiento/{codigo}`, que hace polling cada 3 s hasta que el estado deje de ser `PENDIENTE_PAGO` (así se ve la asincronía en la demo).
- Quitar **todos** los `console.log` de tokens y headers en `apiClient.js`.
- `apiClient`: manejar 401 (re-login), 403 ("No tienes permiso para esta acción") y 400 (mostrar `fieldErrors` bajo cada campo).

### 6.2 Diseño del dashboard (referencias en `docs/referencias-dashboard/`)

Estilo: cafetería cálida y limpia, mezcla de las 3 referencias. **No** copiar logos ni textos de las referencias; solo el estilo.

- **Paleta (tokens CSS en `:root`)**: fondo `#FAF6F1`, superficie `#FFFFFF`, sidebar `#F5EBDD`,
  ítem activo `#EAD7C3`, espresso `#5C3420` (primario y texto fuerte), caramelo `#A8744A`,
  latte `#E6CFB5`, crema `#F3E6D6`, texto secundario `#8A7565`, éxito `#2F8F5B`, error `#C0392B`.
  Bordes `#EFE4D8`, radio 14px, sombras muy suaves.
- **Tipografía**: `Poppins` para la UI; `Playfair Display` para el saludo y los títulos grandes (como la ref 2).
- **Layout**: sidebar fija de 240px con logo, grupos "Menú" y "Otros" (ref 1), ítems con icono y la opción
  activa en forma de píldora. Card "Mensajería" abajo solo para ADMIN. Topbar con buscador, selector de
  rango de fechas, chip con el rol activo, campana con contador de alertas (alertas + mensajes en DLQ) y
  avatar con nombre. Bajo 900px la sidebar se vuelve un drawer.
- **Encabezado**: "Bienvenido de vuelta, {nombre}" en serif + subtítulo "Así va la cafetería hoy".
- **Fila 1, KPIs** (5 cards como la ref 2): Ventas hoy, Pedidos hoy, Ticket promedio, Clientes nuevos,
  Mensajes en DLQ. Cada una con icono, valor grande y delta `↑ 12,4% vs semana anterior` (verde o rojo).
- **Fila 2**: gráfico de barras finas "Ventas últimos 7 días" (esta semana espresso vs semana anterior latte,
  refs 1 y 3), 2/3 del ancho, con botón "Ver reporte". Al lado, donut "Pedidos por franja"
  (Mañana/Tarde/Noche) con tooltip tipo tarjeta (ref 1).
- **Fila 3**: "Productos más pedidos" con miniatura, nombre y monto (ref 1 / "Today's Orders" de la ref 3);
  línea "Pedidos por hora hoy" (ref 1); panel "Alertas" (stock bajo + DLQ + pedidos listos).
- **Fila 4, solo ADMIN**: "Estado de mensajería": tabla de colas con mensajes listos/no confirmados/consumidores,
  DLQ en rojo si > 0, botón "Reprocesar". Además, el estado de los nodos del cluster.
- **Por rol**: BARISTA ve un kanban "Cola de preparación" (PAGADO → EN_PREPARACION → LISTO → ENTREGADO) con
  botones para avanzar. CAJERO ve "Caja del día" (total por método de pago + últimos pagos). BODEGUERO ve
  "Insumos críticos" + botón "Registrar entrada".
- **Páginas CRUD** dentro del mismo layout: tabla con búsqueda, filtros y paginación, modal de
  crear/editar con validación, confirmación al eliminar, toasts de éxito/error, estados de carga
  (skeleton), vacío y error. Los botones se ocultan según el rol.
- Gráficos con **recharts**. Auto-refresco del dashboard cada 30 s.
- Página **Mensajería** (ADMIN): formularios para crear/eliminar colas, exchanges y bindings usando
  `ms-rabbitmq-admin` y mostrando sus errores de validación.

---

## 7. Azure Entra ID: paso a paso para arreglar roles y validación

> Hazlo en el **mismo tenant** donde están tus 2 App Registrations.

1. **Confirma tu issuer real.** Abre en el navegador
   `https://login.microsoftonline.com/<TENANT_ID>/v2.0/.well-known/openid-configuration`
   y copia el valor de `"issuer"`. Ese es tu `AZURE_ISSUER_URI` exacto.
   - Si tu tenant es **External ID** (dominio `*.ciamlogin.com`), usa
     `https://<subdominio>.ciamlogin.com/<TENANT_ID>/v2.0/.well-known/openid-configuration`.
     En ese caso la `authority` del frontend es `https://<subdominio>.ciamlogin.com/`.
2. **Fuerza tokens v2 en el backend.** App registrations → `cafeteria-backend` → **Manifest**:
   - manifiesto nuevo: `"api": { "requestedAccessTokenVersion": 2 }`
   - manifiesto antiguo: `"accessTokenAcceptedVersion": 2`
   - Guarda.
3. **Audiencias.** Con tokens v2, el claim `aud` es el **Application (client) ID (GUID)** del backend.
   Configura `AZURE_AUDIENCES=<GUID_BACKEND>,api://cafeteria-backend` (el agente hará que el backend acepte una lista).
4. **App roles** (en `cafeteria-backend` → App roles → Create app role), uno por rol:
   - Display name: `Administrador` · Allowed member types: **Users/Groups** · Value: `ADMIN` · Description · Enabled
   - Repite con `GERENTE`, `BARISTA`, `CAJERO`, `BODEGUERO`. El **Value** va en MAYÚSCULAS, sin espacios.
   - Si ya existían, revisa que el Value sea exactamente ese (no `Admin` ni `admin`).
5. **Usuarios de prueba**: Microsoft Entra ID → Users → New user → Create new user:
   `admin@<tu-dominio>`, `gerente@…`, `barista@…`, `cajero@…`, `bodega@…`. Anota las contraseñas temporales.
6. **Asignar roles en la Enterprise App del BACKEND** (no en la del frontend):
   Enterprise applications → `cafeteria-backend` → Users and groups → Add user/group → usuario → rol → Assign.
   Haz una asignación por usuario. Asignar **grupos** requiere Entra ID P1; asigna usuarios directamente.
   Tu propio usuario debe tener **solo** ADMIN (o ADMIN + otros, si quieres probar el selector de perfil).
7. *(Recomendado)* Enterprise applications → `cafeteria-backend` → Properties →
   **Assignment required? = Yes**. Así, un usuario sin rol no obtiene token para la API.
8. **Frontend SPA** (`cafeteria-frontend` → Authentication → Single-page application):
   Redirect URIs: `http://localhost:4200`, `https://<IP_O_DOMINIO_EC2>` y `https://<dominio CloudFront>` si lo usas.
   Azure exige HTTPS para todo lo que no sea localhost.
9. **API permissions** del frontend: `cafeteria-backend` → `pedidos.read`, `pedidos.write` → **Grant admin consent**.
10. **Limpia la caché del token**: cierra sesión en la app, borra los datos del sitio
    (DevTools → Application → Clear site data) y vuelve a entrar. Los roles nuevos solo
    aparecen en tokens emitidos **después** de asignarlos.
11. **Verifica** el token en `https://jwt.ms` (pega el access token): `ver = 2.0`,
    `iss` = el issuer del paso 1, `aud` = el GUID del backend, `roles = ["ADMIN"]`, `scp = "pedidos.read pedidos.write"`.
12. Verifica que el backend lee lo mismo: `GET /api/me` con ese token debe devolver `roles: ["ADMIN"]`.
13. **Y lo más importante para "entro como barista":** en tu `.env` de la raíz pon
    `VITE_AUTH_DISABLED=false` y `SPRING_PROFILES_ACTIVE=` (vacío), y reconstruye el frontend
    (`docker compose build --no-cache frontend`). Si el frontend sigue mostrando el selector
    "Ver como", sigues en modo demo y el rol no viene de Azure.

### 7-bis. Configuración REAL ya hecha en Azure (tenant External ID "CafeGestion360")

Estado al 30-09-2026 (ya configurado en el portal, no repetir):
- Tenant **External ID** (dominio `CafeGestion360.onmicrosoft.com`, login por `ciamlogin.com`).
- `cafeteria-backend` (client id `f129b256-965a-4bec-9334-db02387882fb`), URI `api://f129b256-965a-4bec-9334-db02387882fb`,
  scopes `pedidos.read` / `pedidos.write`, `requestedAccessTokenVersion = 2`,
  App roles `ADMIN`, `GERENTE`, `BARISTA`, `CAJERO`, `BODEGUERO`, **Asignación requerida = Sí**.
- `cafeteria-frontend` (client id `56962fbf-b90a-47f9-967b-59f32f1df2d9`), solo plataforma **SPA** `http://localhost:4200`,
  sin concesión implícita, permisos `pedidos.read`/`pedidos.write` con consentimiento de administrador.
- Flujo de usuario `signin-cafeteria` (correo + contraseña, atributo Display Name) asociado a `cafeteria-frontend`.
- Usuarios externos de prueba con su rol asignado en la Enterprise App del backend:
  Admin / Barista / Cajero / Gerente / Bodeguero Prueba (`karlabelen026+<rol>@gmail.com`).

`.env` (raíz del repo, NUNCA se sube a git):
```
VITE_AUTH_DISABLED=false
VITE_AZURE_CLIENT_ID=56962fbf-b90a-47f9-967b-59f32f1df2d9
VITE_AZURE_TENANT_ID=e0b4510a-02d8-4f0e-905e-68bf95cf1757
VITE_AZURE_AUTHORITY=https://cafegestion360.ciamlogin.com/
VITE_AZURE_REDIRECT_URI=http://localhost:4200
VITE_API_SCOPES=api://f129b256-965a-4bec-9334-db02387882fb/pedidos.read,api://f129b256-965a-4bec-9334-db02387882fb/pedidos.write
SPRING_PROFILES_ACTIVE=
AZURE_ISSUER_URI=https://e0b4510a-02d8-4f0e-905e-68bf95cf1757.ciamlogin.com/e0b4510a-02d8-4f0e-905e-68bf95cf1757/v2.0
AZURE_JWK_SET_URI=https://cafegestion360.ciamlogin.com/e0b4510a-02d8-4f0e-905e-68bf95cf1757/discovery/v2.0/keys
AZURE_AUDIENCES=f129b256-965a-4bec-9334-db02387882fb,api://f129b256-965a-4bec-9334-db02387882fb
FRONTEND_ORIGIN=http://localhost:4200
```

Cambios de código obligatorios por ser External ID (ya aplicados — ver Fase 1 §0):
- Frontend `authConfig.js`: `authority = VITE_AZURE_AUTHORITY` y `knownAuthorities: ["cafegestion360.ciamlogin.com"]`
  (antes armaba `login.microsoftonline.com/<tenant>`, que NO sirve para este tenant).
- Backend (8 ms + BFF): `jwt.issuer-uri = AZURE_ISSUER_URI`, `jwt.jwk-set-uri = AZURE_JWK_SET_URI`,
  `jwt.audiences = AZURE_AUDIENCES` (lista). El default anterior `https://login.microsoftonline.com/<TENANT_ID>/v2.0`
  NO coincide con el `iss` real del token y provocaba 401: esa era la causa de que "el backend no leía el rol".
  Implementado como un `@Bean JwtDecoder`/`ReactiveJwtDecoder` propio en cada `SecurityConfig.java` (no vía
  las propiedades `spring.security.oauth2.resourceserver.jwt.*` directamente), porque dejar `jwk-set-uri`
  configurado-pero-vacío cuando `AZURE_JWK_SET_URI` no está seteada hace que Spring Boot registre dos beans
  `JwtDecoder` ambiguos y falle el arranque.
- `docker-compose.yml` y `.env.example`: ya documentan estas variables nuevas (con placeholders, nunca los
  valores reales).

---

## 8. AWS + Terraform: paso a paso

### 8.1 Por qué se cae hoy
- 9 JVM (11 con las nuevas) + Postgres + 3 nodos RabbitMQ en una t2/t3.micro (1 GB) → el sistema mata procesos por falta de memoria.
- Sin `restart:` en compose ni servicio systemd → si la instancia se reinicia, nada se levanta solo.
- Sin Elastic IP → la IP cambia y se rompen CORS, el redirect de Azure y el API Gateway.
- En **AWS Academy Learner Lab** las instancias se detienen cuando termina la sesión del lab.
  Con systemd + `restart: unless-stopped` + Elastic IP, al iniciar el lab de nuevo todo vuelve
  a levantarse solo y con la misma IP.

### 8.2 Qué genera el agente (carpeta `infra/terraform/`)

| Archivo | Contenido |
|---|---|
| `versions.tf` | provider `aws ~> 5.0`, terraform `>= 1.5` |
| `variables.tf` | `region` (us-east-1), `instance_type` (t3.large), `key_name` (vockey), `iam_instance_profile` (LabInstanceProfile), `my_ip_cidr`, `repo_url`, `repo_branch`, `azure_tenant_id`, `azure_backend_client_id`, `azure_frontend_client_id`, `db_password` (sensitive), `rabbitmq_password` (sensitive), `rabbitmq_erlang_cookie` (sensitive), `enable_recovery_alarms` (bool) |
| `main.tf` | AMI Ubuntu 22.04 (data source Canonical), Security Group (22 y 15672 solo desde `my_ip_cidr`; 80/443 abiertos; 8080 abierto para el API Gateway), EC2 con disco gp3 de 30 GB, `user_data`, **Elastic IP** asociada |
| `monitoring.tf` | Alarmas CloudWatch: `StatusCheckFailed_System` → acción **recover**; `StatusCheckFailed_Instance` → acción **reboot** (con `count = var.enable_recovery_alarms ? 1 : 0` por si el Learner Lab no las permite) |
| `apigateway.tf` | HTTP API, integración `HTTP_PROXY` a `http://<EIP>:8080/api/{proxy}`, ruta `ANY /api/{proxy+}`, stage `$default` con auto-deploy y throttling (burst 100, rate 50). Inyecta el header `X-Origin-Verify` con un secreto (parameter mapping) |
| `user_data.sh.tftpl` | instala Docker + compose plugin, crea 4 GB de swap, clona el repo, genera el `.env` desde variables, genera un certificado autofirmado para nginx, crea `cafeteria.service` (systemd, `docker compose up -d` al arrancar) y lo habilita |
| `outputs.tf` | IP elástica, URL del frontend, URL del API Gateway, URL de RabbitMQ UI, comando SSH |
| `terraform.tfvars.example` | ejemplo sin secretos reales |
| `README.md` | cómo usarlo |

Cambios en `docker-compose.yml` (Fase 2):
- `restart: unless-stopped` en todos los servicios.
- `JAVA_TOOL_OPTIONS: "-Xms64m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC"` y `mem_limit: 512m` en cada JVM.
- Healthchecks reales; los microservicios esperan `rabbitmq-1: service_healthy`.
- Frontend en producción servido con **nginx** (no `vite preview`): HTTPS en 443 y redirección desde 80.
- El BFF rechaza peticiones sin `X-Origin-Verify` cuando `ORIGIN_VERIFY_SECRET` está definido
  (así nadie se salta el API Gateway entrando directo a `:8080`). Las peticiones locales siguen funcionando si la variable está vacía.

### 8.3 Comandos (los ejecutas tú)

```bash
# 0) En AWS Academy: Start Lab → AWS Details → copia las credenciales a ~/.aws/credentials
#    (incluye aws_session_token; caducan con cada sesión del lab)
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars    # completa tus valores (NUNCA subir este archivo)
terraform init
terraform plan
terraform apply                                   # escribe "yes"
terraform output                                  # IP, URLs

# Ver el arranque (tarda 10-15 min la primera vez, compila todo)
ssh -i labsuser.pem ubuntu@<EIP> 'sudo tail -f /var/log/cloud-init-output.log'
ssh -i labsuser.pem ubuntu@<EIP> 'cd ~/cafeteria && docker compose ps'

# Para actualizar después de un push
ssh -i labsuser.pem ubuntu@<EIP> 'cd ~/cafeteria && git pull && docker compose up -d --build'

# Al terminar el semestre
terraform destroy
```

Después del `apply`:
1. Agrega `https://<EIP>` a los Redirect URIs del SPA en Azure (sección 7, paso 8).
2. `FRONTEND_ORIGIN` y `VITE_API_BASE_URL` (apuntando a la URL del API Gateway) ya los genera el `user_data`.
3. Abre `https://<EIP>` y acepta la advertencia del certificado autofirmado.
4. RabbitMQ UI: `http://<EIP>:15672` (solo desde tu IP).

`.gitignore` de Terraform: `.terraform/`, `*.tfstate`, `*.tfstate.*`, `terraform.tfvars`, `*.pem`.

---

## 9. Reglas para el agente (copiar a `AGENTS.md` y `CLAUDE.md`)

```
Proyecto: CafeGestión360 (DSY1107). Backend: Java 17, Spring Boot 3.3.x, Maven multi-módulo en
cafeteria-backend/cafeteria-backend. Frontend: React 18 + Vite en cafeteria-frontend/cafeteria-frontend.
Especificación completa: docs/EP2_PLAN.md. Ejecuta SOLO la fase que se te pida.

Reglas:
1. No rompas lo que ya funciona: seguridad JWT con Azure Entra ID, perfil noauth, Docker Compose.
2. Al terminar cada fase deben pasar: `./mvnw -q clean verify` (backend) y `npm run build` (frontend).
   Si algo falla, arréglalo antes de terminar.
3. Nombres de colas, exchanges y routing keys SOLO en application.yml (bloque app.rabbitmq) +
   RabbitProperties + RabbitMQConfig. Nunca strings sueltos en services, controllers ni listeners.
4. Controllers sin lógica de negocio ni de RabbitMQ. Services sin RabbitTemplate (usan interfaces *EventPublisher).
5. Listeners con @RabbitListener, ACK manual explícito según docs/EP2_PLAN.md §3.5, agrupados por dominio
   en messaging/consumer/<subdominio>.
6. Nunca subas secretos: solo .env.example y terraform.tfvars.example con placeholders.
7. Nunca guardes número de tarjeta completo ni CVV. Nunca hagas console.log de tokens.
8. Comentarios y mensajes de error en español. Código limpio, sin código muerto.
9. Al final de cada fase, entrega un resumen: archivos cambiados, cómo probarlo (comandos curl) y
   qué queda pendiente. Actualiza README.md con lo nuevo.
```

---

## 10. Prompts para el agente (uno por fase, en orden)

> Pega cada prompt en Claude Code / Codex parado en la raíz del repo. Adjunta las 3 imágenes en la Fase 5.

### Fase 1: Correcciones EP1, roles y reglas de negocio base
```
Lee docs/EP2_PLAN.md completo y ejecuta la FASE 1:
0) [YA APLICADO] sección 7-bis (tenant External ID: authority ciamlogin + knownAuthorities en el
   frontend; issuer-uri, jwk-set-uri y lista de audiencias en los 8 ms + BFF vía un JwtDecoder/
   ReactiveJwtDecoder propio; variables nuevas en docker-compose.yml y .env.example con placeholders).
1) Seguridad y roles (sección 4 y 7):
   - En los 8 microservicios y el BFF, que el JWT acepte una LISTA de audiencias desde AZURE_AUDIENCES
     (coma-separadas; default "api://cafeteria-backend") y el issuer desde AZURE_ISSUER_URI.
   - Converter de roles común: lee el claim "roles", lo pasa a mayúsculas, sin prefijo.
   - BFF: agrega el converter y el endpoint GET /api/me -> {nombre, email, roles[]} desde el JWT (WebFlux).
   - BFF: permitAll para GET /api/productos/**, /api/public/**, /actuator/health y OPTIONS; resto authenticated.
   - Reemplaza todos los @PreAuthorize por la matriz de la sección 4 con roles ADMIN, GERENTE,
     BARISTA, CAJERO, BODEGUERO (usa constantes, hasAnyAuthority). Elimina el bug
     "hasAuthority('ADMIN') or hasAuthority('ADMIN')".
2) Capa de servicio, DTOs con Bean Validation, GlobalExceptionHandler (400/404/409 con
   {timestamp,status,error,message,fieldErrors}) y las reglas de negocio de la sección 5 para
   productos, clientes, empleados, proveedores (validador @Rut) e inventario (insumos, recetas y movimientos).
   Agrega los campos nuevos de las entidades. Pasa application.properties a application.yml en cada ms.
3) Datos semilla con perfil "seed" (productos = los 12 con foto en public/productos/, insumos, recetas, empleados).
4) Frontend: quita todos los console.log de tokens y headers en apiClient.js. useUserRole debe leer
   GET /api/me (no decodificar el token). El selector "Ver como" solo existe con VITE_AUTH_DISABLED=true
   y con un banner "MODO DEMO". Si el usuario tiene varios roles, permite elegir el perfil activo.
5) docker-compose.yml: cambia el default a VITE_AUTH_DISABLED=${VITE_AUTH_DISABLED:-false}
   y documenta en .env.example las variables AZURE_AUDIENCES, AZURE_ISSUER_URI,
   AZURE_JWK_SET_URI y VITE_AZURE_AUTHORITY.
6) Tests: un @WebMvcTest por controller (200/400/403/404) y tests unitarios de los services.
Al final: ./mvnw clean verify y npm run build en verde, y el resumen.
```

### Fase 2: Infraestructura local (RabbitMQ Cluster + estabilidad)
```
Lee docs/EP2_PLAN.md y ejecuta la FASE 2 (secciones 3.6, 3.7 y 8.2 en lo que respecta a docker-compose):
1) docker-compose.yml raíz: cluster RabbitMQ de 3 nodos (rabbitmq:3.13-management) con hostnames
   rabbitmq-1/2/3, RABBITMQ_ERLANG_COOKIE y credenciales desde .env, rabbitmq/rabbitmq.conf con
   peer discovery classic_config (como la guía 2.3.1), volúmenes por nodo, healthcheck
   "rabbitmq-diagnostics -q ping", puertos 5672 y 15672 solo en el nodo 1 (15673/15674 para los otros).
2) restart: unless-stopped en todo; JAVA_TOOL_OPTIONS y mem_limit de la sección 8.2; microservicios con
   depends_on rabbitmq-1 healthy; variable RABBITMQ_ADDRESSES=rabbitmq-1:5672,rabbitmq-2:5672,rabbitmq-3:5672.
3) Crea el módulo Maven cafeteria-common (sección 3.7: eventos, excepciones, AckHandler) y agrégalo como
   dependencia de los microservicios. Actualiza el Dockerfile para copiar su pom y su src.
4) Agrega spring-boot-starter-amqp a los microservicios y el bloque spring.rabbitmq de la sección 3.6.
   En noauth/local que siga funcionando con un solo nodo (RABBITMQ_ADDRESSES=localhost:5672).
5) Frontend: Dockerfile de producción con nginx (HTTPS 443 con cert montado + redirección desde 80;
   fallback a http si no hay cert), con try_files para el SPA.
6) Documenta en README: cómo levantar, cómo ver el cluster (docker exec rabbitmq-1 rabbitmqctl cluster_status)
   y cómo entrar a la UI.
Verifica con docker compose config que el YAML es válido.
```

### Fase 3: Mensajería (productores, consumidores, exchanges, bindings, DLX/DLQ, ACK) + flujo de negocio
```
Lee docs/EP2_PLAN.md y ejecuta la FASE 3, siguiendo EXACTAMENTE las secciones 3.1 a 3.7 y 5:
1) En cada microservicio: RabbitProperties + RabbitMQConfig con beans Queue (quorum, con los argumentos
   de 3.4), Exchange, Binding, DLQ y binding DLQ->cafeteria.dlx, Jackson2JsonMessageConverter y
   RabbitTemplate con ConfirmCallback y ReturnsCallback que registran en el log.
2) Productores (interfaz + implementación Rabbit) en ms-pedidos, ms-pagos y ms-inventario.
3) Consumidores con @RabbitListener y ACK manual según la tabla 3.5 (ack, nack sin requeue para
   NonRecoverable, nack con requeue para Recoverable con límite por x-delivery-limit), idempotencia por
   eventId (tabla eventos_procesados) y DlqListener por servicio que registra x-death con log.error.
   Agrupa los consumidores en messaging/consumer/<subdominio>.
4) Crea el microservicio ms-notificaciones (8089) con tickets y alertas (sección 5) y agrégalo a compose y al BFF.
5) ms-pedidos: POST /api/public/checkout, GET /api/public/pedidos/{codigo}, máquina de estados, precios
   desde ms-productos (RestClient). ms-pagos: simulación de pasarela con tarjetas 0000/9999/8888.
   ms-reportes: modelo de lectura desde eventos y GET /api/reportes/dashboard.
6) Comenta en cada RabbitMQConfig un diagrama ASCII de las rutas de mensajería de ese servicio
   (pauta: "cada ruta de mensajería está claramente identificada y documentada en código").
7) Tests: listeners con Channel mockeado (verifica basicAck / basicNack(false,false) / basicNack(false,true)),
   y un test de integración con Testcontainers RabbitMQ para el flujo checkout -> PAGADO
   (marcado @Tag("integration") para que no bloquee el build si no hay Docker).
8) Agrega a docs/EP2_PLAN.md (anexo) una tabla final con las colas, exchanges y bindings implementados.
```

### Fase 4: Microservicio administrador de RabbitMQ
```
Lee docs/EP2_PLAN.md y ejecuta la FASE 4 (sección 3.8): crea ms-rabbitmq-admin (8090) con
RabbitAdminConfig, RabbitAdminService (encapsula RabbitAdmin + API HTTP de management con RestClient),
RabbitAdminController con TODOS los endpoints de la tabla, DTOs validados, recursos protegidos desde
app.rabbitmq.protected, GlobalExceptionHandler, springdoc-openapi y solo rol ADMIN. Agrégalo a compose y
al BFF (/api/rabbitmq/**). Tests MockMvc: nombre vacío -> 400, nombre inválido -> 400, prefijo amq. -> 400,
cola duplicada -> 409, borrar cola protegida -> 409, crear y borrar OK -> 201/204, sin rol ADMIN -> 403.
```

### Fase 5: Frontend (público/privado + dashboard nuevo + CRUD completos)
```
Lee docs/EP2_PLAN.md y ejecuta la FASE 5 (sección 6). Te adjunto 3 imágenes de referencia
(también están en docs/referencias-dashboard/). Toma el estilo, no los logos ni los textos.
1) Rutas públicas y privadas de 6.1 con layout /dashboard (sidebar + topbar) y ProtectedRoute que
   espera a que MSAL termine (inProgress) antes de redirigir.
2) Dashboard de 6.2 con tokens CSS en :root, Poppins + Playfair Display, recharts, KPIs con delta,
   barras de 7 días, donut por franja, top productos, pedidos por hora, alertas, panel de mensajería
   (ADMIN) y vistas por rol (kanban BARISTA, caja CAJERO, stock BODEGUERO). Responsive y con
   estados de carga, vacío y error.
3) Todos los CRUD (menú, pedidos, clientes, pagos, inventario + recetas + movimientos, empleados,
   proveedores) funcionando contra el backend: tabla con búsqueda y paginación, modal con errores por campo
   (fieldErrors), confirmación al eliminar, toasts y botones según permiso.
4) Checkout público contra /api/public/checkout y página /seguimiento/:codigo con polling y boleta.
5) Página Mensajería (ADMIN) que usa ms-rabbitmq-admin.
6) npm run build sin errores ni warnings. Prueba que con VITE_AUTH_DISABLED=true todo navega.
```

### Fase 6: Terraform + despliegue AWS
```
Lee docs/EP2_PLAN.md y ejecuta la FASE 6 (sección 8.2): crea infra/terraform con todos los archivos de
la tabla, compatible con AWS Academy Learner Lab (key vockey, LabInstanceProfile, sin crear roles IAM).
El user_data debe dejar el sistema arriba solo y el servicio systemd debe levantarlo en cada arranque.
Agrega el filtro X-Origin-Verify en el BFF (activo solo si ORIGIN_VERIFY_SECRET no está vacío).
Agrega .gitignore de Terraform. Ejecuta terraform fmt y terraform validate (con init -backend=false).
Documenta en infra/terraform/README.md.
```

### Fase 7: Pruebas, evidencias y documentación
```
Lee docs/EP2_PLAN.md y ejecuta la FASE 7 (sección 11):
1) Colección Postman docs/postman/CafeGestion360_EP2.postman_collection.json + environment de ejemplo
   con TODAS las pruebas de la sección 11 (con tests de status code).
2) Script scripts/smoke-test.sh que con curl recorre: productos públicos, checkout, seguimiento hasta PAGADO,
   tarjeta 9999 hasta DLQ, y los endpoints del admin de RabbitMQ.
3) README.md final: arquitectura, diagrama de mensajería, tabla de colas/exchanges/bindings, matriz de roles,
   cómo correr local/noauth/Azure/AWS y cómo probar. Revisa que .gitignore excluya target/, node_modules/,
   .env, *.tfstate, terraform.tfvars, data/.
4) Corre ./mvnw clean verify y npm run build y reporta el resultado.
```

---

## 11. Pruebas funcionales (y qué screenshot sacar para AVA)

### Seguridad (Azure / AWS)
| # | Prueba | Esperado |
|---|---|---|
| S1 | `GET /api/productos` sin token | 200 |
| S2 | `GET /api/pedidos` sin token | 401 |
| S3 | Token BARISTA → `DELETE /api/productos/1` | 403 |
| S4 | Token ADMIN → `DELETE /api/productos/1` | 204 |
| S5 | Token alterado (cambiar 1 letra de la firma) | 401 |
| S6 | `GET /api/me` con cada usuario de prueba | roles correctos |
| S7 | Cada rol entra al dashboard y ve solo sus módulos | screenshot por rol |
| S8 | `http://<EIP>:8080/api/pedidos` directo (sin pasar por el API Gateway) | 403 por X-Origin-Verify |
| S9 | Abrir `/dashboard` sin sesión | redirige a `/login` |
| S10 | Tienda + checkout sin sesión | funciona |

### Mensajería
| # | Prueba | Esperado / evidencia |
|---|---|---|
| M1 | UI RabbitMQ → Exchanges y Queues | 4 exchanges, 16 colas, bindings |
| M2 | Checkout con tarjeta OK | Pedido PAGADO, stock descontado, puntos sumados, ticket, reporte actualizado (screenshot de cada uno) |
| M3 | Tarjeta `0000` | PAGO_RECHAZADO |
| M4 | Tarjeta `9999` | 3 entregas (log WARN) → mensaje en `pagos.pedido-creado.queue.dlq` + log ERROR del DlqListener |
| M5 | Tarjeta `8888` | DLQ inmediato |
| M6 | `docker compose stop ms-inventario`, comprar, `start` | Mensajes acumulados que luego se consumen (durabilidad) |
| M7 | `docker compose stop rabbitmq-2` | El sistema sigue funcionando; `rabbitmqctl cluster_status` |
| M8 | Admin API: crear cola con nombre `""` / `"Mi Cola!"` / `"amq.x"` | 400 con mensaje claro |
| M9 | Admin API: crear → binding → borrar cola/exchange | 201 / 201 / 204 |
| M10 | Admin API: borrar `pagos.pedido-creado.queue` | 409 protegida |
| M11 | Reprocesar DLQ desde el dashboard | El mensaje sale de la DLQ |
| M12 | Logs `docker compose logs ms-pagos` | Líneas de ACK/NACK/DLQ |

---

## 12. Presentación (defensa técnica), ~10 láminas

1. Problema y solución (CafeGestión360, de papel a cloud native).
2. Arquitectura completa (diagrama de la sección 2).
3. Seguridad: Azure Entra ID, JWT, roles, `/api/me`, API Gateway + X-Origin-Verify.
4. Diseño de mensajería: tabla de exchanges/colas/bindings + flujo 3.1.
5. Configuración centralizada (`application.yml` + `RabbitMQConfig`): mostrar código.
6. Consumidores y ACK: tabla 3.5 + código de un listener.
7. DLX/DLQ y políticas de retención: TTL, delivery-limit, max-length.
8. RabbitMQ Cluster + quorum queues + demo de caída de un nodo.
9. Microservicio administrador: endpoints, validaciones, Swagger.
10. Infraestructura: Terraform, EC2, Elastic IP, systemd, alarmas.
11. **Demo en vivo** (M2 → M4 → M8 → S3), con un video grabado de respaldo por si falla la red.
12. Lecciones aprendidas y próximos pasos.

---

## 13. Cronograma sugerido (2 semanas)

| Días | Tarea (tu planificación) | Fase |
|---|---|---|
| 1–2 | Ajustes según EP1 + roles Azure | Fase 1 + sección 7 |
| 3 | Levantar RabbitMQ (cluster) local y en EC2 | Fase 2 |
| 3 | Definir colas y exchanges (ya está en la sección 3; revisarlo con tu pareja) | — |
| 4–6 | Productores, consumidores, exchanges, bindings, colas, DLX, DLQ y ACK | Fase 3 |
| 7 | RabbitMQ Cluster y Rabbit Admin | Fase 4 |
| 8–9 | Frontend dashboard + CRUD | Fase 5 |
| 10 | Terraform + despliegue AWS | Fase 6 + sección 8.3 |
| 11–12 | Pruebas funcionales (seguridad + colas) y evidencias | Fase 7 + sección 11 |
| 13 | Preparar presentación | sección 12 |
| 14 | Entrega: links GitHub + evidencias en AVA (los mismos archivos en encargo y presentación) | — |

---

## Anexo Fase 3: colas, exchanges y bindings implementados

Estado real tras implementar la Fase 3 (mensajería): coincide exactamente con el diseño de la
sección 3 (4 exchanges, 8 colas principales + 8 DLQ = 16 colas).

### Exchanges (4)

| Exchange | Tipo | Productor |
|---|---|---|
| `cafeteria.pedidos.exchange` | topic | ms-pedidos |
| `cafeteria.pagos.exchange` | direct | ms-pagos |
| `cafeteria.inventario.exchange` | direct | ms-inventario |
| `cafeteria.dlx` | direct | RabbitMQ (automático) |

### Colas principales (8) + su DLQ cada una

| Cola | Binding(s) | Consumidor | Clase |
|---|---|---|---|
| `pagos.pedido-creado.queue` | pedidos → `pedido.creado` | ms-pagos | `PedidoCreadoListener` |
| `pedidos.pago-resultado.queue` | pagos → `pago.aprobado` y `pago.rechazado` | ms-pedidos | `PagoResultadoListener` |
| `inventario.pago-aprobado.queue` | pagos → `pago.aprobado` | ms-inventario | `PagoAprobadoListener` |
| `clientes.pago-aprobado.queue` | pagos → `pago.aprobado` | ms-clientes | `PagoAprobadoListener` |
| `reportes.pago-aprobado.queue` | pagos → `pago.aprobado` | ms-reportes | `PagoAprobadoListener` |
| `reportes.pedido-eventos.queue` | pedidos → `pedido.#` | ms-reportes | `PedidoEventosListener` (despacho por tipo: `PedidoCreadoEvent` / `PedidoEstadoActualizadoEvent`) |
| `notificaciones.ticket.queue` | pagos → `pago.aprobado` | ms-notificaciones | `TicketListener` |
| `notificaciones.alertas.queue` | inventario → `stock.bajo`; pedidos → `pedido.estado.actualizado` | ms-notificaciones | `AlertaListener` (despacho por tipo: `StockBajoEvent` / `PedidoEstadoActualizadoEvent`) |

Cada cola principal tiene su `*.dlq` enlazada a `cafeteria.dlx` con routing key `<cola>.dlq`, observada
por un `*DlqListener` que solo registra en `log.error` (payload + header `x-death`) y confirma (ack).

### Decisiones de diseño no explícitas en la sección 3 original

- **`PedidoCreadoEvent` ganó el campo `clienteEmail`** (antes solo lo tenía la entidad `Pedido`, no el
  evento): lo necesita `ms-reportes` para calcular "clientes nuevos" sin tener que llamar a `ms-clientes`
  por REST desde un listener. Es nullable (ventas de mostrador sin cliente identificado quedan sin email).
- **"Top productos" en `ms-reportes` se calcula desde `pedido.creado`** (vía `reportes.pedido-eventos.queue`),
  no desde `pago.aprobado`: `PagoProcesadoEvent` no trae el detalle de items y ampliarlo habría afectado a
  4 microservicios ya construidos en la Fase 2. Efecto práctico: cuenta productos *pedidos* por día, no solo
  los que terminaron pagados (las ventas en dinero sí usan exclusivamente `pago.aprobado`).
- **`ms-notificaciones` resuelve el detalle del ticket (items, código de seguimiento, cliente) llamando a
  `GET /api/pedidos/{id}` de ms-pedidos** (mismo patrón ya usado por `ms-clientes/client/PedidoClient`),
  porque `PagoProcesadoEvent` solo trae `pedidoId`, `monto` y `metodoPago`. Mismo 401/403 conocido y
  documentado: sin autenticación servicio-a-servicio todavía, se trata como error transitorio y se reintenta.

## Anexo Fase 4: `ms-rabbitmq-admin`

Microservicio nuevo (puerto 8090, sin base de datos propia — es un proxy hacia RabbitMQ) con todos los
endpoints de la tabla de la sección 3.8. `RabbitAdminService` encapsula dos vías de acceso a RabbitMQ:

- **`RabbitAdmin` de Spring AMQP** (protocolo AMQP): crear/eliminar colas y exchanges, declarar/quitar
  bindings, purgar una cola.
- **`ManagementClient`** (API HTTP de administración, puerto 15672, con `RestClient` + autenticación
  básica): listar colas/exchanges/bindings, ver el estado del cluster (`GET /api/nodes`), y leer mensajes
  de una cola (`POST /api/queues/{vhost}/{name}/get`, usado para reprocesar la DLQ).

El controller no importa nada de `org.springframework.amqp` ni del cliente HTTP: todo pasa por el service.

### Decisiones de diseño no explícitas en el plan original

- **`app.rabbitmq.protected` se implementó como `app.rabbitmq.protegidos`**: `protected` es palabra
  reservada de Java y no puede ser el nombre de una propiedad (`@ConfigurationProperties`) ni de un getter.
  La lista contiene los 4 exchanges y las 16 colas del sistema (igual que `app.rabbitmq.protegidos` en
  `application.yml` de `ms-rabbitmq-admin`), y bloquea con 409 tanto el `DELETE` como el `POST .../purge`.
- **Reprocesar una DLQ usa el header `x-death`** que RabbitMQ agrega automáticamente a todo mensaje
  muerto: se toma el primer registro (`x-death[0].exchange` y `x-death[0]["routing-keys"][0]`) como el
  exchange/routing-key original, y se reenvía el payload crudo con `RabbitTemplate`. Si un mensaje no
  trae `x-death` (no debería pasar en las 16 colas del sistema), se deja fuera del conteo y se registra
  un `log.warn`.
- **El prefijo `amq.` prohibido y el patrón de nombre válido se validan con una sola expresión regular**
  (`^(?!amq\.)[a-z0-9]+([._-][a-z0-9]+)*$`) en el DTO, en vez de dos validaciones separadas: ambas
  reglas del plan terminan en el mismo 400, así que no hace falta distinguirlas en código.
- **Toda la API exige rol ADMIN** (`anyRequest().hasAuthority("ADMIN")` en `SecurityConfig`), no solo
  autenticación: a diferencia del resto de los microservicios, aquí no hay ninguna acción de solo lectura
  permitida a otros roles (ver sección 3.8: "Solo rol ADMIN"). Swagger UI y el health check quedan abiertos.

---

## Anexo Fase 8: Kafka + Kafka Streams (Unidad 3)

Adaptación de la guía 3.2.2 ("Monitoreo de transacciones bancarias en tiempo real") al dominio de la
cafetería: **monitoreo antifraude de pagos**. RabbitMQ queda igual (comandos y tareas del negocio); Kafka
se agrega en paralelo como plataforma de streaming (eventos retenidos, varios consumidores, replay).

### Infraestructura (`docker-compose.yml`)

| Servicio | Imagen | Notas |
|---|---|---|
| `zookeeper` | confluentinc/cp-zookeeper:7.5.0 | `mem_limit 384m`, heap 192 MB |
| `kafka` | confluentinc/cp-kafka:7.5.0 | Listeners `PLAINTEXT://kafka:29092` y `PLAINTEXT_HOST://localhost:9092`; `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`; retención por defecto 168 h; `mem_limit 1g`, heap 512 MB; healthcheck con `kafka-broker-api-versions` |
| `kafka-ui` | provectuslabs/kafka-ui | Publicada en el **8095** (el 8080 es del BFF); `mem_limit 512m` |
| `ms-antifraude` | módulo nuevo | Puerto 8091, sin base de datos |

Como Kafka Streams usa `exactly_once_v2` (transacciones) y hay un solo broker, también se fijan
`KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1` y `KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1`.

### Pipeline

```
ms-pagos (PasarelaPagoService)
  └─ TransaccionStreamPublisher ──> cafeteria.pagos.transacciones   key = clienteEmail ("mostrador" si no hay)
                                         │
     ms-antifraude · AntifraudeTopology (application-id cafeteria-antifraude, exactly_once_v2)
       Source → filter(monto ≥ 10) → mapValues(+ montoUsd) → split()
         ├─ monto > 50.000 CLP → AlertaFraudeEvent ALTA  "Transacción inusualmente alta vía {metodoPago}"
         ├─ aprobado = false   → AlertaFraudeEvent MEDIA "Pago rechazado vía {metodoPago}"
         ├─ groupByKey().aggregate() → KTable total-por-cliente ──> cafeteria.antifraude.totales-por-cliente
         └─ groupByKey().windowedBy(tumbling 5 min).count() > 5 → AlertaFraudeEvent MEDIA "Ráfaga de pagos"
       Sink ──> alertas-fraude   key = cliente
                  ├─ ms-notificaciones · AlertaFraudeListener (group notificaciones-antifraude)
                  │     → alerta FRAUDE del dashboard; errores → alertas-fraude.DLT
                  └─ ms-antifraude · AlertasFraudeListener (group antifraude-monitor)
                        → últimas alertas en memoria (GET /api/antifraude/alertas)
```

### Topics

| Topic | Particiones | RF | Key | Retención | Productor | Consumidores |
|---|---|---|---|---|---|---|
| `cafeteria.pagos.transacciones` | 3 | 1 | email del cliente | 7 días | ms-pagos, simulador de ms-antifraude | Kafka Streams (`cafeteria-antifraude`) |
| `cafeteria.antifraude.totales-por-cliente` | 3 | 1 | email del cliente | **compactado** | Kafka Streams | (consulta/replay) |
| `alertas-fraude` | 3 | 1 | email del cliente | 30 días | Kafka Streams | `notificaciones-antifraude`, `antifraude-monitor` |
| `alertas-fraude.DLT` | 3 | 1 | la del original | 14 días | DeadLetterPublishingRecoverer | grupo `notificaciones-dlt-reinyeccion` (solo al reinyectar) |
| `cafeteria-antifraude-*-changelog` | 3 | 1 | — | (interno) | Kafka Streams | Kafka Streams (restaura los state stores) |

Cada aplicación crea sus topics con beans `NewTopic` (`TopicBuilder`): ms-pagos y ms-antifraude el de
transacciones, ms-antifraude los de totales y alertas, ms-notificaciones su DLT. RF 1 porque hay un solo
broker; en producción serían 3 brokers con RF 3 y `min.insync.replicas=2`.

### Configuración centralizada

- Topics, consumer groups, particiones, réplicas y retención: bloque `app.kafka` de cada `application.yml`,
  mapeado por `KafkaTopicsProperties` (no `KafkaProperties`, para no chocar con la de Spring Boot).
- Reglas: bloque `app.antifraude` (`monto-minimo`, `tasa-clp-usd`, `umbral-alto`,
  `max-transacciones-ventana`, `ventana-minutos`, nombres de los state stores), mapeado por
  `AntifraudeProperties`.
- `spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`.
- Los services nunca usan `KafkaTemplate`: `TransaccionStreamPublisher` (ms-pagos),
  `TransaccionPublisher` (ms-antifraude) y `ReinyeccionDltService` (ms-notificaciones).

### Productor (ms-pagos)

`acks=all`, `enable.idempotence=true`, `linger.ms=20`, `batch.size=32768`, `compression.type=snappy`,
`JsonSerializer` sin headers de tipo. `max.block.ms=3000` y el `CompletableFuture` de `send` solo registra
el error: si Kafka no está disponible el pago se completa igual.

### Manejo de errores del consumidor (ms-notificaciones, guía 3.3.3)

- `ErrorHandlingDeserializer` envolviendo `JsonDeserializer`: un JSON inválido va a la DLT, sin bucle.
- `@Valid` + Bean Validation sobre `AlertaFraudeEvent` (validador registrado con `KafkaListenerConfigurer`).
- `DefaultErrorHandler` + `ExponentialBackOffWithMaxRetries(3)`: reintentos a 1 s, 2 s y 4 s; después
  `DeadLetterPublishingRecoverer` → `alertas-fraude.DLT`, misma partición.
- No se reintentan: `NonRecoverableMessageException`, `MethodArgumentNotValidException` (payload inválido)
  y `ConstraintViolationException` (la entidad no pasa la validación al guardarse).
- Idempotencia por `eventId` en `eventos_procesados`.
- `POST /api/notificaciones/antifraude/dlt/reinyectar?max=100` (ADMIN): reinyecta solo lo que había en la
  DLT al momento de la llamada; si un mensaje vuelve a fallar, queda para la próxima.

### Decisiones de diseño

- **State stores en memoria** (`Stores.inMemoryKeyValueStore` / `inMemoryWindowStore`), respaldados por
  su topic changelog: evitan depender de RocksDB nativo y de disco en el contenedor.
- **Sin caché en Kafka Streams** (`statestore.cache.max.bytes=0`): la regla de ráfaga necesita ver cada
  conteo, y emite una sola alerta por ventana (cuando el conteo pasa a máximo + 1).
- **Imagen Alpine con `gcompat` y `libstdc++`**: la librería nativa de snappy está compilada contra glibc.
- **La prueba de carga usa montos normales** (1.500–20.000 CLP, todas aprobadas): mide rendimiento sin
  inundar el dashboard de alertas.
- **ms-antifraude excluye `RabbitAutoConfiguration`**: no usa RabbitMQ, pero `cafeteria-common` trae la
  dependencia y su health check lo dejaba en 503.

### Pruebas (sin broker)

- `AntifraudeTopologyTest` (TopologyTestDriver): filtro, sin alerta, ALTA, MEDIA por rechazo, ráfaga con 6
  pagos y no con 5, total acumulado y conversión a USD.
- `SimuladorServiceTest`, `KafkaTransaccionStreamPublisherTest` (mock de `KafkaTemplate`),
  `AlertaFraudeListenerTest` (idempotencia) y `KafkaErrorHandlerTest` (DLT y reintentos).
- Los `contextLoads` usan `spring.kafka.streams.auto-startup=false`, `spring.kafka.listener.auto-startup=false`
  y `spring.kafka.admin.auto-create=false`: no se conectan a un broker.

### AWS

`user_data_backend.sh.tftpl` levanta también `zookeeper kafka kafka-ui ms-antifraude`. El Security Group
abre el **8095** (Kafka UI) solo para `my_ip_cidr`; el 9092 no se abre. Output nuevo: `kafka_ui_url`. El
frontend recibe `VITE_KAFKA_UI_URL`.

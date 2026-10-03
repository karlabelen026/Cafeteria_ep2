# CafeGestión360 — DSY1107 Desarrollo Cloud Native I — EP1

Sistema de gestión para una cafetería (menú, inventario, pedidos, clientes, pagos,
empleados, proveedores y reportes) que hoy funciona en papel. Arquitectura:
React + MSAL → Azure Entra ID (IDaaS) → AWS API Gateway → BFF (Spring Cloud
Gateway) → 8 microservicios Spring Boot → MySQL.

> **Nota:** el enunciado y la pauta del EP1 piden explícitamente Angular. El
> cambio a React se hizo porque el estudiante confirmó por escrito con el
> docente que estaba autorizado — si tú lees esto y no tienes esa autorización,
> no lo copies tal cual, vuelve a la versión Angular.

---

## Qué tiene el proyecto (estado actual)

### Stack tecnológico y cómo se integra

- **Backend:** Java 17 + Spring Boot 3.3.4, organizado como **proyecto Maven
  multi-módulo** (un `pom.xml` padre en `cafeteria-backend/cafeteria-backend`
  con Maven Wrapper `mvnw`, y 9 módulos hijos: 8 microservicios +
  `bff-gateway`, cada uno con su propio `pom.xml`).
- **`bff-gateway`:** **Spring Cloud Gateway 2023.0.3** (reactivo, sobre
  WebFlux/Netty, no Spring MVC). Enruta por `Path` predicate a cada
  microservicio (definido en `spring.cloud.gateway.routes` dentro de su
  `application.yml`) y valida el JWT con `spring-boot-starter-oauth2-resource-server`
  + `spring-boot-starter-security` (`ServerHttpSecurity` /
  `@EnableWebFluxSecurity`, ver `config/SecurityConfig.java`).
- **Los 8 microservicios:** Spring MVC clásico (`spring-boot-starter-web`,
  servlet, no reactivo) + **Spring Data JPA/Hibernate**
  (`spring-boot-starter-data-jpa`) contra MySQL (driver
  `com.mysql:mysql-connector-j`), cada uno con su propio
  `@RestController` / `@Entity` / `@Repository` en su propio paquete. Cada
  uno valida el mismo JWT **de forma independiente** del BFF
  (`spring-boot-starter-oauth2-resource-server` + `HttpSecurity` /
  `@EnableMethodSecurity`, para poder usar `@PreAuthorize` en los
  controllers) — defensa en profundidad, no confían ciegamente en que ya
  pasó por el gateway.
- **Autenticación/IDaaS:** Azure Entra ID (Microsoft identity platform) — 2
  App Registrations (backend API + frontend SPA) en el mismo Tenant, App
  Roles (`ADMIN` / `BARISTA` / `CAJERO`) y scopes (`pedidos.read` /
  `pedidos.write`) expuestos en el backend y consentidos en el frontend (ver
  sección 4).
- **Frontend:** **React 18.3 + Vite 5.4**, `@azure/msal-browser` 3.20 +
  `@azure/msal-react` 2.1 (login, manejo de tokens y su cache) y
  `react-router-dom` 6.26 (rutas y el guard `ProtectedRoute`).
- **Base de datos:** MySQL 8 (`mysql:8.4` en Docker), una base por
  microservicio dentro del mismo servidor (`CREATE SCHEMA` = `CREATE DATABASE`
  en MySQL, ver `init-db/01-schemas.sql`). En modo `noauth` cada microservicio
  usa su propia base **H2** embebida en su lugar (sin MySQL).
- **Contenedores:** los 9 módulos backend comparten un único `Dockerfile`
  multi-stage (build con Maven + JDK 17, runtime con JRE 17 Alpine),
  parametrizado por `build.args.MODULE` para compilar solo el módulo que
  corresponde a cada imagen; el frontend tiene su propio `Dockerfile` (build
  con Node 20, se sirve con `vite preview`). Todo orquestado por un único
  `docker-compose.yml` en la raíz del repo.
- **Despliegue de referencia:** instancia EC2 (Ubuntu) con Docker + Nginx
  como reverse proxy con certificado SSL autofirmado (ver sección 9).

### Módulos de negocio y puertos

| Microservicio | Puerto | Qué gestiona | Endpoint base |
|---|---|---|---|
| `bff-gateway` | 8080 | Único punto de entrada del frontend; enruta y valida JWT antes de reenviar a cualquiera de los 10 de abajo | `/api/**` |
| `ms-productos` | 8081 | Menú (catálogo de productos) — el único módulo con lectura pública (`GET`) | `/api/productos` |
| `ms-inventario` | 8082 | Insumos y stock | `/api/inventario` |
| `ms-pedidos` | 8083 | Pedidos y sus ítems | `/api/pedidos` |
| `ms-clientes` | 8084 | Clientes registrados | `/api/clientes` |
| `ms-pagos` | 8085 | Pagos | `/api/pagos` |
| `ms-empleados` | 8086 | Empleados | `/api/empleados` |
| `ms-proveedores` | 8087 | Proveedores | `/api/proveedores` |
| `ms-reportes` | 8088 | Reportes (dashboard de KPIs, construido solo desde eventos de RabbitMQ) | `/api/reportes` |
| `ms-notificaciones` | 8089 | Tickets/boletas y alertas (stock bajo, pedido listo), también desde eventos | `/api/notificaciones`, `/api/public/tickets/{codigo}` |
| `ms-rabbitmq-admin` | 8090 | Administración de colas/exchanges/bindings/DLQ de RabbitMQ (solo ADMIN, sin BD propia) | `/api/rabbitmq` |

El frontend (`http://localhost:4200`) solo le habla al `bff-gateway`
(`http://localhost:8080/api/...`) — nunca llama directo a un microservicio.
Los puertos 8081-8088 quedan expuestos igual en Docker por conveniencia (para
probar un microservicio aislado con `curl`), no porque el frontend los use.

**Seguridad implementada de punta a punta (no es un mockup):**
- Login real con MSAL contra Azure Entra ID desde el frontend (`src/auth/authConfig.js`), con guard de rutas (`ProtectedRoute.jsx`) y hook que adjunta el access token como `Authorization: Bearer` a cada llamada (`src/services/apiClient.js`).
- El `bff-gateway` valida el JWT (issuer, audience, firma) antes de reenviar cualquier petición — sin token válido, responde `401` sin tocar el backend de negocio. Cada microservicio individual valida el JWT otra vez de forma independiente (defensa en profundidad).
- Autorización por rol con `@PreAuthorize` en los controllers (`hasAuthority('ADMIN')`, etc.), usando el claim `roles` del **access token** (no del ID token — los App Roles se asignan en el Enterprise Application del backend, así que solo viajan en el token pedido con el scope del backend).
- `GET /api/productos` es la única ruta pública (un cliente ve el menú sin loguearse); crear/editar/eliminar productos y el resto de los módulos del staff exigen JWT + rol.
- CORS resuelto dentro de la cadena de Spring Security (`CorsConfig.java`), no como filtro aparte, para que también lleve los headers correctos en respuestas de error (401/403), no solo en las exitosas.

**Modos de ejecución:**
- `noauth` (default en Docker): sin Azure, JWT sin validar, cada microservicio con su propia H2 embebida — para demos rápidas.
- Azure real: valida JWT contra tu Tenant, usa MySQL compartido con una base por microservicio.

**Infraestructura:** todo dockerizado (`docker-compose.yml` en la raíz — MySQL + 8 microservicios + BFF + frontend con un solo `docker compose up --build -d`), y con un despliegue de referencia corriendo en una instancia EC2 (nginx como proxy con SSL autofirmado, ver sección 9).

---

## 0. Qué se evalúa realmente (para priorizar tu tiempo)

Según la pauta oficial del EP1, la nota se calcula sobre 2 indicadores:

| Indicador | Ponderación | Qué revisa |
|---|---|---|
| MSAL en el frontend | 60% | Login/logout funcionan, se obtienen los tokens, se adjuntan como Bearer a las llamadas al backend (en React: hook `useApiClient`, ver sección 6) |
| BFF valida el JWT | 40% | El backend valida issuer, audience y firma del token, autoriza por rol, responde códigos correctos |

El despliegue en AWS (EC2 + API Gateway) es la capa "de vitrina" que pide el
enunciado general, pero no es lo que más pesa en la pauta — ver sección 9 para
la versión rápida de eso.

---

## 1. Correrlo AHORA MISMO con Docker (la forma más rápida y confiable)

Todo el stack (MySQL + 8 microservicios + BFF + frontend) está dockerizado
y por defecto corre en modo `noauth` (sin Azure configurado todavía, JWT sin
validar) — es la forma más rápida de dejarlo funcionando en un computador
nuevo, por ejemplo el del instituto.

**Requisito único: Docker Desktop instalado y corriendo.**

```bash
# parado en la raíz del repo (donde está este README y docker-compose.yml)
docker compose up --build -d
```

La primera vez tarda varios minutos (descarga imágenes base y compila los 9
módulos Maven + el frontend). Cuando termine:

- Frontend: http://localhost:4200
- BFF: http://localhost:8080/api/productos
- Cada microservicio también queda expuesto individualmente (8081-8088) y
  MySQL en 3306, aunque en modo `noauth` no se usa (cada microservicio usa
  su propia base H2 embebida, persistida en un volumen Docker).

Comandos útiles:
```bash
docker compose ps              # ver estado de cada contenedor
docker compose logs -f bff-gateway   # logs de un servicio en particular
docker compose down            # apagar todo (los datos quedan en volúmenes)
docker compose down -v         # apagar y borrar también los datos
```

**El menú viene vacío en una base nueva.** Para cargar productos de prueba,
con el stack arriba:
```bash
curl -X POST http://localhost:8080/api/productos \
  -H "Content-Type: application/json" \
  -d '{"nombre":"Latte Vainilla","descripcion":"Espresso con leche vaporizada y jarabe de vainilla","precio":3200,"categoria":"Bebidas calientes"}'
```
(repite con los productos que quieras — en modo `noauth` no hace falta token).

Si ves el error "ports are not available" al levantar el stack, es porque ya
tienes el backend/frontend corriendo manualmente (sección 3) en esos mismos
puertos — ciérralos primero.

---

## 2. Requisitos previos

- **Docker Desktop** — la forma recomendada de correr el proyecto (sección 1).
- Java 17 y Node.js 18+ — solo si prefieres el modo manual sin Docker (sección 3).
- Cuenta de Azure for Students activa (`portal.azure.com`) — solo para el flujo
  con autenticación real (sección 4).
- Git y una cuenta de GitHub.

---

## 3. Modo manual sin Docker (alternativa, perfil `noauth`)

Si no tienes Docker a mano, puedes correr todo directo con Maven/npm. Deja
todos los endpoints abiertos, sin pedir JWT. **Nunca debe quedar activo en la
entrega final.**

### 3.1 Backend en modo noauth (con H2, sin Docker, sin instalar Maven)

El proyecto incluye **Maven Wrapper** (`mvnw` / `mvnw.cmd`): no necesitas
instalar Maven, solo Java 17.

Atajo con un solo comando (abre una ventana por servicio):
```bash
cd cafeteria-backend/cafeteria-backend
# Windows (doble click o desde cmd/PowerShell):
start-noauth.bat
# Git Bash / Mac / Linux:
./start-noauth.sh
```

O manualmente, paso a paso:
```bash
cd cafeteria-backend/cafeteria-backend
./mvnw clean install
export SPRING_PROFILES_ACTIVE=noauth
./mvnw -f ms-productos/pom.xml spring-boot:run &
# ... el resto de los microservicios igual
./mvnw -f bff-gateway/pom.xml spring-boot:run
```
En Windows/Git Bash, si `&` no te deja varios procesos en la misma terminal,
abre una terminal nueva por cada comando (o usa `start-noauth.bat`).

### 3.2 Frontend en modo noauth
```bash
cd cafeteria-frontend/cafeteria-frontend
npm install
copy .env.example .env    # Windows: copy · Git Bash/Mac/Linux: cp
# En .env, deja VITE_AUTH_DISABLED=true
npm run dev
```
Abre `http://localhost:4200` — salta el login directo al menú.

### 3.3 Cuando tengas Azure configurado (sección 4)
1. Backend: no exportes `SPRING_PROFILES_ACTIVE` (o ponlo en cualquier otro
   valor), y exporta `AZURE_ISSUER_URI` / `AZURE_AUDIENCE` reales.
2. Frontend: en `.env`, cambia `VITE_AUTH_DISABLED=false` y completa
   `VITE_AZURE_CLIENT_ID` / `VITE_AZURE_TENANT_ID` con tus valores reales.
3. Antes de subir a GitHub, revisa que ningún `.env` real quede con `noauth`
   o `AUTH_DISABLED=true` — la pauta exige que el JWT SÍ se valide.

---

## 4. Configurar Azure Entra ID (IDaaS) — ~45 min

Necesitas **2 App Registrations** dentro del mismo Tenant: una para el backend
(API) y otra para el frontend (SPA).

### 4.1 Crear el Tenant (si no lo tienes)
1. Entra a `portal.azure.com` con tu cuenta académica.
2. Busca el recurso **Microsoft Entra External ID** y créalo.
3. Guarda el **Directory (tenant) ID**.

### 4.2 Registrar el Backend (API)
1. En tu Tenant → **Entra ID → Registros de aplicaciones → Nuevo registro**.
2. Nombre: `cafeteria-backend`. Tipo de cuenta: "Solo cuentas de este directorio
   organizativo".
3. Guarda el **Application (client) ID**.
4. Ve a **Expose an API**:
   - Application ID URI: acepta el propuesto o define `api://cafeteria-backend`.
   - Agrega los scopes: `pedidos.read` y `pedidos.write`.
5. Ve a **App roles** y crea roles (llegan en el claim `roles` del JWT, usado
   por `@PreAuthorize` en el backend): `ADMIN`, `BARISTA`, `CAJERO`.

### 4.3 Registrar el Frontend (SPA)
1. Nuevo registro → Nombre: `cafeteria-frontend`.
2. Tipo de plataforma: **SPA (Single-page application)**.
3. Redirect URI: `http://localhost:4200`.
4. Guarda el **Application (client) ID** (distinto al del backend).
5. **API permissions → Add a permission → APIs de mi organización** →
   `cafeteria-backend` → selecciona `pedidos.read` y `pedidos.write`.
6. **Grant admin consent**.

### 4.4 Asignarte un rol a ti mismo (para poder probar POST/PUT/DELETE)
Entra ID → Enterprise applications → `cafeteria-backend` → **Users and
groups → Add user/group** → tu usuario → rol `ADMIN`.

### 4.5 Datos que debes tener anotados al terminar esta sección
```
TENANT_ID            = ...
BACKEND_CLIENT_ID    = ...
BACKEND_APP_ID_URI   = api://cafeteria-backend
FRONTEND_CLIENT_ID   = ...
```

---

## 5. Backend local con Azure real (MySQL, sin `noauth`)

### 5.1 Base de datos
```bash
# parado en la raíz del repo (donde está docker-compose.yml)
docker compose up -d mysql
```
Levanta MySQL en `localhost:3306` (usuario/clave `cafeteria`/`cafeteria`)
con una base separada por microservicio (`init-db/01-schemas.sql`).

### 5.2 Variables de entorno
```bash
export AZURE_ISSUER_URI="https://login.microsoftonline.com/<TENANT_ID>/v2.0"
export AZURE_AUDIENCE="api://cafeteria-backend"
```

### 5.3 Compilar y levantar
```bash
./mvnw clean install
./mvnw -f ms-productos/pom.xml spring-boot:run &
# ... resto de microservicios ...
export FRONTEND_ORIGIN="http://localhost:4200"
./mvnw -f bff-gateway/pom.xml spring-boot:run
```
Sin token, `curl -i http://localhost:8080/api/productos` debe responder 401.

---

## 6. Frontend local con Azure real

```bash
cd cafeteria-frontend/cafeteria-frontend
npm install
copy .env.example .env
```
Edita `.env`: `VITE_AZURE_CLIENT_ID` y `VITE_AZURE_TENANT_ID` (sección 4.5),
`VITE_AUTH_DISABLED=false`. Luego `npm run dev` y abre `http://localhost:4200`.
Click en "Iniciar sesión" → Azure Entra ID → vuelves autenticado → las
llamadas al BFF llevan el token adjunto automáticamente (hook `useApiClient`,
`src/services/apiClient.js`). **Esto es lo que mide el 60% de la pauta.**

---

## 7. Docker — referencia completa

El `docker-compose.yml` de la raíz define:

- `mysql` — base para el modo con Azure real (sección 5).
- `ms-productos` … `ms-reportes` (8) y `bff-gateway` — cada uno se construye
  con [cafeteria-backend/cafeteria-backend/Dockerfile](cafeteria-backend/cafeteria-backend/Dockerfile)
  (multi-stage: compila con Maven, corre con JRE Alpine), parametrizado por
  `build.args.MODULE`. Todos corren en `SPRING_PROFILES_ACTIVE=noauth` por
  defecto, con su propia base H2 en un volumen nombrado.
- `frontend` — se construye con
  [cafeteria-frontend/cafeteria-frontend/Dockerfile](cafeteria-frontend/cafeteria-frontend/Dockerfile)
  (build con Node + `vite build`, se sirve con `vite preview`).

Para cambiar a modo con Azure real dentro de Docker **no edites
`docker-compose.yml`**: copia `.env.example` a `.env` en la raíz y completa
ahí `SPRING_PROFILES_ACTIVE=` (vacío), `AZURE_ISSUER_URI`, `AZURE_AUDIENCE`,
`VITE_AUTH_DISABLED=false`, `VITE_AZURE_CLIENT_ID`, `VITE_AZURE_TENANT_ID` y
`VITE_API_SCOPES` con tus datos reales — Docker Compose lo lee automático.
Ese `.env` nunca se sube a git (ver `.gitignore` raíz). Después:
```bash
docker compose up --build -d
```

**CORS y rutas públicas:** el CORS se configura dentro de la cadena de Spring
Security del BFF (`bff-gateway/.../config/CorsConfig.java`), no como un
filtro aparte — un `CorsWebFilter` separado no alcanza a agregar los headers
cuando Security corta la respuesta con 401/403, y el navegador termina
bloqueando hasta los errores legítimos como si fueran de CORS. Por diseño,
`GET /api/productos` es público (un cliente ve el menú sin loguearse); crear/
editar/eliminar productos y el resto de las rutas del staff siguen exigiendo
JWT + rol.

---

## 8. Subir a GitHub

Todo el proyecto vive en **un solo repositorio**:
```bash
# parado en la raíz (donde está este README)
git init
git add .
git commit -m "CafeGestion360: backend (8 microservicios + BFF) y frontend React con MSAL"
git branch -M main
git remote add origin https://github.com/<tu-usuario>/<tu-repo>.git
git push -u origin main
```

Los `.gitignore` (raíz, backend y frontend) ya excluyen `target/`,
`node_modules/`, las bases H2 locales (`data/`) y cualquier `.env` real.

**Importante:** antes de subir, revisa que ningún `.env` real quede en el
commit y que solo dejes `.env.example` con placeholders — nunca subas
secretos de producción a un repo público. Copia el link del repo a AVA y
envíalo también al correo del docente (según el enunciado).

---

## 9. ¿Hay que migrar esto a la nube? (versión rápida, si te queda tiempo)

El enunciado general describe el backend "desplegado en instancias EC2 y
protegido por AWS API Gateway", pero la pauta de evaluación **no** pide una URL
en producción como entregable — pide el código fuente. Trátalo como algo
deseable, no bloqueante. Si te sobra tiempo, la versión mínima es:

1. **EC2**: crea una instancia (Ubuntu, t2.micro/t3.micro), instala Docker, y
   corre `docker compose up --build -d` directo desde ahí (mismo repo
   clonado) — o, sin Docker, instala Java 17 y copia los `.jar` generados por
   `mvn package` de cada microservicio y del BFF (`java -jar nombre.jar`, con
   `nohup ... &` o systemd). Abre en el Security Group los puertos 8080-8088.
2. **RDS**: crea una instancia MySQL (free tier), y cambia `DB_URL`,
   `DB_USER`, `DB_PASSWORD` como variables de entorno en la EC2 apuntando a esa
   RDS en vez de a tu Docker local.
3. **AWS API Gateway**: crea un HTTP API con una ruta `ANY /{proxy+}` que
   apunte a la IP pública (o DNS) del BFF en el puerto 8080, y configura CORS
   con el origen de tu frontend desplegado.
4. Crea un `.env.production` en el frontend con `VITE_API_BASE_URL` apuntando
   a la URL del API Gateway, y compila con `npm run build`.

Si el tiempo no alcanza para esto, prioriza sin culpa las secciones 1-6: un
sistema que corre local (o en Docker) con el flujo de seguridad completo y
bien explicado en el README vale más, frente a la pauta, que un despliegue a
medio hacer en AWS.

### 9.1 Cambiar la IP pública del EC2 (si la instancia se reinicia)

Una instancia EC2 sin Elastic IP cambia de IP pública cada vez que se
detiene y se vuelve a iniciar. Cuando eso pase, en la instancia (por SSH):

```bash
# 1. Actualizar el .env con la IP nueva
sed -i 's|<IP_VIEJA>|<IP_NUEVA>|g' .env
cat .env  # verificar que cambió

# 2. Regenerar el certificado SSL autofirmado para la nueva IP
sudo openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout /etc/ssl/private/cafeteria.key \
  -out /etc/ssl/certs/cafeteria.crt \
  -subj "/CN=<IP_NUEVA>"
sudo systemctl reload nginx

# 3. Reconstruir el frontend con la nueva IP (VITE_API_BASE_URL se hornea
#    en el build, no es una variable de entorno normal de contenedor)
docker compose stop frontend
docker compose rm -f frontend
docker compose build --no-cache frontend
docker compose up -d frontend
```

Para evitar este problema de raíz, lo correcto es asignarle una **Elastic
IP** a la instancia (gratis mientras esté asociada a una instancia corriendo)
para que la IP pública quede fija.

---

## 10. Estructura del repositorio

```
docker-compose.yml            # stack completo: mysql + 8 ms + bff + frontend

cafeteria-backend/cafeteria-backend/
  pom.xml                     # padre Maven multi-modulo (fix: repackage bindeado en package)
  Dockerfile                  # generico, parametrizado por build-arg MODULE
  ms-productos/ ... ms-reportes/   # 8 microservicios (mismo patron cada uno)
  bff-gateway/                # Spring Cloud Gateway + validacion JWT
  init-db/01-schemas.sql      # esquemas (bases) por microservicio
  start-noauth.bat/.sh        # atajo modo manual sin Docker

cafeteria-frontend/cafeteria-frontend/
  Dockerfile
  src/auth/authConfig.js       # configuracion MSAL (clientId, scopes, authority)
  src/auth/ProtectedRoute.jsx  # guard de rutas (equivalente a MsalGuard)
  src/services/apiClient.js    # hook useApiClient (equivalente a MsalInterceptor)
  src/pages/                   # Store, Login, Pedidos, Productos, etc.
  public/productos/            # fotos de producto (<nombre-slug>.jpg, ver ProductVisual.jsx)
  public/banner/hero.jpg       # foto de fondo del banner principal
  .env.example                 # variables VITE_* (copiar a .env con tus datos)
```

<img width="1652" height="1002" alt="Diagrama sin título drawio (1)" src="https://github.com/user-attachments/assets/2e8a78a1-de1f-4999-9654-500460d2638b" />

## 11. EP2 — Fase 1: seguridad, roles y reglas de negocio

Ver el plan completo en `docs/EP2_PLAN.md`. Resumen de lo que cambió en esta fase:

### Seguridad
- La variable `AZURE_AUDIENCE` pasó a llamarse **`AZURE_AUDIENCES`** (acepta una lista separada por coma: el GUID del backend y/o `api://cafeteria-backend`). Actualiza tu `.env`.
- El conversor de roles del JWT ahora normaliza cada rol a MAYÚSCULAS sin prefijo en los 8 microservicios y en el BFF.
- El BFF expone **`GET /api/me`** → `{ nombre, email, roles[] }`, leído directamente del JWT ya validado. Es la fuente de verdad del rol: el frontend ya no decodifica el token por su cuenta (`useUserRole`/`useAuthProfile` ahora llaman a este endpoint).
- `docker-compose.yml` cambió el valor por defecto de `VITE_AUTH_DISABLED` a `false` — si quieres seguir en modo demo sin Azure, déjalo explícito en tu `.env` (`.env.example` ya lo trae en `true` junto con `SPRING_PROFILES_ACTIVE=noauth`).
- El selector "Ver como" del NavBar ahora solo aparece con `VITE_AUTH_DISABLED=true` y muestra un banner **"MODO DEMO"**. En modo Azure real, si tu usuario tiene más de un rol asignado, aparece un selector de "perfil activo".
- Se eliminaron los `console.log` de tokens/headers en `src/services/apiClient.js`.

### Roles y permisos
- Se corrigió el bug `@PreAuthorize("hasAuthority('ADMIN') or hasAuthority('ADMIN')")` (12 instancias en 6 controllers) y se aplicó la matriz de roles completa de `docs/EP2_PLAN.md` sección 4 (`ADMIN, GERENTE, BARISTA, CAJERO, BODEGUERO`) vía `hasAnyAuthority` + una clase `security/Roles.java` de constantes en cada microservicio.

### Capa de servicio, DTOs y validaciones
`ms-productos`, `ms-inventario`, `ms-clientes`, `ms-empleados` y `ms-proveedores` ahora tienen capa `service/`, DTOs con Bean Validation (`dto/`) y un `GlobalExceptionHandler` (`exception/`) que devuelve `{timestamp, status, error, message, fieldErrors}` en 400/404/409/403. Los controllers ya no acceden al repositorio directamente.

Novedades por módulo:
- **ms-productos**: campos nuevos `disponible` e `imagenUrl`; `GET /api/productos?categoria=&disponible=`.
- **ms-inventario**: nuevas entidades **`RecetaItem`** (`/api/recetas`) y **`MovimientoStock`** (`POST/GET /api/inventario/{id}/movimientos`, tipos `ENTRADA/SALIDA/AJUSTE`); `GET /api/inventario/alertas` (stock bajo mínimo).
- **ms-clientes**: `POST /api/clientes/{id}/canje` para descontar puntos de fidelización.
- **ms-empleados**: campo nuevo `fechaIngreso`; `PATCH /api/empleados/{id}/desactivar` (alternativa a borrar).
- **ms-proveedores**: campo nuevo `rut` con validador propio `@Rut` (dígito verificador módulo 11) y `insumosQueProvee`.
- **ms-pedidos, ms-pagos, ms-reportes**: solo se corrigió la matriz de `@PreAuthorize` y se agregó el `GlobalExceptionHandler`; la capa de servicio/DTOs completa de estos tres llega en una fase posterior (checkout público, máquina de estados, modelo de lectura por eventos).

### Datos de prueba
Cada uno de los 5 microservicios con capa de servicio completa trae un perfil `seed` (`SPRING_PROFILES_ACTIVE=noauth,seed`) que inserta datos de ejemplo si la tabla está vacía (los 12 productos con foto, insumos con una alerta de stock bajo ya activa, clientes, empleados de cada rol, proveedores con RUT válido).

### Todos los microservicios pasaron de `application.properties` a `application.yml`
Con un bloque `spring.security.oauth2.resourceserver.jwt` consistente en los 8 + BFF.

Backend verificado con `./mvnw clean verify`: **102 tests, 0 fallos**. Frontend verificado con `npm run build`.

## 12. EP2 — Fase 2: cluster RabbitMQ local + estabilidad

### Cluster RabbitMQ (3 nodos)
`docker-compose.yml` levanta `rabbitmq-1`, `rabbitmq-2` y `rabbitmq-3` (imagen `rabbitmq:3.13-management`), con
peer discovery `classic_config` (`rabbitmq/rabbitmq.conf`, montado en los 3) — al arrancar juntos con datos
vacíos, forman el cluster solos. Verificado manualmente: los 3 nodos aparecen como "Running Nodes" en
`cluster_status` y un microservicio (`ms-productos`) se conecta correctamente a los 3 vía `RABBITMQ_ADDRESSES`.

```bash
docker compose up -d --build          # levanta todo, incluido el cluster
docker exec rabbitmq-1 rabbitmqctl cluster_status   # confirma los 3 nodos
```

UI de administración: `http://localhost:15672` (nodo 1), `http://localhost:15673` (nodo 2),
`http://localhost:15674` (nodo 3) — usuario/clave en `.env` (`RABBITMQ_USER`/`RABBITMQ_PASSWORD`,
por defecto `cafeteria`/`cafeteria`). Solo el nodo 1 expone el puerto AMQP 5672 al host; los microservicios
usan la red interna de Docker para llegar a los 3.

### Módulo `cafeteria-common`
Nuevo módulo Maven (librería, no una app Spring Boot) con los eventos de dominio que viajarán por RabbitMQ en
la Fase 3 (`PedidoCreadoEvent`, `PagoProcesadoEvent`, `StockBajoEvent`, `PedidoEstadoActualizadoEvent`,
`ItemEvent`), las excepciones `NonRecoverableMessageException`/`RecoverableMessageException` y el helper
`AckHandler` (ack / nack sin reintento / nack con reintento). Los 8 microservicios ya lo tienen como
dependencia, además de `spring-boot-starter-amqp` y el bloque `spring.rabbitmq.*` (conexión, no las colas —
eso es de la Fase 3) en su `application.yml`.

### Estabilidad (`docker-compose.yml`)
- `restart: unless-stopped` en todos los servicios.
- `mem_limit: 512m` + `JAVA_TOOL_OPTIONS` (heap acotado) en cada JVM, para que 9+ contenedores Java no se
  maten entre sí por falta de memoria en una instancia pequeña.
- Los 8 microservicios esperan a `rabbitmq-1: service_healthy` además de `mysql`.
- En `noauth`/desarrollo local sin Docker, `RABBITMQ_ADDRESSES` cae a `localhost:5672` por defecto — sigue
  funcionando con un solo RabbitMQ local (sin cluster).

### Frontend: imagen de producción con nginx
El `Dockerfile` del frontend ahora es multi-stage: build de Vite + una imagen `nginx:1.27-alpine` que sirve
el estático (ya no `vite preview`). `docker-entrypoint.sh` elige la config de nginx en el arranque:
- Sin certificado montado en `/etc/nginx/certs` → solo HTTP (puerto 80), con `try_files` para que las rutas
  de React Router no den 404 al recargar la página.
- Con `fullchain.pem`/`privkey.pem` montados ahí → HTTPS en 443 + redirección automática desde 80 (lo que
  usará la Fase 6 en la EC2, con el certificado que genera Terraform).

En local, `docker-compose.yml` sigue publicando el frontend en `http://localhost:4200` (mapeado al puerto 80
del contenedor).

---

## 13. EP2 — Fase 3: mensajería (productores, consumidores, DLX/DLQ, ACK)

### Configuración centralizada (`RabbitProperties` + `RabbitMQConfig`)
Cada microservicio con mensajería (`ms-pagos`, `ms-pedidos`, `ms-clientes`, `ms-inventario`, `ms-reportes`,
`ms-notificaciones`) tiene un bloque `app.rabbitmq` en su `application.yml` (exchanges, routing-keys, colas,
políticas de retención) mapeado por una clase `RabbitProperties` (`@ConfigurationProperties`). `RabbitMQConfig`
arma los beans `Queue`/`Exchange`/`Binding` leyendo esa clase — nunca un string suelto. Los `@RabbitListener`
usan placeholders (`"${app.rabbitmq.queues.pago-aprobado}"`), no constantes Java, para que el nombre real de
la cola salga siempre de `application.yml`.

### Colas, exchanges y bindings
4 exchanges, 8 colas principales + 8 DLQ = 16 colas, exactamente como en el diseño original (ver
`docs/EP2_PLAN.md`, anexo al final del archivo, con la tabla completa y las 3 decisiones de diseño que no
estaban explícitas en el plan original: `clienteEmail` agregado a `PedidoCreadoEvent`, "top productos" en
`ms-reportes` calculado desde `pedido.creado` en vez de `pago.aprobado`, y `ms-notificaciones` resolviendo el
detalle del ticket vía REST a `ms-pedidos`).

### `ms-reportes`: modelo de lectura + dashboard
Nuevo modelo de lectura construido solo desde eventos: `VentaProducto`, `PedidosPorHora`, `PedidoEstadoActual`,
`ClienteVisto` (además de `VentaDiaria`, que ya existía). `GET /api/reportes/dashboard?desde=&hasta=` calcula
ventas de hoy, variación semanal, ticket promedio, clientes nuevos, ventas de los últimos 7 días (actual vs.
semana anterior), pedidos por hora/franja, top 5 productos y pedidos por estado.

### `ms-notificaciones` (nuevo, puerto 8089)
Tickets/boletas (`Ticket` + `ItemTicket`) y alertas (`Alerta`, tipo `STOCK_BAJO`/`PEDIDO_LISTO`/`DLQ`).
`TicketListener` consume `pago.aprobado`, completa el detalle (items, código de seguimiento, cliente) con una
llamada REST a `ms-pedidos` y "envía" el ticket simulando el email con un `log.info` estructurado.
`AlertaListener` despacha por tipo de evento (`stock.bajo` / `pedido.estado.actualizado`) en una sola cola.

### Cómo probarlo

```bash
# Dashboard de reportes (requiere al menos un pago aprobado antes)
curl http://localhost:8080/api/reportes/dashboard

# Tickets (staff)
curl http://localhost:8080/api/notificaciones/tickets

# Boleta pública por código de seguimiento (sin login)
curl http://localhost:8080/api/public/tickets/<codigoSeguimiento>

# Alertas y marcarla como leída
curl http://localhost:8080/api/notificaciones/alertas
curl -X PATCH http://localhost:8080/api/notificaciones/alertas/1/leida

# Ver las 16 colas en la UI de RabbitMQ
# http://localhost:15672 (usuario/clave: cafeteria/cafeteria)
```

### Qué queda pendiente
- Test de integración con Testcontainers (`@Tag("integration")`) del flujo checkout → PAGADO end-to-end.
- Fase 5: página "Mensajería" y paneles de alertas/DLQ en el dashboard del frontend.

---

## 14. EP2 — Fase 4: `ms-rabbitmq-admin`

Nuevo microservicio (puerto 8090, **sin base de datos propia**) que administra RabbitMQ vía API REST, solo
para el rol `ADMIN`. `RabbitAdminService` combina `RabbitAdmin` de Spring AMQP (crear/eliminar/purgar por
protocolo AMQP) con un `RestClient` autenticado contra la API de administración de RabbitMQ en el puerto
15672 (listar colas/exchanges/bindings, ver el cluster, leer mensajes de una cola). El controller no conoce
ninguno de los dos directamente.

### Endpoints (`/api/rabbitmq/**`, todos requieren rol ADMIN)

| Verbo | Ruta | Qué hace |
|---|---|---|
| GET | `/queues`, `/queues/{name}` | Lista o detalla colas (mensajes listos/no confirmados, consumidores) |
| POST | `/queues` | Crea una cola (quorum, durable) |
| DELETE | `/queues/{name}?ifUnused=&ifEmpty=` | Elimina una cola (409 si está protegida) |
| POST | `/queues/{name}/purge` | Vacía una cola sin eliminarla |
| GET | `/exchanges` | Lista exchanges |
| POST | `/exchanges` | Crea un exchange (direct/topic/fanout/headers) |
| DELETE | `/exchanges/{name}` | Elimina un exchange (409 si está protegido) |
| GET | `/bindings` | Lista bindings |
| POST / DELETE | `/bindings` | Crea / elimina un binding cola↔exchange |
| GET | `/dlq` | Resumen de mensajes pendientes en las 8 DLQ del sistema |
| POST | `/dlq/{name}/reprocess?max=10` | Reenvía mensajes de una DLQ a su exchange de origen (usa el header `x-death`) |
| GET | `/cluster` | Estado de los nodos del cluster |

Validaciones: nombre `@NotBlank/@Size(3,120)` con patrón que además prohíbe el prefijo `amq.` (reservado);
`type` de exchange restringido a `direct|topic|fanout|headers`; `ttlMs`/`maxLength`/`deliveryLimit`
opcionales con rangos acotados. Las 16 colas + 4 exchanges del sistema (`app.rabbitmq.protegidos` en
`application.yml`) no se pueden eliminar ni purgar (409 con mensaje claro).

Documentación interactiva: `http://localhost:8090/swagger-ui.html` (abierta, sin JWT, para poder revisarla).

### Cómo probarlo

```bash
# Requiere un token ADMIN (o SPRING_PROFILES_ACTIVE=noauth para probar sin Azure)
curl http://localhost:8080/api/rabbitmq/queues
curl http://localhost:8080/api/rabbitmq/dlq
curl http://localhost:8080/api/rabbitmq/cluster

curl -X POST http://localhost:8080/api/rabbitmq/queues \
  -H "Content-Type: application/json" \
  -d '{"name":"cola-demo"}'
curl -X DELETE http://localhost:8080/api/rabbitmq/queues/cola-demo

# Protegida -> 409
curl -i -X DELETE http://localhost:8080/api/rabbitmq/queues/pagos.pedido-creado.queue
```

### Qué queda pendiente
- Fase 5: página "Mensajería" en el frontend que consuma esta API.

## 15. EP2 — Fase 6: Terraform + despliegue en AWS

Todo el stack (8 microservicios + `bff-gateway` + `ms-notificaciones` + `ms-rabbitmq-admin` + frontend +
cluster RabbitMQ + MySQL) corre en **una sola EC2** creada con Terraform, compatible con **AWS Academy
Learner Lab**: usa el key pair `vockey` y el `LabInstanceProfile` ya existentes y no crea ningún rol ni
política IAM (el Lab no lo permite). Delante de la EC2 va un **API Gateway** (HTTP API) que reenvía
`/api/**` al `bff-gateway` en el puerto 8080.

Archivos nuevos en [`infra/terraform/`](infra/terraform/) (detalle completo en su propio
[README](infra/terraform/README.md)):

| Archivo | Contenido |
|---|---|
| `versions.tf` | providers `aws ~> 5.0` y `random ~> 3.6`, Terraform `>= 1.5` |
| `variables.tf` | región, tipo de instancia, `key_name`/`iam_instance_profile` del Lab, `my_ip_cidr`, datos de Azure, passwords sensibles (`db_password`, `rabbitmq_password`, `rabbitmq_erlang_cookie`) |
| `main.tf` | AMI Ubuntu 22.04 (data source Canonical), Security Group (22 y 15672 solo desde `my_ip_cidr`; 80/443 públicos; 8080 para el API Gateway), Elastic IP, la instancia EC2 (disco gp3 30 GB) con su `user_data` |
| `monitoring.tf` | alarmas CloudWatch `StatusCheckFailed_System` (recover) y `StatusCheckFailed_Instance` (reboot), desactivables con `enable_recovery_alarms` |
| `apigateway.tf` | API Gateway HTTP API → `HTTP_PROXY` hacia `http://<EIP>:8080/api/{proxy}`, inyectando el header `X-Origin-Verify` (parameter mapping) |
| `user_data.sh.tftpl` | instala Docker + plugin de compose, crea 4 GB de swap, clona el repo, genera el `.env` y un certificado autofirmado para nginx, crea y habilita el servicio systemd `cafeteria.service` |
| `outputs.tf` | IP elástica, URL del frontend, URL del API Gateway, URL de la UI de RabbitMQ, comando SSH |
| `terraform.tfvars.example` | ejemplo sin secretos reales |
| `backend.tf` + `init-backend.sh` | backend remoto (S3 + bloqueo DynamoDB) para que el state no viva solo en un disco: imprescindible para que GitHub Actions (runners nuevos en cada corrida) y tu máquina local compartan el mismo state |

### CI/CD: `.github/workflows/terraform-deploy.yml` y `terraform-destroy.yml`

Dos workflows, **ambos solo con disparo manual** (`workflow_dispatch`): las credenciales temporales de
AWS Academy Learner Lab duran unas pocas horas, así que correrlos en cada push fallaría la mayoría de las
veces y además re-aplicaría infraestructura real sin que nadie lo pidiera. `terraform-deploy.yml` corre
`plan` + `apply` y publica la IP/URLs en el resumen del job; `terraform-destroy.yml` corre `destroy` y pide
escribir `destruir` para confirmar. Ambos arrancan con `init-backend.sh` (crean el bucket S3 y la tabla
DynamoDB si no existen) para no duplicar infraestructura entre corridas. Detalle de los secrets necesarios
(`AWS_ACCESS_KEY_ID`, `MY_IP_CIDR`, los datos de Azure, los passwords) en
[`infra/terraform/README.md`](infra/terraform/README.md#secrets-necesarios-en-el-repo).

### RabbitMQ en el dashboard

Ya implementado en la Fase 5 (no es parte de esta fase, se deja constancia porque se preguntó): el panel
"Estado de mensajería" de `DashboardHome.jsx` (ADMIN) muestra el KPI "Mensajes en DLQ", el estado del
cluster (nodos activos), la tabla de colas con botón "Reprocesar" para las DLQ con mensajes, y hay una
página `Mensajeria.jsx` dedicada para crear/eliminar colas, exchanges y bindings contra
`ms-rabbitmq-admin`.

### Filtro `X-Origin-Verify` en el BFF

`OriginVerifyGlobalFilter` (nuevo, `bff-gateway/.../filter/`) exige el header `X-Origin-Verify` en toda
petición **solo si** la variable de entorno `ORIGIN_VERIFY_SECRET` no está vacía. El API Gateway de
Terraform genera ese secreto (`random_password`) y lo inyecta en cada petición que reenvía; si alguien le
pega directo al puerto 8080 de la EC2 saltándose el API Gateway, no trae el header y el BFF responde
**403** (prueba S8 de `docs/EP2_PLAN.md` sección 11). En local / Docker Compose, `ORIGIN_VERIFY_SECRET`
queda vacío por defecto y el filtro no exige nada: no rompe nada de lo que ya funcionaba.

También se parametrizó el password de MySQL (`DB_PASSWORD`, antes fijo en `cafeteria` dentro de
`docker-compose.yml`) para que Terraform pueda generarlo en EC2 sin tocar el compose; en local sigue
usando `cafeteria` por defecto si no defines la variable.

### Cómo probarlo

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # completa tus valores reales
./init-backend.sh                               # crea el backend S3+DynamoDB (si no existe) + terraform init
terraform plan
terraform apply                                 # escribe "yes"
terraform output                                # IP elástica, URLs, comando SSH

# Validar sin credenciales de AWS (no aplica nada, solo valida sintaxis):
terraform init -backend=false
terraform validate
terraform fmt -check

# S8 una vez desplegado: pegarle directo al puerto 8080 de la EC2 (sin pasar
# por el API Gateway) debe responder 403.
curl -i http://<EIP_ELASTICA>:8080/api/productos

# O desde GitHub: pestaña Actions -> "Terraform - Deploy a AWS (Academy Lab)" -> Run workflow
# (después de cargar los secrets del repo, ver infra/terraform/README.md)
```

### Qué queda pendiente
- Ejecutar el deploy en una sesión real del Learner Lab, ya sea `terraform apply` local o el workflow de
  GitHub Actions (los pasos de Azure/AWS de las secciones 7 y 8.3 del plan los hace la persona, no el
  agente).

## 16. EP2 — Fase 7: pruebas, evidencias y documentación

### Colección Postman
[`docs/postman/CafeGestion360_EP2.postman_collection.json`](docs/postman/CafeGestion360_EP2.postman_collection.json)
+ [`CafeGestion360_EP2.postman_environment.json`](docs/postman/CafeGestion360_EP2.postman_environment.json),
con las pruebas con status code de la sección 11 del plan, en 3 carpetas:

- **S - Seguridad**: S1 (público 200), S2 (sin token 401), S3/S4 (BARISTA 403 / ADMIN 204 sobre el mismo
  DELETE), S5 (token alterado 401), S6 (`GET /api/me` por cada uno de los 5 roles), S8 (pegarle directo al
  puerto 8080 de la EC2 sin pasar por el API Gateway -> 403).
- **Checkout público**: checkout con tarjeta normal + seguimiento hasta `PAGADO` (S10/M2), tarjeta `0000`
  hasta `PAGO_RECHAZADO` (M3), tarjeta `9999` (reintentos -> DLQ, M4) y `8888` (DLQ inmediato, M5).
- **Mensajería - RabbitMQ Admin**: conteo de colas/exchanges (M1), crear cola con nombre vacío / inválido /
  con prefijo `amq.` -> 400 (M8), crear cola + binding + borrar binding + borrar cola -> 201/201/204/204
  (M9), borrar cola protegida -> 409 (M10), reprocesar una DLQ (M11).

Los tokens (`adminToken`, `baristaToken`, etc.) se completan a mano en el Environment con un access token
real de cada usuario de prueba (sección 7, paso 11). Sin Azure configurado (perfil `noauth`), las pruebas
de seguridad no aplican porque todo queda `permitAll`.

### Script de smoke test
[`scripts/smoke-test.sh`](scripts/smoke-test.sh) recorre con `curl`, sin depender de Postman: productos
públicos, un checkout completo con polling cada 3 s hasta `PAGADO`, un checkout con tarjeta `9999` con
espera de ~35 s (3 reintentos) y verificación de que el mensaje cae en
`pagos.pedido-creado.queue.dlq`, y los endpoints principales de `ms-rabbitmq-admin` (listar, crear cola
inválida -> 400, borrar protegida -> 409, crear/borrar cola de prueba).

```bash
# Sin token (perfil noauth, o solo las pruebas públicas):
./scripts/smoke-test.sh

# Contra otra URL y con las pruebas de ms-rabbitmq-admin (requieren rol ADMIN):
./scripts/smoke-test.sh http://localhost:8080 "<ACCESS_TOKEN_ADMIN>"
```

### Documentación
Esta Fase consolida lo que ya se fue documentando fase a fase en este mismo README (arquitectura en la
sección "Qué tiene el proyecto", diagrama al inicio de la sección 11 original, tabla de colas/exchanges en
el Anexo Fase 3 de `docs/EP2_PLAN.md`, matriz de roles en la sección 4 del plan) más la Fase 6 (Terraform +
CI/CD). `.gitignore` ya excluye `target/`, `node_modules/`, `.env`, `*.tfstate`, `terraform.tfvars` y
`data/` (verificado: cada módulo tiene su propio `.gitignore` para `target/`/`data/`/`node_modules/`, y
`infra/terraform/.gitignore` cubre el state y los `.tfvars`).

### Cómo probarlo
```bash
# Postman: importa los dos archivos de docs/postman/ en Postman (o Newman) y corre la colección.
newman run docs/postman/CafeGestion360_EP2.postman_collection.json \
  -e docs/postman/CafeGestion360_EP2.postman_environment.json

# Smoke test:
chmod +x scripts/smoke-test.sh
./scripts/smoke-test.sh http://localhost:8080
```


## 17. Migración de base de datos: PostgreSQL → MySQL

Los 9 microservicios con base de datos (todos excepto `bff-gateway` y `ms-rabbitmq-admin`, que no
tienen) pasaron de PostgreSQL a **MySQL 8**. Cambio mecánico, no estructural: ningún módulo usaba SQL
nativo, tipos específicos de Postgres (`jsonb`, `uuid` column, `ILIKE`, `ON CONFLICT`, etc.) ni dialecto
de Hibernate explícito — todo es JPA estándar con `ddl-auto` (el esquema lo genera Hibernate solo), así
que no hizo falta tocar ninguna entidad, repositorio ni query.

### Qué cambió
- **`pom.xml`** (9 módulos): `org.postgresql:postgresql` → `com.mysql:mysql-connector-j`.
- **`application.yml`** (9 módulos): `DB_URL` default de `jdbc:postgresql://localhost:5432/cafeteria` a
  `jdbc:mysql://localhost:3306/cafeteria?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC`
  (el `characterEncoding=UTF-8` es necesario por los acentos en `categoria` de productos — "Bebidas
  frías", "Pastelería" — MySQL no usa UTF-8 por defecto en la conexión).
- **H2 de `noauth`/tests** (18 archivos): `MODE=PostgreSQL` → `MODE=MySQL`, para que el modo sin Azure y
  los tests ejerciten semántica MySQL en vez de seguir emulando Postgres.
- **`docker-compose.yml`**: el servicio `postgres` (imagen `postgres:16-alpine`) pasó a `mysql` (imagen
  `mysql:8.4`), con su propio volumen, puerto 3306 y healthcheck (`mysqladmin ping`). Los 9
  microservicios que dependían de `postgres: condition: service_healthy` ahora dependen de `mysql`.
- **`init-db/01-schemas.sql`**: sin cambios de contenido — en MySQL `CREATE SCHEMA` es sinónimo de
  `CREATE DATABASE`, así que las mismas 9 sentencias `CREATE SCHEMA IF NOT EXISTS <servicio>` crean una
  base por microservicio igual que antes creaban un esquema. Cada servicio sigue seleccionando la suya
  vía `spring.jpa.properties.hibernate.default_schema` en su `application.yml`, sin tocar.
- **Terraform**: `variables.tf`/`terraform.tfvars.example`/`user_data.sh.tftpl`/`main.tf` actualizan sus
  comentarios y descripciones de `DB_PASSWORD` de "Postgres" a "MySQL" (la variable y el flujo no
  cambian: Terraform no sabe ni le importa qué motor de base de datos hay detrás).

### Qué NO cambió (a propósito)
- Ninguna entidad `@Entity`, repositorio ni query — Hibernate regenera el DDL correcto para MySQL solo
  con el cambio de driver/URL.
- Los campos `UUID` (`EventoProcesado.eventId`, usado para idempotencia en 6 módulos) siguen siendo
  `java.util.UUID` en el código Java; Hibernate los mapea a `binary(16)` en MySQL (antes `uuid` nativo en
  Postgres) sin que haya que anotar nada distinto.

### Cómo probarlo
```bash
docker compose up -d mysql
# o el stack completo:
docker compose up --build -d
mysql -h 127.0.0.1 -P 3306 -u cafeteria -pcafeteria -e "SHOW DATABASES;"
```

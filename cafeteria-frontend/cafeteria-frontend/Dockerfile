# Imagen de produccion para el frontend React + Vite: build estatico servido
# por nginx (no "vite preview", ver docs/EP2_PLAN.md seccion 8.2).
# Las variables VITE_* se "hornean" en el bundle en tiempo de build (asi
# funciona Vite), por eso se reciben como build-args en vez de variables de
# entorno normales de contenedor (ver docker-compose.yml en la raiz del repo).

FROM node:20-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

ARG VITE_AUTH_DISABLED=true
ARG VITE_API_BASE_URL=http://localhost:8080/api
ARG VITE_AZURE_CLIENT_ID=placeholder
ARG VITE_AZURE_TENANT_ID=placeholder
# Solo para tenants Microsoft Entra External ID (ciamlogin); vacio = usa la
# authority clasica login.microsoftonline.com/<TENANT_ID> (ver authConfig.js).
ARG VITE_AZURE_AUTHORITY=
ARG VITE_AZURE_REDIRECT_URI=http://localhost:4200
ARG VITE_API_SCOPES=api://cafeteria-backend/pedidos.read,api://cafeteria-backend/pedidos.write
# Panel de administracion propio de RabbitMQ (puerto 15672 del nodo 1): a donde
# lleva el boton "Dashboard RabbitMQ" que solo ve el rol ADMIN.
ARG VITE_RABBITMQ_DASHBOARD_URL=http://localhost:15672
ENV VITE_AUTH_DISABLED=$VITE_AUTH_DISABLED \
    VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_AZURE_CLIENT_ID=$VITE_AZURE_CLIENT_ID \
    VITE_AZURE_TENANT_ID=$VITE_AZURE_TENANT_ID \
    VITE_AZURE_AUTHORITY=$VITE_AZURE_AUTHORITY \
    VITE_AZURE_REDIRECT_URI=$VITE_AZURE_REDIRECT_URI \
    VITE_API_SCOPES=$VITE_API_SCOPES \
    VITE_RABBITMQ_DASHBOARD_URL=$VITE_RABBITMQ_DASHBOARD_URL

COPY . .
RUN npm run build

# ---- Produccion: nginx sirviendo el build estatico ----
# docker-entrypoint.sh elige entre nginx/http.conf (sin certificado, solo
# puerto 80) y nginx/https.conf (certificado montado en /etc/nginx/certs:
# 443 + redireccion desde 80) segun lo que encuentre montado en runtime.
FROM nginx:1.27-alpine
RUN rm -f /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx/http.conf /etc/nginx/templates/http.conf
COPY nginx/https.conf /etc/nginx/templates/https.conf
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 80 443
ENTRYPOINT ["/docker-entrypoint.sh"]

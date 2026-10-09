// URL del panel de administracion propio de RabbitMQ (plugin management,
// puerto 15672 del nodo 1). Se entra con el usuario y la clave definidos en
// docker-compose (RABBITMQ_USER / RABBITMQ_PASSWORD).
export const rabbitDashboardUrl = import.meta.env.VITE_RABBITMQ_DASHBOARD_URL || 'http://localhost:15672';

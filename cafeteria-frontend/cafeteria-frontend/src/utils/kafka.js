// URL de Kafka UI (topics, mensajes, consumer groups y su lag). En local se
// publica en el 8095 porque el 8080 lo usa bff-gateway.
export const kafkaUiUrl = import.meta.env.VITE_KAFKA_UI_URL || 'http://localhost:8095';

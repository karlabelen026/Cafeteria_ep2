package cl.duoc.cafeteria.pagos.messaging.producer;

import cl.duoc.cafeteria.common.evento.TransaccionPagoEvent;

/**
 * Productor del stream de transacciones de pago hacia Kafka (Fase 8).
 * PasarelaPagoService depende de esta interfaz, nunca de KafkaTemplate
 * directamente (misma regla que PagoEventPublisher con RabbitTemplate).
 */
public interface TransaccionStreamPublisher {

    /** Publica sin bloquear ni lanzar excepciones: si Kafka falla, el pago sigue. */
    void publicar(TransaccionPagoEvent evento);
}

package cl.duoc.cafeteria.pagos.messaging.producer;

import cl.duoc.cafeteria.common.evento.TransaccionPagoEvent;
import cl.duoc.cafeteria.pagos.messaging.config.KafkaTopicsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica cada transaccion de pago en el topic de transacciones con
 * key = clienteEmail: todas las transacciones de un mismo cliente caen en la
 * misma particion y Kafka Streams las procesa en orden (guia 3.2.1).
 *
 * Kafka es un canal de monitoreo, no parte del cobro: si el broker no esta
 * disponible el pago NO falla. El error se registra en el log desde el
 * callback del envio asincrono (o al instante, si ni siquiera se pudo
 * encolar el mensaje en el productor).
 */
@Component
public class KafkaTransaccionStreamPublisher implements TransaccionStreamPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaTransaccionStreamPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicsProperties props;

    public KafkaTransaccionStreamPublisher(KafkaTemplate<String, Object> kafkaTemplate, KafkaTopicsProperties props) {
        this.kafkaTemplate = kafkaTemplate;
        this.props = props;
    }

    @Override
    public void publicar(TransaccionPagoEvent evento) {
        String topic = props.getTopics().get("transacciones");
        String key = evento.clienteEmail();
        try {
            kafkaTemplate.send(topic, key, evento).whenComplete((resultado, error) -> {
                if (error != null) {
                    log.error("No se pudo publicar la transaccion del pago {} en Kafka (topic {}): {}",
                            evento.pagoId(), topic, error.getMessage());
                } else {
                    log.info("Transaccion del pago {} publicada en {} (particion {}, offset {})", evento.pagoId(),
                            topic, resultado.getRecordMetadata().partition(), resultado.getRecordMetadata().offset());
                }
            });
        } catch (RuntimeException e) {
            log.error("Kafka no disponible: la transaccion del pago {} no se publico ({})", evento.pagoId(),
                    e.getMessage());
        }
    }
}

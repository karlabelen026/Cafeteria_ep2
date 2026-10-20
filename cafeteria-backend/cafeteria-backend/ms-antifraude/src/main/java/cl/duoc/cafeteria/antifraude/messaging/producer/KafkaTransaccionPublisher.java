package cl.duoc.cafeteria.antifraude.messaging.producer;

import cl.duoc.cafeteria.antifraude.config.KafkaTopicsProperties;
import cl.duoc.cafeteria.common.evento.TransaccionPagoEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/** Key = cliente: mismo criterio de particionado que ms-pagos (orden por cliente). */
@Component
public class KafkaTransaccionPublisher implements TransaccionPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicsProperties topics;

    public KafkaTransaccionPublisher(KafkaTemplate<String, Object> kafkaTemplate, KafkaTopicsProperties topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public CompletableFuture<Void> publicar(TransaccionPagoEvent evento) {
        return kafkaTemplate.send(topics.topic("transacciones"), evento.clienteEmail(), evento)
                .thenApply(resultado -> null);
    }

    @Override
    public void vaciar() {
        kafkaTemplate.flush();
    }
}

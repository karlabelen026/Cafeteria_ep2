package cl.duoc.cafeteria.notificaciones.messaging.dlt;

import cl.duoc.cafeteria.notificaciones.messaging.config.KafkaTopicsProperties;
import cl.duoc.cafeteria.notificaciones.service.ReinyeccionDltService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Lee la DLT con un consumer group propio (notificaciones-dlt-reinyeccion) y
 * reenvia cada mensaje, tal cual (bytes originales), al topic principal.
 * Confirma el offset de cada mensaje recien cuando Kafka acepto el reenvio:
 * un mensaje nunca se reinyecta dos veces ni se pierde a medio camino.
 *
 * Solo reinyecta lo que habia en la DLT al momento de la llamada (foto de los
 * offsets finales al empezar): si un mensaje reinyectado vuelve a fallar y
 * regresa a la DLT, queda para una proxima reinyeccion en vez de entrar en
 * un bucle dentro de esta misma.
 */
@Service
public class KafkaReinyeccionDltService implements ReinyeccionDltService {

    private static final Logger log = LoggerFactory.getLogger(KafkaReinyeccionDltService.class);
    private static final Duration ESPERA_POLL = Duration.ofMillis(500);
    private static final int POLLS_VACIOS_PARA_TERMINAR = 4;

    private final KafkaProperties kafkaProperties;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final KafkaTopicsProperties topics;

    public KafkaReinyeccionDltService(KafkaProperties kafkaProperties, KafkaTemplate<Object, Object> kafkaTemplate,
            KafkaTopicsProperties topics) {
        this.kafkaProperties = kafkaProperties;
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public int reinyectar(int maximo) {
        String dlt = topics.topic("alertas-fraude-dlt");
        String destino = topics.topic("alertas-fraude");
        int reinyectados = 0;
        int pollsVacios = 0;

        try (Consumer<String, byte[]> consumer = new KafkaConsumer<>(propiedadesConsumidor(maximo))) {
            List<TopicPartition> particiones = consumer.partitionsFor(dlt).stream()
                    .map(info -> new TopicPartition(dlt, info.partition()))
                    .toList();
            consumer.assign(particiones);
            Map<TopicPartition, Long> finAlEmpezar = consumer.endOffsets(particiones);

            while (reinyectados < maximo && pollsVacios < POLLS_VACIOS_PARA_TERMINAR
                    && !alcanzoElFinal(consumer, finAlEmpezar)) {
                var registros = consumer.poll(ESPERA_POLL);
                if (registros.isEmpty()) {
                    pollsVacios++;
                    continue;
                }
                for (ConsumerRecord<String, byte[]> registro : registros) {
                    TopicPartition particion = new TopicPartition(registro.topic(), registro.partition());
                    if (reinyectados >= maximo || registro.offset() >= finAlEmpezar.get(particion)) {
                        // Llego a la DLT despues de empezar: sin confirmar, queda para la proxima.
                        continue;
                    }
                    reenviar(destino, registro);
                    consumer.commitSync(Map.of(particion, new OffsetAndMetadata(registro.offset() + 1)));
                    reinyectados++;
                }
            }
        }
        log.info("Reinyectados {} mensajes desde {} hacia {}", reinyectados, dlt, destino);
        return reinyectados;
    }

    private boolean alcanzoElFinal(Consumer<String, byte[]> consumer, Map<TopicPartition, Long> fin) {
        return fin.entrySet().stream().allMatch(e -> consumer.position(e.getKey()) >= e.getValue());
    }

    private void reenviar(String destino, ConsumerRecord<String, byte[]> registro) {
        try {
            kafkaTemplate.send(destino, registro.key(), registro.value()).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Se interrumpio la reinyeccion", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Kafka no confirmo la reinyeccion del offset " + registro.offset(), e);
        }
    }

    private Map<String, Object> propiedadesConsumidor(int maximo) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        props.put(ConsumerConfig.GROUP_ID_CONFIG, topics.getGroupIds().get("reinyeccion-dlt"));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, Math.min(maximo, 500));
        return props;
    }
}

package cl.duoc.cafeteria.pagos.messaging.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topic de Kafka que produce ms-pagos (Fase 8). El broker tiene
 * KAFKA_AUTO_CREATE_TOPICS_ENABLE=false: los topics los crea la aplicacion
 * con estos beans al arrancar (KafkaAdmin), idempotente si ya existen.
 *
 * PRODUCE:
 *   cafeteria.pagos.transacciones (key = clienteEmail) --> ms-antifraude (Kafka Streams)
 */
@Configuration
@EnableConfigurationProperties(KafkaTopicsProperties.class)
public class KafkaConfig {

    @Bean
    public NewTopic transaccionesTopic(KafkaTopicsProperties props) {
        return TopicBuilder.name(props.getTopics().get("transacciones"))
                .partitions(props.getParticiones())
                .replicas(props.getReplicas())
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(props.getRetencionMs().get("transacciones")))
                .build();
    }
}

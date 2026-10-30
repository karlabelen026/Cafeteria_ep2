package cl.duoc.cafeteria.antifraude.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Configuracion de Kafka de ms-antifraude (Fase 8). Habilita Kafka Streams
 * (la topologia esta en streams/AntifraudeTopology) y declara los topics que
 * usa: el broker tiene KAFKA_AUTO_CREATE_TOPICS_ENABLE=false, asi que la
 * aplicacion los crea al arrancar (idempotente si ya existen).
 *
 * CONSUME (Kafka Streams, application-id cafeteria-antifraude):
 *   cafeteria.pagos.transacciones (key = cliente)  <-- ms-pagos
 * PRODUCE:
 *   cafeteria.antifraude.totales-por-cliente (compactado, key = cliente)
 *   alertas-fraude (key = cliente) --> ms-notificaciones y el monitor propio
 *
 * 3 particiones por topic (hasta 3 consumidores en paralelo por grupo), 3
 * replicas (una en cada broker) y min.insync.replicas=2: con acks=all se
 * tolera la caida de un broker sin perder datos ni dejar de escribir.
 */
@Configuration
@EnableKafkaStreams
@EnableConfigurationProperties({KafkaTopicsProperties.class, AntifraudeProperties.class})
public class KafkaStreamsConfig {

    @Bean
    public NewTopic transaccionesTopic(KafkaTopicsProperties props) {
        return TopicBuilder.name(props.topic("transacciones"))
                .partitions(props.getParticiones())
                .replicas(props.getReplicas())
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(props.getMinInsyncReplicas()))
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(props.getRetencionMs().get("transacciones")))
                .build();
    }

    /**
     * Compactado: Kafka conserva solo el ULTIMO total de cada cliente (la
     * key), no todo el historial de actualizaciones (guia 3.3.1).
     */
    @Bean
    public NewTopic totalesTopic(KafkaTopicsProperties props) {
        return TopicBuilder.name(props.topic("totales"))
                .partitions(props.getParticiones())
                .replicas(props.getReplicas())
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(props.getMinInsyncReplicas()))
                .compact()
                .build();
    }

    @Bean
    public NewTopic alertasFraudeTopic(KafkaTopicsProperties props) {
        return TopicBuilder.name(props.topic("alertas-fraude"))
                .partitions(props.getParticiones())
                .replicas(props.getReplicas())
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(props.getMinInsyncReplicas()))
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(props.getRetencionMs().get("alertas-fraude")))
                .build();
    }
}

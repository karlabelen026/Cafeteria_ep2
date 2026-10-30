package cl.duoc.cafeteria.kafkaadmin.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AdminClient de Kafka, encapsulado en KafkaAdminService: el controller no lo
 * conoce. Crearlo no exige que el broker este disponible; las llamadas que no
 * obtengan respuesta terminan en 503.
 */
@Configuration
@EnableConfigurationProperties(KafkaAdminProperties.class)
public class KafkaAdminClientConfig {

    @Bean(destroyMethod = "close")
    public AdminClient adminClient(KafkaProperties kafkaProperties) {
        return AdminClient.create(kafkaProperties.buildAdminProperties(null));
    }
}

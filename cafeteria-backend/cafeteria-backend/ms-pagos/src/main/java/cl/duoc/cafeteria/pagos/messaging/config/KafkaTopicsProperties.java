package cl.duoc.cafeteria.pagos.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapea el bloque app.kafka de application.yml (Fase 8). Unica fuente de
 * nombres de topics, particiones, replicas y retencion: nunca hay strings
 * sueltos en services ni productores. Se llama KafkaTopicsProperties para no
 * chocar con KafkaProperties de Spring Boot (spring.kafka.*).
 */
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaTopicsProperties {

    private Map<String, String> topics = new HashMap<>();
    private int particiones = 3;
    private int replicas = 1;
    private Map<String, Long> retencionMs = new HashMap<>();

    public Map<String, String> getTopics() {
        return topics;
    }

    public void setTopics(Map<String, String> topics) {
        this.topics = topics;
    }

    public int getParticiones() {
        return particiones;
    }

    public void setParticiones(int particiones) {
        this.particiones = particiones;
    }

    public int getReplicas() {
        return replicas;
    }

    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }

    public Map<String, Long> getRetencionMs() {
        return retencionMs;
    }

    public void setRetencionMs(Map<String, Long> retencionMs) {
        this.retencionMs = retencionMs;
    }
}

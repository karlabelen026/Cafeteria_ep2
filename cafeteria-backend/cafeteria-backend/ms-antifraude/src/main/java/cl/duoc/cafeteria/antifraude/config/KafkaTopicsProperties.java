package cl.duoc.cafeteria.antifraude.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapea el bloque app.kafka de application.yml (Fase 8): nombres de topics,
 * consumer groups, particiones, replicas y retencion. Unica fuente de esos
 * valores: nunca hay strings sueltos en la topologia, services ni listeners.
 * Se llama KafkaTopicsProperties para no chocar con KafkaProperties de
 * Spring Boot (spring.kafka.*).
 */
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaTopicsProperties {

    private Map<String, String> topics = new HashMap<>();
    private Map<String, String> groupIds = new HashMap<>();
    private int particiones = 3;
    private int replicas = 1;
    private Map<String, Long> retencionMs = new HashMap<>();

    public String topic(String clave) {
        return topics.get(clave);
    }

    public Map<String, String> getTopics() {
        return topics;
    }

    public void setTopics(Map<String, String> topics) {
        this.topics = topics;
    }

    public Map<String, String> getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(Map<String, String> groupIds) {
        this.groupIds = groupIds;
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

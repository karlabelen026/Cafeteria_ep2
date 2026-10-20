package cl.duoc.cafeteria.notificaciones.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Mapea el bloque app.kafka de application.yml (Fase 8): topics, consumer
 * groups, particiones, retencion y politica de reintentos. Unica fuente de
 * esos valores (nunca strings sueltos en listeners ni services). Se llama
 * KafkaTopicsProperties para no chocar con KafkaProperties de Spring Boot.
 */
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaTopicsProperties {

    private Map<String, String> topics = new HashMap<>();
    private Map<String, String> groupIds = new HashMap<>();
    private int particiones = 3;
    private int replicas = 1;
    private Map<String, Long> retencionMs = new HashMap<>();
    private Reintentos reintentos = new Reintentos();

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

    public Reintentos getReintentos() {
        return reintentos;
    }

    public void setReintentos(Reintentos reintentos) {
        this.reintentos = reintentos;
    }

    public static class Reintentos {
        private long intervaloInicialMs = 1000;
        private double multiplicador = 2.0;
        private int maximo = 3;

        public long getIntervaloInicialMs() {
            return intervaloInicialMs;
        }

        public void setIntervaloInicialMs(long intervaloInicialMs) {
            this.intervaloInicialMs = intervaloInicialMs;
        }

        public double getMultiplicador() {
            return multiplicador;
        }

        public void setMultiplicador(double multiplicador) {
            this.multiplicador = multiplicador;
        }

        public int getMaximo() {
            return maximo;
        }

        public void setMaximo(int maximo) {
            this.maximo = maximo;
        }
    }
}

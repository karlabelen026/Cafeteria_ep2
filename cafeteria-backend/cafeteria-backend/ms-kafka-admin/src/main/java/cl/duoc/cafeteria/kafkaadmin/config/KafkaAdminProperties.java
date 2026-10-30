package cl.duoc.cafeteria.kafkaadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapea el bloque app.kafka-admin de application.yml: tiempos de espera,
 * valores por defecto, topics protegidos y configuraciones modificables.
 * Unica fuente de esos valores: nunca hay strings sueltos en el service.
 */
@ConfigurationProperties(prefix = "app.kafka-admin")
public class KafkaAdminProperties {

    private long timeoutSegundos = 15;
    private int particionesPorDefecto = 3;
    private short replicasPorDefecto = 3;
    private List<String> protegidos = new ArrayList<>();
    private List<String> prefijosInternos = new ArrayList<>();
    private List<String> configsPermitidas = new ArrayList<>();

    public boolean esProtegido(String topic) {
        return protegidos.contains(topic);
    }

    public boolean esInterno(String topic) {
        return prefijosInternos.stream().anyMatch(topic::startsWith);
    }

    public long getTimeoutSegundos() {
        return timeoutSegundos;
    }

    public void setTimeoutSegundos(long timeoutSegundos) {
        this.timeoutSegundos = timeoutSegundos;
    }

    public int getParticionesPorDefecto() {
        return particionesPorDefecto;
    }

    public void setParticionesPorDefecto(int particionesPorDefecto) {
        this.particionesPorDefecto = particionesPorDefecto;
    }

    public short getReplicasPorDefecto() {
        return replicasPorDefecto;
    }

    public void setReplicasPorDefecto(short replicasPorDefecto) {
        this.replicasPorDefecto = replicasPorDefecto;
    }

    public List<String> getProtegidos() {
        return protegidos;
    }

    public void setProtegidos(List<String> protegidos) {
        this.protegidos = protegidos;
    }

    public List<String> getPrefijosInternos() {
        return prefijosInternos;
    }

    public void setPrefijosInternos(List<String> prefijosInternos) {
        this.prefijosInternos = prefijosInternos;
    }

    public List<String> getConfigsPermitidas() {
        return configsPermitidas;
    }

    public void setConfigsPermitidas(List<String> configsPermitidas) {
        this.configsPermitidas = configsPermitidas;
    }
}

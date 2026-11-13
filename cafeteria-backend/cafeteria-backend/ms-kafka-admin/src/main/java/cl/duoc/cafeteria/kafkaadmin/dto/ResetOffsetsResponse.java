package cl.duoc.cafeteria.kafkaadmin.dto;

import java.util.List;

/**
 * Resultado del reset: por cada particion, el offset que tenia el grupo
 * (null si nunca habia confirmado uno) y el nuevo.
 */
public record ResetOffsetsResponse(String groupId, String topic, String to, List<OffsetReseteado> particiones) {

    public record OffsetReseteado(int partition, Long offsetAnterior, long offsetNuevo) {
    }
}

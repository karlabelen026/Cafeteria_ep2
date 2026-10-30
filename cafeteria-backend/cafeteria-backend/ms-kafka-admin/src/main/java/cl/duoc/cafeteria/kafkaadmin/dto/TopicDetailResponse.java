package cl.duoc.cafeteria.kafkaadmin.dto;

import java.util.List;
import java.util.Map;

/** Detalle de un topic: sus particiones y su configuracion. */
public record TopicDetailResponse(
        String name,
        boolean internal,
        boolean protegido,
        int replicationFactor,
        List<PartitionResponse> partitions,
        Map<String, String> configs) {
}

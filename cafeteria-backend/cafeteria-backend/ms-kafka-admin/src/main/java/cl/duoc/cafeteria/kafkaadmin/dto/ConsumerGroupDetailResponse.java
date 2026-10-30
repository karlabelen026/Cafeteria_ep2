package cl.duoc.cafeteria.kafkaadmin.dto;

import java.util.List;

/** Detalle de un consumer group: sus miembros, offsets y lag total. */
public record ConsumerGroupDetailResponse(
        String groupId,
        String state,
        String assignor,
        Integer coordinator,
        List<GroupMemberResponse> members,
        List<PartitionLagResponse> offsets,
        long totalLag) {
}

package cl.duoc.cafeteria.kafkaadmin.dto;

import java.util.List;

/** Un consumidor del grupo y las particiones que tiene asignadas ("topic-particion"). */
public record GroupMemberResponse(String memberId, String clientId, String host, List<String> assignments) {
}

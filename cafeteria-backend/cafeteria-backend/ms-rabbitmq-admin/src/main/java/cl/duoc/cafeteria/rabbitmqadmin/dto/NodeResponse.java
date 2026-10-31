package cl.duoc.cafeteria.rabbitmqadmin.dto;

import cl.duoc.cafeteria.rabbitmqadmin.management.ManagementNodeDto;

/** Un nodo del cluster. colasLider cuenta las colas quorum que este nodo lidera ahora. */
public record NodeResponse(String name, boolean running, long memoriaUsada, long memoriaLimite,
        boolean alarmaMemoria, boolean alarmaDisco, long uptimeMs, long colasLider) {

    public static NodeResponse desde(ManagementNodeDto dto, long colasLider) {
        return new NodeResponse(
                dto.name(),
                Boolean.TRUE.equals(dto.running()),
                dto.memUsed() != null ? dto.memUsed() : 0,
                dto.memLimit() != null ? dto.memLimit() : 0,
                Boolean.TRUE.equals(dto.memAlarm()),
                Boolean.TRUE.equals(dto.diskFreeAlarm()),
                dto.uptime() != null ? dto.uptime() : 0,
                colasLider);
    }
}

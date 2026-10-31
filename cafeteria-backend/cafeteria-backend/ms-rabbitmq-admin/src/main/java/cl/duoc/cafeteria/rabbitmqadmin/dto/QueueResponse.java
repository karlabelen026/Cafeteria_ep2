package cl.duoc.cafeteria.rabbitmqadmin.dto;

import cl.duoc.cafeteria.rabbitmqadmin.management.ManagementQueueDto;

/**
 * messagesReady y messagesUnacknowledged son lo que hay en la cola ahora y casi
 * siempre valen 0, porque los consumidores procesan al instante: el movimiento
 * se ve en la actividad acumulada.
 */
public record QueueResponse(String name, String type, long messagesReady, long messagesUnacknowledged,
        int consumers, String leader, ActividadResponse actividad) {

    public QueueResponse(String name, String type, long messagesReady, long messagesUnacknowledged, int consumers) {
        this(name, type, messagesReady, messagesUnacknowledged, consumers, null, ActividadResponse.desde(null));
    }

    public static QueueResponse desde(ManagementQueueDto dto) {
        return new QueueResponse(
                dto.name(),
                dto.type(),
                dto.messagesReady() != null ? dto.messagesReady() : 0,
                dto.messagesUnacknowledged() != null ? dto.messagesUnacknowledged() : 0,
                dto.consumers() != null ? dto.consumers() : 0,
                dto.leader(),
                ActividadResponse.desde(dto.messageStats()));
    }
}

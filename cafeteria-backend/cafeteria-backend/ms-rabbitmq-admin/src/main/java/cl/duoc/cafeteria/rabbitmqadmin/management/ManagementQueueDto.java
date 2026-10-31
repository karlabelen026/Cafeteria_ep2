package cl.duoc.cafeteria.rabbitmqadmin.management;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** Forma minima de lo que devuelve GET /api/queues de la API de management de RabbitMQ. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ManagementQueueDto(
        String name,
        String type,
        @JsonProperty("messages_ready") Long messagesReady,
        @JsonProperty("messages_unacknowledged") Long messagesUnacknowledged,
        Integer consumers,
        Map<String, Object> arguments,
        String leader,
        @JsonProperty("message_stats") ManagementMessageStatsDto messageStats) {
}

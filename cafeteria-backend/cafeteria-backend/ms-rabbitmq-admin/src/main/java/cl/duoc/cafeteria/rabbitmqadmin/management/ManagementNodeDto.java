package cl.duoc.cafeteria.rabbitmqadmin.management;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Forma minima de lo que devuelve GET /api/nodes de la API de management de
 * RabbitMQ. Un nodo caido sigue en la lista con running=false y sin metricas.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ManagementNodeDto(
        String name,
        Boolean running,
        String type,
        @JsonProperty("mem_used") Long memUsed,
        @JsonProperty("mem_limit") Long memLimit,
        @JsonProperty("mem_alarm") Boolean memAlarm,
        @JsonProperty("disk_free_alarm") Boolean diskFreeAlarm,
        Long uptime) {
}

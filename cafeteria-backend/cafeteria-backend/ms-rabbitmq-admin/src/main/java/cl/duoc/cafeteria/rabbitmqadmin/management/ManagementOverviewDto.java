package cl.duoc.cafeteria.rabbitmqadmin.management;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Forma minima de lo que devuelve GET /api/overview de la API de management de RabbitMQ. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ManagementOverviewDto(
        @JsonProperty("message_stats") ManagementMessageStatsDto messageStats,
        @JsonProperty("object_totals") Totales objectTotals) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Totales(Integer connections, Integer channels, Integer queues, Integer consumers) {
    }
}

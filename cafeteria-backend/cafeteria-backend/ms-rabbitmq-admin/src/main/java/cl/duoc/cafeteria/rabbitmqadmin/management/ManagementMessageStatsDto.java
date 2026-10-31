package cl.duoc.cafeteria.rabbitmqadmin.management;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Bloque message_stats de la API de management: contadores acumulados y su tasa por segundo. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ManagementMessageStatsDto(
        Long publish,
        @JsonProperty("publish_details") Detalle publishDetails,
        @JsonProperty("deliver_get") Long deliverGet,
        @JsonProperty("deliver_get_details") Detalle deliverGetDetails,
        Long ack,
        @JsonProperty("ack_details") Detalle ackDetails) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detalle(Double rate) {
    }
}

package cl.duoc.cafeteria.kafkaadmin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Nuevo TOTAL de particiones del topic (Kafka solo permite aumentarlas). */
public record AddPartitionsRequest(
        @NotNull(message = "total es obligatorio")
        @Min(value = 2, message = "total debe ser al menos 2")
        @Max(value = 50, message = "total no puede superar 50")
        Integer total) {
}

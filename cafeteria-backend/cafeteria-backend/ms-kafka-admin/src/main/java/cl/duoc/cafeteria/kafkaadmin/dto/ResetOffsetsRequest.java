package cl.duoc.cafeteria.kafkaadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Hacia donde mover los offsets de un consumer group en un topic: al inicio
 * (EARLIEST, para volver a procesar todo lo retenido), al final (LATEST, para
 * saltarse lo pendiente) o al primer mensaje desde "timestamp" (TIMESTAMP,
 * obligatorio solo en ese caso, en formato ISO-8601).
 */
public record ResetOffsetsRequest(
        @NotBlank(message = "El topic es obligatorio")
        String topic,

        @NotNull(message = "Indica a donde mover los offsets: EARLIEST, LATEST o TIMESTAMP")
        DestinoOffset to,

        Instant timestamp) {

    public enum DestinoOffset {
        EARLIEST, LATEST, TIMESTAMP
    }
}

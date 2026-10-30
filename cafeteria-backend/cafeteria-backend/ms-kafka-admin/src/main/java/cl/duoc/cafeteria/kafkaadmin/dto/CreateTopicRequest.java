package cl.duoc.cafeteria.kafkaadmin.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Datos para crear un topic. Solo el nombre es obligatorio: particiones y
 * replicas usan los valores por defecto de app.kafka-admin, y la limpieza es
 * "delete". El maximo de 3 replicas es porque el cluster tiene 3 brokers.
 */
public record CreateTopicRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Pattern(regexp = "^(?!__)[a-zA-Z0-9._-]{3,120}$",
                message = "El nombre debe tener entre 3 y 120 caracteres (letras, numeros, . _ o -) y no puede empezar con '__' (reservado)")
        String name,

        @Min(value = 1, message = "partitions debe ser al menos 1")
        @Max(value = 50, message = "partitions no puede superar 50")
        Integer partitions,

        @Min(value = 1, message = "replicationFactor debe ser al menos 1")
        @Max(value = 3, message = "replicationFactor no puede superar 3 (el cluster tiene 3 brokers)")
        Short replicationFactor,

        @Min(value = 60000, message = "retentionMs debe ser al menos 60000 (1 minuto)")
        Long retentionMs,

        @Pattern(regexp = "delete|compact", message = "cleanupPolicy debe ser delete o compact")
        String cleanupPolicy,

        @Min(value = 1, message = "minInsyncReplicas debe ser al menos 1")
        @Max(value = 3, message = "minInsyncReplicas no puede superar 3")
        Integer minInsyncReplicas) {

    /** Con mas min.insync.replicas que replicas, ninguna escritura con acks=all podria confirmarse. */
    @JsonIgnore
    @AssertTrue(message = "minInsyncReplicas no puede ser mayor que replicationFactor")
    public boolean isMinInsyncCoherente() {
        return minInsyncReplicas == null || replicationFactor == null || minInsyncReplicas <= replicationFactor;
    }
}

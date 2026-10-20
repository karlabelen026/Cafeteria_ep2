package cl.duoc.cafeteria.common.evento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/**
 * Alerta de posible fraude que emite la topologia de Kafka Streams de
 * ms-antifraude al topic alertas-fraude (key = cliente). La consumen
 * ms-notificaciones (la muestra en la pagina de Alertas del dashboard) y el
 * propio ms-antifraude (ultimas alertas para su vista). Las anotaciones de
 * validacion las aplica cada consumidor con @Valid: un payload invalido va
 * directo a la DLT, sin reintentos.
 */
public record AlertaFraudeEvent(
        @NotNull(message = "eventId es obligatorio")
        UUID eventId,

        @NotNull(message = "detectadaEn es obligatorio")
        Instant detectadaEn,

        @NotBlank(message = "cliente es obligatorio")
        String cliente,

        @NotBlank(message = "severidad es obligatoria")
        @Pattern(regexp = "ALTA|MEDIA", message = "severidad debe ser ALTA o MEDIA")
        String severidad,

        @NotBlank(message = "motivo es obligatorio")
        String motivo,

        double monto,
        double montoUsd,
        String metodoPago,
        Long pagoId) {

    public static final String SEVERIDAD_ALTA = "ALTA";
    public static final String SEVERIDAD_MEDIA = "MEDIA";
}

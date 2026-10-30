package cl.duoc.cafeteria.kafkaadmin.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

/**
 * Configuraciones a cambiar, p.ej. {"retention.ms":"86400000"}. Un valor null
 * vuelve esa configuracion al valor por defecto del broker. Solo se aceptan
 * las de app.kafka-admin.configs-permitidas.
 */
public record UpdateTopicConfigRequest(
        @NotEmpty(message = "Debes indicar al menos una configuracion")
        Map<String, String> configs) {
}

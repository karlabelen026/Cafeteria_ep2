package cl.duoc.cafeteria.notificaciones.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MensajeChatRequest(
        @NotBlank @Size(max = 80) String autor,
        @NotBlank @Size(max = 30) String rol,
        @NotBlank @Size(max = 500) String texto) {
}

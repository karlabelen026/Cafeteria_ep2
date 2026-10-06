package cl.duoc.cafeteria.common.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Publicado por ms-notificaciones (cafeteria.chat.exchange, fanout: sin
 * routing key) cuando un miembro del equipo envia un mensaje al chat
 * interno. El mismo ms-notificaciones lo consume de vuelta (notificaciones.chat.queue)
 * para persistirlo y que el resto del equipo lo vea al hacer polling (ver
 * ChatController).
 */
public record MensajeChatEvent(
        UUID eventId,
        Instant ocurridoEn,
        int version,
        String autor,
        String rol,
        String texto) {
}

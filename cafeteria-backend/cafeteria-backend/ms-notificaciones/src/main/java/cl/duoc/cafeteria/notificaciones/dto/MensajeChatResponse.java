package cl.duoc.cafeteria.notificaciones.dto;

import cl.duoc.cafeteria.notificaciones.model.MensajeChat;

import java.time.Instant;

public record MensajeChatResponse(Long id, String autor, String rol, String texto, Instant creadoEn) {

    public static MensajeChatResponse desde(MensajeChat mensaje) {
        return new MensajeChatResponse(
                mensaje.getId(), mensaje.getAutor(), mensaje.getRol(), mensaje.getTexto(), mensaje.getCreadoEn());
    }
}

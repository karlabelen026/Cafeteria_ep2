package cl.duoc.cafeteria.notificaciones.service;

import cl.duoc.cafeteria.common.evento.MensajeChatEvent;
import cl.duoc.cafeteria.notificaciones.dto.MensajeChatRequest;
import cl.duoc.cafeteria.notificaciones.dto.MensajeChatResponse;

import java.time.Instant;
import java.util.List;

public interface ChatService {

    // Publica el mensaje en RabbitMQ (no lo persiste directo: lo persiste
    // ChatListener al consumirlo de vuelta, ver docs/EP2_PLAN.md seccion 3.7).
    void enviar(MensajeChatRequest request);

    // Llamado solo por ChatListener al consumir el evento.
    void guardar(MensajeChatEvent evento);

    // Sin "desde": los ultimos 50 mensajes (carga inicial del chat).
    List<MensajeChatResponse> historial();

    // Con "desde": solo los mensajes nuevos (polling periodico del frontend).
    List<MensajeChatResponse> desde(Instant desde);
}

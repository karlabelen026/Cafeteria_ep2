package cl.duoc.cafeteria.notificaciones.messaging.producer;

import cl.duoc.cafeteria.common.evento.MensajeChatEvent;

/**
 * Productor del evento de mensaje del chat interno. ChatService depende de
 * esta interfaz, nunca de RabbitTemplate directamente (ver docs/EP2_PLAN.md
 * seccion 3.7).
 */
public interface ChatEventPublisher {

    void publicar(MensajeChatEvent evento);
}

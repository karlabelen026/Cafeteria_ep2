package cl.duoc.cafeteria.notificaciones.messaging.producer;

import cl.duoc.cafeteria.common.evento.MensajeChatEvent;
import cl.duoc.cafeteria.notificaciones.messaging.config.RabbitProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica cada mensaje del chat en cafeteria.chat.exchange (fanout: sin
 * routing key, lo recibe cualquier cola enlazada — hoy solo
 * notificaciones.chat.queue, pero permite agregar mas consumidores despues
 * sin tocar este publicador).
 */
@Component
public class RabbitChatEventPublisher implements ChatEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitProperties props;

    public RabbitChatEventPublisher(RabbitTemplate rabbitTemplate, RabbitProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    @Override
    public void publicar(MensajeChatEvent evento) {
        rabbitTemplate.convertAndSend(props.getExchanges().get("chat"), "", evento);
    }
}

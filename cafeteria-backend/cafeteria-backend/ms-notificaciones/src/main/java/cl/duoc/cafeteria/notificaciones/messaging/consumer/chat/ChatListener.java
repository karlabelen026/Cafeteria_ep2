package cl.duoc.cafeteria.notificaciones.messaging.consumer.chat;

import cl.duoc.cafeteria.common.evento.MensajeChatEvent;
import cl.duoc.cafeteria.common.exception.NonRecoverableMessageException;
import cl.duoc.cafeteria.common.exception.RecoverableMessageException;
import cl.duoc.cafeteria.common.messaging.AckHandler;
import cl.duoc.cafeteria.notificaciones.model.EventoProcesado;
import cl.duoc.cafeteria.notificaciones.repository.EventoProcesadoRepository;
import cl.duoc.cafeteria.notificaciones.service.ChatService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Consume notificaciones.chat.queue (cafeteria.chat.exchange, fanout): el
 * mismo ms-notificaciones que publica el mensaje (ver ChatController) lo
 * recibe de vuelta y recien aqui lo persiste — asi el "enviar" pasa de
 * verdad por RabbitMQ en vez de guardar directo en la base de datos.
 */
@Component
@RabbitListener(queues = "${app.rabbitmq.queues.chat}")
public class ChatListener {

    private final ChatService chatService;
    private final EventoProcesadoRepository eventoProcesadoRepository;

    public ChatListener(ChatService chatService, EventoProcesadoRepository eventoProcesadoRepository) {
        this.chatService = chatService;
        this.eventoProcesadoRepository = eventoProcesadoRepository;
    }

    @org.springframework.amqp.rabbit.annotation.RabbitHandler
    public void recibir(MensajeChatEvent evento, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            if (eventoProcesadoRepository.existsById(evento.eventId())) {
                AckHandler.ack(channel, deliveryTag);
                return;
            }

            chatService.guardar(evento);
            eventoProcesadoRepository.save(new EventoProcesado(evento.eventId(), Instant.now()));
            AckHandler.ack(channel, deliveryTag);
        } catch (NonRecoverableMessageException e) {
            AckHandler.nackSinReintento(channel, deliveryTag, e);
        } catch (RecoverableMessageException e) {
            AckHandler.nackConReintento(channel, deliveryTag, e);
        } catch (Exception e) {
            AckHandler.nackConReintento(channel, deliveryTag, e);
        }
    }
}

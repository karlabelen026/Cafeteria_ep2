package cl.duoc.cafeteria.notificaciones.messaging.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rutas de mensajeria de ms-notificaciones (ver docs/EP2_PLAN.md seccion 3).
 * Todos los nombres de exchanges, routing keys y colas vienen del bloque
 * app.rabbitmq de application.yml, mapeado por RabbitProperties.
 *
 * CONSUME:
 *   cafeteria.pagos.exchange (direct) --[pago.aprobado]--> notificaciones.ticket.queue
 *     -> TicketListener (genera el ticket; fallidos sin reintento -> notificaciones.ticket.queue.dlq)
 *   cafeteria.inventario.exchange (direct) --[stock.bajo]--------------\
 *   cafeteria.pedidos.exchange   (topic)  --[pedido.estado.actualizado]-+--> notificaciones.alertas.queue
 *     -> AlertaListener (alerta STOCK_BAJO / PEDIDO_LISTO; fallidos sin reintento -> notificaciones.alertas.queue.dlq)
 *
 * PUBLICA Y CONSUME (chat interno del equipo, ver docs/EP2_PLAN.md seccion 6.2):
 *   cafeteria.chat.exchange (fanout, sin routing key) --> notificaciones.chat.queue
 *     -> ChatListener (persiste el mensaje; fallidos sin reintento -> notificaciones.chat.queue.dlq)
 */
@Configuration
@EnableConfigurationProperties(RabbitProperties.class)
public class RabbitMQConfig {

    private final RabbitProperties props;

    public RabbitMQConfig(RabbitProperties props) {
        this.props = props;
    }

    @Bean
    public DirectExchange pagosExchange() {
        return new DirectExchange(props.getExchanges().get("pagos"), true, false);
    }

    @Bean
    public DirectExchange inventarioExchange() {
        return new DirectExchange(props.getExchanges().get("inventario"), true, false);
    }

    @Bean
    public TopicExchange pedidosExchange() {
        return new TopicExchange(props.getExchanges().get("pedidos"), true, false);
    }

    @Bean
    public DirectExchange dlx() {
        return new DirectExchange(props.getExchanges().get("dlx"), true, false);
    }

    @Bean
    public Queue ticketQueue() {
        return colaPrincipal("ticket");
    }

    @Bean
    public Queue ticketDlq() {
        return colaDlq("ticket");
    }

    @Bean
    public Binding ticketBinding() {
        return BindingBuilder.bind(ticketQueue()).to(pagosExchange())
                .with(props.getRoutingKeys().get("pago-aprobado"));
    }

    @Bean
    public Binding ticketDlqBinding() {
        return BindingBuilder.bind(ticketDlq()).to(dlx())
                .with(props.getQueues().get("ticket") + ".dlq");
    }

    @Bean
    public Queue alertasQueue() {
        return colaPrincipal("alertas");
    }

    @Bean
    public Queue alertasDlq() {
        return colaDlq("alertas");
    }

    @Bean
    public Binding stockBajoBinding() {
        return BindingBuilder.bind(alertasQueue()).to(inventarioExchange())
                .with(props.getRoutingKeys().get("stock-bajo"));
    }

    @Bean
    public Binding pedidoEstadoActualizadoBinding() {
        return BindingBuilder.bind(alertasQueue()).to(pedidosExchange())
                .with(props.getRoutingKeys().get("pedido-estado-actualizado"));
    }

    @Bean
    public Binding alertasDlqBinding() {
        return BindingBuilder.bind(alertasDlq()).to(dlx())
                .with(props.getQueues().get("alertas") + ".dlq");
    }

    @Bean
    public FanoutExchange chatExchange() {
        return new FanoutExchange(props.getExchanges().get("chat"), true, false);
    }

    @Bean
    public Queue chatQueue() {
        return colaPrincipal("chat");
    }

    @Bean
    public Queue chatDlq() {
        return colaDlq("chat");
    }

    @Bean
    public Binding chatBinding() {
        return BindingBuilder.bind(chatQueue()).to(chatExchange());
    }

    @Bean
    public Binding chatDlqBinding() {
        return BindingBuilder.bind(chatDlq()).to(dlx())
                .with(props.getQueues().get("chat") + ".dlq");
    }

    private Queue colaPrincipal(String clave) {
        String nombre = props.getQueues().get(clave);
        return QueueBuilder.durable(nombre)
                .quorum()
                .deadLetterExchange(props.getExchanges().get("dlx"))
                .deadLetterRoutingKey(nombre + ".dlq")
                .deliveryLimit(props.getPolicies().getDeliveryLimit())
                .ttl((int) props.getPolicies().getMessageTtlMs())
                .maxLength(props.getPolicies().getMaxLength())
                .build();
    }

    private Queue colaDlq(String clave) {
        String nombre = props.getQueues().get(clave) + ".dlq";
        return QueueBuilder.durable(nombre)
                .quorum()
                .ttl((int) props.getPolicies().getDlqTtlMs())
                .maxLength(props.getPolicies().getMaxLength())
                .build();
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        Logger log = LoggerFactory.getLogger(RabbitTemplate.class);
        template.setMandatory(true);
        template.setConfirmCallback((correlation, ack, cause) -> {
            if (!ack) {
                log.error("RabbitMQ no confirmo el mensaje (publisher confirm), causa: {}", cause);
            }
        });
        template.setReturnsCallback(returned ->
                log.error("Mensaje no enrutable: exchange={}, routingKey={}, respuesta={}",
                        returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        return template;
    }
}

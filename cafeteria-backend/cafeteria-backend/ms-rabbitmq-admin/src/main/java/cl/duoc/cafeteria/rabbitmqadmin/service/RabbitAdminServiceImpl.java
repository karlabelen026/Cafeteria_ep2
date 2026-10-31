package cl.duoc.cafeteria.rabbitmqadmin.service;

import cl.duoc.cafeteria.rabbitmqadmin.config.RabbitAdminProperties;
import cl.duoc.cafeteria.rabbitmqadmin.dto.*;
import cl.duoc.cafeteria.rabbitmqadmin.exception.ConflictoDeNegocioException;
import cl.duoc.cafeteria.rabbitmqadmin.exception.RecursoNoEncontradoException;
import cl.duoc.cafeteria.rabbitmqadmin.management.ManagementClient;
import cl.duoc.cafeteria.rabbitmqadmin.management.ManagementMessageDto;
import cl.duoc.cafeteria.rabbitmqadmin.management.ManagementQueueDto;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementa toda la logica de administracion de RabbitMQ (ver
 * docs/EP2_PLAN.md seccion 3.8): RabbitAdmin (AMQP) para crear/eliminar/
 * purgar, y ManagementClient (API HTTP de management) para listar y ver el
 * estado del cluster.
 */
@Service
public class RabbitAdminServiceImpl implements RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminServiceImpl.class);
    private static final String SUFIJO_DLQ = ".dlq";

    private final RabbitAdmin rabbitAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final ManagementClient managementClient;
    private final RabbitAdminProperties props;

    public RabbitAdminServiceImpl(RabbitAdmin rabbitAdmin, RabbitTemplate rabbitTemplate,
            ManagementClient managementClient, RabbitAdminProperties props) {
        this.rabbitAdmin = rabbitAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.managementClient = managementClient;
        this.props = props;
    }

    @Override
    public List<QueueResponse> listarColas() {
        return managementClient.listarColas().stream().map(QueueResponse::desde).toList();
    }

    @Override
    public QueueResponse obtenerCola(String nombre) {
        return managementClient.obtenerCola(nombre)
                .map(QueueResponse::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la cola " + nombre));
    }

    @Override
    public QueueResponse crearCola(CreateQueueRequest request) {
        if (managementClient.obtenerCola(request.name()).isPresent()) {
            throw new ConflictoDeNegocioException("Ya existe una cola con el nombre " + request.name());
        }

        QueueBuilder builder = QueueBuilder.durable(request.name()).quorum();
        if (request.ttlMs() != null) {
            builder.ttl(request.ttlMs().intValue());
        }
        if (request.maxLength() != null) {
            builder.maxLength(request.maxLength());
        }
        if (request.deliveryLimit() != null) {
            builder.deliveryLimit(request.deliveryLimit());
        }
        Queue queue = builder.build();
        rabbitAdmin.declareQueue(queue);

        return new QueueResponse(request.name(), "quorum", 0, 0, 0);
    }

    @Override
    public void eliminarCola(String nombre, boolean ifUnused, boolean ifEmpty) {
        exigirNoProtegido(nombre);
        exigirColaExiste(nombre);
        try {
            rabbitAdmin.deleteQueue(nombre, ifUnused, ifEmpty);
        } catch (AmqpException e) {
            throw new ConflictoDeNegocioException(
                    "No se pudo eliminar la cola " + nombre + ": revise si tiene consumidores o mensajes "
                            + "pendientes (ifUnused/ifEmpty)");
        }
    }

    @Override
    public PurgeResponse purgarCola(String nombre) {
        exigirNoProtegido(nombre);
        exigirColaExiste(nombre);
        int purgados = rabbitAdmin.purgeQueue(nombre);
        return new PurgeResponse(nombre, purgados);
    }

    @Override
    public List<ExchangeResponse> listarExchanges() {
        return managementClient.listarExchanges().stream().map(ExchangeResponse::desde).toList();
    }

    @Override
    public ExchangeResponse crearExchange(CreateExchangeRequest request) {
        if (managementClient.obtenerExchange(request.name()).isPresent()) {
            throw new ConflictoDeNegocioException("Ya existe un exchange con el nombre " + request.name());
        }

        Exchange exchange = construirExchange(request.name(), request.type());
        rabbitAdmin.declareExchange(exchange);

        return new ExchangeResponse(request.name(), request.type(), true);
    }

    @Override
    public void eliminarExchange(String nombre) {
        exigirNoProtegido(nombre);
        if (managementClient.obtenerExchange(nombre).isEmpty()) {
            throw new RecursoNoEncontradoException("No existe el exchange " + nombre);
        }
        rabbitAdmin.deleteExchange(nombre);
    }

    @Override
    public List<BindingResponse> listarBindings() {
        return managementClient.listarBindings().stream().map(BindingResponse::desde).toList();
    }

    @Override
    public BindingResponse crearBinding(BindingRequest request) {
        exigirColaExiste(request.queue());
        exigirExchangeExiste(request.exchange());

        String routingKey = request.routingKey() != null ? request.routingKey() : "";
        Binding binding = new Binding(request.queue(), Binding.DestinationType.QUEUE,
                request.exchange(), routingKey, Map.of());
        rabbitAdmin.declareBinding(binding);

        return new BindingResponse(request.exchange(), request.queue(), "queue", routingKey);
    }

    @Override
    public void eliminarBinding(BindingRequest request) {
        exigirColaExiste(request.queue());
        exigirExchangeExiste(request.exchange());

        String routingKey = request.routingKey() != null ? request.routingKey() : "";
        Binding binding = new Binding(request.queue(), Binding.DestinationType.QUEUE,
                request.exchange(), routingKey, Map.of());
        rabbitAdmin.removeBinding(binding);
    }

    @Override
    public List<DlqResumenResponse> resumenDlq() {
        return props.getProtegidos().stream()
                .filter(nombre -> nombre.endsWith(SUFIJO_DLQ))
                .map(nombre -> new DlqResumenResponse(nombre, mensajesListos(nombre)))
                .toList();
    }

    @Override
    public ReprocessResponse reprocesarDlq(String nombreDlq, int max) {
        exigirColaExiste(nombreDlq);

        List<ManagementMessageDto> mensajes = managementClient.leerMensajes(nombreDlq, max);
        int reprocesados = 0;
        for (ManagementMessageDto mensaje : mensajes) {
            Map<String, Object> origen = origenDesdeXDeath(mensaje);
            if (origen == null) {
                log.warn("Mensaje de {} sin header x-death utilizable, se deja fuera del reproceso", nombreDlq);
                continue;
            }
            String exchangeOrigen = String.valueOf(origen.get("exchange"));
            String routingKeyOrigen = primeraRoutingKey(origen);
            rabbitTemplate.send(exchangeOrigen, routingKeyOrigen, construirMensaje(mensaje));
            reprocesados++;
        }
        return new ReprocessResponse(nombreDlq, reprocesados);
    }

    @Override
    public ClusterResponse estadoCluster() {
        Map<String, Long> colasPorLider = managementClient.listarColas().stream()
                .filter(cola -> cola.leader() != null)
                .collect(Collectors.groupingBy(ManagementQueueDto::leader, Collectors.counting()));
        return new ClusterResponse(managementClient.listarNodos().stream()
                .map(nodo -> NodeResponse.desde(nodo, colasPorLider.getOrDefault(nodo.name(), 0L)))
                .toList());
    }

    @Override
    public ResumenActividadResponse resumenActividad() {
        return ResumenActividadResponse.desde(managementClient.obtenerResumen());
    }

    // ---- privados ----

    private void exigirNoProtegido(String nombre) {
        if (props.esProtegido(nombre)) {
            throw new ConflictoDeNegocioException(
                    "'" + nombre + "' es un recurso del sistema protegido: no se puede eliminar ni purgar");
        }
    }

    private void exigirColaExiste(String nombre) {
        if (managementClient.obtenerCola(nombre).isEmpty()) {
            throw new RecursoNoEncontradoException("No existe la cola " + nombre);
        }
    }

    private void exigirExchangeExiste(String nombre) {
        if (managementClient.obtenerExchange(nombre).isEmpty()) {
            throw new RecursoNoEncontradoException("No existe el exchange " + nombre);
        }
    }

    private long mensajesListos(String nombreCola) {
        return managementClient.obtenerCola(nombreCola).map(ManagementQueueDto::messagesReady).orElse(0L);
    }

    private Exchange construirExchange(String name, String type) {
        return switch (type) {
            case "direct" -> ExchangeBuilder.directExchange(name).durable(true).build();
            case "topic" -> ExchangeBuilder.topicExchange(name).durable(true).build();
            case "fanout" -> ExchangeBuilder.fanoutExchange(name).durable(true).build();
            case "headers" -> ExchangeBuilder.headersExchange(name).durable(true).build();
            default -> throw new ConflictoDeNegocioException("Tipo de exchange invalido: " + type);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> origenDesdeXDeath(ManagementMessageDto mensaje) {
        if (mensaje.properties() == null) {
            return null;
        }
        Object headersObj = mensaje.properties().get("headers");
        if (!(headersObj instanceof Map<?, ?> headers)) {
            return null;
        }
        Object xDeathObj = headers.get("x-death");
        if (!(xDeathObj instanceof List<?> xDeathList) || xDeathList.isEmpty()) {
            return null;
        }
        Object primero = xDeathList.get(0);
        return primero instanceof Map<?, ?> mapa ? (Map<String, Object>) mapa : null;
    }

    private String primeraRoutingKey(Map<String, Object> entradaXDeath) {
        Object routingKeys = entradaXDeath.get("routing-keys");
        if (routingKeys instanceof List<?> lista && !lista.isEmpty()) {
            return String.valueOf(lista.get(0));
        }
        return "";
    }

    private Message construirMensaje(ManagementMessageDto mensaje) {
        byte[] payload = "base64".equals(mensaje.payloadEncoding())
                ? Base64.getDecoder().decode(mensaje.payload())
                : mensaje.payload().getBytes(StandardCharsets.UTF_8);
        return new Message(payload, new MessageProperties());
    }
}

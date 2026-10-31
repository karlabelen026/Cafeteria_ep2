package cl.duoc.cafeteria.rabbitmqadmin.management;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Encapsula las llamadas a la API HTTP de administracion de RabbitMQ (puerto
 * 15672, ver docs/EP2_PLAN.md seccion 3.8). Solo lectura: listar colas,
 * exchanges, bindings, nodos del cluster, y leer mensajes de una cola (usado
 * para reprocesar la DLQ). Crear/eliminar se hace por AMQP con RabbitAdmin,
 * no aqui (ver RabbitAdminServiceImpl).
 */
@Component
public class ManagementClient {

    // Vhost por defecto de RabbitMQ ("/"). Se pasa sin codificar: RestClient
    // ya hace el URL-encoding del placeholder {vhost} (si aqui se pasara ya
    // codificado como "%2F", RestClient lo codificaria de nuevo a "%252F" y
    // la API de management responde 404 "Object Not Found").
    private static final String VHOST = "/";

    private final RestClient restClient;

    public ManagementClient(RestClient rabbitManagementRestClient) {
        this.restClient = rabbitManagementRestClient;
    }

    public List<ManagementQueueDto> listarColas() {
        return restClient.get().uri("/api/queues/{vhost}", VHOST)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ManagementQueueDto>>() {
                });
    }

    public Optional<ManagementQueueDto> obtenerCola(String nombre) {
        try {
            return Optional.ofNullable(restClient.get().uri("/api/queues/{vhost}/{nombre}", VHOST, nombre)
                    .retrieve()
                    .body(ManagementQueueDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public List<ManagementExchangeDto> listarExchanges() {
        return restClient.get().uri("/api/exchanges/{vhost}", VHOST)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ManagementExchangeDto>>() {
                });
    }

    public Optional<ManagementExchangeDto> obtenerExchange(String nombre) {
        try {
            return Optional.ofNullable(restClient.get().uri("/api/exchanges/{vhost}/{nombre}", VHOST, nombre)
                    .retrieve()
                    .body(ManagementExchangeDto.class));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        }
    }

    public List<ManagementBindingDto> listarBindings() {
        return restClient.get().uri("/api/bindings/{vhost}", VHOST)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ManagementBindingDto>>() {
                });
    }

    public List<ManagementNodeDto> listarNodos() {
        return restClient.get().uri("/api/nodes")
                .retrieve()
                .body(new ParameterizedTypeReference<List<ManagementNodeDto>>() {
                });
    }

    public ManagementOverviewDto obtenerResumen() {
        return restClient.get().uri("/api/overview")
                .retrieve()
                .body(ManagementOverviewDto.class);
    }

    /** Lee (y saca, ackmode=ack_requeue_false) hasta "max" mensajes de una cola. */
    public List<ManagementMessageDto> leerMensajes(String nombreCola, int max) {
        Map<String, Object> body = new HashMap<>();
        body.put("count", max);
        body.put("ackmode", "ack_requeue_false");
        body.put("encoding", "auto");
        body.put("truncate", 50000);
        return restClient.post().uri("/api/queues/{vhost}/{nombre}/get", VHOST, nombreCola)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ManagementMessageDto>>() {
                });
    }
}

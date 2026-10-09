package cl.duoc.cafeteria.notificaciones.client;

import cl.duoc.cafeteria.common.exception.NonRecoverableMessageException;
import cl.duoc.cafeteria.common.exception.RecoverableMessageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cliente HTTP hacia ms-pedidos para obtener el detalle (items, codigo de
 * seguimiento, cliente) de un pedido pagado y armar su boleta.
 *
 * Es una llamada servicio-a-servicio (la dispara un listener de RabbitMQ, sin
 * usuario logueado): usa el endpoint interno /internal/pedidos/{id} de
 * ms-pedidos y se autentica con el secreto compartido X-Internal-Token.
 */
@Component
public class PedidoClient {

    private static final String HEADER_TOKEN_INTERNO = "X-Internal-Token";

    private final RestClient restClient;

    public PedidoClient(@Value("${app.clients.pedidos-url:http://localhost:8083}") String pedidosUrl,
            @Value("${app.security.internal-token}") String tokenInterno) {
        this.restClient = RestClient.builder()
                .baseUrl(pedidosUrl)
                .defaultHeader(HEADER_TOKEN_INTERNO, tokenInterno)
                .build();
    }

    public PedidoDto obtener(Long pedidoId) {
        try {
            return restClient.get()
                    .uri("/internal/pedidos/{id}", pedidoId)
                    .retrieve()
                    .body(PedidoDto.class);
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 401 || status.value() == 403) {
                throw new RecoverableMessageException(
                        "ms-pedidos rechazo la autenticacion (HTTP " + status.value() + ") al consultar el pedido "
                                + pedidoId + ". Limitacion conocida: no existe auth servicio-a-servicio todavia, "
                                + "se reintentara.", e);
            }
            if (status.value() == 404) {
                throw new NonRecoverableMessageException(
                        "No existe el pedido " + pedidoId + " en ms-pedidos (HTTP 404)", e);
            }
            throw new RecoverableMessageException(
                    "Error HTTP " + status.value() + " al consultar el pedido " + pedidoId + " en ms-pedidos", e);
        } catch (RestClientException e) {
            throw new RecoverableMessageException(
                    "No se pudo contactar a ms-pedidos para obtener el pedido " + pedidoId, e);
        }
    }
}

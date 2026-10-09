package cl.duoc.cafeteria.inventario.client;

import cl.duoc.cafeteria.common.exception.NonRecoverableMessageException;
import cl.duoc.cafeteria.common.exception.RecoverableMessageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Cliente HTTP hacia ms-pedidos para resolver que productos (y en que
 * cantidad) componen un pedido ya pagado: PagoProcesadoEvent (cafeteria-common)
 * NO trae esa lista, asi que DescuentoStockService la consulta aqui antes de
 * aplicar la receta de cada producto.
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

    public List<ItemDto> obtenerItems(Long pedidoId) {
        try {
            PedidoDto pedido = restClient.get()
                    .uri("/internal/pedidos/{id}", pedidoId)
                    .retrieve()
                    .body(PedidoDto.class);

            if (pedido == null || pedido.items() == null) {
                return List.of();
            }
            return pedido.items();
        } catch (HttpClientErrorException ex) {
            HttpStatusCode status = ex.getStatusCode();
            if (status.value() == 401 || status.value() == 403) {
                throw new NonRecoverableMessageException(
                        "ms-pedidos rechazo la consulta del pedido " + pedidoId + " por falta de autorizacion ("
                                + status.value() + "): revisa que INTERNAL_API_TOKEN sea el mismo en ambos servicios", ex);
            }
            if (status.value() == 404) {
                throw new NonRecoverableMessageException(
                        "No existe el pedido " + pedidoId + " en ms-pedidos", ex);
            }
            throw new RecoverableMessageException(
                    "Error HTTP al consultar el pedido " + pedidoId + " en ms-pedidos: " + status, ex);
        } catch (RestClientException ex) {
            // Timeout, conexion rechazada, 5xx, ms-pedidos caido, etc: error
            // transitorio, se reintenta (ver AckHandler.nackConReintento).
            throw new RecoverableMessageException(
                    "No se pudo consultar el pedido " + pedidoId + " en ms-pedidos", ex);
        }
    }
}

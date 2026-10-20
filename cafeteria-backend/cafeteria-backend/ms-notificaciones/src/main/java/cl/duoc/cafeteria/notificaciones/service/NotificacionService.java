package cl.duoc.cafeteria.notificaciones.service;

import cl.duoc.cafeteria.common.evento.AlertaFraudeEvent;
import cl.duoc.cafeteria.common.evento.PagoProcesadoEvent;
import cl.duoc.cafeteria.common.evento.PedidoEstadoActualizadoEvent;
import cl.duoc.cafeteria.common.evento.StockBajoEvent;
import cl.duoc.cafeteria.notificaciones.dto.AlertaResponse;
import cl.duoc.cafeteria.notificaciones.dto.TicketResponse;

import java.util.List;

/**
 * Reglas de negocio de tickets y alertas (ver docs/EP2_PLAN.md seccion 5). No
 * conoce RabbitMQ: los listeners solo traducen mensaje -> llamada a este
 * service + ACK/NACK.
 */
public interface NotificacionService {

    /** pago.aprobado: genera el ticket/boleta y simula el envio por email (log). */
    void generarTicket(PagoProcesadoEvent evento);

    /** stock.bajo: registra una alerta STOCK_BAJO para el dashboard. */
    void registrarAlertaStockBajo(StockBajoEvent evento);

    /** alertas-fraude (Kafka): registra una alerta FRAUDE para el dashboard. */
    void registrarAlertaFraude(AlertaFraudeEvent alerta);

    /** pedido.estado.actualizado: registra una alerta PEDIDO_LISTO cuando corresponde. */
    void registrarAlertaCambioEstado(PedidoEstadoActualizadoEvent evento);

    List<TicketResponse> listarTickets();

    TicketResponse obtenerTicketPorCodigoSeguimiento(String codigoSeguimiento);

    List<AlertaResponse> listarAlertas();

    AlertaResponse marcarAlertaLeida(Long id);
}

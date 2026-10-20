package cl.duoc.cafeteria.notificaciones.service;

import cl.duoc.cafeteria.common.evento.AlertaFraudeEvent;
import cl.duoc.cafeteria.common.evento.PagoProcesadoEvent;
import cl.duoc.cafeteria.common.evento.PedidoEstadoActualizadoEvent;
import cl.duoc.cafeteria.common.evento.StockBajoEvent;
import cl.duoc.cafeteria.notificaciones.client.PedidoClient;
import cl.duoc.cafeteria.notificaciones.client.PedidoDto;
import cl.duoc.cafeteria.notificaciones.dto.AlertaResponse;
import cl.duoc.cafeteria.notificaciones.dto.TicketResponse;
import cl.duoc.cafeteria.notificaciones.exception.RecursoNoEncontradoException;
import cl.duoc.cafeteria.notificaciones.model.Alerta;
import cl.duoc.cafeteria.notificaciones.model.ItemTicket;
import cl.duoc.cafeteria.notificaciones.model.Ticket;
import cl.duoc.cafeteria.notificaciones.repository.AlertaRepository;
import cl.duoc.cafeteria.notificaciones.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificacionServiceImpl implements NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionServiceImpl.class);

    private static final String ESTADO_LISTO = "LISTO";

    private final TicketRepository ticketRepository;
    private final AlertaRepository alertaRepository;
    private final PedidoClient pedidoClient;

    public NotificacionServiceImpl(TicketRepository ticketRepository, AlertaRepository alertaRepository,
            PedidoClient pedidoClient) {
        this.ticketRepository = ticketRepository;
        this.alertaRepository = alertaRepository;
        this.pedidoClient = pedidoClient;
    }

    @Override
    @Transactional
    public void generarTicket(PagoProcesadoEvent evento) {
        PedidoDto pedido = pedidoClient.obtener(evento.pedidoId());

        Ticket ticket = new Ticket();
        ticket.setPedidoId(evento.pedidoId());
        ticket.setCodigoSeguimiento(pedido.codigoSeguimiento());
        ticket.setClienteNombre(pedido.clienteNombre());
        ticket.setClienteEmail(pedido.clienteEmail());
        ticket.setTotal(evento.monto());
        ticket.setMetodoPago(evento.metodoPago());
        ticket.setFecha(Instant.now());
        ticket.setItems(pedido.items() == null ? List.of() : pedido.items().stream()
                .map(item -> new ItemTicket(item.productoId(), item.nombreProducto(), item.cantidad(),
                        item.precioUnitario()))
                .toList());

        Ticket guardado = ticketRepository.save(ticket);

        // "Envio" del ticket al cliente: en este proyecto se simula con un log
        // estructurado (ver docs/EP2_PLAN.md seccion 5), nunca con un correo real.
        log.info("Ticket #{} generado para el pedido {} (codigo {}), enviado (simulado) a {}",
                guardado.getId(), guardado.getPedidoId(), guardado.getCodigoSeguimiento(),
                guardado.getClienteEmail());
    }

    @Override
    @Transactional
    public void registrarAlertaStockBajo(StockBajoEvent evento) {
        Alerta alerta = new Alerta();
        alerta.setTipo("STOCK_BAJO");
        alerta.setMensaje("Stock bajo de " + evento.nombreInsumo() + ": quedan " + evento.stockActual()
                + " (minimo " + evento.stockMinimo() + ")");
        alerta.setLeida(false);
        alerta.setFecha(Instant.now());
        alertaRepository.save(alerta);
    }

    @Override
    @Transactional
    public void registrarAlertaFraude(AlertaFraudeEvent evento) {
        Alerta alerta = new Alerta();
        alerta.setTipo("FRAUDE");
        String detalleMonto = evento.monto() > 0
                ? " (" + String.format(java.util.Locale.ROOT, "%,.0f", evento.monto()).replace(',', '.') + " CLP)"
                : "";
        alerta.setMensaje("[" + evento.severidad() + "] " + evento.cliente() + ": " + evento.motivo() + detalleMonto);
        alerta.setLeida(false);
        alerta.setFecha(evento.detectadaEn());
        alertaRepository.save(alerta);
    }

    @Override
    @Transactional
    public void registrarAlertaCambioEstado(PedidoEstadoActualizadoEvent evento) {
        if (!ESTADO_LISTO.equals(evento.estadoNuevo())) {
            // Solo "pedido listo" genera alerta; otras transiciones de estado no.
            return;
        }
        Alerta alerta = new Alerta();
        alerta.setTipo("PEDIDO_LISTO");
        alerta.setMensaje("Pedido " + evento.codigoSeguimiento() + " listo para entregar");
        alerta.setLeida(false);
        alerta.setFecha(Instant.now());
        alertaRepository.save(alerta);
    }

    @Override
    public List<TicketResponse> listarTickets() {
        return ticketRepository.findAll().stream().map(TicketResponse::desde).toList();
    }

    @Override
    public TicketResponse obtenerTicketPorCodigoSeguimiento(String codigoSeguimiento) {
        return ticketRepository.findByCodigoSeguimiento(codigoSeguimiento)
                .map(TicketResponse::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe un ticket con codigo de seguimiento " + codigoSeguimiento));
    }

    @Override
    public List<AlertaResponse> listarAlertas() {
        return alertaRepository.findAll().stream().map(AlertaResponse::desde).toList();
    }

    @Override
    @Transactional
    public AlertaResponse marcarAlertaLeida(Long id) {
        Alerta alerta = alertaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe una alerta con id " + id));
        alerta.setLeida(true);
        return AlertaResponse.desde(alertaRepository.save(alerta));
    }
}

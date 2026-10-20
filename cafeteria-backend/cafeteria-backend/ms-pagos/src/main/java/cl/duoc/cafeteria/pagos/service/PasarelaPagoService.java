package cl.duoc.cafeteria.pagos.service;

import cl.duoc.cafeteria.common.evento.PagoProcesadoEvent;
import cl.duoc.cafeteria.common.evento.PedidoCreadoEvent;
import cl.duoc.cafeteria.common.evento.TransaccionPagoEvent;
import cl.duoc.cafeteria.common.exception.NonRecoverableMessageException;
import cl.duoc.cafeteria.common.exception.RecoverableMessageException;
import cl.duoc.cafeteria.pagos.messaging.producer.PagoEventPublisher;
import cl.duoc.cafeteria.pagos.messaging.producer.TransaccionStreamPublisher;
import cl.duoc.cafeteria.pagos.model.EventoProcesado;
import cl.duoc.cafeteria.pagos.model.Pago;
import cl.duoc.cafeteria.pagos.repository.EventoProcesadoRepository;
import cl.duoc.cafeteria.pagos.repository.PagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Simula la pasarela de pago a partir del evento pedido.creado (ver
 * docs/EP2_PLAN.md seccion 3.5, el escenario de demo para la defensa). No
 * conoce RabbitMQ ni hace ACK/NACK: eso lo decide PedidoCreadoListener a
 * partir de si este metodo retorna normalmente o lanza una excepcion.
 */
@Service
public class PasarelaPagoService {

    private static final Logger log = LoggerFactory.getLogger(PasarelaPagoService.class);

    /** Tarjeta de prueba: pago rechazado (regla de negocio, no es un error). */
    private static final String TARJETA_RECHAZO_NEGOCIO = "0000";
    /** Tarjeta de prueba: error transitorio de la pasarela (se reintenta). */
    private static final String TARJETA_ERROR_TRANSITORIO = "9999";
    /** Tarjeta de prueba: error no recuperable de la pasarela (va directo a la DLQ). */
    private static final String TARJETA_ERROR_NO_RECUPERABLE = "8888";

    private static final String ESTADO_APROBADO = "APROBADO";
    private static final String ESTADO_RECHAZADO = "RECHAZADO";

    private final PagoRepository pagoRepository;
    private final EventoProcesadoRepository eventoProcesadoRepository;
    private final PagoEventPublisher eventPublisher;
    private final TransaccionStreamPublisher transaccionStreamPublisher;

    public PasarelaPagoService(PagoRepository pagoRepository,
            EventoProcesadoRepository eventoProcesadoRepository,
            PagoEventPublisher eventPublisher,
            TransaccionStreamPublisher transaccionStreamPublisher) {
        this.pagoRepository = pagoRepository;
        this.eventoProcesadoRepository = eventoProcesadoRepository;
        this.eventPublisher = eventPublisher;
        this.transaccionStreamPublisher = transaccionStreamPublisher;
    }

    @Transactional
    public void procesar(PedidoCreadoEvent evento) {
        if (eventoProcesadoRepository.existsById(evento.eventId())) {
            log.info("Evento {} ya fue procesado antes, se ignora (idempotencia)", evento.eventId());
            return;
        }

        String ultimos4 = evento.ultimos4();

        if (TARJETA_ERROR_TRANSITORIO.equals(ultimos4)) {
            throw new RecoverableMessageException(
                    "Error transitorio simulado de la pasarela para el pedido " + evento.pedidoId());
        }
        if (TARJETA_ERROR_NO_RECUPERABLE.equals(ultimos4)) {
            throw new NonRecoverableMessageException(
                    "Tarjeta rechazada por la pasarela para el pedido " + evento.pedidoId());
        }

        boolean aprobado = !TARJETA_RECHAZO_NEGOCIO.equals(ultimos4);
        Pago pago = crearPago(evento, aprobado ? ESTADO_APROBADO : ESTADO_RECHAZADO);
        Pago guardado = pagoRepository.save(pago);

        eventPublisher.publicar(new PagoProcesadoEvent(
                UUID.randomUUID(),
                Instant.now(),
                1,
                evento.pedidoId(),
                guardado.getId(),
                aprobado,
                guardado.getMonto(),
                guardado.getMetodoPago()));

        // Ademas del evento de RabbitMQ (que dispara el resto del flujo de
        // negocio), la transaccion se publica en Kafka para el monitoreo
        // antifraude en tiempo real (Fase 8). No afecta al pago si falla.
        transaccionStreamPublisher.publicar(new TransaccionPagoEvent(
                UUID.randomUUID(),
                Instant.now(),
                1,
                evento.pedidoId(),
                guardado.getId(),
                TransaccionPagoEvent.clienteOMostrador(evento.clienteEmail()),
                guardado.getMonto(),
                guardado.getMetodoPago(),
                aprobado));

        eventoProcesadoRepository.save(new EventoProcesado(evento.eventId(), Instant.now()));
    }

    private Pago crearPago(PedidoCreadoEvent evento, String estado) {
        Pago pago = new Pago();
        pago.setPedidoId(evento.pedidoId());
        pago.setMonto(evento.total());
        pago.setMetodoPago(evento.metodoPago());
        pago.setUltimos4(evento.ultimos4());
        pago.setEstado(estado);
        pago.setFecha(Instant.now());
        return pago;
    }
}

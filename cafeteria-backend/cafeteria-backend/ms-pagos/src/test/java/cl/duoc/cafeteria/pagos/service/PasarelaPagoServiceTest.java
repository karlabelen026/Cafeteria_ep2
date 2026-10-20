package cl.duoc.cafeteria.pagos.service;

import cl.duoc.cafeteria.common.evento.PagoProcesadoEvent;
import cl.duoc.cafeteria.common.evento.PedidoCreadoEvent;
import cl.duoc.cafeteria.common.exception.NonRecoverableMessageException;
import cl.duoc.cafeteria.common.exception.RecoverableMessageException;
import cl.duoc.cafeteria.pagos.messaging.producer.PagoEventPublisher;
import cl.duoc.cafeteria.pagos.messaging.producer.TransaccionStreamPublisher;
import cl.duoc.cafeteria.pagos.model.Pago;
import cl.duoc.cafeteria.pagos.repository.EventoProcesadoRepository;
import cl.duoc.cafeteria.pagos.repository.PagoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Prueba unitaria de la simulacion de la pasarela de pago (ver
 * docs/EP2_PLAN.md seccion 3.5): los 3 escenarios de demo con tarjetas de
 * prueba, el camino feliz y la idempotencia por eventId.
 */
@ExtendWith(MockitoExtension.class)
class PasarelaPagoServiceTest {

    @Mock
    private PagoRepository pagoRepository;

    @Mock
    private EventoProcesadoRepository eventoProcesadoRepository;

    @Mock
    private PagoEventPublisher eventPublisher;

    @Mock
    private TransaccionStreamPublisher transaccionStreamPublisher;

    @InjectMocks
    private PasarelaPagoService service;

    private PedidoCreadoEvent eventoConTarjeta(String ultimos4) {
        return new PedidoCreadoEvent(UUID.randomUUID(), Instant.now(), 1, 10L, "COD-10",
                "cliente@test.cl", "CREDITO", ultimos4, 5000.0, List.of());
    }

    @BeforeEach
    void setUp() {
        lenient().when(pagoRepository.save(any(Pago.class))).thenAnswer(invocation -> {
            Pago pago = invocation.getArgument(0);
            pago.setId(1L);
            return pago;
        });
    }

    @Test
    void procesar_conTarjetaTerminadaEn0000_creaPagoRechazadoSinLanzarExcepcion() {
        PedidoCreadoEvent evento = eventoConTarjeta("0000");
        when(eventoProcesadoRepository.existsById(evento.eventId())).thenReturn(false);

        service.procesar(evento);

        ArgumentCaptor<Pago> captor = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo("RECHAZADO");

        ArgumentCaptor<PagoProcesadoEvent> eventoCaptor = ArgumentCaptor.forClass(PagoProcesadoEvent.class);
        verify(eventPublisher).publicar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().aprobado()).isFalse();

        verify(eventoProcesadoRepository).save(any());
    }

    @Test
    void procesar_conTarjetaTerminadaEn9999_lanzaRecoverableMessageException() {
        PedidoCreadoEvent evento = eventoConTarjeta("9999");
        when(eventoProcesadoRepository.existsById(evento.eventId())).thenReturn(false);

        assertThatThrownBy(() -> service.procesar(evento))
                .isInstanceOf(RecoverableMessageException.class);

        verify(pagoRepository, never()).save(any());
        verify(eventPublisher, never()).publicar(any());
        verify(eventoProcesadoRepository, never()).save(any());
    }

    @Test
    void procesar_conTarjetaTerminadaEn8888_lanzaNonRecoverableMessageException() {
        PedidoCreadoEvent evento = eventoConTarjeta("8888");
        when(eventoProcesadoRepository.existsById(evento.eventId())).thenReturn(false);

        assertThatThrownBy(() -> service.procesar(evento))
                .isInstanceOf(NonRecoverableMessageException.class);

        verify(pagoRepository, never()).save(any());
        verify(eventPublisher, never()).publicar(any());
        verify(eventoProcesadoRepository, never()).save(any());
    }

    @Test
    void procesar_conCualquierOtraTarjeta_creaPagoAprobado() {
        PedidoCreadoEvent evento = eventoConTarjeta("1234");
        when(eventoProcesadoRepository.existsById(evento.eventId())).thenReturn(false);

        service.procesar(evento);

        ArgumentCaptor<Pago> captor = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo("APROBADO");
        assertThat(captor.getValue().getMonto()).isEqualTo(5000.0);

        ArgumentCaptor<PagoProcesadoEvent> eventoCaptor = ArgumentCaptor.forClass(PagoProcesadoEvent.class);
        verify(eventPublisher).publicar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().aprobado()).isTrue();

        verify(eventoProcesadoRepository).save(any());
    }

    @Test
    void procesar_conEventoYaProcesado_noReprocesa() {
        PedidoCreadoEvent evento = eventoConTarjeta("1234");
        when(eventoProcesadoRepository.existsById(evento.eventId())).thenReturn(true);

        service.procesar(evento);

        verify(pagoRepository, never()).save(any());
        verify(eventPublisher, never()).publicar(any());
        verify(eventoProcesadoRepository, never()).save(any());
    }
}

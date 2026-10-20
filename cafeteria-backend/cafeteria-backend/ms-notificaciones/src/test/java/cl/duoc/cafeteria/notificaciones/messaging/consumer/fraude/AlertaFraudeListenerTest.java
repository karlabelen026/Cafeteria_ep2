package cl.duoc.cafeteria.notificaciones.messaging.consumer.fraude;

import cl.duoc.cafeteria.common.evento.AlertaFraudeEvent;
import cl.duoc.cafeteria.notificaciones.model.EventoProcesado;
import cl.duoc.cafeteria.notificaciones.repository.EventoProcesadoRepository;
import cl.duoc.cafeteria.notificaciones.service.NotificacionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Idempotencia del consumidor de alertas-fraude (Fase 8). */
@ExtendWith(MockitoExtension.class)
class AlertaFraudeListenerTest {

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private EventoProcesadoRepository eventoProcesadoRepository;

    @InjectMocks
    private AlertaFraudeListener listener;

    private AlertaFraudeEvent alerta() {
        return new AlertaFraudeEvent(UUID.randomUUID(), Instant.now(), "juan@mail.cl", "ALTA",
                "Transacción inusualmente alta vía CREDITO", 60000, 63.16, "CREDITO", 1L);
    }

    @Test
    void alertaNueva_seRegistraYSeMarcaComoProcesada() {
        AlertaFraudeEvent alerta = alerta();
        when(eventoProcesadoRepository.existsById(alerta.eventId())).thenReturn(false);

        listener.recibir(alerta);

        verify(notificacionService).registrarAlertaFraude(alerta);
        verify(eventoProcesadoRepository).save(any(EventoProcesado.class));
    }

    @Test
    void alertaRepetida_noSeRegistraDosVeces() {
        AlertaFraudeEvent alerta = alerta();
        when(eventoProcesadoRepository.existsById(alerta.eventId())).thenReturn(true);

        listener.recibir(alerta);

        verify(notificacionService, never()).registrarAlertaFraude(any());
        verify(eventoProcesadoRepository, never()).save(any());
    }
}

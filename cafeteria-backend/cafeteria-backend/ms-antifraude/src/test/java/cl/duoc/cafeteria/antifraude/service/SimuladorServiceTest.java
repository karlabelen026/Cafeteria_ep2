package cl.duoc.cafeteria.antifraude.service;

import cl.duoc.cafeteria.antifraude.config.AntifraudeProperties;
import cl.duoc.cafeteria.antifraude.dto.CargaResponse;
import cl.duoc.cafeteria.antifraude.dto.TransaccionSimuladaRequest;
import cl.duoc.cafeteria.antifraude.exception.ServicioNoDisponibleException;
import cl.duoc.cafeteria.antifraude.messaging.producer.TransaccionPublisher;
import cl.duoc.cafeteria.common.evento.TransaccionPagoEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Simulador y generador de carga (Fase 8). */
@ExtendWith(MockitoExtension.class)
class SimuladorServiceTest {

    @Mock
    private TransaccionPublisher publisher;

    @InjectMocks
    private SimuladorService service;

    @Test
    void simular_publicaLaTransaccionConElClienteComoKey() {
        when(publisher.publicar(any())).thenReturn(CompletableFuture.completedFuture(null));

        TransaccionPagoEvent evento = service.simular(
                new TransaccionSimuladaRequest("juan@mail.cl", 60000.0, "CREDITO", null));

        assertThat(evento.clienteEmail()).isEqualTo("juan@mail.cl");
        assertThat(evento.monto()).isEqualTo(60000.0);
        assertThat(evento.aprobado()).isTrue();
        verify(publisher).publicar(evento);
    }

    @Test
    void simular_siKafkaNoConfirma_responde503() {
        when(publisher.publicar(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("caido")));

        assertThatThrownBy(() -> service.simular(new TransaccionSimuladaRequest("juan@mail.cl", 500.0, "DEBITO", true)))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void generarCarga_publicaTransaccionesNormalesQueNoInundanLasAlertas() {
        when(publisher.publicar(any())).thenReturn(CompletableFuture.completedFuture(null));

        CargaResponse resultado = service.generarCarga(500);

        assertThat(resultado.cantidad()).isEqualTo(500);
        ArgumentCaptor<TransaccionPagoEvent> captor = ArgumentCaptor.forClass(TransaccionPagoEvent.class);
        verify(publisher, times(500)).publicar(captor.capture());
        double umbralAlto = new AntifraudeProperties().getUmbralAlto();
        assertThat(captor.getAllValues())
                .allSatisfy(t -> {
                    assertThat(t.aprobado()).isTrue();
                    assertThat(t.monto()).isLessThan(umbralAlto);
                });
        verify(publisher).vaciar();
    }
}

package cl.duoc.cafeteria.reportes.service;

import cl.duoc.cafeteria.common.evento.TransaccionPagoEvent;
import cl.duoc.cafeteria.common.exception.NonRecoverableMessageException;
import cl.duoc.cafeteria.reportes.dto.MetricasTiempoRealResponse;
import cl.duoc.cafeteria.reportes.model.AuditoriaTransaccion;
import cl.duoc.cafeteria.reportes.repository.AuditoriaTransaccionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueba unitaria de la reporteria en tiempo real: registro idempotente en la
 * auditoria, descarte de eventos invalidos y calculo de las metricas.
 */
@ExtendWith(MockitoExtension.class)
class MetricasTiempoRealServiceImplTest {

    private static final Instant AHORA = Instant.parse("2026-11-10T15:30:20Z");

    @Mock
    private AuditoriaTransaccionRepository repository;

    private MetricasTiempoRealServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MetricasTiempoRealServiceImpl(repository, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    private TransaccionPagoEvent evento(UUID id, Instant ocurridoEn, double monto, boolean aprobado) {
        return new TransaccionPagoEvent(id, ocurridoEn, 1, 10L, 20L, "ana@cafe.cl", monto, "CREDITO", aprobado);
    }

    private AuditoriaTransaccion registro(Instant ocurridoEn, double monto, String metodo, boolean aprobado,
            long latenciaMs) {
        AuditoriaTransaccion r = new AuditoriaTransaccion();
        r.setEventId(UUID.randomUUID());
        r.setOcurridoEn(ocurridoEn);
        r.setProcesadoEn(ocurridoEn.plusMillis(latenciaMs));
        r.setMonto(monto);
        r.setMetodoPago(metodo);
        r.setAprobado(aprobado);
        return r;
    }

    @Test
    void registrar_eventoNuevo_loGuardaConParticionOffsetYHoraDeProceso() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);

        boolean guardado = service.registrar(evento(id, AHORA.minusSeconds(2), 4500, true), 1, 42L);

        assertThat(guardado).isTrue();
        ArgumentCaptor<AuditoriaTransaccion> captor = ArgumentCaptor.forClass(AuditoriaTransaccion.class);
        verify(repository).save(captor.capture());
        AuditoriaTransaccion r = captor.getValue();
        assertThat(r.getEventId()).isEqualTo(id);
        assertThat(r.getParticion()).isEqualTo(1);
        assertThat(r.getOffsetKafka()).isEqualTo(42L);
        assertThat(r.getProcesadoEn()).isEqualTo(AHORA);
        assertThat(r.getMonto()).isEqualTo(4500);
    }

    @Test
    void registrar_eventoDuplicado_noLoGuardaDeNuevo() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true);

        boolean guardado = service.registrar(evento(id, AHORA, 4500, true), 0, 7L);

        assertThat(guardado).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void registrar_eventoInvalido_lanzaNoRecuperableSinGuardar() {
        assertThatThrownBy(() -> service.registrar(evento(null, AHORA, 4500, true), 0, 1L))
                .isInstanceOf(NonRecoverableMessageException.class);
        assertThatThrownBy(() -> service.registrar(evento(UUID.randomUUID(), null, 4500, true), 0, 1L))
                .isInstanceOf(NonRecoverableMessageException.class);
        assertThatThrownBy(() -> service.registrar(evento(UUID.randomUUID(), AHORA, -1, true), 0, 1L))
                .isInstanceOf(NonRecoverableMessageException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void metricas_calculaTotalesTasaTicketPorMetodoSerieYLatencia() {
        Instant minutoActual = Instant.parse("2026-11-10T15:30:05Z");
        Instant minutoAnterior = Instant.parse("2026-11-10T15:29:40Z");
        when(repository.findByOcurridoEnGreaterThanEqual(Instant.parse("2026-11-10T15:16:00Z"))).thenReturn(List.of(
                registro(minutoActual, 3000, "CREDITO", true, 100),
                registro(minutoActual, 5000, "DEBITO", true, 300),
                registro(minutoAnterior, 9000, "CREDITO", false, 200),
                registro(minutoAnterior, 1000, "CREDITO", true, 200)));

        MetricasTiempoRealResponse m = service.metricas(15);

        assertThat(m.totalTransacciones()).isEqualTo(4);
        assertThat(m.aprobadas()).isEqualTo(3);
        assertThat(m.rechazadas()).isEqualTo(1);
        assertThat(m.tasaAprobacionPct()).isEqualTo(75.0);
        assertThat(m.montoAprobado()).isEqualTo(9000);
        assertThat(m.ticketPromedio()).isEqualTo(3000);
        assertThat(m.montoAprobadoPorMetodo()).containsEntry("CREDITO", 4000.0).containsEntry("DEBITO", 5000.0);
        assertThat(m.latenciaPromedioMs()).isEqualTo(200);
        assertThat(m.serie()).hasSize(15);
        MetricasTiempoRealResponse.PuntoMinuto ultimo = m.serie().get(14);
        assertThat(ultimo.minuto()).isEqualTo(Instant.parse("2026-11-10T15:30:00Z"));
        assertThat(ultimo.transacciones()).isEqualTo(2);
        assertThat(ultimo.montoAprobado()).isEqualTo(8000);
        assertThat(m.serie().get(13).transacciones()).isEqualTo(2);
        assertThat(m.serie().get(13).aprobadas()).isEqualTo(1);
    }

    @Test
    void metricas_aprobadaSinMetodoPago_seAgrupaComoDesconocidoSinFallar() {
        when(repository.findByOcurridoEnGreaterThanEqual(any())).thenReturn(List.of(
                registro(AHORA.minusSeconds(10), 2500, null, true, 50),
                registro(AHORA.minusSeconds(5), 1500, "DEBITO", true, 50)));

        MetricasTiempoRealResponse m = service.metricas(5);

        assertThat(m.montoAprobadoPorMetodo())
                .containsEntry("DESCONOCIDO", 2500.0)
                .containsEntry("DEBITO", 1500.0);
        assertThat(m.montoAprobado()).isEqualTo(4000);
    }

    @Test
    void metricas_ventanaVacia_devuelveCerosYUnPuntoPorMinuto() {
        when(repository.findByOcurridoEnGreaterThanEqual(any())).thenReturn(List.of());

        MetricasTiempoRealResponse m = service.metricas(5);

        assertThat(m.totalTransacciones()).isZero();
        assertThat(m.tasaAprobacionPct()).isZero();
        assertThat(m.ticketPromedio()).isZero();
        assertThat(m.latenciaPromedioMs()).isZero();
        assertThat(m.montoAprobadoPorMetodo()).isEmpty();
        assertThat(m.serie()).hasSize(5).allSatisfy(p -> assertThat(p.transacciones()).isZero());
    }
}

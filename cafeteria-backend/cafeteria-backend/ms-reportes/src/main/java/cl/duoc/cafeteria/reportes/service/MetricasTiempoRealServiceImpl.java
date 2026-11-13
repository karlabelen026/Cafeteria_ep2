package cl.duoc.cafeteria.reportes.service;

import cl.duoc.cafeteria.common.evento.TransaccionPagoEvent;
import cl.duoc.cafeteria.common.exception.NonRecoverableMessageException;
import cl.duoc.cafeteria.reportes.dto.MetricasTiempoRealResponse;
import cl.duoc.cafeteria.reportes.dto.MetricasTiempoRealResponse.PuntoMinuto;
import cl.duoc.cafeteria.reportes.dto.TransaccionAuditadaResponse;
import cl.duoc.cafeteria.reportes.model.AuditoriaTransaccion;
import cl.duoc.cafeteria.reportes.repository.AuditoriaTransaccionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@Service
public class MetricasTiempoRealServiceImpl implements MetricasTiempoRealService {

    private static final Logger log = LoggerFactory.getLogger(MetricasTiempoRealServiceImpl.class);

    private static final int MINUTOS_MAXIMOS = 1440;
    private static final int LIMITE_MAXIMO = 200;
    // ms-pagos solo valida el formato de metodoPago (@Pattern), no que venga:
    // un null como clave haria fallar el TreeMap del desglose por metodo.
    private static final String METODO_DESCONOCIDO = "DESCONOCIDO";

    private final AuditoriaTransaccionRepository repository;
    private final Clock clock;

    public MetricasTiempoRealServiceImpl(AuditoriaTransaccionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean registrar(TransaccionPagoEvent evento, int particion, long offset) {
        validar(evento, particion, offset);
        if (repository.existsById(evento.eventId())) {
            log.info("Transaccion {} ya auditada (particion {}, offset {}): se ignora el duplicado",
                    evento.eventId(), particion, offset);
            return false;
        }

        AuditoriaTransaccion registro = new AuditoriaTransaccion();
        registro.setEventId(evento.eventId());
        registro.setOcurridoEn(evento.ocurridoEn());
        registro.setVersion(evento.version());
        registro.setPedidoId(evento.pedidoId());
        registro.setPagoId(evento.pagoId());
        registro.setClienteEmail(TransaccionPagoEvent.clienteOMostrador(evento.clienteEmail()));
        registro.setMonto(evento.monto());
        registro.setMetodoPago(evento.metodoPago());
        registro.setAprobado(evento.aprobado());
        registro.setProcesadoEn(Instant.now(clock));
        registro.setParticion(particion);
        registro.setOffsetKafka(offset);
        repository.save(registro);

        log.info("Transaccion {} auditada: pedido {}, aprobado={}, particion {}, offset {}",
                evento.eventId(), evento.pedidoId(), evento.aprobado(), particion, offset);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public MetricasTiempoRealResponse metricas(int minutos) {
        int ventana = Math.max(1, Math.min(minutos, MINUTOS_MAXIMOS));
        Instant hasta = Instant.now(clock);
        Instant desde = hasta.truncatedTo(ChronoUnit.MINUTES).minus(ventana - 1L, ChronoUnit.MINUTES);
        List<AuditoriaTransaccion> transacciones = repository.findByOcurridoEnGreaterThanEqual(desde);

        long aprobadas = 0;
        double montoAprobado = 0;
        double latenciaTotalMs = 0;
        Map<String, Double> porMetodo = new TreeMap<>();
        Map<Instant, long[]> conteoPorMinuto = new TreeMap<>();
        Map<Instant, Double> montoPorMinuto = new TreeMap<>();
        for (int i = 0; i < ventana; i++) {
            Instant minuto = desde.plus(i, ChronoUnit.MINUTES);
            conteoPorMinuto.put(minuto, new long[2]);
            montoPorMinuto.put(minuto, 0.0);
        }

        for (AuditoriaTransaccion t : transacciones) {
            Instant minuto = t.getOcurridoEn().truncatedTo(ChronoUnit.MINUTES);
            long[] conteo = conteoPorMinuto.get(minuto);
            if (conteo != null) {
                conteo[0]++;
            }
            if (t.isAprobado()) {
                aprobadas++;
                montoAprobado += t.getMonto();
                porMetodo.merge(Objects.requireNonNullElse(t.getMetodoPago(), METODO_DESCONOCIDO), t.getMonto(),
                        Double::sum);
                if (conteo != null) {
                    conteo[1]++;
                    montoPorMinuto.merge(minuto, t.getMonto(), Double::sum);
                }
            }
            latenciaTotalMs += Duration.between(t.getOcurridoEn(), t.getProcesadoEn()).toMillis();
        }

        List<PuntoMinuto> serie = new ArrayList<>();
        conteoPorMinuto.forEach((minuto, conteo) ->
                serie.add(new PuntoMinuto(minuto, conteo[0], conteo[1], montoPorMinuto.get(minuto))));

        long total = transacciones.size();
        return new MetricasTiempoRealResponse(
                ventana,
                desde,
                hasta,
                total,
                aprobadas,
                total - aprobadas,
                total == 0 ? 0 : redondear(aprobadas * 100.0 / total),
                montoAprobado,
                aprobadas == 0 ? 0 : redondear(montoAprobado / aprobadas),
                porMetodo,
                serie,
                total == 0 ? 0 : redondear(latenciaTotalMs / total));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransaccionAuditadaResponse> ultimas(int limite, String cliente) {
        PageRequest pagina = PageRequest.of(0, Math.max(1, Math.min(limite, LIMITE_MAXIMO)));
        List<AuditoriaTransaccion> registros = cliente == null || cliente.isBlank()
                ? repository.findAllByOrderByOcurridoEnDesc(pagina)
                : repository.findByClienteEmailOrderByOcurridoEnDesc(cliente.trim(), pagina);
        return registros.stream().map(TransaccionAuditadaResponse::desde).toList();
    }

    private void validar(TransaccionPagoEvent evento, int particion, long offset) {
        String problema = null;
        if (evento.eventId() == null) {
            problema = "sin eventId";
        } else if (evento.ocurridoEn() == null) {
            problema = "sin fecha (ocurridoEn)";
        } else if (evento.monto() < 0) {
            problema = "con monto negativo (" + evento.monto() + ")";
        }
        if (problema != null) {
            log.warn("Transaccion invalida {} en particion {}, offset {}: se descarta a la DLT",
                    problema, particion, offset);
            throw new NonRecoverableMessageException("Transaccion de pago invalida: " + problema);
        }
    }

    private static double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}

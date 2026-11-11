package cl.duoc.cafeteria.reportes.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Respuesta de GET /api/reportes/tiempo-real: metricas de las transacciones de
 * pago de los ultimos "ventanaMinutos", calculadas desde lo consumido de Kafka.
 * tasaAprobacionPct va de 0 a 100. La serie trae un punto por minuto, tambien
 * los minutos sin transacciones, para graficarla sin huecos.
 */
public record MetricasTiempoRealResponse(
        int ventanaMinutos,
        Instant desde,
        Instant hasta,
        long totalTransacciones,
        long aprobadas,
        long rechazadas,
        double tasaAprobacionPct,
        double montoAprobado,
        double ticketPromedio,
        Map<String, Double> montoAprobadoPorMetodo,
        List<PuntoMinuto> serie,
        double latenciaPromedioMs) {

    public record PuntoMinuto(Instant minuto, long transacciones, long aprobadas, double montoAprobado) {
    }
}

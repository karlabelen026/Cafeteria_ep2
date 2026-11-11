package cl.duoc.cafeteria.reportes.dto;

import cl.duoc.cafeteria.reportes.model.AuditoriaTransaccion;

import java.time.Instant;
import java.util.UUID;

/** Una fila de GET /api/reportes/auditoria/transacciones, con su particion y offset de Kafka. */
public record TransaccionAuditadaResponse(
        UUID eventId,
        Instant ocurridoEn,
        Instant procesadoEn,
        Long pedidoId,
        Long pagoId,
        String clienteEmail,
        double monto,
        String metodoPago,
        boolean aprobado,
        int particion,
        long offset) {

    public static TransaccionAuditadaResponse desde(AuditoriaTransaccion registro) {
        return new TransaccionAuditadaResponse(
                registro.getEventId(),
                registro.getOcurridoEn(),
                registro.getProcesadoEn(),
                registro.getPedidoId(),
                registro.getPagoId(),
                registro.getClienteEmail(),
                registro.getMonto(),
                registro.getMetodoPago(),
                registro.isAprobado(),
                registro.getParticion(),
                registro.getOffsetKafka());
    }
}

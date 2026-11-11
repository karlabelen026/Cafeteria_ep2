package cl.duoc.cafeteria.reportes.service;

import cl.duoc.cafeteria.common.evento.TransaccionPagoEvent;
import cl.duoc.cafeteria.reportes.dto.MetricasTiempoRealResponse;
import cl.duoc.cafeteria.reportes.dto.TransaccionAuditadaResponse;

import java.util.List;

/**
 * Reporteria en tiempo real y auditoria de las transacciones de pago que llegan
 * por Kafka. No depende de Kafka: el listener le pasa el evento ya
 * deserializado junto con su particion y offset.
 */
public interface MetricasTiempoRealService {

    /**
     * Guarda la transaccion en la auditoria. Es idempotente: si el eventId ya
     * estaba registrado no hace nada y devuelve false.
     *
     * @throws cl.duoc.cafeteria.common.exception.NonRecoverableMessageException
     *         si el evento no trae eventId u ocurridoEn, o si el monto es negativo
     */
    boolean registrar(TransaccionPagoEvent evento, int particion, long offset);

    /** Metricas de los ultimos "minutos" (entre 1 y 1440). */
    MetricasTiempoRealResponse metricas(int minutos);

    /** Ultimas transacciones auditadas (entre 1 y 200), opcionalmente de un cliente. */
    List<TransaccionAuditadaResponse> ultimas(int limite, String cliente);
}

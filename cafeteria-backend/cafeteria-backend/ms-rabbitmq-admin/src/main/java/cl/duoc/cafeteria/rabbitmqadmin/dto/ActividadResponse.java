package cl.duoc.cafeteria.rabbitmqadmin.dto;

import cl.duoc.cafeteria.rabbitmqadmin.management.ManagementMessageStatsDto;

/**
 * Mensajes publicados, entregados y confirmados desde que arranco el cluster,
 * con su tasa por segundo. RabbitMQ omite estos datos si aun no hay trafico:
 * en ese caso todo vale 0.
 */
public record ActividadResponse(long publicados, long entregados, long confirmados,
        double publicadosPorSeg, double entregadosPorSeg, double confirmadosPorSeg) {

    public static ActividadResponse desde(ManagementMessageStatsDto stats) {
        if (stats == null) {
            return new ActividadResponse(0, 0, 0, 0, 0, 0);
        }
        return new ActividadResponse(
                total(stats.publish()),
                total(stats.deliverGet()),
                total(stats.ack()),
                tasa(stats.publishDetails()),
                tasa(stats.deliverGetDetails()),
                tasa(stats.ackDetails()));
    }

    private static long total(Long valor) {
        return valor != null ? valor : 0;
    }

    private static double tasa(ManagementMessageStatsDto.Detalle detalle) {
        return detalle != null && detalle.rate() != null ? detalle.rate() : 0;
    }
}

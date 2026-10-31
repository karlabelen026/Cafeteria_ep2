package cl.duoc.cafeteria.rabbitmqadmin.dto;

import cl.duoc.cafeteria.rabbitmqadmin.management.ManagementOverviewDto;

/** Actividad global del cluster y sus totales de colas, consumidores y conexiones. */
public record ResumenActividadResponse(ActividadResponse actividad, int colas, int consumidores,
        int conexiones, int canales) {

    public static ResumenActividadResponse desde(ManagementOverviewDto dto) {
        ManagementOverviewDto.Totales totales = dto.objectTotals();
        return new ResumenActividadResponse(
                ActividadResponse.desde(dto.messageStats()),
                totales != null ? valor(totales.queues()) : 0,
                totales != null ? valor(totales.consumers()) : 0,
                totales != null ? valor(totales.connections()) : 0,
                totales != null ? valor(totales.channels()) : 0);
    }

    private static int valor(Integer numero) {
        return numero != null ? numero : 0;
    }
}

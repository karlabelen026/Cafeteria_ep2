package cl.duoc.cafeteria.antifraude.streams;

import java.time.Instant;

/**
 * Valor del state store "total gastado por cliente" (paso 5 de la
 * topologia). Inmutable: cada transaccion aprobada produce un total nuevo.
 */
public record TotalCliente(
        String cliente,
        double totalClp,
        double totalUsd,
        long cantidadTransacciones,
        Instant ultimaTransaccion) {

    public static TotalCliente vacio() {
        return new TotalCliente(null, 0, 0, 0, null);
    }

    public TotalCliente sumar(String clienteKey, TransaccionEnriquecida transaccion) {
        return new TotalCliente(
                clienteKey,
                totalClp + transaccion.monto(),
                Montos.redondear(totalUsd + transaccion.montoUsd()),
                cantidadTransacciones + 1,
                transaccion.ocurridoEn());
    }
}

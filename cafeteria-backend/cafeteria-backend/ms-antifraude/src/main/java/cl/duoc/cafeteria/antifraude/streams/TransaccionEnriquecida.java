package cl.duoc.cafeteria.antifraude.streams;

import java.time.Instant;
import java.util.UUID;

/** Transaccion ya filtrada y enriquecida con su monto en USD (paso 3 de la topologia). */
public record TransaccionEnriquecida(
        UUID eventId,
        Instant ocurridoEn,
        Long pagoId,
        String cliente,
        double monto,
        double montoUsd,
        String metodoPago,
        boolean aprobado) {
}

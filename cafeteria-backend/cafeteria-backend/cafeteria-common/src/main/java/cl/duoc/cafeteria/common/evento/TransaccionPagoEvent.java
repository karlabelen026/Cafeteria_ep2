package cl.duoc.cafeteria.common.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de streaming (Kafka, topic de transacciones) que publica ms-pagos
 * por cada pago simulado, y que analiza ms-antifraude con Kafka Streams (ver
 * docs/EP2_PLAN.md, Fase 8). Viaja con key = clienteEmail para que todas las
 * transacciones de un mismo cliente caigan en la misma particion y se
 * procesen en orden.
 *
 * metodoPago cumple el rol de "origen" de la transaccion (guia 3.2.2). Nunca
 * trae el numero de tarjeta ni el CVV. clienteEmail es "mostrador" cuando el
 * pedido no identifica al cliente.
 */
public record TransaccionPagoEvent(
        UUID eventId,
        Instant ocurridoEn,
        int version,
        Long pedidoId,
        Long pagoId,
        String clienteEmail,
        double monto,
        String metodoPago,
        boolean aprobado) {

    /** Key que se usa cuando el pedido no trae email (venta de mostrador). */
    public static final String CLIENTE_MOSTRADOR = "mostrador";

    public static String clienteOMostrador(String clienteEmail) {
        return clienteEmail == null || clienteEmail.isBlank() ? CLIENTE_MOSTRADOR : clienteEmail;
    }
}

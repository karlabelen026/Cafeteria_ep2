package cl.duoc.cafeteria.antifraude.messaging.producer;

import cl.duoc.cafeteria.common.evento.TransaccionPagoEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Publica transacciones de prueba (simulador y generador de carga) en el
 * topic de transacciones. Los services dependen de esta interfaz, nunca de
 * KafkaTemplate directamente.
 */
public interface TransaccionPublisher {

    CompletableFuture<Void> publicar(TransaccionPagoEvent evento);

    /** Espera a que todo lo encolado en el productor se haya enviado. */
    void vaciar();
}

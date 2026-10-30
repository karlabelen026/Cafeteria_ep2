package cl.duoc.cafeteria.kafkaadmin.exception;

/** Parametros que Kafka o las reglas de esta API rechazan -> HTTP 400. */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}

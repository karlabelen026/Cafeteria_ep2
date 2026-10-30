package cl.duoc.cafeteria.kafkaadmin.exception;

/** El cluster de Kafka no respondio a tiempo -> HTTP 503. */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}

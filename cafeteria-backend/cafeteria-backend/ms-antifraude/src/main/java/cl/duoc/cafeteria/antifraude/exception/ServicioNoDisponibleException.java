package cl.duoc.cafeteria.antifraude.exception;

/** Kafka o Kafka Streams todavia no estan disponibles: se responde 503. */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}

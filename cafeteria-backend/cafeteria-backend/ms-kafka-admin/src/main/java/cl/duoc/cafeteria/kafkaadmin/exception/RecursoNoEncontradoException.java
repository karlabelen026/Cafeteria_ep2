package cl.duoc.cafeteria.kafkaadmin.exception;

/** Topic o consumer group inexistente -> HTTP 404. */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}

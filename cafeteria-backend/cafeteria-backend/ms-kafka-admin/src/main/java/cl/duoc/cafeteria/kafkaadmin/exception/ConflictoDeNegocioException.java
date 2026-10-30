package cl.duoc.cafeteria.kafkaadmin.exception;

/** Accion que choca con el estado actual (topic protegido, ya existe, grupo activo) -> HTTP 409. */
public class ConflictoDeNegocioException extends RuntimeException {

    public ConflictoDeNegocioException(String mensaje) {
        super(mensaje);
    }
}

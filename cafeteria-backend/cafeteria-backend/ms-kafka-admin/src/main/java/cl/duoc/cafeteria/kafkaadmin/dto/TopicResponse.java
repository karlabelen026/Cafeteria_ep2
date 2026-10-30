package cl.duoc.cafeteria.kafkaadmin.dto;

/** Resumen de un topic. protegido = no se puede eliminar ni cambiar sus particiones. */
public record TopicResponse(String name, boolean internal, boolean protegido, int partitions, int replicationFactor) {
}

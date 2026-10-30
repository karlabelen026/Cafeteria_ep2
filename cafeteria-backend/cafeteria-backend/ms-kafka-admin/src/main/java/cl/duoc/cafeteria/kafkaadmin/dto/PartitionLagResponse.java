package cl.duoc.cafeteria.kafkaadmin.dto;

/** Avance de un grupo en una particion: lag = logEndOffset - currentOffset. */
public record PartitionLagResponse(String topic, int partition, long currentOffset, long logEndOffset, long lag) {
}

package cl.duoc.cafeteria.kafkaadmin.dto;

import java.util.List;

/** Broker lider, replicas e ISR (replicas en sincronia) de una particion. */
public record PartitionResponse(int partition, Integer leader, List<Integer> replicas, List<Integer> isr) {
}

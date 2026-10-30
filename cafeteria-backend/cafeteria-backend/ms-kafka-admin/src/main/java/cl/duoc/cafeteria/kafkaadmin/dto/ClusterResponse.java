package cl.duoc.cafeteria.kafkaadmin.dto;

import java.util.List;

/** Estado del cluster: sus brokers y cual es el controlador. */
public record ClusterResponse(String clusterId, List<BrokerResponse> brokers) {
}

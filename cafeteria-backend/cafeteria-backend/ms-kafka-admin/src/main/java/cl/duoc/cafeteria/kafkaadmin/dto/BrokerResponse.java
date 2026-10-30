package cl.duoc.cafeteria.kafkaadmin.dto;

/** Un broker del cluster. */
public record BrokerResponse(int id, String host, int port, boolean controller) {
}

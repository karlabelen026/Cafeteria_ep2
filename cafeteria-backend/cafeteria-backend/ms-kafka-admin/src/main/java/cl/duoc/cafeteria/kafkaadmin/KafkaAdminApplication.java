package cl.duoc.cafeteria.kafkaadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio administrador de Kafka (equivalente a ms-rabbitmq-admin):
 * API REST para crear, modificar, eliminar y listar topics, y para inspeccionar
 * consumer groups con sus offsets y lag.
 */
@SpringBootApplication
public class KafkaAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaAdminApplication.class, args);
    }
}

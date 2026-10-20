package cl.duoc.cafeteria.antifraude;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Monitoreo antifraude de pagos en tiempo real (Fase 8): consume el stream de
 * transacciones que publica ms-pagos en Kafka, lo analiza con Kafka Streams y
 * emite alertas al topic alertas-fraude.
 */
@SpringBootApplication
public class AntifraudeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AntifraudeApplication.class, args);
    }
}

package cl.duoc.cafeteria.antifraude.messaging.consumer.alerta;

import cl.duoc.cafeteria.antifraude.service.AlertasRecientesService;
import cl.duoc.cafeteria.common.evento.AlertaFraudeEvent;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Lee alertas-fraude con su PROPIO consumer group (antifraude-monitor), asi
 * recibe todas las alertas aunque ms-notificaciones tambien las consuma con
 * el suyo. Solo las guarda en memoria para GET /api/antifraude/alertas. Los
 * errores los maneja config/KafkaConsumerConfig (registrar y continuar).
 */
@Component
public class AlertasFraudeListener {

    private static final Logger log = LoggerFactory.getLogger(AlertasFraudeListener.class);

    private final AlertasRecientesService alertasRecientes;

    public AlertasFraudeListener(AlertasRecientesService alertasRecientes) {
        this.alertasRecientes = alertasRecientes;
    }

    @KafkaListener(topics = "${app.kafka.topics.alertas-fraude}", groupId = "${app.kafka.group-ids.monitor}")
    public void recibir(@Valid @Payload AlertaFraudeEvent alerta,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
            @Header(KafkaHeaders.OFFSET) long offset) {
        log.debug("Alerta {} recibida (particion {}, offset {})", alerta.eventId(), particion, offset);
        alertasRecientes.registrar(alerta);
    }
}

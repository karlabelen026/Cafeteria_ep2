package cl.duoc.cafeteria.antifraude.messaging.consumer.alerta;

import cl.duoc.cafeteria.antifraude.service.AlertasRecientesService;
import cl.duoc.cafeteria.common.evento.AlertaFraudeEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Lee alertas-fraude con su PROPIO consumer group (antifraude-monitor), asi
 * recibe todas las alertas aunque ms-notificaciones tambien las consuma con
 * el suyo. Solo las guarda en memoria para GET /api/antifraude/alertas.
 */
@Component
public class AlertasFraudeListener {

    private final AlertasRecientesService alertasRecientes;

    public AlertasFraudeListener(AlertasRecientesService alertasRecientes) {
        this.alertasRecientes = alertasRecientes;
    }

    @KafkaListener(topics = "${app.kafka.topics.alertas-fraude}", groupId = "${app.kafka.group-ids.monitor}")
    public void recibir(AlertaFraudeEvent alerta) {
        alertasRecientes.registrar(alerta);
    }
}

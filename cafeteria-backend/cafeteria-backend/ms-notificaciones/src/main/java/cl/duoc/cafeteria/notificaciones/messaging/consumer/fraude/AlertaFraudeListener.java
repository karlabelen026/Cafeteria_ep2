package cl.duoc.cafeteria.notificaciones.messaging.consumer.fraude;

import cl.duoc.cafeteria.common.evento.AlertaFraudeEvent;
import cl.duoc.cafeteria.notificaciones.model.EventoProcesado;
import cl.duoc.cafeteria.notificaciones.repository.EventoProcesadoRepository;
import cl.duoc.cafeteria.notificaciones.service.NotificacionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Consume alertas-fraude (Kafka, Fase 8) y registra cada una como alerta
 * FRAUDE del dashboard, igual que las de stock bajo que llegan por RabbitMQ.
 *
 * Errores (ver KafkaConsumerConfig): el payload se valida con @Valid (si no
 * pasa, va directo a la DLT); cualquier otra excepcion se reintenta con
 * backoff exponencial y, agotados los reintentos, tambien va a la DLT.
 * Idempotente por eventId (tabla eventos_procesados): una alerta que llegue
 * dos veces (reintento o reinyeccion desde la DLT) se registra una sola vez.
 */
@Component
public class AlertaFraudeListener {

    private static final Logger log = LoggerFactory.getLogger(AlertaFraudeListener.class);

    private final NotificacionService notificacionService;
    private final EventoProcesadoRepository eventoProcesadoRepository;

    public AlertaFraudeListener(NotificacionService notificacionService,
            EventoProcesadoRepository eventoProcesadoRepository) {
        this.notificacionService = notificacionService;
        this.eventoProcesadoRepository = eventoProcesadoRepository;
    }

    @KafkaListener(topics = "${app.kafka.topics.alertas-fraude}", groupId = "${app.kafka.group-ids.notificaciones}")
    public void recibir(@Valid @Payload AlertaFraudeEvent alerta) {
        if (eventoProcesadoRepository.existsById(alerta.eventId())) {
            log.info("Alerta de fraude {} ya fue procesada antes, se descarta (idempotencia)", alerta.eventId());
            return;
        }
        notificacionService.registrarAlertaFraude(alerta);
        eventoProcesadoRepository.save(new EventoProcesado(alerta.eventId(), Instant.now()));
    }
}

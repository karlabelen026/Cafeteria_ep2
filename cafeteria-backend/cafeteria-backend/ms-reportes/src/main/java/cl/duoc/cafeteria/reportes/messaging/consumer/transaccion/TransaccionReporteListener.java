package cl.duoc.cafeteria.reportes.messaging.consumer.transaccion;

import cl.duoc.cafeteria.common.evento.TransaccionPagoEvent;
import cl.duoc.cafeteria.reportes.service.MetricasTiempoRealService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consume cafeteria.pagos.transacciones con el grupo reportes-tiempo-real, en
 * paralelo al grupo de ms-antifraude: cada grupo recibe todas las
 * transacciones. Solo delega en el service; los reintentos y la DLT los maneja
 * el DefaultErrorHandler de KafkaReportesConfig.
 */
@Component
public class TransaccionReporteListener {

    private final MetricasTiempoRealService service;

    public TransaccionReporteListener(MetricasTiempoRealService service) {
        this.service = service;
    }

    @KafkaListener(topics = "${app.kafka.topics.transacciones}", groupId = "${app.kafka.group-ids.reportes}")
    public void onTransaccion(@Payload TransaccionPagoEvent evento,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
            @Header(KafkaHeaders.OFFSET) long offset) {
        service.registrar(evento, particion, offset);
    }
}

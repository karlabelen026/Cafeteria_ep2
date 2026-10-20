package cl.duoc.cafeteria.pagos.messaging.producer;

import cl.duoc.cafeteria.common.evento.TransaccionPagoEvent;
import cl.duoc.cafeteria.pagos.messaging.config.KafkaTopicsProperties;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Prueba del productor de transacciones hacia Kafka (Fase 8): usa el topic
 * configurado, key = cliente, y nunca propaga un error de Kafka al pago.
 */
@ExtendWith(MockitoExtension.class)
class KafkaTransaccionStreamPublisherTest {

    private static final String TOPIC = "cafeteria.pagos.transacciones";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaTransaccionStreamPublisher publisher;

    @BeforeEach
    void setUp() {
        KafkaTopicsProperties props = new KafkaTopicsProperties();
        props.setTopics(Map.of("transacciones", TOPIC));
        publisher = new KafkaTransaccionStreamPublisher(kafkaTemplate, props);
    }

    private TransaccionPagoEvent transaccion() {
        return new TransaccionPagoEvent(UUID.randomUUID(), Instant.now(), 1, 10L, 20L,
                "juan@mail.cl", 60000.0, "CREDITO", true);
    }

    @Test
    void publicar_enviaAlTopicDeTransaccionesConKeyIgualAlCliente() {
        TransaccionPagoEvent evento = transaccion();
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(new CompletableFuture<SendResult<String, Object>>());

        publisher.publicar(evento);

        verify(kafkaTemplate).send(eq(TOPIC), eq("juan@mail.cl"), eq(evento));
    }

    @Test
    void publicar_siElEnvioAsincronoFalla_noPropagaLaExcepcion() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker caido")));

        assertThatCode(() -> publisher.publicar(transaccion())).doesNotThrowAnyException();
    }

    @Test
    void publicar_siKafkaNoEstaDisponibleAlEncolar_noPropagaLaExcepcion() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new org.apache.kafka.common.KafkaException("sin metadata del topic"));

        assertThatCode(() -> publisher.publicar(transaccion())).doesNotThrowAnyException();
    }
}

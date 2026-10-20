package cl.duoc.cafeteria.notificaciones.messaging.config;

import cl.duoc.cafeteria.common.exception.NonRecoverableMessageException;
import cl.duoc.cafeteria.common.exception.RecoverableMessageException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.core.MethodParameter;
import cl.duoc.cafeteria.common.evento.AlertaFraudeEvent;
import cl.duoc.cafeteria.notificaciones.messaging.consumer.fraude.AlertaFraudeListener;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Politica de errores del consumidor de alertas-fraude (guia 3.3.3): lo no
 * recuperable y lo invalido va directo a la DLT (misma particion); lo
 * transitorio se reintenta en vez de mandarse a la DLT al primer fallo.
 */
@ExtendWith(MockitoExtension.class)
class KafkaErrorHandlerTest {

    private static final String DLT = "alertas-fraude.DLT";

    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Mock
    private Consumer<?, ?> consumer;

    @Mock
    private MessageListenerContainer container;

    private DefaultErrorHandler errorHandler;
    private final ConsumerRecord<Object, Object> registro =
            new ConsumerRecord<>("alertas-fraude", 2, 15L, "juan@mail.cl", "{}");

    @BeforeEach
    void setUp() {
        KafkaTopicsProperties props = new KafkaTopicsProperties();
        props.setTopics(Map.of("alertas-fraude", "alertas-fraude", "alertas-fraude-dlt", DLT));
        props.getReintentos().setIntervaloInicialMs(1);
        errorHandler = new KafkaConsumerConfig().kafkaErrorHandler(kafkaTemplate, props);
        lenient().when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    private ListenerExecutionFailedException fallo(Exception causa) {
        return new ListenerExecutionFailedException("fallo en el listener", causa);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ProducerRecord<Object, Object> enviadoALaDlt() {
        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void errorNoRecuperable_vaDirectoALaDltEnLaMismaParticion() {
        boolean recuperado = errorHandler.handleOne(
                fallo(new NonRecoverableMessageException("dato corrupto")), registro, consumer, container);

        assertThat(recuperado).isTrue();
        ProducerRecord<Object, Object> enviado = enviadoALaDlt();
        assertThat(enviado.topic()).isEqualTo(DLT);
        assertThat(enviado.partition()).isEqualTo(2);
    }

    @Test
    void payloadInvalido_vaDirectoALaDltSinReintentos() throws NoSuchMethodException {
        // La misma excepcion que lanza @Valid cuando el payload no pasa la validacion.
        MethodParameter parametro = new MethodParameter(
                AlertaFraudeListener.class.getMethod("recibir", AlertaFraudeEvent.class), 0);
        MethodArgumentNotValidException invalido = new MethodArgumentNotValidException(
                MessageBuilder.withPayload("{}").build(), parametro);

        boolean recuperado = errorHandler.handleOne(fallo(invalido), registro, consumer, container);

        assertThat(recuperado).isTrue();
        assertThat(enviadoALaDlt().topic()).isEqualTo(DLT);
    }

    @Test
    void errorTransitorio_seReintentaEnVezDeIrALaDlt() {
        boolean recuperado = errorHandler.handleOne(
                fallo(new RecoverableMessageException("base de datos caida")), registro, consumer, container);

        assertThat(recuperado).isFalse();
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }
}

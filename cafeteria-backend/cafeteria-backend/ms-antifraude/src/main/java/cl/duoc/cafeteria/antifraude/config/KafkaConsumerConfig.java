package cl.duoc.cafeteria.antifraude.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListenerConfigurer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Manejo de errores del consumidor propio de alertas-fraude (grupo
 * antifraude-monitor). Esa lista de alertas es solo una vista en memoria: el
 * dato original sigue en el topic y ms-notificaciones ya tiene su propia DLT,
 * asi que aqui un mensaje que no se puede procesar se registra y se salta, sin
 * detener al consumidor. Payload invalido o JSON corrupto: sin reintentos.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);
    private static final long INTERVALO_REINTENTO_MS = 1000;
    private static final long MAXIMO_REINTENTOS = 2;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (registro, error) -> log.error("Alerta descartada de la vista: topic={}, particion={}, offset={}: {}",
                        registro.topic(), registro.partition(), registro.offset(), error.getMessage()),
                new FixedBackOff(INTERVALO_REINTENTO_MS, MAXIMO_REINTENTOS));
        errorHandler.addNotRetryableExceptions(MethodArgumentNotValidException.class);
        return errorHandler;
    }

    /** Habilita @Valid en los parametros de los @KafkaListener (Bean Validation). */
    @Bean
    public KafkaListenerConfigurer validacionKafkaListeners(LocalValidatorFactoryBean validator) {
        return registrar -> registrar.setValidator(validator);
    }
}

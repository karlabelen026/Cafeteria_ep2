package cl.duoc.cafeteria.notificaciones.messaging.config;

import cl.duoc.cafeteria.common.exception.NonRecoverableMessageException;
import jakarta.validation.ConstraintViolationException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListenerConfigurer;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consumo de Kafka de ms-notificaciones (Fase 8) y su manejo de errores
 * (guia 3.3.3):
 *
 * CONSUME:
 *   alertas-fraude (group notificaciones-antifraude) -> AlertaFraudeListener
 *     error transitorio  -> reintento con backoff exponencial (1s, 2s, 4s)
 *     agotados / no recuperable / payload invalido -> alertas-fraude.DLT
 *
 * El topic principal lo crea ms-antifraude (su productor); aqui solo se
 * declara la DLT, que es de este consumidor.
 */
@Configuration
@EnableConfigurationProperties(KafkaTopicsProperties.class)
public class KafkaConsumerConfig {

    @Bean
    public NewTopic alertasFraudeDltTopic(KafkaTopicsProperties props) {
        return TopicBuilder.name(props.topic("alertas-fraude-dlt"))
                .partitions(props.getParticiones())
                .replicas(props.getReplicas())
                .config(TopicConfig.RETENTION_MS_CONFIG,
                        String.valueOf(props.getRetencionMs().get("alertas-fraude-dlt")))
                .build();
    }

    /**
     * Productor para la DLT y la reinyeccion. Un registro que fallo al
     * deserializar llega como byte[] (el JSON original, se reenvia tal cual);
     * uno que fallo en el listener llega como objeto y se serializa a JSON.
     */
    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    public KafkaTemplate<Object, Object> kafkaTemplate(KafkaProperties kafkaProperties) {
        Map<Class<?>, Serializer<?>> porTipo = new LinkedHashMap<>();
        porTipo.put(byte[].class, new ByteArraySerializer());
        porTipo.put(Object.class, new JsonSerializer<>().noTypeInfo());
        DefaultKafkaProducerFactory<Object, Object> factory = new DefaultKafkaProducerFactory<>(
                kafkaProperties.buildProducerProperties(null),
                (Serializer) new StringSerializer(),
                new DelegatingByTypeSerializer(porTipo, true));
        return new KafkaTemplate<>(factory);
    }

    /**
     * Spring Boot lo aplica a todos los @KafkaListener. Reintenta errores
     * transitorios con backoff exponencial y, al agotarlos, publica el registro
     * en la DLT (misma particion). NonRecoverableMessageException y los
     * payloads que no pasan @Valid van directo a la DLT, sin reintentos.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate,
            KafkaTopicsProperties props) {
        String dlt = props.topic("alertas-fraude-dlt");
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (registro, error) -> new TopicPartition(dlt, registro.partition()));
        // La DLT se declara con las mismas particiones que el topic principal
        // (alertasFraudeDltTopic): no hace falta consultar su metadata antes
        // de cada envio.
        recoverer.setVerifyPartition(false);

        KafkaTopicsProperties.Reintentos reintentos = props.getReintentos();
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(reintentos.getMaximo());
        backOff.setInitialInterval(reintentos.getIntervaloInicialMs());
        backOff.setMultiplier(reintentos.getMultiplicador());

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // Datos invalidos (payload que no pasa @Valid, o entidad que no pasa
        // Bean Validation al guardarse) no se arreglan reintentando.
        errorHandler.addNotRetryableExceptions(NonRecoverableMessageException.class,
                MethodArgumentNotValidException.class, ConstraintViolationException.class);
        return errorHandler;
    }

    /** Habilita @Valid en los parametros de los @KafkaListener (Bean Validation). */
    @Bean
    public KafkaListenerConfigurer validacionKafkaListeners(LocalValidatorFactoryBean validator) {
        return registrar -> registrar.setValidator(validator);
    }
}

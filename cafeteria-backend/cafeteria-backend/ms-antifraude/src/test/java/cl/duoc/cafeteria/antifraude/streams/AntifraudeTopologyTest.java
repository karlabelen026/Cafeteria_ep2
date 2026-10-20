package cl.duoc.cafeteria.antifraude.streams;

import cl.duoc.cafeteria.antifraude.config.AntifraudeProperties;
import cl.duoc.cafeteria.antifraude.config.KafkaTopicsProperties;
import cl.duoc.cafeteria.common.evento.AlertaFraudeEvent;
import cl.duoc.cafeteria.common.evento.TransaccionPagoEvent;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba la topologia completa con TopologyTestDriver: sin broker, sin red,
 * con el tiempo de cada evento controlado por la prueba.
 */
class AntifraudeTopologyTest {

    private static final String TRANSACCIONES = "transacciones";
    private static final String TOTALES = "totales";
    private static final String ALERTAS = "alertas-fraude";
    private static final String CLIENTE = "juan@mail.cl";
    private static final Instant INICIO = Instant.parse("2026-10-20T12:00:00Z");

    private TopologyTestDriver driver;
    private TestInputTopic<String, TransaccionPagoEvent> entrada;
    private TestOutputTopic<String, AlertaFraudeEvent> alertas;
    private AntifraudeProperties reglas;

    @BeforeEach
    void setUp() {
        KafkaTopicsProperties topics = new KafkaTopicsProperties();
        topics.setTopics(Map.of("transacciones", TRANSACCIONES, "totales", TOTALES, "alertas-fraude", ALERTAS));
        reglas = new AntifraudeProperties();

        StreamsBuilder builder = new StreamsBuilder();
        new AntifraudeTopology(topics, reglas).construir(builder);

        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "antifraude-test");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        config.put(StreamsConfig.STATESTORE_CACHE_MAX_BYTES_CONFIG, 0);
        driver = new TopologyTestDriver(builder.build(), config, INICIO);

        entrada = driver.createInputTopic(TRANSACCIONES, new StringSerializer(),
                AntifraudeTopology.jsonSerde(TransaccionPagoEvent.class).serializer());
        alertas = driver.createOutputTopic(ALERTAS, new StringDeserializer(),
                AntifraudeTopology.jsonSerde(AlertaFraudeEvent.class).deserializer());
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    private void enviar(double monto, boolean aprobado, Instant cuando) {
        entrada.pipeInput(CLIENTE, new TransaccionPagoEvent(UUID.randomUUID(), cuando, 1, 1L, 1L,
                CLIENTE, monto, "CREDITO", aprobado), cuando);
    }

    private KeyValueStore<String, TotalCliente> totales() {
        return driver.getKeyValueStore(reglas.getStoreTotales());
    }

    @Test
    void transaccionMenorAlMinimo_seFiltraYNoSeContabiliza() {
        enviar(5, true, INICIO);

        assertThat(alertas.isEmpty()).isTrue();
        assertThat(totales().get(CLIENTE)).isNull();
    }

    @Test
    void transaccionNormal_noGeneraAlerta() {
        enviar(500, true, INICIO);

        assertThat(alertas.isEmpty()).isTrue();
        assertThat(totales().get(CLIENTE).totalClp()).isEqualTo(500);
    }

    @Test
    void transaccionSobreElUmbral_generaAlertaAlta() {
        enviar(60000, true, INICIO);

        AlertaFraudeEvent alerta = alertas.readValue();
        assertThat(alerta.severidad()).isEqualTo(AlertaFraudeEvent.SEVERIDAD_ALTA);
        assertThat(alerta.cliente()).isEqualTo(CLIENTE);
        assertThat(alerta.motivo()).isEqualTo("Transacción inusualmente alta vía CREDITO");
        assertThat(alerta.monto()).isEqualTo(60000);
        assertThat(alertas.isEmpty()).isTrue();
    }

    @Test
    void pagoRechazado_generaAlertaMedia() {
        enviar(3000, false, INICIO);

        AlertaFraudeEvent alerta = alertas.readValue();
        assertThat(alerta.severidad()).isEqualTo(AlertaFraudeEvent.SEVERIDAD_MEDIA);
        assertThat(alerta.motivo()).startsWith("Pago rechazado");
        // Un pago rechazado no suma al total gastado.
        assertThat(totales().get(CLIENTE)).isNull();
    }

    @Test
    void seisPagosEnCincoMinutos_generanAlertaDeRafaga() {
        for (int i = 0; i < 6; i++) {
            enviar(500, true, INICIO.plus(Duration.ofSeconds(30L * i)));
        }

        List<AlertaFraudeEvent> generadas = alertas.readValuesToList();
        assertThat(generadas).hasSize(1);
        assertThat(generadas.get(0).motivo()).startsWith("Ráfaga de pagos");
        assertThat(generadas.get(0).severidad()).isEqualTo(AlertaFraudeEvent.SEVERIDAD_MEDIA);
    }

    @Test
    void cincoPagosEnLaVentana_noGeneranRafaga() {
        for (int i = 0; i < 5; i++) {
            enviar(500, true, INICIO.plus(Duration.ofSeconds(30L * i)));
        }

        assertThat(alertas.isEmpty()).isTrue();
    }

    @Test
    void totalPorCliente_seAcumulaYSeConvierteAUsd() {
        enviar(1000, true, INICIO);
        enviar(2000, true, INICIO.plusSeconds(10));

        TotalCliente total = totales().get(CLIENTE);
        assertThat(total.totalClp()).isEqualTo(3000);
        assertThat(total.cantidadTransacciones()).isEqualTo(2);
        // 1000/950 = 1.05 + 2000/950 = 2.11 -> 3.16 USD
        assertThat(total.totalUsd()).isEqualTo(3.16);
    }

    @Test
    void alertaAlta_traeElMontoConvertidoAUsd() {
        enviar(60000, true, INICIO);

        // 60000 / 950 = 63.157... -> 63.16
        assertThat(alertas.readValue().montoUsd()).isEqualTo(63.16);
    }
}

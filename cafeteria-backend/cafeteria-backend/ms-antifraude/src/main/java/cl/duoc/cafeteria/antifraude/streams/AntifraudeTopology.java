package cl.duoc.cafeteria.antifraude.streams;

import cl.duoc.cafeteria.antifraude.config.AntifraudeProperties;
import cl.duoc.cafeteria.antifraude.config.KafkaTopicsProperties;
import cl.duoc.cafeteria.common.evento.AlertaFraudeEvent;
import cl.duoc.cafeteria.common.evento.TransaccionPagoEvent;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Topologia de Kafka Streams del monitoreo antifraude (guia 3.2.2 adaptada a
 * la cafeteria). Source -> Process -> Sink, paso a paso:
 *
 * <pre>
 *  cafeteria.pagos.transacciones (key = cliente)
 *    1. Source         lee TransaccionPagoEvent
 *    2. Filtro         descarta montos menores a app.antifraude.monto-minimo
 *    3. Enriquecer     agrega montoUsd (app.antifraude.tasa-clp-usd)
 *    4. Deteccion      split(): monto alto -> ALTA; pago rechazado -> MEDIA
 *    5. Agregacion     total gastado por cliente (KTable, store total-por-cliente)
 *                      --> cafeteria.antifraude.totales-por-cliente (compactado)
 *    6. Ventana        conteo por cliente en ventanas tumbling de 5 min;
 *                      mas de app.antifraude.max-transacciones-ventana -> MEDIA "Rafaga"
 *    7. Sink           todas las alertas --> alertas-fraude (key = cliente)
 * </pre>
 *
 * Los state stores son en memoria (respaldados por su topic changelog en
 * Kafka, que los reconstruye al reiniciar): evitan depender de RocksDB nativo
 * y de disco dentro del contenedor.
 */
@Component
public class AntifraudeTopology {

    private static final Logger log = LoggerFactory.getLogger(AntifraudeTopology.class);

    private final KafkaTopicsProperties topics;
    private final AntifraudeProperties reglas;

    public AntifraudeTopology(KafkaTopicsProperties topics, AntifraudeProperties reglas) {
        this.topics = topics;
        this.reglas = reglas;
    }

    @Autowired
    public void construir(StreamsBuilder builder) {
        Serde<String> claveSerde = Serdes.String();
        Serde<TransaccionPagoEvent> transaccionSerde = jsonSerde(TransaccionPagoEvent.class);
        Serde<TransaccionEnriquecida> enriquecidaSerde = jsonSerde(TransaccionEnriquecida.class);
        Serde<TotalCliente> totalSerde = jsonSerde(TotalCliente.class);
        Serde<AlertaFraudeEvent> alertaSerde = jsonSerde(AlertaFraudeEvent.class);

        // 1. Source
        KStream<String, TransaccionPagoEvent> transacciones = builder.stream(
                topics.topic("transacciones"), Consumed.with(claveSerde, transaccionSerde));

        // 2. Filtro + 3. Enriquecimiento
        KStream<String, TransaccionEnriquecida> enriquecidas = transacciones
                .filter((cliente, t) -> t != null && t.monto() >= reglas.getMontoMinimo(), Named.as("filtro-monto-minimo"))
                .mapValues((cliente, t) -> enriquecer(cliente, t), Named.as("enriquecer-usd"))
                .peek((cliente, t) -> log.info("✅ Transaccion procesada: cliente={} monto={} CLP (~{} USD) via {}",
                        cliente, t.monto(), t.montoUsd(), t.metodoPago()));

        // 4. Deteccion por reglas
        Map<String, KStream<String, TransaccionEnriquecida>> ramas = enriquecidas
                .split(Named.as("deteccion-"))
                .branch((cliente, t) -> t.monto() > reglas.getUmbralAlto(), Branched.as("monto-alto"))
                .branch((cliente, t) -> !t.aprobado(), Branched.as("rechazada"))
                .noDefaultBranch();

        KStream<String, AlertaFraudeEvent> alertasMontoAlto = ramas.get("deteccion-monto-alto")
                .mapValues(t -> alerta(t, AlertaFraudeEvent.SEVERIDAD_ALTA,
                        "Transacción inusualmente alta vía " + t.metodoPago()));
        KStream<String, AlertaFraudeEvent> alertasRechazo = ramas.get("deteccion-rechazada")
                .mapValues(t -> alerta(t, AlertaFraudeEvent.SEVERIDAD_MEDIA,
                        "Pago rechazado vía " + t.metodoPago()));

        // 5. Agregacion (stateful): total gastado por cliente, solo pagos aprobados.
        KTable<String, TotalCliente> totales = enriquecidas
                .filter((cliente, t) -> t.aprobado(), Named.as("solo-aprobadas"))
                .groupByKey(Grouped.with(claveSerde, enriquecidaSerde))
                .aggregate(TotalCliente::vacio,
                        (cliente, t, total) -> total.sumar(cliente, t),
                        Materialized.<String, TotalCliente>as(Stores.inMemoryKeyValueStore(reglas.getStoreTotales()))
                                .withKeySerde(claveSerde)
                                .withValueSerde(totalSerde));
        totales.toStream().to(topics.topic("totales"), Produced.with(claveSerde, totalSerde));

        // 6. Windowing: cuantas transacciones hace cada cliente en cada ventana.
        Duration ventana = Duration.ofMinutes(reglas.getVentanaMinutos());
        KStream<String, AlertaFraudeEvent> alertasRafaga = enriquecidas
                .groupByKey(Grouped.with(claveSerde, enriquecidaSerde))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(ventana))
                .count(Materialized.<String, Long>as(
                                Stores.inMemoryWindowStore(reglas.getStoreRafagas(), ventana.multipliedBy(2), ventana, false))
                        .withKeySerde(claveSerde)
                        .withValueSerde(Serdes.Long()))
                .toStream()
                // Una sola alerta por ventana: justo cuando se pasa del maximo.
                .filter((v, cantidad) -> cantidad != null && cantidad == reglas.getMaxTransaccionesVentana() + 1)
                .map((v, cantidad) -> KeyValue.pair(v.key(), alertaRafaga(v.key(), cantidad)));

        // 7. Sink
        alertasMontoAlto.merge(alertasRechazo).merge(alertasRafaga)
                .peek((cliente, a) -> log.warn("🚨 Alerta {} para {}: {}", a.severidad(), cliente, a.motivo()))
                .to(topics.topic("alertas-fraude"), Produced.with(claveSerde, alertaSerde));
    }

    private TransaccionEnriquecida enriquecer(String cliente, TransaccionPagoEvent t) {
        return new TransaccionEnriquecida(t.eventId(), t.ocurridoEn(), t.pagoId(),
                TransaccionPagoEvent.clienteOMostrador(cliente), t.monto(),
                Montos.aUsd(t.monto(), reglas.getTasaClpUsd()), t.metodoPago(), t.aprobado());
    }

    private AlertaFraudeEvent alerta(TransaccionEnriquecida t, String severidad, String motivo) {
        return new AlertaFraudeEvent(UUID.randomUUID(), Instant.now(), t.cliente(), severidad, motivo,
                t.monto(), t.montoUsd(), t.metodoPago(), t.pagoId());
    }

    private AlertaFraudeEvent alertaRafaga(String cliente, long cantidad) {
        return new AlertaFraudeEvent(UUID.randomUUID(), Instant.now(), cliente, AlertaFraudeEvent.SEVERIDAD_MEDIA,
                "Ráfaga de pagos: " + cantidad + " transacciones en " + reglas.getVentanaMinutos() + " minutos",
                0, 0, null, null);
    }

    /** Serde JSON sin headers de tipo: cada topic tiene un solo tipo conocido. */
    static <T> JsonSerde<T> jsonSerde(Class<T> tipo) {
        return new JsonSerde<>(tipo, JacksonUtils.enhancedObjectMapper()).noTypeInfo().ignoreTypeHeaders();
    }
}

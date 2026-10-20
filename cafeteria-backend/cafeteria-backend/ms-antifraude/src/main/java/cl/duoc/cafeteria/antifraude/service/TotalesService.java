package cl.duoc.cafeteria.antifraude.service;

import cl.duoc.cafeteria.antifraude.config.AntifraudeProperties;
import cl.duoc.cafeteria.antifraude.exception.RecursoNoEncontradoException;
import cl.duoc.cafeteria.antifraude.exception.ServicioNoDisponibleException;
import cl.duoc.cafeteria.antifraude.streams.TotalCliente;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.errors.InvalidStateStoreException;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Interactive Queries: lee el total gastado por cliente directamente del
 * state store de Kafka Streams (total-por-cliente), sin pasar por ninguna
 * base de datos. Si la aplicacion de Streams aun no esta RUNNING (arrancando
 * o rebalanceando), responde 503.
 */
@Service
public class TotalesService {

    private final StreamsBuilderFactoryBean streamsFactory;
    private final AntifraudeProperties reglas;

    public TotalesService(StreamsBuilderFactoryBean streamsFactory, AntifraudeProperties reglas) {
        this.streamsFactory = streamsFactory;
        this.reglas = reglas;
    }

    /** Ranking: de mayor a menor total gastado. */
    public List<TotalCliente> listar() {
        List<TotalCliente> totales = new ArrayList<>();
        try (KeyValueIterator<String, TotalCliente> it = store().all()) {
            while (it.hasNext()) {
                KeyValue<String, TotalCliente> par = it.next();
                totales.add(par.value);
            }
        }
        totales.sort(Comparator.comparingDouble(TotalCliente::totalClp).reversed());
        return totales;
    }

    public TotalCliente obtener(String cliente) {
        TotalCliente total = store().get(cliente);
        if (total == null) {
            throw new RecursoNoEncontradoException("No hay transacciones registradas para el cliente " + cliente);
        }
        return total;
    }

    private ReadOnlyKeyValueStore<String, TotalCliente> store() {
        KafkaStreams streams = streamsFactory.getKafkaStreams();
        if (streams == null || streams.state() != KafkaStreams.State.RUNNING) {
            throw new ServicioNoDisponibleException(
                    "Kafka Streams todavia no esta listo (estado: " + (streams == null ? "detenido" : streams.state()) + ")");
        }
        try {
            return streams.store(StoreQueryParameters.fromNameAndType(
                    reglas.getStoreTotales(), QueryableStoreTypes.keyValueStore()));
        } catch (InvalidStateStoreException e) {
            throw new ServicioNoDisponibleException("El state store de totales no esta disponible: " + e.getMessage());
        }
    }
}

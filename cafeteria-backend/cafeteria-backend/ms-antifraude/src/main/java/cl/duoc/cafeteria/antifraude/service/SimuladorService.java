package cl.duoc.cafeteria.antifraude.service;

import cl.duoc.cafeteria.antifraude.dto.CargaResponse;
import cl.duoc.cafeteria.antifraude.dto.TransaccionSimuladaRequest;
import cl.duoc.cafeteria.antifraude.exception.ServicioNoDisponibleException;
import cl.duoc.cafeteria.antifraude.messaging.producer.TransaccionPublisher;
import cl.duoc.cafeteria.common.evento.TransaccionPagoEvent;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Simulador de transacciones (guia 3.2.2, SimuladorController) y generador de
 * carga (guia 3.3.2): publica transacciones de prueba en el mismo topic que
 * usa ms-pagos, para ver la topologia trabajar sin pasar por la tienda.
 */
@Service
public class SimuladorService {

    private static final List<String> CLIENTES_CARGA = List.of(
            "ana@mail.cl", "juan@mail.cl", "camila@mail.cl", "diego@mail.cl", "sofia@mail.cl",
            "matias@mail.cl", "valentina@mail.cl", "benjamin@mail.cl", "isidora@mail.cl", "mostrador");
    private static final List<String> METODOS = List.of("DEBITO", "CREDITO", "EFECTIVO", "TRANSFERENCIA");
    private static final long ESPERA_CONFIRMACION_SEGUNDOS = 10;
    /**
     * La carga mide rendimiento con transacciones NORMALES (montos de consumo
     * de una cafeteria, todas aprobadas): asi no inunda el dashboard de
     * alertas. Solo puede disparar la regla de rafaga, una vez por cliente
     * y ventana.
     */
    private static final int MONTO_MINIMO_CARGA = 1_500;
    private static final int MONTO_MAXIMO_CARGA = 20_000;

    private final TransaccionPublisher publisher;

    public SimuladorService(TransaccionPublisher publisher) {
        this.publisher = publisher;
    }

    public TransaccionPagoEvent simular(TransaccionSimuladaRequest request) {
        TransaccionPagoEvent evento = transaccion(request.clienteEmail(), request.monto(), request.metodoPago(),
                request.aprobado() == null || request.aprobado());
        esperar(publisher.publicar(evento));
        return evento;
    }

    /**
     * Publica "cantidad" transacciones aleatorias tan rapido como el productor
     * permita (batching + compresion) y mide cuanto tarda hasta que Kafka las
     * confirmo todas (guia 3.3.2).
     */
    public CargaResponse generarCarga(int cantidad) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long inicio = System.nanoTime();
        CompletableFuture<?>[] envios = new CompletableFuture<?>[cantidad];
        for (int i = 0; i < cantidad; i++) {
            TransaccionPagoEvent evento = transaccion(
                    CLIENTES_CARGA.get(random.nextInt(CLIENTES_CARGA.size())),
                    random.nextInt(MONTO_MINIMO_CARGA, MONTO_MAXIMO_CARGA),
                    METODOS.get(random.nextInt(METODOS.size())),
                    true);
            envios[i] = publicar(evento);
        }
        publisher.vaciar();
        esperar(CompletableFuture.allOf(envios));
        long milisegundos = Math.max(1, (System.nanoTime() - inicio) / 1_000_000);
        return new CargaResponse(cantidad, milisegundos, Math.round(cantidad * 1000.0 / milisegundos));
    }

    private CompletableFuture<Void> publicar(TransaccionPagoEvent evento) {
        try {
            return publisher.publicar(evento);
        } catch (RuntimeException e) {
            throw new ServicioNoDisponibleException("Kafka no esta disponible: " + e.getMessage());
        }
    }

    private void esperar(CompletableFuture<?> futuro) {
        try {
            futuro.get(ESPERA_CONFIRMACION_SEGUNDOS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServicioNoDisponibleException("Se interrumpio el envio a Kafka");
        } catch (ExecutionException | TimeoutException e) {
            throw new ServicioNoDisponibleException("Kafka no confirmo el envio: " + e.getMessage());
        }
    }

    private TransaccionPagoEvent transaccion(String cliente, double monto, String metodoPago, boolean aprobado) {
        return new TransaccionPagoEvent(UUID.randomUUID(), Instant.now(), 1, null, null,
                TransaccionPagoEvent.clienteOMostrador(cliente), monto, metodoPago, aprobado);
    }
}

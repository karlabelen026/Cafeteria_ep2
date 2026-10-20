package cl.duoc.cafeteria.antifraude.service;

import cl.duoc.cafeteria.antifraude.config.AntifraudeProperties;
import cl.duoc.cafeteria.common.evento.AlertaFraudeEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Ultimas alertas de fraude recibidas, en memoria (como maximo
 * app.antifraude.max-alertas-memoria). El historial completo vive en el topic
 * alertas-fraude (30 dias de retencion): al reiniciar, el consumidor vuelve a
 * leer desde su ultimo offset confirmado.
 */
@Service
public class AlertasRecientesService {

    private final Deque<AlertaFraudeEvent> alertas = new ConcurrentLinkedDeque<>();
    private final int maximo;

    public AlertasRecientesService(AntifraudeProperties reglas) {
        this.maximo = reglas.getMaxAlertasMemoria();
    }

    public void registrar(AlertaFraudeEvent alerta) {
        alertas.addFirst(alerta);
        while (alertas.size() > maximo) {
            alertas.pollLast();
        }
    }

    /** Las mas recientes primero. */
    public List<AlertaFraudeEvent> ultimas(int cantidad) {
        return new ArrayList<>(alertas).stream().limit(cantidad).toList();
    }
}

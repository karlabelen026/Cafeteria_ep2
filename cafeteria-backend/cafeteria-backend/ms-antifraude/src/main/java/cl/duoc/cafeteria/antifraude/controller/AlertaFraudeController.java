package cl.duoc.cafeteria.antifraude.controller;

import cl.duoc.cafeteria.antifraude.service.AlertasRecientesService;
import cl.duoc.cafeteria.common.evento.AlertaFraudeEvent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Ultimas alertas de fraude (las mas recientes primero). Solo ADMIN. */
@RestController
@RequestMapping("/api/antifraude/alertas")
@PreAuthorize("hasAuthority(T(cl.duoc.cafeteria.antifraude.security.Roles).ADMIN)")
public class AlertaFraudeController {

    private final AlertasRecientesService alertasRecientes;

    public AlertaFraudeController(AlertasRecientesService alertasRecientes) {
        this.alertasRecientes = alertasRecientes;
    }

    @GetMapping
    public List<AlertaFraudeEvent> listar(
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "cantidad debe ser al menos 1")
            @Max(value = 200, message = "cantidad no puede superar 200")
            int cantidad) {
        return alertasRecientes.ultimas(cantidad);
    }
}

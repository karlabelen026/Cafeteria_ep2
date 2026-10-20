package cl.duoc.cafeteria.notificaciones.controller;

import cl.duoc.cafeteria.notificaciones.service.ReinyeccionDltService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Reinyecta mensajes de alertas-fraude.DLT al topic principal (Fase 8). Solo
 * ADMIN. Sin logica de Kafka: delega en ReinyeccionDltService.
 */
@RestController
@RequestMapping("/api/notificaciones/antifraude/dlt")
public class AntifraudeDltController {

    private final ReinyeccionDltService reinyeccionDltService;

    public AntifraudeDltController(ReinyeccionDltService reinyeccionDltService) {
        this.reinyeccionDltService = reinyeccionDltService;
    }

    @PostMapping("/reinyectar")
    @PreAuthorize("hasAuthority(T(cl.duoc.cafeteria.notificaciones.security.Roles).ADMIN)")
    public Map<String, Integer> reinyectar(
            @RequestParam(defaultValue = "100")
            @Min(value = 1, message = "max debe ser al menos 1")
            @Max(value = 1000, message = "max no puede superar 1000")
            int max) {
        return Map.of("reinyectados", reinyeccionDltService.reinyectar(max));
    }
}

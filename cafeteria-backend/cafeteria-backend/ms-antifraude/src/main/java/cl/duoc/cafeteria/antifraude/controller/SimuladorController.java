package cl.duoc.cafeteria.antifraude.controller;

import cl.duoc.cafeteria.antifraude.dto.CargaResponse;
import cl.duoc.cafeteria.antifraude.dto.TransaccionSimuladaRequest;
import cl.duoc.cafeteria.antifraude.service.SimuladorService;
import cl.duoc.cafeteria.common.evento.TransaccionPagoEvent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulador (guia 3.2.2) y generador de carga (guia 3.3.2). Solo ADMIN. Sin
 * logica: delega en SimuladorService.
 */
@RestController
@RequestMapping("/api/antifraude")
@PreAuthorize("hasAuthority(T(cl.duoc.cafeteria.antifraude.security.Roles).ADMIN)")
public class SimuladorController {

    private final SimuladorService simuladorService;

    public SimuladorController(SimuladorService simuladorService) {
        this.simuladorService = simuladorService;
    }

    @PostMapping("/simular")
    public ResponseEntity<TransaccionPagoEvent> simular(@Valid @RequestBody TransaccionSimuladaRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(simuladorService.simular(request));
    }

    @PostMapping("/carga")
    public CargaResponse carga(
            @RequestParam(defaultValue = "10000")
            @Min(value = 1, message = "cantidad debe ser al menos 1")
            @Max(value = 100000, message = "cantidad no puede superar 100000")
            int cantidad) {
        return simuladorService.generarCarga(cantidad);
    }
}

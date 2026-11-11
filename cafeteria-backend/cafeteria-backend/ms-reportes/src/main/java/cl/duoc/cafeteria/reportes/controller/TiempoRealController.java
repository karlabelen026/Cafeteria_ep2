package cl.duoc.cafeteria.reportes.controller;

import cl.duoc.cafeteria.reportes.dto.MetricasTiempoRealResponse;
import cl.duoc.cafeteria.reportes.dto.TransaccionAuditadaResponse;
import cl.duoc.cafeteria.reportes.service.MetricasTiempoRealService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reporteria en tiempo real y auditoria de transacciones, alimentadas por el
 * topic de Kafka cafeteria.pagos.transacciones (ver TransaccionReporteListener).
 * Sin logica de negocio: todo lo calcula MetricasTiempoRealService.
 */
@RestController
@RequestMapping("/api/reportes")
@PreAuthorize("hasAnyAuthority(T(cl.duoc.cafeteria.reportes.security.Roles).ADMIN, "
        + "T(cl.duoc.cafeteria.reportes.security.Roles).GERENTE)")
public class TiempoRealController {

    private final MetricasTiempoRealService service;

    public TiempoRealController(MetricasTiempoRealService service) {
        this.service = service;
    }

    @GetMapping("/tiempo-real")
    public MetricasTiempoRealResponse tiempoReal(@RequestParam(defaultValue = "15") int minutos) {
        return service.metricas(minutos);
    }

    @GetMapping("/auditoria/transacciones")
    public List<TransaccionAuditadaResponse> auditoria(@RequestParam(defaultValue = "50") int limite,
            @RequestParam(required = false) String cliente) {
        return service.ultimas(limite, cliente);
    }
}

package cl.duoc.cafeteria.antifraude.controller;

import cl.duoc.cafeteria.antifraude.service.TotalesService;
import cl.duoc.cafeteria.antifraude.streams.TotalCliente;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Interactive Queries sobre el state store total-por-cliente. Solo ADMIN. */
@RestController
@RequestMapping("/api/antifraude/totales")
@PreAuthorize("hasAuthority(T(cl.duoc.cafeteria.antifraude.security.Roles).ADMIN)")
public class TotalesController {

    private final TotalesService totalesService;

    public TotalesController(TotalesService totalesService) {
        this.totalesService = totalesService;
    }

    @GetMapping
    public List<TotalCliente> listar() {
        return totalesService.listar();
    }

    @GetMapping("/{cliente}")
    public TotalCliente obtener(@PathVariable String cliente) {
        return totalesService.obtener(cliente);
    }
}

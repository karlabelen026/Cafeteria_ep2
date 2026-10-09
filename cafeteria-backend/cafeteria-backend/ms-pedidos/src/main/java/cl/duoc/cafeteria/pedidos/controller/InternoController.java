package cl.duoc.cafeteria.pedidos.controller;

import cl.duoc.cafeteria.pedidos.dto.PedidoResponse;
import cl.duoc.cafeteria.pedidos.service.PedidoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint SOLO para llamadas servicio-a-servicio dentro de la red interna:
 * los consumidores de pago.aprobado (ms-inventario, ms-clientes y
 * ms-notificaciones) necesitan el detalle del pedido (items, cliente, codigo)
 * y no tienen un usuario logueado del que sacar un JWT.
 *
 * No vive bajo /api, asi que el BFF (y el API Gateway) nunca lo enrutan hacia
 * afuera, y exige el secreto compartido X-Internal-Token (ver
 * config/InternalTokenFilter). Sin logica de negocio: delega en PedidoService.
 */
@RestController
@RequestMapping("/internal/pedidos")
public class InternoController {

    private final PedidoService service;

    public InternoController(PedidoService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public PedidoResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }
}

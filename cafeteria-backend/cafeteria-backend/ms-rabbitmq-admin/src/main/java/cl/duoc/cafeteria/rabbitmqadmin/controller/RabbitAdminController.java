package cl.duoc.cafeteria.rabbitmqadmin.controller;

import cl.duoc.cafeteria.rabbitmqadmin.dto.*;
import cl.duoc.cafeteria.rabbitmqadmin.service.RabbitAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API de administracion de RabbitMQ (ver docs/EP2_PLAN.md seccion 3.8): solo
 * ADMIN (ver SecurityConfig), montada en el BFF bajo /api/rabbitmq/**. Sin
 * logica de negocio ni de RabbitMQ aqui: todo delega en RabbitAdminService.
 */
@Tag(name = "RabbitMQ admin", description = "Administracion de colas, exchanges, bindings y DLQ")
@RestController
@RequestMapping("/api/rabbitmq")
public class RabbitAdminController {

    private final RabbitAdminService service;

    public RabbitAdminController(RabbitAdminService service) {
        this.service = service;
    }

    @Operation(summary = "Lista todas las colas de RabbitMQ con su estado")
    @GetMapping("/queues")
    public List<QueueResponse> listarColas() {
        return service.listarColas();
    }

    @Operation(summary = "Obtiene el detalle de una cola por nombre")
    @GetMapping("/queues/{name}")
    public QueueResponse obtenerCola(@PathVariable String name) {
        return service.obtenerCola(name);
    }

    @Operation(summary = "Crea una cola nueva (quorum, durable)")
    @PostMapping("/queues")
    public ResponseEntity<QueueResponse> crearCola(@Valid @RequestBody CreateQueueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearCola(request));
    }

    @Operation(summary = "Elimina una cola (409 si es un recurso protegido del sistema)")
    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Void> eliminarCola(@PathVariable String name,
            @RequestParam(defaultValue = "false") boolean ifUnused,
            @RequestParam(defaultValue = "false") boolean ifEmpty) {
        service.eliminarCola(name, ifUnused, ifEmpty);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Purga (vacia) una cola sin eliminarla; devuelve cuantos mensajes se descartaron")
    @PostMapping("/queues/{name}/purge")
    public PurgeResponse purgarCola(@PathVariable String name) {
        return service.purgarCola(name);
    }

    @Operation(summary = "Lista todos los exchanges de RabbitMQ")
    @GetMapping("/exchanges")
    public List<ExchangeResponse> listarExchanges() {
        return service.listarExchanges();
    }

    @Operation(summary = "Crea un exchange nuevo")
    @PostMapping("/exchanges")
    public ResponseEntity<ExchangeResponse> crearExchange(@Valid @RequestBody CreateExchangeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearExchange(request));
    }

    @Operation(summary = "Elimina un exchange (409 si es un recurso protegido del sistema)")
    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Void> eliminarExchange(@PathVariable String name) {
        service.eliminarExchange(name);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Lista todos los bindings (cola <-> exchange) de RabbitMQ")
    @GetMapping("/bindings")
    public List<BindingResponse> listarBindings() {
        return service.listarBindings();
    }

    @Operation(summary = "Crea un binding entre una cola y un exchange ya existentes")
    @PostMapping("/bindings")
    public ResponseEntity<BindingResponse> crearBinding(@Valid @RequestBody BindingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearBinding(request));
    }

    @Operation(summary = "Elimina un binding entre una cola y un exchange")
    @DeleteMapping("/bindings")
    public ResponseEntity<Void> eliminarBinding(@Valid @RequestBody BindingRequest request) {
        service.eliminarBinding(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Resumen de mensajes pendientes en las DLQ del sistema, para las alertas del dashboard")
    @GetMapping("/dlq")
    public List<DlqResumenResponse> resumenDlq() {
        return service.resumenDlq();
    }

    @Operation(summary = "Reprocesa hasta 'max' mensajes de una DLQ, reenviandolos a su exchange de origen")
    @PostMapping("/dlq/{name}/reprocess")
    public ReprocessResponse reprocesarDlq(@PathVariable String name,
            @RequestParam(defaultValue = "10") int max) {
        return service.reprocesarDlq(name, max);
    }

    @Operation(summary = "Estado de los nodos del cluster RabbitMQ")
    @GetMapping("/cluster")
    public ClusterResponse estadoCluster() {
        return service.estadoCluster();
    }

    @Operation(summary = "Mensajes publicados, entregados y confirmados por el cluster, con su tasa por segundo")
    @GetMapping("/actividad")
    public ResumenActividadResponse resumenActividad() {
        return service.resumenActividad();
    }
}

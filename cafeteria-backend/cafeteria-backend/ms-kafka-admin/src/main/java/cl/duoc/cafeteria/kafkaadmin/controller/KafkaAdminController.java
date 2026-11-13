package cl.duoc.cafeteria.kafkaadmin.controller;

import cl.duoc.cafeteria.kafkaadmin.dto.AddPartitionsRequest;
import cl.duoc.cafeteria.kafkaadmin.dto.ClusterResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.ConsumerGroupDetailResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.ConsumerGroupResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.CreateTopicRequest;
import cl.duoc.cafeteria.kafkaadmin.dto.ResetOffsetsRequest;
import cl.duoc.cafeteria.kafkaadmin.dto.ResetOffsetsResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.TopicDetailResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.TopicResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.UpdateTopicConfigRequest;
import cl.duoc.cafeteria.kafkaadmin.service.KafkaAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API de administracion de Kafka: solo ADMIN (ver SecurityConfig), montada en
 * el BFF bajo /api/kafka/**. Sin logica de negocio ni de Kafka aqui: todo
 * delega en KafkaAdminService.
 */
@Tag(name = "Kafka admin", description = "Administracion de topics y consumer groups")
@RestController
@RequestMapping("/api/kafka")
public class KafkaAdminController {

    private final KafkaAdminService service;

    public KafkaAdminController(KafkaAdminService service) {
        this.service = service;
    }

    @Operation(summary = "Lista todos los topics con sus particiones y replicas")
    @GetMapping("/topics")
    public List<TopicResponse> listarTopics() {
        return service.listarTopics();
    }

    @Operation(summary = "Detalle de un topic: lider, replicas e ISR por particion, y su configuracion")
    @GetMapping("/topics/{name}")
    public TopicDetailResponse obtenerTopic(@PathVariable String name) {
        return service.obtenerTopic(name);
    }

    @Operation(summary = "Crea un topic (por defecto 3 particiones, 3 replicas y min.insync.replicas=2)")
    @PostMapping("/topics")
    public ResponseEntity<TopicResponse> crearTopic(@Valid @RequestBody CreateTopicRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearTopic(request));
    }

    @Operation(summary = "Elimina un topic (409 si es un topic del sistema)")
    @DeleteMapping("/topics/{name}")
    public ResponseEntity<Void> eliminarTopic(@PathVariable String name) {
        service.eliminarTopic(name);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Modifica la configuracion de un topic, p.ej. retention.ms o cleanup.policy")
    @PutMapping("/topics/{name}/config")
    public TopicDetailResponse actualizarConfig(@PathVariable String name,
            @Valid @RequestBody UpdateTopicConfigRequest request) {
        return service.actualizarConfig(name, request);
    }

    @Operation(summary = "Aumenta el total de particiones de un topic (409 si es un topic del sistema)")
    @PostMapping("/topics/{name}/partitions")
    public TopicDetailResponse aumentarParticiones(@PathVariable String name,
            @Valid @RequestBody AddPartitionsRequest request) {
        return service.aumentarParticiones(name, request);
    }

    @Operation(summary = "Lista los consumer groups y su estado")
    @GetMapping("/consumer-groups")
    public List<ConsumerGroupResponse> listarGrupos() {
        return service.listarGrupos();
    }

    @Operation(summary = "Detalle de un consumer group: miembros, offsets y lag por particion")
    @GetMapping("/consumer-groups/{groupId}")
    public ConsumerGroupDetailResponse obtenerGrupo(@PathVariable String groupId) {
        return service.obtenerGrupo(groupId);
    }

    @Operation(summary = "Elimina un consumer group sin consumidores activos (409 si esta activo)")
    @DeleteMapping("/consumer-groups/{groupId}")
    public ResponseEntity<Void> eliminarGrupo(@PathVariable String groupId) {
        service.eliminarGrupo(groupId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mueve los offsets de un grupo detenido en un topic: EARLIEST (replay), LATEST o TIMESTAMP "
            + "(409 si el grupo tiene consumidores activos)")
    @PostMapping("/consumer-groups/{groupId}/reset-offsets")
    public ResetOffsetsResponse resetearOffsets(@PathVariable String groupId,
            @Valid @RequestBody ResetOffsetsRequest request) {
        return service.resetearOffsets(groupId, request);
    }

    @Operation(summary = "Brokers del cluster y cual es el controlador")
    @GetMapping("/cluster")
    public ClusterResponse estadoCluster() {
        return service.estadoCluster();
    }
}

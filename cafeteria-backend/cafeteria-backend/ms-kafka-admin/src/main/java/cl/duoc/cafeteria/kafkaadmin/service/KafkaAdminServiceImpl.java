package cl.duoc.cafeteria.kafkaadmin.service;

import cl.duoc.cafeteria.kafkaadmin.config.KafkaAdminProperties;
import cl.duoc.cafeteria.kafkaadmin.dto.AddPartitionsRequest;
import cl.duoc.cafeteria.kafkaadmin.dto.BrokerResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.ClusterResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.ConsumerGroupDetailResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.ConsumerGroupResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.CreateTopicRequest;
import cl.duoc.cafeteria.kafkaadmin.dto.GroupMemberResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.PartitionLagResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.PartitionResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.ResetOffsetsRequest;
import cl.duoc.cafeteria.kafkaadmin.dto.ResetOffsetsResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.TopicDetailResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.TopicResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.UpdateTopicConfigRequest;
import cl.duoc.cafeteria.kafkaadmin.exception.ConflictoDeNegocioException;
import cl.duoc.cafeteria.kafkaadmin.exception.RecursoNoEncontradoException;
import cl.duoc.cafeteria.kafkaadmin.exception.ServicioNoDisponibleException;
import cl.duoc.cafeteria.kafkaadmin.exception.SolicitudInvalidaException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.GroupNotEmptyException;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Implementa la administracion con AdminClient: createTopics, deleteTopics,
 * describeTopics, incrementalAlterConfigs, createPartitions,
 * listConsumerGroups, describeConsumerGroups, listConsumerGroupOffsets,
 * listOffsets y alterConsumerGroupOffsets. Cada error de Kafka se traduce a
 * una excepcion de esta API.
 */
@Service
public class KafkaAdminServiceImpl implements KafkaAdminService {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminServiceImpl.class);

    /** Nunca por encima de 2: con menos replicas, acks=all no podria confirmar. */
    private static final int MIN_INSYNC_POR_DEFECTO = 2;

    private final AdminClient adminClient;
    private final KafkaAdminProperties props;

    public KafkaAdminServiceImpl(AdminClient adminClient, KafkaAdminProperties props) {
        this.adminClient = adminClient;
        this.props = props;
    }

    @Override
    public List<TopicResponse> listarTopics() {
        Set<String> nombres = esperar(adminClient.listTopics(new ListTopicsOptions().listInternal(true)).names());
        Map<String, TopicDescription> descripciones = esperar(adminClient.describeTopics(nombres).allTopicNames());
        return descripciones.values().stream()
                .map(this::aTopicResponse)
                .sorted(Comparator.comparing(TopicResponse::name))
                .toList();
    }

    @Override
    public TopicDetailResponse obtenerTopic(String nombre) {
        TopicDescription descripcion = esperar(adminClient.describeTopics(List.of(nombre)).allTopicNames()).get(nombre);
        return aTopicDetail(descripcion, leerConfig(nombre));
    }

    @Override
    public TopicResponse crearTopic(CreateTopicRequest request) {
        int particiones = request.partitions() != null ? request.partitions() : props.getParticionesPorDefecto();
        short replicas = request.replicationFactor() != null ? request.replicationFactor() : props.getReplicasPorDefecto();
        int minInsync = request.minInsyncReplicas() != null
                ? request.minInsyncReplicas()
                : Math.min(MIN_INSYNC_POR_DEFECTO, replicas);
        if (minInsync > replicas) {
            throw new SolicitudInvalidaException("minInsyncReplicas (" + minInsync + ") no puede ser mayor que las replicas ("
                    + replicas + ")");
        }

        Map<String, String> configs = new TreeMap<>();
        configs.put(TopicConfig.CLEANUP_POLICY_CONFIG,
                request.cleanupPolicy() != null ? request.cleanupPolicy() : TopicConfig.CLEANUP_POLICY_DELETE);
        configs.put(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(minInsync));
        if (request.retentionMs() != null) {
            configs.put(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(request.retentionMs()));
        }

        NewTopic topic = new NewTopic(request.name(), particiones, replicas).configs(configs);
        esperar(adminClient.createTopics(List.of(topic)).all());
        log.info("Topic creado: {} ({} particiones, {} replicas)", request.name(), particiones, replicas);
        return new TopicResponse(request.name(), false, false, particiones, replicas);
    }

    @Override
    public void eliminarTopic(String nombre) {
        exigirModificable(nombre);
        if (props.esProtegido(nombre)) {
            throw new ConflictoDeNegocioException("'" + nombre + "' es un topic del sistema protegido: no se puede eliminar");
        }
        esperar(adminClient.deleteTopics(List.of(nombre)).all());
        log.info("Topic eliminado: {}", nombre);
    }

    @Override
    public TopicDetailResponse actualizarConfig(String nombre, UpdateTopicConfigRequest request) {
        exigirModificable(nombre);
        List<String> noPermitidas = request.configs().keySet().stream()
                .filter(clave -> !props.getConfigsPermitidas().contains(clave))
                .sorted()
                .toList();
        if (!noPermitidas.isEmpty()) {
            throw new SolicitudInvalidaException("Configuraciones no permitidas: " + noPermitidas
                    + ". Permitidas: " + props.getConfigsPermitidas());
        }

        List<AlterConfigOp> operaciones = request.configs().entrySet().stream()
                .map(e -> new AlterConfigOp(new ConfigEntry(e.getKey(), e.getValue()),
                        e.getValue() == null ? AlterConfigOp.OpType.DELETE : AlterConfigOp.OpType.SET))
                .toList();
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        esperar(adminClient.incrementalAlterConfigs(Map.of(recurso, operaciones)).all());
        log.info("Configuracion del topic {} actualizada: {}", nombre, request.configs());
        return obtenerTopic(nombre);
    }

    @Override
    public TopicDetailResponse aumentarParticiones(String nombre, AddPartitionsRequest request) {
        exigirModificable(nombre);
        if (props.esProtegido(nombre)) {
            throw new ConflictoDeNegocioException("'" + nombre + "' es un topic del sistema: cambiar sus particiones "
                    + "romperia el orden por key y la topologia de Kafka Streams");
        }
        esperar(adminClient.createPartitions(Map.of(nombre, NewPartitions.increaseTo(request.total()))).all());
        log.info("Topic {} ahora tiene {} particiones", nombre, request.total());
        return obtenerTopic(nombre);
    }

    @Override
    public List<ConsumerGroupResponse> listarGrupos() {
        return esperar(adminClient.listConsumerGroups().all()).stream()
                .map(g -> new ConsumerGroupResponse(g.groupId(),
                        g.state().map(ConsumerGroupState::toString).orElse(ConsumerGroupState.UNKNOWN.toString())))
                .sorted(Comparator.comparing(ConsumerGroupResponse::groupId))
                .toList();
    }

    @Override
    public ConsumerGroupDetailResponse obtenerGrupo(String groupId) {
        ConsumerGroupDescription descripcion =
                esperar(adminClient.describeConsumerGroups(List.of(groupId)).all()).get(groupId);
        // Para un grupo inexistente Kafka no da error: responde estado DEAD.
        if (descripcion == null || descripcion.state() == ConsumerGroupState.DEAD) {
            throw new RecursoNoEncontradoException("No existe el consumer group '" + groupId + "'");
        }

        List<GroupMemberResponse> miembros = descripcion.members().stream()
                .map(m -> new GroupMemberResponse(m.consumerId(), m.clientId(), m.host(),
                        m.assignment().topicPartitions().stream()
                                .map(TopicPartition::toString)
                                .sorted()
                                .toList()))
                .sorted(Comparator.comparing(GroupMemberResponse::clientId))
                .toList();
        List<PartitionLagResponse> offsets = calcularLag(groupId);
        long lagTotal = offsets.stream().mapToLong(PartitionLagResponse::lag).sum();
        Node coordinador = descripcion.coordinator();

        return new ConsumerGroupDetailResponse(groupId, descripcion.state().toString(),
                descripcion.partitionAssignor(), coordinador != null ? coordinador.id() : null,
                miembros, offsets, lagTotal);
    }

    @Override
    public void eliminarGrupo(String groupId) {
        esperar(adminClient.deleteConsumerGroups(List.of(groupId)).all());
        log.info("Consumer group eliminado: {}", groupId);
    }

    @Override
    public ResetOffsetsResponse resetearOffsets(String groupId, ResetOffsetsRequest request) {
        ResetOffsetsRequest.DestinoOffset destino = request.to();
        if (destino == ResetOffsetsRequest.DestinoOffset.TIMESTAMP && request.timestamp() == null) {
            throw new SolicitudInvalidaException("Con to=TIMESTAMP debes indicar timestamp en formato ISO-8601, "
                    + "p.ej. 2026-11-12T10:00:00Z");
        }

        ConsumerGroupDescription grupo = esperar(adminClient.describeConsumerGroups(List.of(groupId)).all()).get(groupId);
        // Para un grupo inexistente Kafka no da error: responde estado DEAD.
        if (grupo == null || grupo.state() == ConsumerGroupState.DEAD) {
            throw new RecursoNoEncontradoException("No existe el consumer group '" + groupId + "'");
        }
        // Kafka no deja cambiar los offsets de un grupo con consumidores conectados.
        if (grupo.state() != ConsumerGroupState.EMPTY) {
            throw new ConflictoDeNegocioException("Detén los consumidores del grupo antes de resetear sus offsets");
        }

        TopicDescription topic = esperar(adminClient.describeTopics(List.of(request.topic())).allTopicNames())
                .get(request.topic());
        List<TopicPartition> particiones = topic.partitions().stream()
                .map(p -> new TopicPartition(request.topic(), p.partition()))
                .toList();
        Map<TopicPartition, Long> nuevos = offsetsDestino(particiones, destino, request);

        Map<TopicPartition, OffsetAndMetadata> anteriores =
                esperar(adminClient.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata());
        Map<TopicPartition, OffsetAndMetadata> aplicar = nuevos.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new OffsetAndMetadata(e.getValue())));
        esperar(adminClient.alterConsumerGroupOffsets(groupId, aplicar).all());

        List<ResetOffsetsResponse.OffsetReseteado> resultado = particiones.stream()
                .map(tp -> {
                    OffsetAndMetadata anterior = anteriores.get(tp);
                    return new ResetOffsetsResponse.OffsetReseteado(tp.partition(),
                            anterior != null ? anterior.offset() : null, nuevos.get(tp));
                })
                .sorted(Comparator.comparingInt(ResetOffsetsResponse.OffsetReseteado::partition))
                .toList();
        log.info("Offsets del grupo {} en el topic {} reseteados a {}: {}", groupId, request.topic(), destino, resultado);
        return new ResetOffsetsResponse(groupId, request.topic(), destino.toString(), resultado);
    }

    @Override
    public ClusterResponse estadoCluster() {
        DescribeClusterResult cluster = adminClient.describeCluster();
        Node controlador = esperar(cluster.controller());
        Collection<Node> nodos = esperar(cluster.nodes());
        List<BrokerResponse> brokers = nodos.stream()
                .map(n -> new BrokerResponse(n.id(), n.host(), n.port(), controlador != null && n.id() == controlador.id()))
                .sorted(Comparator.comparingInt(BrokerResponse::id))
                .toList();
        return new ClusterResponse(esperar(cluster.clusterId()), brokers);
    }

    private List<PartitionLagResponse> calcularLag(String groupId) {
        Map<TopicPartition, OffsetAndMetadata> confirmados =
                esperar(adminClient.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata());
        Map<TopicPartition, OffsetSpec> consulta = confirmados.keySet().stream()
                .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> finales =
                consulta.isEmpty() ? Map.of() : esperar(adminClient.listOffsets(consulta).all());

        List<PartitionLagResponse> resultado = new ArrayList<>();
        confirmados.forEach((particion, confirmado) -> {
            ListOffsetsResult.ListOffsetsResultInfo fin = finales.get(particion);
            if (confirmado != null && fin != null) {
                resultado.add(new PartitionLagResponse(particion.topic(), particion.partition(), confirmado.offset(),
                        fin.offset(), Math.max(0, fin.offset() - confirmado.offset())));
            }
        });
        resultado.sort(Comparator.comparing(PartitionLagResponse::topic).thenComparingInt(PartitionLagResponse::partition));
        return resultado;
    }

    /**
     * Offset al que debe quedar cada particion. Con TIMESTAMP, Kafka responde -1
     * en las particiones sin mensajes desde esa fecha: esas quedan al final,
     * igual que lo hace kafka-consumer-groups --to-datetime.
     */
    private Map<TopicPartition, Long> offsetsDestino(List<TopicPartition> particiones,
            ResetOffsetsRequest.DestinoOffset destino, ResetOffsetsRequest request) {
        OffsetSpec spec = switch (destino) {
            case EARLIEST -> OffsetSpec.earliest();
            case LATEST -> OffsetSpec.latest();
            case TIMESTAMP -> OffsetSpec.forTimestamp(request.timestamp().toEpochMilli());
        };
        Map<TopicPartition, Long> offsets = consultarOffsets(particiones, spec);
        List<TopicPartition> sinMensajes = offsets.entrySet().stream()
                .filter(e -> e.getValue() < 0)
                .map(Map.Entry::getKey)
                .toList();
        if (!sinMensajes.isEmpty()) {
            offsets.putAll(consultarOffsets(sinMensajes, OffsetSpec.latest()));
        }
        return offsets;
    }

    private Map<TopicPartition, Long> consultarOffsets(List<TopicPartition> particiones, OffsetSpec spec) {
        Map<TopicPartition, OffsetSpec> consulta = particiones.stream().collect(Collectors.toMap(tp -> tp, tp -> spec));
        Map<TopicPartition, Long> offsets = new HashMap<>();
        esperar(adminClient.listOffsets(consulta).all()).forEach((tp, info) -> offsets.put(tp, info.offset()));
        return offsets;
    }

    private Config leerConfig(String nombre) {
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        return esperar(adminClient.describeConfigs(List.of(recurso)).all()).get(recurso);
    }

    private void exigirModificable(String nombre) {
        if (props.esInterno(nombre)) {
            throw new ConflictoDeNegocioException("'" + nombre + "' es un topic interno de Kafka: no se puede modificar");
        }
    }

    private TopicResponse aTopicResponse(TopicDescription d) {
        return new TopicResponse(d.name(), d.isInternal() || props.esInterno(d.name()),
                props.esProtegido(d.name()) || props.esInterno(d.name()), d.partitions().size(), factorReplicacion(d));
    }

    private TopicDetailResponse aTopicDetail(TopicDescription d, Config config) {
        List<PartitionResponse> particiones = d.partitions().stream().map(this::aPartitionResponse).toList();
        // Solo las modificables y las que difieren del default: el resto es ruido.
        Map<String, String> configs = new TreeMap<>();
        config.entries().stream()
                .filter(e -> props.getConfigsPermitidas().contains(e.name())
                        || e.source() == ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG)
                .forEach(e -> configs.put(e.name(), e.value()));
        return new TopicDetailResponse(d.name(), d.isInternal() || props.esInterno(d.name()),
                props.esProtegido(d.name()) || props.esInterno(d.name()), factorReplicacion(d), particiones, configs);
    }

    private PartitionResponse aPartitionResponse(TopicPartitionInfo p) {
        return new PartitionResponse(p.partition(), p.leader() != null ? p.leader().id() : null,
                p.replicas().stream().map(Node::id).toList(), p.isr().stream().map(Node::id).toList());
    }

    private int factorReplicacion(TopicDescription d) {
        return d.partitions().isEmpty() ? 0 : d.partitions().get(0).replicas().size();
    }

    /** Espera el resultado de AdminClient y traduce cada error de Kafka a uno de esta API. */
    private <T> T esperar(KafkaFuture<T> futuro) {
        try {
            return futuro.get(props.getTimeoutSegundos(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServicioNoDisponibleException("Se interrumpio la operacion contra Kafka");
        } catch (TimeoutException e) {
            throw new ServicioNoDisponibleException("Kafka no respondio en " + props.getTimeoutSegundos() + " segundos");
        } catch (ExecutionException e) {
            throw traducir(e.getCause());
        }
    }

    private RuntimeException traducir(Throwable causa) {
        if (causa instanceof UnknownTopicOrPartitionException) {
            return new RecursoNoEncontradoException("El topic no existe");
        }
        if (causa instanceof GroupIdNotFoundException) {
            return new RecursoNoEncontradoException("El consumer group no existe");
        }
        if (causa instanceof TopicExistsException) {
            return new ConflictoDeNegocioException("Ya existe un topic con ese nombre");
        }
        if (causa instanceof GroupNotEmptyException) {
            return new ConflictoDeNegocioException("El consumer group tiene consumidores activos: no se puede eliminar");
        }
        if (causa instanceof InvalidReplicationFactorException || causa instanceof InvalidPartitionsException
                || causa instanceof InvalidConfigurationException || causa instanceof InvalidRequestException
                || causa instanceof InvalidTopicException) {
            return new SolicitudInvalidaException(causa.getMessage());
        }
        if (causa instanceof org.apache.kafka.common.errors.TimeoutException) {
            return new ServicioNoDisponibleException("Kafka no respondio a tiempo");
        }
        log.error("Error inesperado de Kafka", causa);
        return new ServicioNoDisponibleException("Error de Kafka: " + causa.getMessage());
    }
}

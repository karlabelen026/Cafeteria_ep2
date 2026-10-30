package cl.duoc.cafeteria.kafkaadmin.service;

import cl.duoc.cafeteria.kafkaadmin.config.KafkaAdminProperties;
import cl.duoc.cafeteria.kafkaadmin.dto.AddPartitionsRequest;
import cl.duoc.cafeteria.kafkaadmin.dto.ConsumerGroupDetailResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.CreateTopicRequest;
import cl.duoc.cafeteria.kafkaadmin.dto.UpdateTopicConfigRequest;
import cl.duoc.cafeteria.kafkaadmin.exception.ConflictoDeNegocioException;
import cl.duoc.cafeteria.kafkaadmin.exception.RecursoNoEncontradoException;
import cl.duoc.cafeteria.kafkaadmin.exception.ServicioNoDisponibleException;
import cl.duoc.cafeteria.kafkaadmin.exception.SolicitudInvalidaException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DescribeConsumerGroupsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.ConsumerGroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Reglas de la administracion de Kafka, con AdminClient mockeado. */
@ExtendWith(MockitoExtension.class)
class KafkaAdminServiceImplTest {

    @Mock
    private AdminClient adminClient;

    private KafkaAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        KafkaAdminProperties props = new KafkaAdminProperties();
        props.setTimeoutSegundos(1);
        props.setProtegidos(List.of("alertas-fraude"));
        props.setPrefijosInternos(List.of("__", "cafeteria-antifraude-"));
        props.setConfigsPermitidas(List.of("retention.ms", "cleanup.policy"));
        service = new KafkaAdminServiceImpl(adminClient, props);
    }

    private CreateTopicsResult resultadoCreacion(KafkaFuture<Void> futuro) {
        CreateTopicsResult resultado = mock(CreateTopicsResult.class);
        when(resultado.all()).thenReturn(futuro);
        when(adminClient.createTopics(anyCollection())).thenReturn(resultado);
        return resultado;
    }

    private CreateTopicRequest soloNombre(String nombre) {
        return new CreateTopicRequest(nombre, null, null, null, null, null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void crearTopic_sinParametros_usaTresParticionesTresReplicasYMinInsyncDos() {
        resultadoCreacion(KafkaFuture.completedFuture(null));

        service.crearTopic(soloNombre("demo.topic"));

        ArgumentCaptor<Collection<NewTopic>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(adminClient).createTopics(captor.capture());
        NewTopic creado = captor.getValue().iterator().next();
        assertThat(creado.numPartitions()).isEqualTo(3);
        assertThat(creado.replicationFactor()).isEqualTo((short) 3);
        assertThat(creado.configs()).containsEntry("min.insync.replicas", "2").containsEntry("cleanup.policy", "delete");
    }

    @Test
    @SuppressWarnings("unchecked")
    void crearTopic_conUnaReplica_bajaElMinInsyncParaQuePuedaEscribirse() {
        resultadoCreacion(KafkaFuture.completedFuture(null));

        service.crearTopic(new CreateTopicRequest("demo.topic", 1, (short) 1, null, null, null));

        ArgumentCaptor<Collection<NewTopic>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(adminClient).createTopics(captor.capture());
        assertThat(captor.getValue().iterator().next().configs()).containsEntry("min.insync.replicas", "1");
    }

    @Test
    void crearTopic_minInsyncMayorQueReplicasPorDefecto_lanzaSolicitudInvalida() {
        assertThatThrownBy(() -> service.crearTopic(new CreateTopicRequest("demo.topic", null, (short) 2, null, null, 3)))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void crearTopic_yaExistente_lanzaConflicto() {
        KafkaFutureImpl<Void> falla = new KafkaFutureImpl<>();
        falla.completeExceptionally(new TopicExistsException("existe"));
        resultadoCreacion(falla);

        assertThatThrownBy(() -> service.crearTopic(soloNombre("demo.topic")))
                .isInstanceOf(ConflictoDeNegocioException.class);
    }

    @Test
    void crearTopic_siKafkaNoResponde_lanzaServicioNoDisponible() {
        resultadoCreacion(new KafkaFutureImpl<>());

        assertThatThrownBy(() -> service.crearTopic(soloNombre("demo.topic")))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void eliminarTopic_protegido_lanzaConflictoSinLlamarAKafka() {
        assertThatThrownBy(() -> service.eliminarTopic("alertas-fraude"))
                .isInstanceOf(ConflictoDeNegocioException.class);
        verifyNoInteractions(adminClient);
    }

    @Test
    void eliminarTopic_interno_lanzaConflictoSinLlamarAKafka() {
        assertThatThrownBy(() -> service.eliminarTopic("__consumer_offsets"))
                .isInstanceOf(ConflictoDeNegocioException.class);
        verifyNoInteractions(adminClient);
    }

    @Test
    void actualizarConfig_clavesNoPermitidas_lanzaSolicitudInvalidaSinLlamarAKafka() {
        UpdateTopicConfigRequest request = new UpdateTopicConfigRequest(Map.of("compression.type", "gzip"));

        assertThatThrownBy(() -> service.actualizarConfig("demo.topic", request))
                .isInstanceOf(SolicitudInvalidaException.class)
                .hasMessageContaining("compression.type");
        verifyNoInteractions(adminClient);
    }

    @Test
    void aumentarParticiones_deTopicProtegido_lanzaConflictoSinLlamarAKafka() {
        assertThatThrownBy(() -> service.aumentarParticiones("alertas-fraude", new AddPartitionsRequest(6)))
                .isInstanceOf(ConflictoDeNegocioException.class);
        verifyNoInteractions(adminClient);
    }

    @Test
    void obtenerGrupo_estadoDead_lanzaNoEncontrado() {
        grupoConEstado("fantasma", ConsumerGroupState.DEAD);

        assertThatThrownBy(() -> service.obtenerGrupo("fantasma")).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void obtenerGrupo_calculaElLagPorParticionYElTotal() {
        grupoConEstado("demo-group", ConsumerGroupState.STABLE);

        TopicPartition p0 = new TopicPartition("demo.topic", 0);
        TopicPartition p1 = new TopicPartition("demo.topic", 1);
        ListConsumerGroupOffsetsResult confirmados = mock(ListConsumerGroupOffsetsResult.class);
        when(confirmados.partitionsToOffsetAndMetadata()).thenReturn(KafkaFuture.completedFuture(
                Map.of(p0, new OffsetAndMetadata(90), p1, new OffsetAndMetadata(50))));
        when(adminClient.listConsumerGroupOffsets(anyString())).thenReturn(confirmados);

        ListOffsetsResult finales = mock(ListOffsetsResult.class);
        when(finales.all()).thenReturn(KafkaFuture.completedFuture(Map.of(
                p0, new ListOffsetsResult.ListOffsetsResultInfo(100, 0L, java.util.Optional.empty()),
                p1, new ListOffsetsResult.ListOffsetsResultInfo(50, 0L, java.util.Optional.empty()))));
        when(adminClient.listOffsets(anyMap())).thenReturn(finales);

        ConsumerGroupDetailResponse detalle = service.obtenerGrupo("demo-group");

        assertThat(detalle.totalLag()).isEqualTo(10);
        assertThat(detalle.offsets()).hasSize(2);
        assertThat(detalle.offsets().get(0).lag()).isEqualTo(10);
        assertThat(detalle.offsets().get(1).lag()).isZero();
        assertThat(detalle.coordinator()).isEqualTo(1);
    }

    private void grupoConEstado(String groupId, ConsumerGroupState estado) {
        ConsumerGroupDescription descripcion = new ConsumerGroupDescription(groupId, false, List.of(), "range", estado,
                new Node(1, "kafka-1", 29092));
        DescribeConsumerGroupsResult resultado = mock(DescribeConsumerGroupsResult.class);
        when(resultado.all()).thenReturn(KafkaFuture.completedFuture(Map.of(groupId, descripcion)));
        when(adminClient.describeConsumerGroups(any())).thenReturn(resultado);
    }
}

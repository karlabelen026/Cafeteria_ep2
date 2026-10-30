package cl.duoc.cafeteria.kafkaadmin.service;

import cl.duoc.cafeteria.kafkaadmin.dto.AddPartitionsRequest;
import cl.duoc.cafeteria.kafkaadmin.dto.ClusterResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.ConsumerGroupDetailResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.ConsumerGroupResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.CreateTopicRequest;
import cl.duoc.cafeteria.kafkaadmin.dto.TopicDetailResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.TopicResponse;
import cl.duoc.cafeteria.kafkaadmin.dto.UpdateTopicConfigRequest;

import java.util.List;

/**
 * Toda la administracion de Kafka del sistema. El controller llama estos
 * metodos de alto nivel y no conoce AdminClient.
 */
public interface KafkaAdminService {

    List<TopicResponse> listarTopics();

    TopicDetailResponse obtenerTopic(String nombre);

    TopicResponse crearTopic(CreateTopicRequest request);

    void eliminarTopic(String nombre);

    TopicDetailResponse actualizarConfig(String nombre, UpdateTopicConfigRequest request);

    TopicDetailResponse aumentarParticiones(String nombre, AddPartitionsRequest request);

    List<ConsumerGroupResponse> listarGrupos();

    ConsumerGroupDetailResponse obtenerGrupo(String groupId);

    void eliminarGrupo(String groupId);

    ClusterResponse estadoCluster();
}

package cl.duoc.cafeteria.rabbitmqadmin.service;

import cl.duoc.cafeteria.rabbitmqadmin.dto.*;

import java.util.List;

/**
 * Encapsula TODA la logica de administracion de RabbitMQ (ver
 * docs/EP2_PLAN.md seccion 3.8): RabbitAdmin de Spring AMQP para crear y
 * eliminar, y la API HTTP de management para listar y ver el cluster. El
 * controller no conoce nada de org.springframework.amqp ni de la API HTTP.
 */
public interface RabbitAdminService {

    List<QueueResponse> listarColas();

    QueueResponse obtenerCola(String nombre);

    QueueResponse crearCola(CreateQueueRequest request);

    void eliminarCola(String nombre, boolean ifUnused, boolean ifEmpty);

    PurgeResponse purgarCola(String nombre);

    List<ExchangeResponse> listarExchanges();

    ExchangeResponse crearExchange(CreateExchangeRequest request);

    void eliminarExchange(String nombre);

    List<BindingResponse> listarBindings();

    BindingResponse crearBinding(BindingRequest request);

    void eliminarBinding(BindingRequest request);

    List<DlqResumenResponse> resumenDlq();

    ReprocessResponse reprocesarDlq(String nombreDlq, int max);

    ClusterResponse estadoCluster();

    ResumenActividadResponse resumenActividad();
}

package cl.duoc.cafeteria.rabbitmqadmin.service;

import cl.duoc.cafeteria.rabbitmqadmin.config.RabbitAdminProperties;
import cl.duoc.cafeteria.rabbitmqadmin.dto.CreateExchangeRequest;
import cl.duoc.cafeteria.rabbitmqadmin.dto.CreateQueueRequest;
import cl.duoc.cafeteria.rabbitmqadmin.dto.DlqResumenResponse;
import cl.duoc.cafeteria.rabbitmqadmin.dto.NodeResponse;
import cl.duoc.cafeteria.rabbitmqadmin.dto.QueueResponse;
import cl.duoc.cafeteria.rabbitmqadmin.dto.ResumenActividadResponse;
import cl.duoc.cafeteria.rabbitmqadmin.exception.ConflictoDeNegocioException;
import cl.duoc.cafeteria.rabbitmqadmin.exception.RecursoNoEncontradoException;
import cl.duoc.cafeteria.rabbitmqadmin.management.ManagementClient;
import cl.duoc.cafeteria.rabbitmqadmin.management.ManagementMessageStatsDto;
import cl.duoc.cafeteria.rabbitmqadmin.management.ManagementNodeDto;
import cl.duoc.cafeteria.rabbitmqadmin.management.ManagementOverviewDto;
import cl.duoc.cafeteria.rabbitmqadmin.management.ManagementQueueDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Prueba unitaria de la logica de negocio de RabbitAdminServiceImpl (ver
 * docs/EP2_PLAN.md seccion 3.8): duplicados, recursos protegidos y recursos
 * que no existen. RabbitAdmin/RabbitTemplate/ManagementClient van mockeados:
 * no se necesita un broker real.
 */
@ExtendWith(MockitoExtension.class)
class RabbitAdminServiceImplTest {

    @Mock
    private RabbitAdmin rabbitAdmin;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private ManagementClient managementClient;

    private RabbitAdminServiceImpl service;
    private RabbitAdminProperties props;

    @BeforeEach
    void setUp() {
        props = new RabbitAdminProperties();
        props.setProtegidos(List.of("pagos.pedido-creado.queue", "pagos.pedido-creado.queue.dlq",
                "cafeteria.pagos.exchange"));
        service = new RabbitAdminServiceImpl(rabbitAdmin, rabbitTemplate, managementClient, props);
    }

    private ManagementQueueDto colaDto(String nombre) {
        return new ManagementQueueDto(nombre, "quorum", 0L, 0L, 0, null, null, null);
    }

    @Test
    void crearCola_nombreYaExiste_lanzaConflicto() {
        when(managementClient.obtenerCola("cola-demo")).thenReturn(Optional.of(colaDto("cola-demo")));

        assertThatThrownBy(() -> service.crearCola(new CreateQueueRequest("cola-demo", null, null, null)))
                .isInstanceOf(ConflictoDeNegocioException.class);

        verify(rabbitAdmin, never()).declareQueue(any());
    }

    @Test
    void crearCola_nombreNuevo_declaraLaCola() {
        when(managementClient.obtenerCola("cola-demo")).thenReturn(Optional.empty());

        QueueResponse respuesta = service.crearCola(new CreateQueueRequest("cola-demo", null, null, null));

        assertThat(respuesta.name()).isEqualTo("cola-demo");
        verify(rabbitAdmin).declareQueue(any());
    }

    @Test
    void eliminarCola_protegida_lanzaConflictoSinLlamarRabbitAdmin() {
        assertThatThrownBy(() -> service.eliminarCola("pagos.pedido-creado.queue", false, false))
                .isInstanceOf(ConflictoDeNegocioException.class);

        verifyNoInteractions(rabbitAdmin);
    }

    @Test
    void eliminarCola_noExiste_lanza404() {
        when(managementClient.obtenerCola("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminarCola("fantasma", false, false))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void eliminarCola_existeYNoProtegida_laElimina() {
        when(managementClient.obtenerCola("cola-demo")).thenReturn(Optional.of(colaDto("cola-demo")));

        service.eliminarCola("cola-demo", false, false);

        verify(rabbitAdmin).deleteQueue("cola-demo", false, false);
    }

    @Test
    void purgarCola_protegida_lanzaConflicto() {
        assertThatThrownBy(() -> service.purgarCola("pagos.pedido-creado.queue"))
                .isInstanceOf(ConflictoDeNegocioException.class);

        verify(rabbitAdmin, never()).purgeQueue(anyString());
    }

    @Test
    void crearExchange_yaExiste_lanzaConflicto() {
        when(managementClient.obtenerExchange("mi-exchange"))
                .thenReturn(Optional.of(new cl.duoc.cafeteria.rabbitmqadmin.management.ManagementExchangeDto(
                        "mi-exchange", "direct", true)));

        assertThatThrownBy(() -> service.crearExchange(new CreateExchangeRequest("mi-exchange", "direct")))
                .isInstanceOf(ConflictoDeNegocioException.class);
    }

    @Test
    void eliminarExchange_protegido_lanzaConflicto() {
        assertThatThrownBy(() -> service.eliminarExchange("cafeteria.pagos.exchange"))
                .isInstanceOf(ConflictoDeNegocioException.class);

        verifyNoInteractions(rabbitAdmin);
    }

    @Test
    void resumenDlq_devuelveSoloLasColasQueTerminanEnDlq() {
        when(managementClient.obtenerCola("pagos.pedido-creado.queue.dlq"))
                .thenReturn(Optional.of(new ManagementQueueDto("pagos.pedido-creado.queue.dlq", "quorum", 3L, 0L, 0, null, null, null)));

        List<DlqResumenResponse> resumen = service.resumenDlq();

        assertThat(resumen).hasSize(1);
        assertThat(resumen.get(0).nombre()).isEqualTo("pagos.pedido-creado.queue.dlq");
        assertThat(resumen.get(0).mensajes()).isEqualTo(3L);
    }

    @Test
    void estadoCluster_cuentaColasPorLider_yNodoCaidoSinMetricas() {
        when(managementClient.listarNodos()).thenReturn(List.of(
                new ManagementNodeDto("rabbit@rabbitmq-1", true, "disc", 100L, 400L, false, false, 5000L),
                new ManagementNodeDto("rabbit@rabbitmq-2", false, "disc", null, null, null, null, null)));
        when(managementClient.listarColas()).thenReturn(List.of(
                new ManagementQueueDto("a", "quorum", 0L, 0L, 1, null, "rabbit@rabbitmq-1", null),
                new ManagementQueueDto("b", "quorum", 0L, 0L, 1, null, "rabbit@rabbitmq-1", null),
                new ManagementQueueDto("c", "quorum", 0L, 0L, 1, null, null, null)));

        List<NodeResponse> nodos = service.estadoCluster().nodos();

        assertThat(nodos.get(0).running()).isTrue();
        assertThat(nodos.get(0).colasLider()).isEqualTo(2L);
        assertThat(nodos.get(0).memoriaUsada()).isEqualTo(100L);
        assertThat(nodos.get(1).running()).isFalse();
        assertThat(nodos.get(1).colasLider()).isZero();
        assertThat(nodos.get(1).memoriaUsada()).isZero();
    }

    @Test
    void resumenActividad_conTrafico_entregaTotalesYTasas() {
        ManagementMessageStatsDto stats = new ManagementMessageStatsDto(
                10L, new ManagementMessageStatsDto.Detalle(1.5),
                8L, new ManagementMessageStatsDto.Detalle(1.0),
                7L, new ManagementMessageStatsDto.Detalle(0.5));
        when(managementClient.obtenerResumen()).thenReturn(new ManagementOverviewDto(stats,
                new ManagementOverviewDto.Totales(4, 6, 18, 17)));

        ResumenActividadResponse resumen = service.resumenActividad();

        assertThat(resumen.actividad().publicados()).isEqualTo(10L);
        assertThat(resumen.actividad().entregadosPorSeg()).isEqualTo(1.0);
        assertThat(resumen.colas()).isEqualTo(18);
        assertThat(resumen.conexiones()).isEqualTo(4);
    }

    @Test
    void resumenActividad_sinTraficoAun_devuelveCeros() {
        when(managementClient.obtenerResumen()).thenReturn(new ManagementOverviewDto(null, null));

        ResumenActividadResponse resumen = service.resumenActividad();

        assertThat(resumen.actividad().publicados()).isZero();
        assertThat(resumen.actividad().publicadosPorSeg()).isZero();
        assertThat(resumen.colas()).isZero();
    }
}

package cl.duoc.cafeteria.rabbitmqadmin.controller;

import cl.duoc.cafeteria.rabbitmqadmin.config.SecurityConfig;
import cl.duoc.cafeteria.rabbitmqadmin.dto.CreateQueueRequest;
import cl.duoc.cafeteria.rabbitmqadmin.dto.QueueResponse;
import cl.duoc.cafeteria.rabbitmqadmin.exception.ConflictoDeNegocioException;
import cl.duoc.cafeteria.rabbitmqadmin.service.RabbitAdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Prueba la capa HTTP (DTO + validacion + permisos) de RabbitAdminController
 * con el service mockeado (ver docs/EP2_PLAN.md seccion 3.8, checklist de
 * pruebas de la Fase 4). Importa SecurityConfig para ejercitar el mismo
 * "solo ADMIN" que corre en produccion.
 */
@WebMvcTest(RabbitAdminController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class RabbitAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RabbitAdminService service;

    // Evita que Spring intente descargar el JWKS real del issuer configurado.
    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithMockUser(authorities = "ADMIN")
    void crearCola_nombreVacio_retorna400() throws Exception {
        mockMvc.perform(post("/api/rabbitmq/queues")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateQueueRequest("", null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void crearCola_nombreInvalido_retorna400() throws Exception {
        mockMvc.perform(post("/api/rabbitmq/queues")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateQueueRequest("Mi Cola!", null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void crearCola_prefijoAmq_retorna400() throws Exception {
        mockMvc.perform(post("/api/rabbitmq/queues")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateQueueRequest("amq.reservada", null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void crearCola_duplicada_retorna409() throws Exception {
        when(service.crearCola(new CreateQueueRequest("cola-demo", null, null, null)))
                .thenThrow(new ConflictoDeNegocioException("Ya existe una cola con el nombre cola-demo"));

        mockMvc.perform(post("/api/rabbitmq/queues")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateQueueRequest("cola-demo", null, null, null))))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void eliminarCola_protegida_retorna409() throws Exception {
        org.mockito.Mockito.doThrow(new ConflictoDeNegocioException(
                        "'pagos.pedido-creado.queue' es un recurso del sistema protegido"))
                .when(service).eliminarCola("pagos.pedido-creado.queue", false, false);

        mockMvc.perform(delete("/api/rabbitmq/queues/pagos.pedido-creado.queue"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void crearYBorrarCola_ok_retorna201Y204() throws Exception {
        when(service.crearCola(new CreateQueueRequest("cola-demo", null, null, null)))
                .thenReturn(new QueueResponse("cola-demo", "quorum", 0, 0, 0));

        mockMvc.perform(post("/api/rabbitmq/queues")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateQueueRequest("cola-demo", null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("cola-demo"));

        mockMvc.perform(delete("/api/rabbitmq/queues/cola-demo"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "BARISTA")
    void crearCola_sinRolAdmin_retorna403() throws Exception {
        mockMvc.perform(post("/api/rabbitmq/queues")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new CreateQueueRequest("cola-demo", null, null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "BARISTA")
    void resumenActividad_sinRolAdmin_retorna403() throws Exception {
        mockMvc.perform(get("/api/rabbitmq/actividad"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarColas_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(get("/api/rabbitmq/queues"))
                .andExpect(status().isUnauthorized());
    }
}

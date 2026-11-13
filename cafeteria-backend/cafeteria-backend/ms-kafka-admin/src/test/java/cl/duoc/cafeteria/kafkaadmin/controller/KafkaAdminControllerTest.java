package cl.duoc.cafeteria.kafkaadmin.controller;

import cl.duoc.cafeteria.kafkaadmin.config.SecurityConfig;
import cl.duoc.cafeteria.kafkaadmin.dto.TopicResponse;
import cl.duoc.cafeteria.kafkaadmin.exception.ConflictoDeNegocioException;
import cl.duoc.cafeteria.kafkaadmin.exception.RecursoNoEncontradoException;
import cl.duoc.cafeteria.kafkaadmin.service.KafkaAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Capa HTTP (DTO, validacion y permisos) con el service mockeado. Importa
 * SecurityConfig para ejercitar el mismo "solo ADMIN" que corre en produccion.
 */
@WebMvcTest(KafkaAdminController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class KafkaAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KafkaAdminService service;

    @MockBean
    private JwtDecoder jwtDecoder;

    private org.springframework.test.web.servlet.ResultActions crear(String json) throws Exception {
        return mockMvc.perform(post("/api/kafka/topics").contentType("application/json").content(json));
    }

    @Test
    void sinToken_retorna401() throws Exception {
        mockMvc.perform(get("/api/kafka/topics")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "BARISTA")
    void conOtroRol_retorna403() throws Exception {
        mockMvc.perform(get("/api/kafka/topics")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void listarTopics_retorna200() throws Exception {
        when(service.listarTopics()).thenReturn(List.of(new TopicResponse("demo.topic", false, false, 3, 3)));

        mockMvc.perform(get("/api/kafka/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("demo.topic"))
                .andExpect(jsonPath("$[0].replicationFactor").value(3));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void crearTopic_valido_retorna201() throws Exception {
        when(service.crearTopic(any())).thenReturn(new TopicResponse("demo.topic", false, false, 3, 3));

        crear("{\"name\":\"demo.topic\"}").andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void crearTopic_nombreVacio_retorna400() throws Exception {
        crear("{\"name\":\"\"}").andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void crearTopic_nombreReservado_retorna400() throws Exception {
        crear("{\"name\":\"__interno\"}").andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void crearTopic_masReplicasQueBrokers_retorna400() throws Exception {
        crear("{\"name\":\"demo.topic\",\"replicationFactor\":4}").andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void crearTopic_cleanupPolicyInvalida_retorna400() throws Exception {
        crear("{\"name\":\"demo.topic\",\"cleanupPolicy\":\"borrar\"}").andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void crearTopic_minInsyncMayorQueReplicas_retorna400() throws Exception {
        crear("{\"name\":\"demo.topic\",\"replicationFactor\":1,\"minInsyncReplicas\":2}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void eliminarTopic_protegido_retorna409() throws Exception {
        doThrow(new ConflictoDeNegocioException("protegido")).when(service).eliminarTopic("alertas-fraude");

        mockMvc.perform(delete("/api/kafka/topics/alertas-fraude")).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void actualizarConfig_vacia_retorna400() throws Exception {
        mockMvc.perform(put("/api/kafka/topics/demo.topic/config").contentType("application/json")
                        .content("{\"configs\":{}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void aumentarParticiones_totalFueraDeRango_retorna400() throws Exception {
        mockMvc.perform(post("/api/kafka/topics/demo.topic/partitions").contentType("application/json")
                        .content("{\"total\":100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void obtenerGrupo_inexistente_retorna404() throws Exception {
        when(service.obtenerGrupo("no-existe")).thenThrow(new RecursoNoEncontradoException("no existe"));

        mockMvc.perform(get("/api/kafka/consumer-groups/no-existe")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void resetearOffsets_sinTopicNiDestino_retorna400SinLlamarAlService() throws Exception {
        mockMvc.perform(post("/api/kafka/consumer-groups/reportes-tiempo-real/reset-offsets")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void resetearOffsets_grupoActivo_retorna409() throws Exception {
        when(service.resetearOffsets(any(), any()))
                .thenThrow(new ConflictoDeNegocioException("Detén los consumidores del grupo antes de resetear sus offsets"));

        mockMvc.perform(post("/api/kafka/consumer-groups/reportes-tiempo-real/reset-offsets")
                        .contentType("application/json")
                        .content("{\"topic\":\"cafeteria.pagos.transacciones\",\"to\":\"EARLIEST\"}"))
                .andExpect(status().isConflict());
    }
}

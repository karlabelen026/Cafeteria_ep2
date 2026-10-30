package cl.duoc.cafeteria.kafkaadmin;

import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class KafkaAdminApplicationTests {

    @MockBean
    private JwtDecoder jwtDecoder;

    // Sin broker real: el contexto no debe intentar conectarse a Kafka.
    @MockBean
    private AdminClient adminClient;

    @Test
    void contextLoads() {
        // Verifica que el contexto levanta: service, controller y seguridad.
    }
}

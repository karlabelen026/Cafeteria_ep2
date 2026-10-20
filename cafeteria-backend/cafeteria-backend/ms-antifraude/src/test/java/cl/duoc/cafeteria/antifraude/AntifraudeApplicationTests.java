package cl.duoc.cafeteria.antifraude;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * El perfil "test" deja Kafka Streams y el listener sin arrancar (ver
 * application-test.properties): el contexto levanta sin un broker real.
 */
@SpringBootTest
@ActiveProfiles("test")
class AntifraudeApplicationTests {

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void contextLoads() {
        // Verifica que el contexto levanta: topologia, topics, productor y seguridad.
    }
}

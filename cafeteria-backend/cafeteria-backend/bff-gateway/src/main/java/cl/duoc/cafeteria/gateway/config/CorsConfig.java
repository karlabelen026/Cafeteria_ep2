package cl.duoc.cafeteria.gateway.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * CORS registrado DENTRO de la cadena de Spring Security (ver SecurityConfig
 * y NoAuthSecurityConfig), no como un CorsWebFilter aparte. Un filtro CORS
 * separado (como el "globalcors" de Spring Cloud Gateway) no alcanza a
 * agregar los headers cuando Security corta la respuesta con 401/403 antes
 * de llegar a el, y el navegador termina bloqueando hasta las respuestas de
 * error legitimas por "CORS" en vez de dejar ver el 401 real.
 */
@Configuration
public class CorsConfig {

    @Value("${FRONTEND_ORIGIN:http://localhost:4200}")
    private String frontendOrigin;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(frontendOrigin));
        // PATCH incluido: lo usan cambiar el estado de un pedido, marcar una
        // alerta como leida y anular un pago. Sin el, el navegador bloquea la
        // peticion en el preflight ("Failed to fetch") aunque el backend funcione.
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // El JWT viaja en el header Authorization, no en cookies, asi que no
        // hace falta enviar credenciales (cookies) entre origenes.
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

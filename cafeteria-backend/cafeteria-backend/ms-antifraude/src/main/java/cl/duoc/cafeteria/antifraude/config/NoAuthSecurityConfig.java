package cl.duoc.cafeteria.antifraude.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SOLO para desarrollo local sin Azure (perfil "noauth"): deja todos los
 * endpoints abiertos. Nunca debe quedar activo en la entrega final.
 */
@Configuration
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "false")
public class NoAuthSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}

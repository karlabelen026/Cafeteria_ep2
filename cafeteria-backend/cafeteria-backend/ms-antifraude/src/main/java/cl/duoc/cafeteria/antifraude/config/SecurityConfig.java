package cl.duoc.cafeteria.antifraude.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Resource Server OAuth2: toda peticion trae un JWT valido emitido por Azure
 * Entra ID (firma, emisor y audiencia). Los permisos por rol se aplican con
 * @PreAuthorize en cada controller (solo ADMIN). Misma configuracion que los
 * demas microservicios (ver p.ej. ms-clientes/config/SecurityConfig).
 *
 * Activa solo si app.security.enabled=true (default). Para desarrollo local
 * sin Azure, usa el perfil "noauth" (ver NoAuthSecurityConfig).
 */
@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${AZURE_ISSUER_URI:https://login.microsoftonline.com/<TENANT_ID>/v2.0}") String issuerUri,
            @Value("${AZURE_JWK_SET_URI:}") String jwkSetUri,
            @Value("${AZURE_AUDIENCES:api://cafeteria-backend}") String audiencesRaw) {

        NimbusJwtDecoder decoder = StringUtils.hasText(jwkSetUri)
                ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
                : NimbusJwtDecoder.withIssuerLocation(issuerUri).build();

        List<String> audiencias = Arrays.stream(audiencesRaw.split(","))
                .map(String::trim)
                .filter(a -> !a.isEmpty())
                .toList();
        OAuth2TokenValidator<Jwt> validadorAudiencia = new JwtClaimValidator<List<String>>(
                "aud", aud -> aud != null && aud.stream().anyMatch(audiencias::contains));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri), validadorAudiencia));

        return decoder;
    }

    // Lee el claim "roles" del JWT y lo normaliza a MAYUSCULAS sin prefijo.
    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("");

        Converter<Jwt, Collection<GrantedAuthority>> rolesEnMayusculas = jwt -> {
            Collection<GrantedAuthority> authorities = authoritiesConverter.convert(jwt);
            if (authorities == null) {
                return List.of();
            }
            return authorities.stream()
                    .map(authority -> (GrantedAuthority) new SimpleGrantedAuthority(
                            authority.getAuthority().trim().toUpperCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        };

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(rolesEnMayusculas);
        return converter;
    }
}

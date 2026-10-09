package cl.duoc.cafeteria.pedidos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Convierte este microservicio en un Resource Server OAuth2: toda peticion
 * debe traer un JWT valido, emitido por el IDaaS (Azure Entra ID) y firmado
 * con las claves publicas expuestas en el issuer-uri configurado en
 * application.properties. Spring valida automaticamente: firma, expiracion
 * (exp), emisor (iss) y audiencia (aud, via spring.security.oauth2.resourceserver.jwt.audiences).
 *
 * Los roles se extraen del claim "roles" que Azure Entra ID incluye en el
 * Access Token cuando se definen App Roles en el App Registration del backend.
 *
 * Esta configuracion SOLO se activa si app.security.enabled=true (que es el
 * valor por defecto). Para desarrollo local sin Azure configurado todavia,
 * usa el perfil "noauth" (ver NoAuthSecurityConfig y application-noauth.properties).
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
                // Checkout y seguimiento publicos (ver controller/PublicoController):
                // el BFF ya los expone sin JWT, pero este microservicio valida su
                // propio JWT de forma independiente, asi que tambien hay que
                // marcarlos como publicos aqui o quedarian respondiendo 401.
                .requestMatchers("/api/public/**").permitAll()
                // Llamadas servicio-a-servicio: no traen JWT de usuario, las
                // autentica InternalTokenFilter con el secreto compartido.
                .requestMatchers("/internal/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        return http.build();
    }

    // JwtDecoder propio: se define a mano (en vez de dejar que Spring Boot lo
    // arme solo desde las propiedades spring.security.oauth2.resourceserver.jwt.*)
    // porque dejar "jwk-set-uri" configurado-pero-vacio cuando AZURE_JWK_SET_URI
    // no esta seteada hace que Boot registre DOS beans JwtDecoder (uno por
    // issuer-uri y otro por jwk-set-uri) y falle el arranque por ambiguedad.
    // Aqui se decide explicitamente en Java cual construir:
    //  - Si AZURE_JWK_SET_URI viene seteada (tenants Microsoft Entra External ID
    //    / ciamlogin, donde el JWK Set no siempre se descubre bien solo con el
    //    issuer-uri): se arma el decoder directamente desde ese JWK Set.
    //  - Si no: se usa el issuer-uri para descubrir el JWK Set via OIDC (el
    //    comportamiento por defecto de Azure AD "clasico").
    // En ambos casos se valida issuer + audiencias igual que lo haria Boot.
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

    // Conversor de roles comun a todos los microservicios: lee el claim "roles"
    // del JWT y normaliza cada rol a MAYUSCULAS sin prefijo (p.ej. "ADMIN", no
    // "ROLE_admin"), para que coincida exactamente con los valores de App Roles
    // configurados en Azure Entra ID (ver docs/EP2_PLAN.md seccion 7, paso 4).
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

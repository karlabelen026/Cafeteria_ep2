package cl.duoc.cafeteria.pedidos.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protege las rutas /internal/** (ver controller/InternoController) con un
 * secreto compartido entre microservicios, enviado en el header
 * X-Internal-Token. Es la autenticacion servicio-a-servicio del proyecto: un
 * listener de RabbitMQ no tiene un JWT de usuario, pero si conoce este
 * secreto (variable INTERNAL_API_TOKEN, igual en todos los contenedores).
 * Sin el header correcto responde 401, tanto con Azure activo como en noauth.
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    private static final String PREFIJO_RUTAS = "/internal/";

    private final byte[] tokenEsperado;

    public InternalTokenFilter(@Value("${app.security.internal-token}") String tokenEsperado) {
        this.tokenEsperado = tokenEsperado.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIJO_RUTAS);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recibido = request.getHeader(HEADER);
        boolean valido = tokenEsperado.length > 0
                && recibido != null
                && MessageDigest.isEqual(tokenEsperado, recibido.getBytes(StandardCharsets.UTF_8));
        if (!valido) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Falta el secreto interno o no es valido");
            return;
        }
        chain.doFilter(request, response);
    }
}

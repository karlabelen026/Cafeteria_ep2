package cl.duoc.cafeteria.notificaciones.controller;

import cl.duoc.cafeteria.notificaciones.dto.MensajeChatRequest;
import cl.duoc.cafeteria.notificaciones.dto.MensajeChatResponse;
import cl.duoc.cafeteria.notificaciones.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Chat interno del equipo (ver docs/EP2_PLAN.md seccion 6.2): un solo canal
 * de texto donde cualquier staff logueado (ADMIN, GERENTE, BARISTA, CAJERO,
 * BODEGUERO) escribe y todos los demas lo ven. No requiere ningun rol
 * especifico mas alla de estar autenticado (ver SecurityConfig:
 * ".anyRequest().authenticated()"). Sin logica de negocio ni de RabbitMQ en
 * el controller.
 */
@RestController
@RequestMapping("/api/notificaciones/mensajes")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void enviar(@Valid @RequestBody MensajeChatRequest request) {
        chatService.enviar(request);
    }

    // Sin "desde": historial inicial (ultimos 50). Con "desde": solo lo
    // nuevo, para que el frontend haga polling liviano (ver Chat.jsx).
    @GetMapping
    public List<MensajeChatResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde) {
        return desde == null ? chatService.historial() : chatService.desde(desde);
    }
}

package cl.duoc.cafeteria.notificaciones.service;

import cl.duoc.cafeteria.common.evento.MensajeChatEvent;
import cl.duoc.cafeteria.notificaciones.dto.MensajeChatRequest;
import cl.duoc.cafeteria.notificaciones.dto.MensajeChatResponse;
import cl.duoc.cafeteria.notificaciones.messaging.producer.ChatEventPublisher;
import cl.duoc.cafeteria.notificaciones.model.MensajeChat;
import cl.duoc.cafeteria.notificaciones.repository.MensajeChatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ChatServiceImpl implements ChatService {

    private final MensajeChatRepository mensajeChatRepository;
    private final ChatEventPublisher chatEventPublisher;

    public ChatServiceImpl(MensajeChatRepository mensajeChatRepository, ChatEventPublisher chatEventPublisher) {
        this.mensajeChatRepository = mensajeChatRepository;
        this.chatEventPublisher = chatEventPublisher;
    }

    @Override
    public void enviar(MensajeChatRequest request) {
        MensajeChatEvent evento = new MensajeChatEvent(
                UUID.randomUUID(), Instant.now(), 1, request.autor(), request.rol(), request.texto());
        chatEventPublisher.publicar(evento);
    }

    @Override
    @Transactional
    public void guardar(MensajeChatEvent evento) {
        MensajeChat mensaje = new MensajeChat();
        mensaje.setAutor(evento.autor());
        mensaje.setRol(evento.rol());
        mensaje.setTexto(evento.texto());
        mensaje.setCreadoEn(evento.ocurridoEn());
        mensajeChatRepository.save(mensaje);
    }

    @Override
    public List<MensajeChatResponse> historial() {
        return mensajeChatRepository.findTop50ByOrderByCreadoEnDesc().stream()
                .sorted(Comparator.comparing(MensajeChat::getCreadoEn))
                .map(MensajeChatResponse::desde)
                .toList();
    }

    @Override
    public List<MensajeChatResponse> desde(Instant desde) {
        return mensajeChatRepository.findByCreadoEnAfterOrderByCreadoEnAsc(desde).stream()
                .map(MensajeChatResponse::desde)
                .toList();
    }
}

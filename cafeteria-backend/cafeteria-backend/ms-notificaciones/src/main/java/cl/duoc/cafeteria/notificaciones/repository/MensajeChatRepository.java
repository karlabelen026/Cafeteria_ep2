package cl.duoc.cafeteria.notificaciones.repository;

import cl.duoc.cafeteria.notificaciones.model.MensajeChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MensajeChatRepository extends JpaRepository<MensajeChat, Long> {

    // Historial inicial: los ultimos N mensajes, del mas viejo al mas nuevo.
    List<MensajeChat> findTop50ByOrderByCreadoEnDesc();

    // Polling incremental desde el frontend (ver ChatController): solo lo
    // nuevo desde el ultimo mensaje que el cliente ya tiene.
    List<MensajeChat> findByCreadoEnAfterOrderByCreadoEnAsc(Instant desde);
}

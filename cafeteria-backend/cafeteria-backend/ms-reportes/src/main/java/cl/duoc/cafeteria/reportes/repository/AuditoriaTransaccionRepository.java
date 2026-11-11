package cl.duoc.cafeteria.reportes.repository;

import cl.duoc.cafeteria.reportes.model.AuditoriaTransaccion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditoriaTransaccionRepository extends JpaRepository<AuditoriaTransaccion, UUID> {

    List<AuditoriaTransaccion> findByOcurridoEnGreaterThanEqual(Instant desde);

    List<AuditoriaTransaccion> findAllByOrderByOcurridoEnDesc(Pageable pagina);

    List<AuditoriaTransaccion> findByClienteEmailOrderByOcurridoEnDesc(String clienteEmail, Pageable pagina);
}

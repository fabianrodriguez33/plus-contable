package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.EstadoNotificacion;
import com.pluscontable.tributario.model.NotificacionSol;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionSolRepository extends JpaRepository<NotificacionSol, Long> {

    /** Del plazo más próximo al más lejano (la más urgente primero); a igual plazo, por orden de registro. */
    List<NotificacionSol> findByClienteIdOrderByPlazoRespuestaAscIdAsc(Long clienteId);

    List<NotificacionSol> findByClienteIdAndEstadoOrderByPlazoRespuestaAscIdAsc(Long clienteId, EstadoNotificacion estado);
}

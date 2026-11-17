package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.EstadoSolicitud;
import com.pluscontable.tributario.model.SolicitudEspecial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudEspecialRepository extends JpaRepository<SolicitudEspecial, Long> {

    /** HU-26: los pedidos en seguimiento, el de entrega más próxima primero. */
    List<SolicitudEspecial> findByClienteIdAndEstadoOrderByFechaEntregaAscIdAsc(Long clienteId, EstadoSolicitud estado);

    /** HU-27: todas las solicitudes del cliente (pendientes y atendidas), para su historial. */
    List<SolicitudEspecial> findByClienteId(Long clienteId);
}

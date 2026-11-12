package com.pluscontable.auth.repository;

import com.pluscontable.auth.dto.SolicitudPendienteDto;
import com.pluscontable.auth.model.SolicitudRestablecimiento;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SolicitudRestablecimientoRepository extends JpaRepository<SolicitudRestablecimiento, Long> {

    /** A lo sumo una pendiente por cuenta (índice único parcial). */
    Optional<SolicitudRestablecimiento> findByUsuarioIdAndAtendidaEnIsNull(Long usuarioId);

    /** Las pendientes con la empresa y el correo de la cuenta, la más antigua primero. */
    @Query("""
            select new com.pluscontable.auth.dto.SolicitudPendienteDto(u.clienteId, u.username, s.solicitadaEn)
            from SolicitudRestablecimiento s, Usuario u
            where s.usuarioId = u.id and s.atendidaEn is null
            order by s.solicitadaEn
            """)
    List<SolicitudPendienteDto> pendientes();
}

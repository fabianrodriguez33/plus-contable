package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.Incidencia;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidenciaRepository extends JpaRepository<Incidencia, Long> {

    /** Las incidencias asociadas a un cliente (propias y generales), la más reciente primero. */
    @Query("""
            SELECT i FROM Incidencia i
            WHERE i.id IN (SELECT ic.incidenciaId FROM IncidenciaCliente ic WHERE ic.clienteId = :clienteId)
            ORDER BY i.fecha DESC, i.id DESC
            """)
    List<Incidencia> findByCliente(@Param("clienteId") Long clienteId);
}

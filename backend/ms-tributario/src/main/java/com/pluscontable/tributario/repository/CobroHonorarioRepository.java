package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.CobroHonorario;
import com.pluscontable.tributario.model.EstadoCobro;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CobroHonorarioRepository extends JpaRepository<CobroHonorario, Long> {

    List<CobroHonorario> findByClienteIdAndEstadoOrderByFechaCobroAsc(Long clienteId, EstadoCobro estado);

    boolean existsByClienteIdAndFechaCobro(Long clienteId, LocalDate fechaCobro);

    boolean existsByClienteIdAndEstadoAndFechaCobroBetween(Long clienteId, EstadoCobro estado, LocalDate desde, LocalDate hasta);

    /** Al cambiar la periodicidad: quita los cobros pendientes desde una fecha. */
    void deleteByClienteIdAndEstadoAndFechaCobroGreaterThanEqual(Long clienteId, EstadoCobro estado, LocalDate desde);
}

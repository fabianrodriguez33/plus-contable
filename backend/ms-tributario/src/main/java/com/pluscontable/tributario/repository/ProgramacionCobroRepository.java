package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.ProgramacionCobro;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgramacionCobroRepository extends JpaRepository<ProgramacionCobro, Long> {
    Optional<ProgramacionCobro> findByClienteId(Long clienteId);
}

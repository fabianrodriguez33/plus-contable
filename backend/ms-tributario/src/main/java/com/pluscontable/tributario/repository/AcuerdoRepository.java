package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.AcuerdoServicio;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcuerdoRepository extends JpaRepository<AcuerdoServicio, Long> {
    Optional<AcuerdoServicio> findByClienteId(Long clienteId);
}

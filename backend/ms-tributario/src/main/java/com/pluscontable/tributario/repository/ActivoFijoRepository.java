package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.ActivoFijo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivoFijoRepository extends JpaRepository<ActivoFijo, Long> {
    List<ActivoFijo> findByClienteIdOrderById(Long clienteId);
}

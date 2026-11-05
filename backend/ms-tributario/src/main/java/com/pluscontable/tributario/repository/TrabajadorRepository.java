package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.Trabajador;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrabajadorRepository extends JpaRepository<Trabajador, Long> {
    List<Trabajador> findByClienteIdOrderById(Long clienteId);
}

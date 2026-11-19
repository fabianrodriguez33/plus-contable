package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.Fraccionamiento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraccionamientoRepository extends JpaRepository<Fraccionamiento, Long> {

    List<Fraccionamiento> findByClienteIdOrderByRegistradoEnDescIdDesc(Long clienteId);
}

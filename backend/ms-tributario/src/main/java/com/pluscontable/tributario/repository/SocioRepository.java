package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.Socio;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocioRepository extends JpaRepository<Socio, Long> {
    List<Socio> findByClienteIdOrderById(Long clienteId);

    Optional<Socio> findByIdAndClienteId(Long id, Long clienteId);

    void deleteByClienteId(Long clienteId);
}

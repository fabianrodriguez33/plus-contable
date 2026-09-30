package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.Cliente;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    boolean existsByRuc(String ruc);

    Optional<Cliente> findByRuc(String ruc);
}

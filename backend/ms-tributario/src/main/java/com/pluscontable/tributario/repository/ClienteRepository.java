package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.Cliente;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    boolean existsByRuc(String ruc);

    Optional<Cliente> findByRuc(String ruc);

    /** La cartera activa: HU-23 asocia a ella las incidencias generales. */
    List<Cliente> findByActivoTrue();
}

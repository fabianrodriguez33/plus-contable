package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.CredencialSol;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredencialSolRepository extends JpaRepository<CredencialSol, Long> {
    Optional<CredencialSol> findByClienteId(Long clienteId);
}

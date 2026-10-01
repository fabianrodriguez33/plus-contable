package com.pluscontable.auth.repository;

import com.pluscontable.auth.model.ClaveSolCifrada;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaveSolRepository extends JpaRepository<ClaveSolCifrada, Long> {
    Optional<ClaveSolCifrada> findByRuc(String ruc);
}

package com.pluscontable.auth.repository;

import com.pluscontable.auth.model.BitacoraSeguridad;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BitacoraRepository extends JpaRepository<BitacoraSeguridad, Long> {
}

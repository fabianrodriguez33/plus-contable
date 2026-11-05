package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.VencimientoHistorial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VencimientoHistorialRepository extends JpaRepository<VencimientoHistorial, Long> {
    List<VencimientoHistorial> findByObligacionIdOrderByCambiadoEnDesc(Long obligacionId);
}

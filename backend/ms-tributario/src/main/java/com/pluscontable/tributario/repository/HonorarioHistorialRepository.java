package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.HonorarioHistorial;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HonorarioHistorialRepository extends JpaRepository<HonorarioHistorial, Long> {
    List<HonorarioHistorial> findByAcuerdoIdOrderByVigenteDesdeDesc(Long acuerdoId);
}

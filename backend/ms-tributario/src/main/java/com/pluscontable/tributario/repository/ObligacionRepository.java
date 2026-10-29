package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.Obligacion;
import java.util.List;
import java.time.LocalDate;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import org.springframework.data.jpa.repository.JpaRepository;

/** PROVISIONAL hasta integrar HU-07. */
public interface ObligacionRepository extends JpaRepository<Obligacion, Long> {
    List<Obligacion> findByClienteIdOrderByPeriodoDesc(Long clienteId);
    boolean existsByClienteIdAndTipoObligacionAndPeriodo(Long clienteId, TipoObligacion tipoObligacion,
                                                         LocalDate periodo);
}

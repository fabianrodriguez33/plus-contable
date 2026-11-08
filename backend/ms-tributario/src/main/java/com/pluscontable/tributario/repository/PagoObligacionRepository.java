package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.PagoObligacion;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagoObligacionRepository extends JpaRepository<PagoObligacion, Long> {

    List<PagoObligacion> findByObligacionIdOrderByFechaPagoAscIdAsc(Long obligacionId);

    boolean existsByObligacionIdAndNumeroConstancia(Long obligacionId, String numeroConstancia);

    long countByObligacionId(Long obligacionId);

    /** Acumulado de la obligación: suma de sus pagos (0 si no tiene). */
    @Query("select coalesce(sum(p.montoPagado), 0) from PagoObligacion p where p.obligacionId = :obligacionId")
    BigDecimal sumaPagada(@Param("obligacionId") Long obligacionId);
}

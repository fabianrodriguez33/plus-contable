package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.Obligacion;
import java.util.List;
import java.time.LocalDate;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** PROVISIONAL hasta integrar HU-07. */
public interface ObligacionRepository extends JpaRepository<Obligacion, Long> {
    List<Obligacion> findByClienteIdOrderByPeriodoDesc(Long clienteId);

    /** HU-13: bloquea la obligación mientras se suma un pago, para que dos pagos a la vez no superen el saldo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Obligacion o where o.id = :id")
    Optional<Obligacion> buscarParaActualizar(@Param("id") Long id);
    boolean existsByClienteIdAndTipoObligacionAndPeriodo(Long clienteId, TipoObligacion tipoObligacion,
                                                         LocalDate periodo);
}

package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.CobroHonorario;
import com.pluscontable.tributario.model.EstadoCobro;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CobroHonorarioRepository extends JpaRepository<CobroHonorario, Long> {

    List<CobroHonorario> findByClienteIdAndEstadoInOrderByFechaCobroAsc(Long clienteId, Collection<EstadoCobro> estados);

    boolean existsByClienteIdAndFechaCobro(Long clienteId, LocalDate fechaCobro);

    boolean existsByClienteIdAndEstadoAndFechaCobroBetween(Long clienteId, EstadoCobro estado, LocalDate desde, LocalDate hasta);

    boolean existsByClienteIdAndEstadoInAndFechaCobroGreaterThanEqual(Long clienteId, Collection<EstadoCobro> estados, LocalDate desde);

    /** Al cambiar la periodicidad: quita los cobros pendientes desde una fecha. */
    void deleteByClienteIdAndEstadoAndFechaCobroGreaterThanEqual(Long clienteId, EstadoCobro estado, LocalDate desde);

    /** HU-29: bloquea el cobro mientras se suma un pago, para que dos pagos a la vez no superen el saldo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CobroHonorario c where c.id = :id")
    Optional<CobroHonorario> buscarParaActualizar(@Param("id") Long id);
}

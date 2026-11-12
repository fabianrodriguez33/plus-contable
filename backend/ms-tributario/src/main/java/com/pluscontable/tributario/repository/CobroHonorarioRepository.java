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

    /** HU-30: los cobros del cliente con fecha en [desde, hasta], en orden. */
    List<CobroHonorario> findByClienteIdAndFechaCobroBetweenOrderByFechaCobroAscIdAsc(Long clienteId, LocalDate desde, LocalDate hasta);

    /** HU-30: lo cobrado al cliente antes de una fecha. */
    @Query("select coalesce(sum(c.monto), 0) from CobroHonorario c where c.clienteId = :clienteId and c.fechaCobro < :antes")
    java.math.BigDecimal cobradoAntesDe(@Param("clienteId") Long clienteId, @Param("antes") LocalDate antes);

    /** HU-31: por cliente, los cobros con saldo (PENDIENTE o PAGO_PARCIAL) y fecha anterior a hoy. */
    @Query("""
            select new com.pluscontable.tributario.dto.DeudaVencidaDto(cl.id, cl.ruc, cl.razonSocial, sum(c.monto - c.pagado), count(c), min(c.fechaCobro))
            from CobroHonorario c, Cliente cl
            where cl.id = c.clienteId and c.estado in :conSaldo and c.fechaCobro < :hoy
            group by cl.id, cl.ruc, cl.razonSocial
            """)
    List<com.pluscontable.tributario.dto.DeudaVencidaDto> deudasVencidas(@Param("conSaldo") Collection<EstadoCobro> conSaldo, @Param("hoy") LocalDate hoy);

    /** HU-29: bloquea el cobro mientras se suma un pago, para que dos pagos a la vez no superen el saldo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CobroHonorario c where c.id = :id")
    Optional<CobroHonorario> buscarParaActualizar(@Param("id") Long id);
}

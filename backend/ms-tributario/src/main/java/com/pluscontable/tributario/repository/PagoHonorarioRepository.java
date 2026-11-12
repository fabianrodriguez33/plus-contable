package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.dto.PagoHonorarioDto;
import com.pluscontable.tributario.model.PagoHonorario;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagoHonorarioRepository extends JpaRepository<PagoHonorario, Long> {

    /** HU-30: los pagos de los cobros del cliente con fecha de pago en [desde, hasta], en orden. */
    @Query("""
            select new com.pluscontable.tributario.dto.PagoHonorarioDto(p.id, c.id, c.fechaCobro, p.fechaPago, p.monto, p.medioPago, p.medioPagoDetalle)
            from PagoHonorario p, CobroHonorario c
            where p.cobroId = c.id and c.clienteId = :clienteId and p.fechaPago between :desde and :hasta
            order by p.fechaPago, p.id
            """)
    List<PagoHonorarioDto> delPeriodo(@Param("clienteId") Long clienteId, @Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    /** HU-30: lo pagado por el cliente antes de una fecha. */
    @Query("""
            select coalesce(sum(p.monto), 0) from PagoHonorario p, CobroHonorario c
            where p.cobroId = c.id and c.clienteId = :clienteId and p.fechaPago < :antes
            """)
    BigDecimal pagadoAntesDe(@Param("clienteId") Long clienteId, @Param("antes") LocalDate antes);
}

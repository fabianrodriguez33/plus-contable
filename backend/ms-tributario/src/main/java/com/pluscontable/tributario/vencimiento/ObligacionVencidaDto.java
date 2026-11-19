package com.pluscontable.tributario.vencimiento;

import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import java.math.BigDecimal;
import java.time.LocalDate;

/** montoPagado es la suma de los pagos (HU-13) y saldo = monto determinado - pagado (nulo si aún no tiene monto determinado). */
public record ObligacionVencidaDto(Long obligacionId, Long clienteId, String ruc, String razonSocial, TipoObligacion tipoObligacion,
                                   LocalDate periodo, LocalDate fechaVencimiento, long diasVencida, Obligacion.Estado estado,
                                   BigDecimal montoDeterminado, BigDecimal montoPagado, BigDecimal saldo) {
}

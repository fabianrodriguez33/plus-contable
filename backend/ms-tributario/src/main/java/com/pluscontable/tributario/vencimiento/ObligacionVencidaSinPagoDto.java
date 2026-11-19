package com.pluscontable.tributario.vencimiento;

import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ObligacionVencidaSinPagoDto(Long obligacionId, Long clienteId, String ruc, String razonSocial,
                                          TipoObligacion tipoObligacion, LocalDate periodo, LocalDate fechaVencimiento,
                                          long diasVencida, Obligacion.Estado estado, BigDecimal montoDeterminado) {
}

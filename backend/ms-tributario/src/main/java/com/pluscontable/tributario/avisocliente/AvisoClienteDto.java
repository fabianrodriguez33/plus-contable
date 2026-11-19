package com.pluscontable.tributario.avisocliente;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lo que ve el cliente: tributo, monto por pagar y fecha de vencimiento. */
public record AvisoClienteDto(Long id, Long obligacionId, String tributo, BigDecimal monto,
                              LocalDate fechaVencimiento, LocalDate fechaAviso) {
}

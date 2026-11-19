package com.pluscontable.tributario.dto;

import java.math.BigDecimal;

/** HU-14: lo pagado por una obligación (suma de sus pagos de HU-13), calculado por la base. */
public record PagadoPorObligacionDto(Long obligacionId, BigDecimal pagado) {
}

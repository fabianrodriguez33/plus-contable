package com.pluscontable.tributario.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** HU-09: datos con los que el contador marca una obligación como declarada. ObligacionService valida todo junto. */
public record DeclaracionDto(LocalDate fechaDeclaracion, String numeroFormulario, BigDecimal montoDeterminado) {
}

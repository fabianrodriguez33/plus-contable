package com.pluscontable.tributario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** HU-09: datos con los que el contador marca una obligación como declarada. */
public record DeclaracionDto(
        @NotNull(message = "fechaDeclaracion: es obligatoria") LocalDate fechaDeclaracion,
        @NotBlank(message = "numeroFormulario: es obligatorio") String numeroFormulario,
        @NotNull(message = "montoDeterminado: es obligatorio")
        @DecimalMin(value = "0.00", message = "montoDeterminado: no puede ser negativo") BigDecimal montoDeterminado) {
}

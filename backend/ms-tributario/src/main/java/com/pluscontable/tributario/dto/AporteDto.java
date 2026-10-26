package com.pluscontable.tributario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Cuerpo para editar solo el aporte de un socio. */
public record AporteDto(
        @NotNull(message = "El aporte es obligatorio")
        @DecimalMin(value = "0.00", message = "El aporte no puede ser negativo") BigDecimal aporte) {
}

package com.pluscontable.tributario.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Socio que se registra: el porcentaje de participación no se envía, lo calcula el sistema según el aporte. */
public record SocioEntradaDto(
        @NotBlank(message = "El nombre del socio es obligatorio") String nombreCompleto,
        String tipoDocumento,
        @NotBlank(message = "El número de documento del socio es obligatorio") String numeroDocumento,
        @NotNull(message = "El aporte es obligatorio")
        @DecimalMin(value = "0.01", message = "El aporte debe ser mayor a 0") BigDecimal aporte,
        String cargo) {
}

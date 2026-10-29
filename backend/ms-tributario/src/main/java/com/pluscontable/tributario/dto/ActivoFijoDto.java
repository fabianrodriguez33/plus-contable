package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.ActivoFijo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ActivoFijoDto(
        Long id,
        String codigo,
        @NotBlank(message = "La descripción del activo es obligatoria") String descripcion,
        String categoria,
        @NotNull(message = "El valor de adquisición es obligatorio")
        @DecimalMin(value = "0.00", message = "El valor no puede ser negativo") BigDecimal valorAdquisicion,
        LocalDate fechaAdquisicion) {

    public static ActivoFijoDto from(ActivoFijo a) {
        return new ActivoFijoDto(a.getId(), a.getCodigo(), a.getDescripcion(), a.getCategoria(), a.getValorAdquisicion(),
                a.getFechaAdquisicion());
    }
}

package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.Socio;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SocioDto(
        Long id,
        @NotBlank(message = "El nombre del socio es obligatorio") String nombreCompleto,
        String tipoDocumento,
        @NotBlank(message = "El número de documento del socio es obligatorio") String numeroDocumento,
        @NotNull(message = "El porcentaje es obligatorio")
        @DecimalMin(value = "0.01", message = "El porcentaje debe ser mayor a 0")
        @DecimalMax(value = "100.00", message = "El porcentaje no puede exceder 100") BigDecimal porcentaje,
        String cargo) {

    public static SocioDto from(Socio s) {
        return new SocioDto(s.getId(), s.getNombreCompleto(), s.getTipoDocumento(), s.getNumeroDocumento(),
                s.getPorcentaje(), s.getCargo());
    }
}

package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.Trabajador;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TrabajadorDto(
        Long id,
        @NotBlank(message = "nombreCompleto: es obligatorio") String nombreCompleto,
        @NotNull(message = "tipoDocumento: es obligatorio") Trabajador.TipoDocumento tipoDocumento,
        @NotBlank(message = "numeroDocumento: es obligatorio") String numeroDocumento,
        boolean extranjero,
        String nacionalidad,
        @NotNull(message = "sistemaPensionario: es obligatorio (ONP o AFP)") Trabajador.SistemaPensionario sistemaPensionario,
        String nombreAfp,
        @NotNull(message = "fechaIngreso: es obligatoria") LocalDate fechaIngreso,
        @DecimalMin(value = "0.00", message = "remuneracion: no puede ser negativa") BigDecimal remuneracion,
        String tipoContrato,
        LocalDate fechaInicioContrato,
        LocalDate fechaFinContrato) {

    public static TrabajadorDto from(Trabajador t) {
        return new TrabajadorDto(t.getId(), t.getNombreCompleto(), t.getTipoDocumento(), t.getNumeroDocumento(),
                t.isExtranjero(), t.getNacionalidad(), t.getSistemaPensionario(), t.getNombreAfp(),
                t.getFechaIngreso(), t.getRemuneracion(), t.getTipoContrato(), t.getFechaInicioContrato(),
                t.getFechaFinContrato());
    }
}

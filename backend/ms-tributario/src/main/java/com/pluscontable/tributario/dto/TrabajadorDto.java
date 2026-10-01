package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.Trabajador;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TrabajadorDto(
        Long id,
        @NotBlank(message = "El nombre del trabajador es obligatorio") String nombreCompleto,
        String tipoDocumento,
        @NotBlank(message = "El número de documento del trabajador es obligatorio") String numeroDocumento,
        boolean extranjero,
        String nacionalidad,
        @NotNull(message = "El régimen laboral es obligatorio") Trabajador.RegimenLaboral regimenLaboral,
        @NotNull(message = "El sistema pensionario es obligatorio") Trabajador.SistemaPensionario sistemaPensionario,
        String nombreAfp,
        LocalDate fechaIngreso,
        @DecimalMin(value = "0.00", message = "La remuneración no puede ser negativa") BigDecimal remuneracion,
        boolean registroSunafil,
        boolean registroMtpe,
        boolean essalud,
        boolean quintaCategoria) {

    public static TrabajadorDto from(Trabajador t) {
        return new TrabajadorDto(t.getId(), t.getNombreCompleto(), t.getTipoDocumento(), t.getNumeroDocumento(),
                t.isExtranjero(), t.getNacionalidad(), t.getRegimenLaboral(), t.getSistemaPensionario(),
                t.getNombreAfp(), t.getFechaIngreso(), t.getRemuneracion(), t.isRegistroSunafil(),
                t.isRegistroMtpe(), t.isEssalud(), t.isQuintaCategoria());
    }
}

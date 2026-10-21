package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.Trabajador;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Sin anotaciones de validación: TrabajadorService valida todo junto para listar todos los campos faltantes. */
public record TrabajadorDto(
        Long id,
        String nombreCompleto,
        Trabajador.TipoDocumento tipoDocumento,
        String numeroDocumento,
        boolean extranjero,
        String nacionalidad,
        Trabajador.SistemaPensionario sistemaPensionario,
        String nombreAfp,
        LocalDate fechaIngreso,
        BigDecimal remuneracion,
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

package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.AcuerdoServicio;
import com.pluscontable.tributario.model.HonorarioHistorial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Sin anotaciones de validación: AcuerdoService valida todo junto.
 * {@code honorarioDesde} en la entrada es desde cuándo rige un honorario nuevo (por defecto, hoy).
 */
public record AcuerdoDto(
        Long id,
        String serviciosContratados,
        BigDecimal honorarioMensual,
        LocalDate honorarioDesde,
        LocalDate fechaInicio,
        List<Historial> historial) {

    public record Historial(BigDecimal monto, LocalDate vigenteDesde, LocalDate vigenteHasta) {
        public static Historial from(HonorarioHistorial h) {
            return new Historial(h.getMonto(), h.getVigenteDesde(), h.getVigenteHasta());
        }
    }

    public static AcuerdoDto from(AcuerdoServicio a, List<HonorarioHistorial> historial) {
        return new AcuerdoDto(a.getId(), a.getServiciosContratados(), a.getHonorarioMensual(), a.getHonorarioDesde(),
                a.getFechaInicio(), historial.stream().map(Historial::from).toList());
    }
}

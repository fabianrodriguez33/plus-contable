package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.AcuerdoServicio;
import com.pluscontable.tributario.model.HonorarioHistorial;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AcuerdoDto(
        Long id,
        String serviciosContratados,
        @NotNull(message = "honorarioMensual: es obligatorio")
        @DecimalMin(value = "0.01", message = "honorarioMensual: debe ser mayor que cero") BigDecimal honorarioMensual,
        @NotNull(message = "fechaInicio: es obligatoria") LocalDate fechaInicio,
        List<Historial> historial) {

    public record Historial(BigDecimal monto, LocalDate vigenteDesde, LocalDate vigenteHasta) {
        public static Historial from(HonorarioHistorial h) {
            return new Historial(h.getMonto(), h.getVigenteDesde(), h.getVigenteHasta());
        }
    }

    public static AcuerdoDto from(AcuerdoServicio a, List<HonorarioHistorial> historial) {
        return new AcuerdoDto(a.getId(), a.getServiciosContratados(), a.getHonorarioMensual(), a.getFechaInicio(),
                historial.stream().map(Historial::from).toList());
    }
}

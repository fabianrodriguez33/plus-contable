package com.pluscontable.tributario.calendario;

import com.pluscontable.tributario.dto.ObligacionDto;
import java.time.Clock;
import java.time.LocalDate;

/** Vista de calendario calculada sobre una obligación existente. */
public record CalendarioObligacionDto(ObligacionDto obligacion, long diasRestantes,
                                     boolean vencimientoPasado) {
    public static CalendarioObligacionDto from(ObligacionDto obligacion, Clock clock) {
        VencimientoTributario fecha = new VencimientoTributario(obligacion.fechaVencimiento());
        return new CalendarioObligacionDto(obligacion, fecha.diasRestantes(clock), fecha.vencido(clock));
    }

    public LocalDate fechaVencimiento() {
        return obligacion.fechaVencimiento();
    }
}

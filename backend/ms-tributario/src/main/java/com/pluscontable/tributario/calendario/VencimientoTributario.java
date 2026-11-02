package com.pluscontable.tributario.calendario;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Vista calculada de un vencimiento; no persiste un estado VENCIDO. */
public record VencimientoTributario(LocalDate fechaVencimiento) {

    public VencimientoTributario {
        if (fechaVencimiento == null) {
            throw new IllegalArgumentException("fechaVencimiento es obligatoria");
        }
    }

    /** Positivo si falta tiempo, cero si vence hoy y negativo si ya pasó. */
    public long diasRestantes(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock es obligatorio");
        }
        return ChronoUnit.DAYS.between(LocalDate.now(clock), fechaVencimiento);
    }

    public boolean vencido(Clock clock) {
        return diasRestantes(clock) < 0;
    }
}

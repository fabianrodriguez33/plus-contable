package com.pluscontable.tributario.vencimiento;

import com.pluscontable.tributario.model.Obligacion;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Definición de «vencida» confirmada por Beto el 18/11 (la misma del tablero HU-14 y del calendario): una obligación está
 * <b>vencida</b> cuando su fecha límite ya pasó y todavía tiene saldo pendiente, es decir, está DECLARADA sin pago o con PAGO_PARCIAL
 * (sigue vencida mientras quede saldo). La PAGADA nunca. La que sigue <b>sin declarar</b> con la fecha pasada no es vencida: va aparte,
 * como pendiente de atención. El estado se calcula con la fecha de hoy (Lima); no se guarda, así que cambia solo al pasar la fecha límite.
 */
public final class ReglaObligacionVencida {
    private ReglaObligacionVencida() {
    }

    /** El último día para pagar es la fecha límite: ese mismo día todavía no está vencida. */
    private static boolean pasada(LocalDate fechaVencimiento, LocalDate hoy) {
        Objects.requireNonNull(hoy, "hoy");
        return fechaVencimiento != null && fechaVencimiento.isBefore(hoy);
    }

    public static boolean vencida(Obligacion.Estado estado, LocalDate fechaVencimiento, LocalDate hoy) {
        Objects.requireNonNull(estado, "estado");
        return pasada(fechaVencimiento, hoy)
                && (estado == Obligacion.Estado.DECLARADO || estado == Obligacion.Estado.PAGO_PARCIAL);
    }

    /** Pendiente de atención: sin declarar y con la fecha límite ya pasada. */
    public static boolean sinDeclarar(Obligacion.Estado estado, LocalDate fechaVencimiento, LocalDate hoy) {
        Objects.requireNonNull(estado, "estado");
        return pasada(fechaVencimiento, hoy) && estado == Obligacion.Estado.PENDIENTE;
    }

    public static long diasVencida(LocalDate fechaVencimiento, LocalDate hoy) {
        Objects.requireNonNull(fechaVencimiento, "fechaVencimiento");
        Objects.requireNonNull(hoy, "hoy");
        return Math.max(0, ChronoUnit.DAYS.between(fechaVencimiento, hoy));
    }
}

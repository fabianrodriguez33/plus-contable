package com.pluscontable.tributario.vencimiento;

import com.pluscontable.tributario.model.Obligacion;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * HU-16 · regla pura: una obligación está VENCIDA SIN PAGO cuando su fecha límite ya pasó y no tiene ninguna constancia
 * de pago. El estado no se guarda: se calcula con la fecha de hoy, así que cambia solo al pasar la fecha límite (T45).
 * Criterio literal de la historia: cualquier constancia registrada (también la de un pago parcial) impide la marca.
 * Si Beto decide que un pago parcial vencido debe seguir marcado, solo cambia lo que se pasa como «tieneConstancia».
 */
public final class ReglaVencidaSinPago {
    private ReglaVencidaSinPago() {
    }

    /** El último día para pagar es la fecha límite: ese mismo día todavía no está vencida. */
    public static boolean corresponde(Obligacion.Estado estado, LocalDate fechaVencimiento, LocalDate hoy,
                                      boolean tieneConstancia) {
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(hoy, "hoy");
        return fechaVencimiento != null && fechaVencimiento.isBefore(hoy)
                && estado != Obligacion.Estado.PAGADO && !tieneConstancia;
    }

    public static long diasVencida(LocalDate fechaVencimiento, LocalDate hoy) {
        Objects.requireNonNull(fechaVencimiento, "fechaVencimiento");
        Objects.requireNonNull(hoy, "hoy");
        return Math.max(0, ChronoUnit.DAYS.between(fechaVencimiento, hoy));
    }
}

package com.pluscontable.tributario.recordatoriopago;

import com.pluscontable.tributario.model.Obligacion;
import java.time.LocalDate;
import java.util.Objects;

/** Evaluación pura de un recordatorio con fecha elegida explícitamente; no programa ni envía avisos. */
public final class ReglaRecordatorioPago {
    public enum Resultado { NO_ELEGIBLE, FECHA_PENDIENTE, CORRESPONDE_ENVIAR }

    private ReglaRecordatorioPago() {
    }

    /**
     * HU-15 requiere obligación declarada y todavía sin pagos. Por ello PAGO_PARCIAL se excluye junto con
     * PAGADO: ya existe al menos un pago. La fecha de recordatorio la proporciona quien lo programó.
     */
    public static Resultado evaluar(Obligacion.Estado estado, LocalDate fechaVencimiento,
                                    boolean tienePagos, LocalDate fechaRecordatorio, LocalDate hoy) {
        Objects.requireNonNull(hoy, "hoy es obligatoria");
        if (estado != Obligacion.Estado.DECLARADO || tienePagos || fechaVencimiento == null
                || fechaRecordatorio == null || fechaRecordatorio.isAfter(fechaVencimiento)) {
            return Resultado.NO_ELEGIBLE;
        }
        if (hoy.isBefore(fechaRecordatorio)) return Resultado.FECHA_PENDIENTE;
        if (hoy.isAfter(fechaRecordatorio)) return Resultado.NO_ELEGIBLE;
        return Resultado.CORRESPONDE_ENVIAR;
    }

    /** Clave determinista para deduplicar una ocurrencia; reprogramar cambia la clave de fecha. */
    public static String claveEvento(Long obligacionId, LocalDate fechaRecordatorio) {
        if (obligacionId == null || obligacionId <= 0) throw new IllegalArgumentException("obligacionId inválido");
        Objects.requireNonNull(fechaRecordatorio, "fechaRecordatorio es obligatoria");
        return "obligacion:%d:recordatorio:%s".formatted(obligacionId, fechaRecordatorio);
    }
}

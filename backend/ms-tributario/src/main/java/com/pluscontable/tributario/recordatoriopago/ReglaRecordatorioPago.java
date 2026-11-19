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
     * Decisiones de Beto (18/11): se puede recordar el pago de una obligación DECLARADA o con PAGO_PARCIAL (con saldo pendiente; el
     * aviso muestra el saldo). Si el sistema estuvo caído el día elegido, el recordatorio se envía cuando vuelve, y sigue siendo útil
     * <b>mientras la obligación siga pendiente de pago, aunque ya haya vencido</b>; si se pagó por completo ya no corresponde. La fecha
     * del recordatorio la proporciona quien lo programó y no puede ser posterior al vencimiento. Que haya saldo pendiente lo comprueba
     * quien llama (necesita los pagos).
     */
    public static Resultado evaluar(Obligacion.Estado estado, LocalDate fechaVencimiento,
                                    LocalDate fechaRecordatorio, LocalDate hoy) {
        Objects.requireNonNull(hoy, "hoy es obligatoria");
        boolean conSaldo = estado == Obligacion.Estado.DECLARADO || estado == Obligacion.Estado.PAGO_PARCIAL;
        if (!conSaldo || fechaVencimiento == null || fechaRecordatorio == null || fechaRecordatorio.isAfter(fechaVencimiento)) {
            return Resultado.NO_ELEGIBLE;
        }
        if (hoy.isBefore(fechaRecordatorio)) return Resultado.FECHA_PENDIENTE;
        return Resultado.CORRESPONDE_ENVIAR; // el día elegido o, con retraso, el primer día en que el sistema vuelve (aunque ya haya vencido)
    }

    /** Clave determinista para deduplicar una ocurrencia; reprogramar cambia la clave de fecha. */
    public static String claveEvento(Long obligacionId, LocalDate fechaRecordatorio) {
        if (obligacionId == null || obligacionId <= 0) throw new IllegalArgumentException("obligacionId inválido");
        Objects.requireNonNull(fechaRecordatorio, "fechaRecordatorio es obligatoria");
        return "obligacion:%d:recordatorio:%s".formatted(obligacionId, fechaRecordatorio);
    }
}

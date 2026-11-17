package com.pluscontable.tributario.model;

/** HU-28 crea los cobros PENDIENTE; HU-29 los pasa a PAGO_PARCIAL (queda saldo) o PAGADO al registrar pagos. */
public enum EstadoCobro {
    PENDIENTE,
    PAGO_PARCIAL,
    PAGADO
}

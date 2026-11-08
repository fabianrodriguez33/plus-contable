package com.pluscontable.tributario.model;

/** HU-17: una cuota nace PENDIENTE; pasa a PAGADA cuando el contador la marca. «Vencida» no se guarda: se calcula por la fecha. */
public enum EstadoCuota {
    PENDIENTE,
    PAGADA
}

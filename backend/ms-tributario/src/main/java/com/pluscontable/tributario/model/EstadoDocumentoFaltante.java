package com.pluscontable.tributario.model;

/** HU-24: el estado que se muestra. SIN_RESPUESTA = PENDIENTE con la fecha límite anterior a hoy (no se guarda). */
public enum EstadoDocumentoFaltante {
    PENDIENTE,
    ENTREGADO,
    SIN_RESPUESTA
}

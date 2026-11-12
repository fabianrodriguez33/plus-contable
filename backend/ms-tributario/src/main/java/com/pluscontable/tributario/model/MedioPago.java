package com.pluscontable.tributario.model;

/**
 * HU-29: medio con el que el cliente pagó los honorarios (lista confirmada por Beto el 11/11). TRANSFERENCIA y DEPOSITO son
 * bancarios; con OTRO se especifica el medio en el pago.
 */
public enum MedioPago {
    EFECTIVO,
    TRANSFERENCIA,
    DEPOSITO,
    YAPE_PLIN,
    OTRO
}

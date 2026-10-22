package com.pluscontable.tributario.obligacion;

/** Tipos generales acordados para HU-07, sin reglas por régimen tributario. */
public enum TipoObligacion {
    IGV,
    RENTA,
    PLAME,
    ESSALUD,
    RETENCION_QUINTA,
    AFP;

    public static TipoObligacion fromCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("tipo_obligacion es obligatorio");
        }
        try {
            return valueOf(codigo);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("tipo_obligacion no reconocido: " + codigo, e);
        }
    }
}

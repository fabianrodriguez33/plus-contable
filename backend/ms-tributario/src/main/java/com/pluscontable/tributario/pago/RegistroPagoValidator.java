package com.pluscontable.tributario.pago;

/** Precondiciones independientes para registrar una constancia en HU-13. */
public final class RegistroPagoValidator {

    private RegistroPagoValidator() {
    }

    /** declarada debe reflejar el estado DECLARADO de HU-09. */
    public static void validar(Long obligacionId, boolean declarada) {
        if (obligacionId == null || obligacionId <= 0) {
            throw new IllegalArgumentException("obligacion_id debe ser un identificador positivo");
        }
        if (!declarada) {
            throw new IllegalStateException("La obligación debe estar DECLARADA antes de registrar el pago");
        }
    }
}

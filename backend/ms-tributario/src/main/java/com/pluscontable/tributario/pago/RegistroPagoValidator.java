package com.pluscontable.tributario.pago;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    public static void validarDatos(LocalDate fechaPago, BigDecimal montoPagado,
                                    String numeroOperacion, String usuarioRegistro) {
        if (fechaPago == null) {
            throw new IllegalArgumentException("fecha_pago es obligatoria");
        }
        if (montoPagado == null || montoPagado.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("monto_pagado debe ser mayor que cero");
        }
        if (numeroOperacion == null || numeroOperacion.isBlank()) {
            throw new IllegalArgumentException("numero_operacion es obligatorio");
        }
        if (usuarioRegistro == null || usuarioRegistro.isBlank()) {
            throw new IllegalArgumentException("usuario_registro es obligatorio");
        }
    }
}

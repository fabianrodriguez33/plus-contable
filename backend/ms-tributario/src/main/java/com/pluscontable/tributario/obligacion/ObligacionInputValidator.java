package com.pluscontable.tributario.obligacion;

import java.time.LocalDate;

/** Valida solo los datos independientes de HU-07 antes de usar la obligación compartida. */
public final class ObligacionInputValidator {

    private ObligacionInputValidator() {
    }

    public static void validar(Long clienteId, TipoObligacion tipoObligacion, LocalDate periodo) {
        if (clienteId == null || clienteId <= 0) {
            throw new IllegalArgumentException("cliente_id debe ser un identificador positivo");
        }
        if (tipoObligacion == null) {
            throw new IllegalArgumentException("tipo_obligacion es obligatorio");
        }
        if (periodo == null) {
            throw new IllegalArgumentException("periodo es obligatorio");
        }
        // El modelo provisional compartido representa cada mes con su primer día.
        if (periodo.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("periodo debe ser el primer día del mes");
        }
    }
}

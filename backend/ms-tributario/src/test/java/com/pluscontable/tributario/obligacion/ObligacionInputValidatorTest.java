package com.pluscontable.tributario.obligacion;

import java.time.LocalDate;

/** Pruebas unitarias independientes de Spring y de PostgreSQL. */
public final class ObligacionInputValidatorTest {

    private static final LocalDate PERIODO = LocalDate.of(2026, 10, 1);
    private static int casos;

    public static void main(String[] args) {
        for (TipoObligacion tipo : TipoObligacion.values()) {
            validarAceptado(1L, tipo, PERIODO);
            comprobar(TipoObligacion.fromCodigo(tipo.name()) == tipo);
        }
        comprobar(TipoObligacion.values().length == 6);
        validarAceptado(Long.MAX_VALUE, TipoObligacion.IGV, LocalDate.of(2027, 1, 1));

        rechazar(() -> ObligacionInputValidator.validar(null, TipoObligacion.IGV, PERIODO));
        rechazar(() -> ObligacionInputValidator.validar(0L, TipoObligacion.IGV, PERIODO));
        rechazar(() -> ObligacionInputValidator.validar(-1L, TipoObligacion.IGV, PERIODO));
        rechazar(() -> ObligacionInputValidator.validar(1L, null, PERIODO));
        rechazar(() -> TipoObligacion.fromCodigo(null));
        rechazar(() -> TipoObligacion.fromCodigo(""));
        rechazar(() -> TipoObligacion.fromCodigo("OTRO"));
        rechazar(() -> ObligacionInputValidator.validar(1L, TipoObligacion.IGV, null));
        rechazar(() -> ObligacionInputValidator.validar(1L, TipoObligacion.IGV, LocalDate.of(2026, 10, 2)));

        System.out.println("HU-07: " + casos + " comprobaciones unitarias aprobadas");
    }

    private static void validarAceptado(Long clienteId, TipoObligacion tipo, LocalDate periodo) {
        ObligacionInputValidator.validar(clienteId, tipo, periodo);
        casos++;
    }

    private static void comprobar(boolean condicion) {
        if (!condicion) throw new AssertionError("Comprobación fallida");
        casos++;
    }

    private static void rechazar(Runnable accion) {
        try {
            accion.run();
        } catch (IllegalArgumentException esperada) {
            casos++;
            return;
        }
        throw new AssertionError("Se esperaba IllegalArgumentException");
    }
}

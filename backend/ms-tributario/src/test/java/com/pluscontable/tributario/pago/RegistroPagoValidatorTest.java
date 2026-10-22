package com.pluscontable.tributario.pago;

/** Pruebas unitarias ejecutables sin Spring, PostgreSQL ni dependencias nuevas. */
public final class RegistroPagoValidatorTest {

    private static int casos;

    public static void main(String[] args) {
        RegistroPagoValidator.validar(1L, true);
        casos++;
        RegistroPagoValidator.validar(Long.MAX_VALUE, true);
        casos++;

        rechazar(IllegalArgumentException.class, () -> RegistroPagoValidator.validar(null, true));
        rechazar(IllegalArgumentException.class, () -> RegistroPagoValidator.validar(0L, true));
        rechazar(IllegalArgumentException.class, () -> RegistroPagoValidator.validar(-1L, true));
        rechazar(IllegalStateException.class, () -> RegistroPagoValidator.validar(1L, false));
        rechazar(IllegalArgumentException.class, () -> RegistroPagoValidator.validar(null, false));

        System.out.println("HU-13: " + casos + " comprobaciones unitarias aprobadas");
    }

    private static void rechazar(Class<? extends RuntimeException> tipo, Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException e) {
            if (!tipo.isInstance(e)) throw new AssertionError("Excepción inesperada", e);
            casos++;
            return;
        }
        throw new AssertionError("Se esperaba " + tipo.getSimpleName());
    }
}

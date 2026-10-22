package com.pluscontable.tributario.pago;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Pruebas unitarias ejecutables sin Spring, PostgreSQL ni dependencias nuevas. */
public final class RegistroPagoValidatorTest {

    private static int casos;
    private static final LocalDate FECHA = LocalDate.of(2026, 10, 22);
    private static final BigDecimal MONTO = new BigDecimal("120.50");
    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-10-22T15:00:00Z"), ZoneId.of("America/Lima"));

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

        RegistroPago pago = RegistroPago.crear(1L, true, FECHA, MONTO, "OP-123", null, "ricardo", RELOJ);
        comprobar(pago.obligacionId() == 1L);
        comprobar(pago.fechaPago().equals(FECHA));
        comprobar(pago.montoPagado().equals(MONTO));
        comprobar(pago.numeroOperacion().equals("OP-123"));
        comprobar(pago.referenciaConstancia() == null); // opcional hasta que el equipo lo confirme
        comprobar(pago.usuarioRegistro().equals("ricardo"));
        comprobar(pago.registradoEn().equals(LocalDateTime.of(2026, 10, 22, 10, 0)));
        comprobar(RegistroPago.crear(2L, true, FECHA, MONTO, "OP-456", "constancias/456.pdf",
                "ricardo", RELOJ).referenciaConstancia().equals("constancias/456.pdf"));

        rechazar(IllegalStateException.class, () -> RegistroPago.crear(1L, false, FECHA, MONTO,
                "OP-123", null, "ricardo", RELOJ));
        rechazar(IllegalArgumentException.class, () -> RegistroPago.crear(0L, true, FECHA, MONTO,
                "OP-123", null, "ricardo", RELOJ));
        rechazar(IllegalArgumentException.class, () -> RegistroPago.crear(1L, true, null, MONTO,
                "OP-123", null, "ricardo", RELOJ));
        rechazar(IllegalArgumentException.class, () -> RegistroPago.crear(1L, true, FECHA, null,
                "OP-123", null, "ricardo", RELOJ));
        rechazar(IllegalArgumentException.class, () -> RegistroPago.crear(1L, true, FECHA, BigDecimal.ZERO,
                "OP-123", null, "ricardo", RELOJ));
        rechazar(IllegalArgumentException.class, () -> RegistroPago.crear(1L, true, FECHA, new BigDecimal("-0.01"),
                "OP-123", null, "ricardo", RELOJ));
        rechazar(IllegalArgumentException.class, () -> RegistroPago.crear(1L, true, FECHA, MONTO,
                null, null, "ricardo", RELOJ));
        rechazar(IllegalArgumentException.class, () -> RegistroPago.crear(1L, true, FECHA, MONTO,
                " ", null, "ricardo", RELOJ));
        rechazar(IllegalArgumentException.class, () -> RegistroPago.crear(1L, true, FECHA, MONTO,
                "OP-123", null, null, RELOJ));
        rechazar(IllegalArgumentException.class, () -> RegistroPago.crear(1L, true, FECHA, MONTO,
                "OP-123", null, " ", RELOJ));
        rechazar(IllegalArgumentException.class, () -> RegistroPago.crear(1L, true, FECHA, MONTO,
                "OP-123", null, "ricardo", null));

        System.out.println("HU-13: " + casos + " comprobaciones unitarias aprobadas");
    }

    private static void comprobar(boolean condicion) {
        if (!condicion) throw new AssertionError("Comprobación fallida");
        casos++;
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

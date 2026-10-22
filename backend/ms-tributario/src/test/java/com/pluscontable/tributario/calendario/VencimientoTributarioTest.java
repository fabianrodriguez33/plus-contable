package com.pluscontable.tributario.calendario;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Pruebas unitarias ejecutables sin Spring, base de datos ni dependencias nuevas. */
public final class VencimientoTributarioTest {

    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-10-22T15:00:00Z"), ZoneId.of("America/Lima"));
    private static int casos;

    public static void main(String[] args) {
        VencimientoTributario futuro = new VencimientoTributario(LocalDate.of(2026, 10, 27));
        comprobar(futuro.diasRestantes(RELOJ) == 5);
        comprobar(!futuro.vencido(RELOJ));

        VencimientoTributario hoy = new VencimientoTributario(LocalDate.of(2026, 10, 22));
        comprobar(hoy.diasRestantes(RELOJ) == 0);
        comprobar(!hoy.vencido(RELOJ));

        VencimientoTributario pasado = new VencimientoTributario(LocalDate.of(2026, 10, 19));
        comprobar(pasado.diasRestantes(RELOJ) == -3);
        comprobar(pasado.vencido(RELOJ));

        rechazar(IllegalArgumentException.class, () -> new VencimientoTributario(null));
        rechazar(IllegalArgumentException.class, () -> futuro.diasRestantes(null));
        rechazar(IllegalArgumentException.class, () -> futuro.vencido(null));
        rechazar(DateTimeException.class, () -> LocalDate.of(2025, 2, 29));

        System.out.println("HU-08: " + casos + " comprobaciones unitarias aprobadas");
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

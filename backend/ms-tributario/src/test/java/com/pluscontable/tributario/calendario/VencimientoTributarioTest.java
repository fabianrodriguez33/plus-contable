package com.pluscontable.tributario.calendario;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DateTimeException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VencimientoTributarioTest {

    private static final Clock RELOJ = Clock.fixed(
            Instant.parse("2026-10-22T15:00:00Z"), ZoneId.of("America/Lima"));
    @Test void fechaFutura() {
        VencimientoTributario futuro = new VencimientoTributario(LocalDate.of(2026, 10, 27));
        assertEquals(5, futuro.diasRestantes(RELOJ));
        assertFalse(futuro.vencido(RELOJ));
    }
    @Test void fechaActual() {
        VencimientoTributario hoy = new VencimientoTributario(LocalDate.of(2026, 10, 22));
        assertEquals(0, hoy.diasRestantes(RELOJ));
        assertFalse(hoy.vencido(RELOJ));
    }
    @Test void fechaPasada() {
        VencimientoTributario pasado = new VencimientoTributario(LocalDate.of(2026, 10, 19));
        assertEquals(-3, pasado.diasRestantes(RELOJ));
        assertTrue(pasado.vencido(RELOJ));
    }
    @Test void entradasInvalidas() {
        assertThrows(IllegalArgumentException.class, () -> new VencimientoTributario(null));
        var futuro = new VencimientoTributario(LocalDate.of(2026, 10, 27));
        assertThrows(IllegalArgumentException.class, () -> futuro.diasRestantes(null));
        assertThrows(IllegalArgumentException.class, () -> futuro.vencido(null));
        assertThrows(DateTimeException.class, () -> LocalDate.of(2025, 2, 29));
    }
}

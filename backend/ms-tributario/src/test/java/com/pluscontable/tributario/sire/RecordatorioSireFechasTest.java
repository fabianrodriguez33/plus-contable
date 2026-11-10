package com.pluscontable.tributario.sire;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RecordatorioSireFechasTest {
    private static final List<Integer> DIAS = RecordatorioSireFechas.DIAS_AVISO_INICIALES;

    @Test void avisaTresDiasAntes() {
        assertTrue(RecordatorioSireFechas.corresponde(
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 9), DIAS));
    }

    @Test void avisaUnDiaAntes() {
        assertTrue(RecordatorioSireFechas.corresponde(
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 11), DIAS));
    }

    @Test void avisaElMismoDia() {
        assertTrue(RecordatorioSireFechas.corresponde(
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 12), DIAS));
    }

    @Test void noAvisaEnDiaNoConfigurado() {
        assertFalse(RecordatorioSireFechas.corresponde(
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 10), DIAS));
    }

    @Test void noAvisaSiLaRevisionYaPaso() {
        assertFalse(RecordatorioSireFechas.corresponde(
                LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13), DIAS));
    }

    @Test void calculaCruceDeMes() {
        assertTrue(RecordatorioSireFechas.corresponde(
                LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 28), DIAS));
    }

    @Test void calculaCruceDeAnio() {
        assertTrue(RecordatorioSireFechas.corresponde(
                LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 31), DIAS));
    }

    @Test void rechazaConfiguracionInvalida() {
        LocalDate revision = LocalDate.of(2026, 11, 12);
        LocalDate hoy = LocalDate.of(2026, 11, 9);
        assertThrows(IllegalArgumentException.class, () -> RecordatorioSireFechas.corresponde(revision, hoy, null));
        assertThrows(IllegalArgumentException.class, () -> RecordatorioSireFechas.corresponde(revision, hoy, List.of()));
        assertThrows(IllegalArgumentException.class, () -> RecordatorioSireFechas.corresponde(revision, hoy, List.of(-1)));
        assertThrows(IllegalArgumentException.class, () -> RecordatorioSireFechas.corresponde(revision, hoy, List.of(3, 3)));
        assertThrows(IllegalArgumentException.class, () -> RecordatorioSireFechas.corresponde(revision, hoy,
                java.util.Arrays.asList(3, null)));
    }

    @Test void rechazaFechaDeRevisionAusente() {
        assertThrows(IllegalArgumentException.class, () -> RecordatorioSireFechas.corresponde(
                null, LocalDate.of(2026, 11, 9), DIAS));
    }

    @Test void usaLaFechaActualControlada() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-10T01:00:00Z"), ZoneId.of("America/Lima"));
        LocalDate hoyEnLima = LocalDate.now(reloj);
        assertEquals(LocalDate.of(2026, 11, 9), hoyEnLima);
        assertTrue(RecordatorioSireFechas.corresponde(LocalDate.of(2026, 11, 12), hoyEnLima, DIAS));
        assertThrows(IllegalArgumentException.class, () -> RecordatorioSireFechas.corresponde(
                LocalDate.of(2026, 11, 12), null, DIAS));
    }

    @Test void aceptaDiasConfiguradosDistintosSinImponerFechaFija() {
        assertTrue(RecordatorioSireFechas.corresponde(
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 18), List.of(2)));
        assertFalse(RecordatorioSireFechas.corresponde(
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 18), DIAS));
    }
}

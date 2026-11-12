package com.pluscontable.tributario.vencimiento;

import static org.junit.jupiter.api.Assertions.*;

import com.pluscontable.tributario.model.Obligacion;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReglaVencimientoProximoTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 12);

    private ReglaVencimientoProximo.Candidato candidato(long id, Obligacion.Estado estado, LocalDate fecha) {
        return new ReglaVencimientoProximo.Candidato(id, estado, fecha);
    }

    @Test
    void pendienteDentroDeLaVentanaCorresponde() {
        assertTrue(ReglaVencimientoProximo.corresponde(candidato(1, Obligacion.Estado.PENDIENTE, HOY.plusDays(2)), HOY, 3));
    }

    @Test
    void ultimoDiaDeVentanaEstaIncluido() {
        assertTrue(ReglaVencimientoProximo.corresponde(candidato(1, Obligacion.Estado.PENDIENTE, HOY.plusDays(3)), HOY, 3));
    }

    @Test
    void fueraDeVentanaNoCorresponde() {
        assertFalse(ReglaVencimientoProximo.corresponde(candidato(1, Obligacion.Estado.PENDIENTE, HOY.plusDays(4)), HOY, 3));
    }

    @Test
    void vencimientoHoyCorresponde() {
        assertTrue(ReglaVencimientoProximo.corresponde(candidato(1, Obligacion.Estado.PENDIENTE, HOY), HOY, 0));
    }

    @Test
    void vencimientoPasadoNoCorresponde() {
        assertFalse(ReglaVencimientoProximo.corresponde(candidato(1, Obligacion.Estado.PENDIENTE, HOY.minusDays(1)), HOY, 3));
    }

    @Test
    void estadosCumplidosNoCorresponden() {
        for (var estado : List.of(Obligacion.Estado.DECLARADO, Obligacion.Estado.PAGO_PARCIAL, Obligacion.Estado.PAGADO)) {
            assertFalse(ReglaVencimientoProximo.corresponde(candidato(1, estado, HOY.plusDays(1)), HOY, 3));
        }
    }

    @Test
    void fechaAusenteNoCorresponde() {
        assertFalse(ReglaVencimientoProximo.corresponde(candidato(1, Obligacion.Estado.PENDIENTE, null), HOY, 3));
    }

    @Test
    void cambioDeFechaSeReevaluaSinGuardarElResultadoAnterior() {
        assertTrue(ReglaVencimientoProximo.corresponde(candidato(1, Obligacion.Estado.PENDIENTE, HOY.plusDays(1)), HOY, 3));
        assertFalse(ReglaVencimientoProximo.corresponde(candidato(1, Obligacion.Estado.PENDIENTE, HOY.plusDays(5)), HOY, 3));
    }

    @Test
    void seleccionNoDuplicaUnMismoId() {
        var uno = candidato(1, Obligacion.Estado.PENDIENTE, HOY.plusDays(1));
        assertEquals(List.of(uno), ReglaVencimientoProximo.seleccionar(List.of(uno, uno), HOY, 3));
    }

    @Test
    void versionesContradictoriasDelMismoIdSeRechazan() {
        var primero = candidato(1, Obligacion.Estado.PENDIENTE, HOY.plusDays(1));
        var cambiado = candidato(1, Obligacion.Estado.PENDIENTE, HOY.plusDays(5));
        assertThrows(IllegalArgumentException.class,
                () -> ReglaVencimientoProximo.seleccionar(List.of(primero, cambiado), HOY, 3));
    }

    @Test
    void configuracionInvalidaSeRechaza() {
        var uno = candidato(1, Obligacion.Estado.PENDIENTE, HOY);
        assertThrows(IllegalArgumentException.class, () -> ReglaVencimientoProximo.corresponde(uno, HOY, -1));
        assertThrows(IllegalArgumentException.class, () -> ReglaVencimientoProximo.seleccionar(List.of(uno), HOY, -1));
        assertThrows(NullPointerException.class, () -> ReglaVencimientoProximo.corresponde(uno, null, 1));
    }
}

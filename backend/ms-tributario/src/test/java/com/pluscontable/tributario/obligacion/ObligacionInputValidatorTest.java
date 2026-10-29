package com.pluscontable.tributario.obligacion;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ObligacionInputValidatorTest {
    private static final LocalDate PERIODO = LocalDate.of(2026, 10, 1);

    @Test
    void aceptaLosSeisTiposYReconoceSusCodigos() {
        for (TipoObligacion tipo : TipoObligacion.values()) {
            assertDoesNotThrow(() -> ObligacionInputValidator.validar(1L, tipo, PERIODO));
            assertEquals(tipo, TipoObligacion.fromCodigo(tipo.name()));
        }
        assertEquals(6, TipoObligacion.values().length);
        assertDoesNotThrow(() -> ObligacionInputValidator.validar(Long.MAX_VALUE,
                TipoObligacion.IGV, LocalDate.of(2027, 1, 1)));
    }

    @Test
    void rechazaClienteInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> ObligacionInputValidator.validar(null, TipoObligacion.IGV, PERIODO));
        assertThrows(IllegalArgumentException.class,
                () -> ObligacionInputValidator.validar(0L, TipoObligacion.IGV, PERIODO));
        assertThrows(IllegalArgumentException.class,
                () -> ObligacionInputValidator.validar(-1L, TipoObligacion.IGV, PERIODO));
    }

    @Test
    void rechazaTipoInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> ObligacionInputValidator.validar(1L, null, PERIODO));
        assertThrows(IllegalArgumentException.class, () -> TipoObligacion.fromCodigo(null));
        assertThrows(IllegalArgumentException.class, () -> TipoObligacion.fromCodigo(""));
        assertThrows(IllegalArgumentException.class, () -> TipoObligacion.fromCodigo("OTRO"));
    }

    @Test
    void rechazaPeriodoInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> ObligacionInputValidator.validar(1L, TipoObligacion.IGV, null));
        assertThrows(IllegalArgumentException.class,
                () -> ObligacionInputValidator.validar(1L, TipoObligacion.IGV,
                        LocalDate.of(2026, 10, 2)));
    }
}

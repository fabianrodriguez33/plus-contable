package com.pluscontable.tributario.recordatoriopago;

import static com.pluscontable.tributario.recordatoriopago.ReglaRecordatorioPago.Resultado.*;
import static org.junit.jupiter.api.Assertions.*;

import com.pluscontable.tributario.model.Obligacion;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ReglaRecordatorioPagoTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 12);
    private static final LocalDate VENCE = HOY.plusDays(10);

    @Test
    void declaradaSinPagosYEnFechaProgramadaCorresponde() {
        assertEquals(CORRESPONDE_ENVIAR, ReglaRecordatorioPago.evaluar(
                Obligacion.Estado.DECLARADO, VENCE, false, HOY, HOY));
    }

    @Test
    void declaradaSinPagosPeroAunNoLlegaLaFechaQuedaPendiente() {
        assertEquals(FECHA_PENDIENTE, ReglaRecordatorioPago.evaluar(
                Obligacion.Estado.DECLARADO, VENCE, false, HOY.plusDays(1), HOY));
    }

    @Test
    void pagoCompletoNoEsElegible() {
        assertEquals(NO_ELEGIBLE, ReglaRecordatorioPago.evaluar(
                Obligacion.Estado.PAGADO, VENCE, true, HOY, HOY));
    }

    @Test
    void pagoParcialNoEsElegiblePorqueYaExisteUnPago() {
        assertEquals(NO_ELEGIBLE, ReglaRecordatorioPago.evaluar(
                Obligacion.Estado.PAGO_PARCIAL, VENCE, true, HOY, HOY));
    }

    @Test
    void obligacionSinDeclararNoEsElegible() {
        assertEquals(NO_ELEGIBLE, ReglaRecordatorioPago.evaluar(
                Obligacion.Estado.PENDIENTE, VENCE, false, HOY, HOY));
    }

    @Test
    void recordatorioPosteriorAlVencimientoNoEsElegible() {
        assertEquals(NO_ELEGIBLE, ReglaRecordatorioPago.evaluar(
                Obligacion.Estado.DECLARADO, VENCE, false, VENCE.plusDays(1), HOY));
    }

    @Test
    void fechaDeRecordatorioYaPasadaNoSeReenvia() {
        assertEquals(NO_ELEGIBLE, ReglaRecordatorioPago.evaluar(
                Obligacion.Estado.DECLARADO, VENCE, false, HOY.minusDays(1), HOY));
    }

    @Test
    void reprogramarCambiaElDiaElegibleYLaClaveDelEvento() {
        LocalDate nuevaFecha = HOY.plusDays(2);
        assertEquals(FECHA_PENDIENTE, ReglaRecordatorioPago.evaluar(
                Obligacion.Estado.DECLARADO, VENCE, false, nuevaFecha, HOY));
        assertEquals(CORRESPONDE_ENVIAR, ReglaRecordatorioPago.evaluar(
                Obligacion.Estado.DECLARADO, VENCE, false, nuevaFecha, nuevaFecha));
        assertNotEquals(ReglaRecordatorioPago.claveEvento(7L, HOY),
                ReglaRecordatorioPago.claveEvento(7L, nuevaFecha));
    }

    @Test
    void fechasAusentesNoHabilitanRecordatorio() {
        assertEquals(NO_ELEGIBLE, ReglaRecordatorioPago.evaluar(
                Obligacion.Estado.DECLARADO, null, false, HOY, HOY));
        assertEquals(NO_ELEGIBLE, ReglaRecordatorioPago.evaluar(
                Obligacion.Estado.DECLARADO, VENCE, false, null, HOY));
        assertThrows(NullPointerException.class, () -> ReglaRecordatorioPago.evaluar(
                Obligacion.Estado.DECLARADO, VENCE, false, HOY, null));
    }

    @Test
    void mismaObligacionYFechaProduceLaMismaClaveIdempotente() {
        assertEquals(ReglaRecordatorioPago.claveEvento(7L, HOY), ReglaRecordatorioPago.claveEvento(7L, HOY));
    }
}

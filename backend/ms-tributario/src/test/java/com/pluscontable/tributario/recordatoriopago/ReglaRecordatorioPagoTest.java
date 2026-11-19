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
    void declaradaEnLaFechaProgramadaCorresponde() {
        assertEquals(CORRESPONDE_ENVIAR, ReglaRecordatorioPago.evaluar(Obligacion.Estado.DECLARADO, VENCE, HOY, HOY));
    }

    @Test
    void conPagoParcialTambienCorresponde_Beto18Nov() {
        assertEquals(CORRESPONDE_ENVIAR, ReglaRecordatorioPago.evaluar(Obligacion.Estado.PAGO_PARCIAL, VENCE, HOY, HOY));
        assertEquals(FECHA_PENDIENTE, ReglaRecordatorioPago.evaluar(Obligacion.Estado.PAGO_PARCIAL, VENCE, HOY.plusDays(1), HOY));
    }

    @Test
    void aunNoLlegaLaFechaQuedaPendiente() {
        assertEquals(FECHA_PENDIENTE, ReglaRecordatorioPago.evaluar(Obligacion.Estado.DECLARADO, VENCE, HOY.plusDays(1), HOY));
    }

    @Test
    void pagadaNoEsElegible() {
        assertEquals(NO_ELEGIBLE, ReglaRecordatorioPago.evaluar(Obligacion.Estado.PAGADO, VENCE, HOY, HOY));
    }

    @Test
    void obligacionSinDeclararNoEsElegible() {
        assertEquals(NO_ELEGIBLE, ReglaRecordatorioPago.evaluar(Obligacion.Estado.PENDIENTE, VENCE, HOY, HOY));
    }

    @Test
    void recordatorioPosteriorAlVencimientoNoEsElegible() {
        assertEquals(NO_ELEGIBLE, ReglaRecordatorioPago.evaluar(Obligacion.Estado.DECLARADO, VENCE, VENCE.plusDays(1), HOY));
    }

    @Test
    void siElSistemaEstuvoCaidoSeEnviaConRetrasoMientrasSigaSiendoUtil_Beto18Nov() {
        LocalDate fecha = HOY.minusDays(2);
        assertEquals(CORRESPONDE_ENVIAR, ReglaRecordatorioPago.evaluar(Obligacion.Estado.DECLARADO, VENCE, fecha, HOY));
        assertEquals(CORRESPONDE_ENVIAR, ReglaRecordatorioPago.evaluar(Obligacion.Estado.PAGO_PARCIAL, VENCE, fecha, VENCE));
    }

    @Test
    void yaVencidaElRecordatorioAtrasadoDejoDeSerUtil() {
        assertEquals(NO_ELEGIBLE, ReglaRecordatorioPago.evaluar(Obligacion.Estado.DECLARADO, VENCE, HOY, VENCE.plusDays(1)));
    }

    @Test
    void reprogramarCambiaElDiaElegibleYLaClaveDelEvento() {
        LocalDate nuevaFecha = HOY.plusDays(2);
        assertEquals(FECHA_PENDIENTE, ReglaRecordatorioPago.evaluar(Obligacion.Estado.DECLARADO, VENCE, nuevaFecha, HOY));
        assertEquals(CORRESPONDE_ENVIAR, ReglaRecordatorioPago.evaluar(Obligacion.Estado.DECLARADO, VENCE, nuevaFecha, nuevaFecha));
        assertNotEquals(ReglaRecordatorioPago.claveEvento(7L, HOY), ReglaRecordatorioPago.claveEvento(7L, nuevaFecha));
    }

    @Test
    void fechasAusentesNoHabilitanRecordatorio() {
        assertEquals(NO_ELEGIBLE, ReglaRecordatorioPago.evaluar(Obligacion.Estado.DECLARADO, null, HOY, HOY));
        assertEquals(NO_ELEGIBLE, ReglaRecordatorioPago.evaluar(Obligacion.Estado.DECLARADO, VENCE, null, HOY));
        assertThrows(NullPointerException.class, () -> ReglaRecordatorioPago.evaluar(Obligacion.Estado.DECLARADO, VENCE, HOY, null));
    }

    @Test
    void mismaObligacionYFechaProduceLaMismaClaveIdempotente() {
        assertEquals(ReglaRecordatorioPago.claveEvento(7L, HOY), ReglaRecordatorioPago.claveEvento(7L, HOY));
    }
}

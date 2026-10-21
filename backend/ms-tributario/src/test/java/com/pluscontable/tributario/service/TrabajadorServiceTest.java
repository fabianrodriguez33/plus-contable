package com.pluscontable.tributario.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** HU-03 · Tarea 36 (unitarias). La 37 (integración) va en un @SpringBootTest aparte. */
class TrabajadorServiceTest {

    @Test @Disabled("Tarea 36")
    void nacionalConDatosCompletosSeGuarda() { }

    @Test @Disabled("Tarea 36")
    void nacionalSinDatosObligatoriosSenalaLosCamposFaltantes() { }

    @Test @Disabled("Tarea 36")
    void extranjeroConDocumentoYContratoSeGuardaDiferenciado() { }

    @Test @Disabled("Tarea 36")
    void extranjeroSinDatosDeExtranjeriaNoSeGuarda() { }

    @Test @Disabled("Tarea 36")
    void afpSinNombreDeAfpNoSeGuarda() { }
}

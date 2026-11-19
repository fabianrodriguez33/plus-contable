package com.pluscontable.tributario.cierre;

import static com.pluscontable.tributario.cierre.EvaluadorImpedimentosCierre.Hallazgo.*;
import static com.pluscontable.tributario.cierre.EvaluadorImpedimentosCierre.Resultado.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class EvaluadorImpedimentosCierreTest {

    @Test
    void obligacionSinDeclararBloquea() {
        assertEquals(BLOQUEO, EvaluadorImpedimentosCierre.evaluar(Set.of(OBLIGACION_SIN_DECLARAR)).resultado());
    }

    @Test
    void declaracionPendienteBloquea() {
        assertEquals(BLOQUEO, EvaluadorImpedimentosCierre.evaluar(Set.of(DECLARACION_PENDIENTE)).resultado());
    }

    @Test
    void fraccionamientoActivoBloquea() {
        assertEquals(BLOQUEO, EvaluadorImpedimentosCierre.evaluar(Set.of(FRACCIONAMIENTO_ACTIVO)).resultado());
    }

    @Test
    void honorariosPendientesSoloAdvierten() {
        assertEquals(ADVERTENCIA, EvaluadorImpedimentosCierre.evaluar(Set.of(HONORARIOS_PENDIENTES)).resultado());
    }

    @Test
    void documentosFaltantesSoloAdvierten() {
        assertEquals(ADVERTENCIA, EvaluadorImpedimentosCierre.evaluar(Set.of(DOCUMENTOS_FALTANTES)).resultado());
    }

    @Test
    void regularizacionPendienteSoloAdvierte() {
        assertEquals(ADVERTENCIA, EvaluadorImpedimentosCierre.evaluar(Set.of(OBLIGACION_EN_REGULARIZACION)).resultado());
    }

    @Test
    void bloqueoTienePrioridadYConservaAdvertencias() {
        var evaluacion = EvaluadorImpedimentosCierre.evaluar(Set.of(
                OBLIGACION_SIN_DECLARAR, FRACCIONAMIENTO_ACTIVO, HONORARIOS_PENDIENTES, DOCUMENTOS_FALTANTES));
        assertEquals(BLOQUEO, evaluacion.resultado());
        assertEquals(Set.of(OBLIGACION_SIN_DECLARAR, FRACCIONAMIENTO_ACTIVO), evaluacion.bloqueos());
        assertEquals(Set.of(HONORARIOS_PENDIENTES, DOCUMENTOS_FALTANTES), evaluacion.advertencias());
    }

    @Test
    void sinHallazgosProporcionadosNoDeclaraBloqueos() {
        var evaluacion = EvaluadorImpedimentosCierre.evaluar(Set.of());
        assertEquals(SIN_IMPEDIMENTOS, evaluacion.resultado());
        assertTrue(evaluacion.bloqueos().isEmpty());
        assertTrue(evaluacion.advertencias().isEmpty());
    }

    @Test
    void rechazaEntradaNula() {
        assertThrows(NullPointerException.class, () -> EvaluadorImpedimentosCierre.evaluar(null));
    }

    @Test
    void rechazaHallazgoNulo() {
        var hallazgos = new HashSet<EvaluadorImpedimentosCierre.Hallazgo>();
        hallazgos.add(null);
        assertThrows(NullPointerException.class, () -> EvaluadorImpedimentosCierre.evaluar(hallazgos));
    }

    @Test
    void resultadoNoPermiteAlterarHallazgos() {
        var evaluacion = EvaluadorImpedimentosCierre.evaluar(Set.of(OBLIGACION_SIN_DECLARAR));
        assertThrows(UnsupportedOperationException.class, () -> evaluacion.bloqueos().clear());
    }
}

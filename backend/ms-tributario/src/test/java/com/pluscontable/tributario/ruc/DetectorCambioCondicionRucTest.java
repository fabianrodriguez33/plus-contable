package com.pluscontable.tributario.ruc;

import static com.pluscontable.tributario.model.Cliente.CondicionHabido.HABIDO;
import static com.pluscontable.tributario.model.Cliente.CondicionHabido.NO_HABIDO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DetectorCambioCondicionRucTest {

    private static ResultadoConsulta encontrado(String condicion) {
        return new ResultadoConsulta.Encontrado(new DatosRuc("20123456786", "EMPRESA SAC", "ACTIVO",
                condicion, null, null, "prueba", LocalDate.of(2026, 11, 10)));
    }

    @Test
    void deHabidoANoHabidoDetectaCambio() {
        assertThat(DetectorCambioCondicionRuc.evaluar(HABIDO, encontrado("NO HABIDO")))
                .isEqualTo(DetectorCambioCondicionRuc.Resultado.CAMBIO);
    }

    @Test
    void deNoHabidoAHabidoDetectaCambio() {
        assertThat(DetectorCambioCondicionRuc.evaluar(NO_HABIDO, encontrado("HABIDO")))
                .isEqualTo(DetectorCambioCondicionRuc.Resultado.CAMBIO);
    }

    @Test
    void deHabidoAHabidoNoDetectaCambio() {
        assertThat(DetectorCambioCondicionRuc.evaluar(HABIDO, encontrado("HABIDO")))
                .isEqualTo(DetectorCambioCondicionRuc.Resultado.SIN_CAMBIO);
    }

    @Test
    void deNoHabidoANoHabidoNoDetectaCambio() {
        assertThat(DetectorCambioCondicionRuc.evaluar(NO_HABIDO, encontrado("NO_HABIDO")))
                .isEqualTo(DetectorCambioCondicionRuc.Resultado.SIN_CAMBIO);
    }

    @Test
    void proveedorNoDisponibleNoSeInterpretaComoCambio() {
        assertThat(DetectorCambioCondicionRuc.evaluar(HABIDO, new ResultadoConsulta.NoDisponible("sin conexión")))
                .isEqualTo(DetectorCambioCondicionRuc.Resultado.CONSULTA_NO_VALIDA);
    }

    @Test
    void rucNoEncontradoNoSeInterpretaComoCambio() {
        assertThat(DetectorCambioCondicionRuc.evaluar(HABIDO, new ResultadoConsulta.NoEncontrado()))
                .isEqualTo(DetectorCambioCondicionRuc.Resultado.CONSULTA_NO_VALIDA);
    }

    @Test
    void respuestaAusenteNoSeInterpretaComoCambio() {
        assertThat(DetectorCambioCondicionRuc.evaluar(HABIDO, null))
                .isEqualTo(DetectorCambioCondicionRuc.Resultado.CONSULTA_NO_VALIDA);
    }

    @Test
    void datosAusentesNoSeInterpretanComoCambio() {
        assertThat(DetectorCambioCondicionRuc.evaluar(HABIDO, new ResultadoConsulta.Encontrado(null)))
                .isEqualTo(DetectorCambioCondicionRuc.Resultado.CONSULTA_NO_VALIDA);
    }

    @Test
    void condicionAusenteODesconocidaNoSeInterpretaComoCambio() {
        assertThat(DetectorCambioCondicionRuc.evaluar(HABIDO, encontrado(null)))
                .isEqualTo(DetectorCambioCondicionRuc.Resultado.CONSULTA_NO_VALIDA);
        assertThat(DetectorCambioCondicionRuc.evaluar(HABIDO, encontrado("CONDICION DESCONOCIDA")))
                .isEqualTo(DetectorCambioCondicionRuc.Resultado.CONSULTA_NO_VALIDA);
    }

    @Test
    void condicionAnteriorAusenteSeRechazaExplicitamente() {
        assertThatThrownBy(() -> DetectorCambioCondicionRuc.evaluar(null, encontrado("HABIDO")))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("La condición anterior es obligatoria");
    }

    @Test
    void utilizaElMapeoExistenteDeCondicionSinCrearEstadosNuevos() {
        assertThat(DetectorCambioCondicionRuc.evaluar(HABIDO, encontrado(" no habido ")))
                .isEqualTo(DetectorCambioCondicionRuc.Resultado.CAMBIO);
    }
}

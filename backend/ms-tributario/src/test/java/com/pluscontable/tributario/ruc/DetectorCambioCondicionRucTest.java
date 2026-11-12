package com.pluscontable.tributario.ruc;

import static com.pluscontable.tributario.model.Cliente.CondicionHabido.HABIDO;
import static com.pluscontable.tributario.model.Cliente.CondicionHabido.NO_HABIDO;
import static com.pluscontable.tributario.ruc.DetectorCambioCondicionRuc.EstadoSunat.*;
import static com.pluscontable.tributario.ruc.DetectorCambioCondicionRuc.TipoEvaluacion.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import com.pluscontable.tributario.ruc.DetectorCambioCondicionRuc.MotivoAviso;
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

    private static ResultadoConsulta respuesta(String condicion, String estado) {
        return new ResultadoConsulta.Encontrado(new DatosRuc("20123456786", "EMPRESA SAC", estado,
                condicion, null, null, "prueba", LocalDate.of(2026, 11, 11)));
    }

    @Test
    void activoABajaProduceMotivoBaja() {
        var r = DetectorCambioCondicionRuc.evaluar(HABIDO, ACTIVO, respuesta("HABIDO", "BAJA"));
        assertThat(r.tipo()).isEqualTo(CAMBIO_RELEVANTE);
        assertThat(r.cambioCondicion()).isFalse();
        assertThat(r.cambioEstado()).isTrue();
        assertThat(r.motivos()).containsExactly(MotivoAviso.BAJA);
    }

    @Test
    void activoASuspendidoProduceMotivoSuspendido() {
        var r = DetectorCambioCondicionRuc.evaluar(HABIDO, ACTIVO, respuesta("HABIDO", "SUSPENDIDO"));
        assertThat(r.tipo()).isEqualTo(CAMBIO_RELEVANTE);
        assertThat(r.motivos()).containsExactly(MotivoAviso.SUSPENDIDO);
    }

    @Test
    void habidoANoHabidoProduceMotivoNoHabido() {
        var r = DetectorCambioCondicionRuc.evaluar(HABIDO, ACTIVO, respuesta("NO HABIDO", "ACTIVO"));
        assertThat(r.tipo()).isEqualTo(CAMBIO_RELEVANTE);
        assertThat(r.motivos()).containsExactly(MotivoAviso.NO_HABIDO);
    }

    @Test
    void estadosIgualesNoProducenAviso() {
        for (var estado : new DetectorCambioCondicionRuc.EstadoSunat[]{ACTIVO, SUSPENDIDO, BAJA}) {
            for (var condicion : new com.pluscontable.tributario.model.Cliente.CondicionHabido[]{HABIDO, NO_HABIDO}) {
                var r = DetectorCambioCondicionRuc.evaluar(condicion, estado,
                        respuesta(condicion.name(), estado.name()));
                assertThat(r.tipo()).isEqualTo(SIN_CAMBIOS);
                assertThat(r.motivos()).isEmpty();
            }
        }
    }

    @Test
    void proveedorNoDisponibleRespuestaAusenteYNoEncontradoNoProducenMotivos() {
        for (ResultadoConsulta consulta : new ResultadoConsulta[]{
                new ResultadoConsulta.NoDisponible("sin conexión"), new ResultadoConsulta.NoEncontrado(), null}) {
            var r = DetectorCambioCondicionRuc.evaluar(HABIDO, ACTIVO, consulta);
            assertThat(r.tipo()).isEqualTo(CONSULTA_NO_VALIDA);
            assertThat(r.motivos()).isEmpty();
        }
    }

    @Test
    void estadoInvalidoOAusenteNoProduceAvisoAunqueLaCondicionCambie() {
        for (String estado : new String[]{"BAJA DEFINITIVA", "INVENTADO", ""}) {
            var r = DetectorCambioCondicionRuc.evaluar(HABIDO, ACTIVO, respuesta("NO HABIDO", estado));
            assertThat(r.tipo()).isEqualTo(CONSULTA_NO_VALIDA);
            assertThat(r.motivos()).isEmpty();
        }
        assertThat(DetectorCambioCondicionRuc.evaluar(HABIDO, ACTIVO, respuesta("NO HABIDO", null)).tipo())
                .isEqualTo(CONSULTA_NO_VALIDA);
    }

    @Test
    void condicionInvalidaODatosAusentesNoProducenAvisoAunqueElEstadoCambie() {
        assertThat(DetectorCambioCondicionRuc.evaluar(HABIDO, ACTIVO, respuesta("DESCONOCIDA", "BAJA")).tipo())
                .isEqualTo(CONSULTA_NO_VALIDA);
        assertThat(DetectorCambioCondicionRuc.evaluar(HABIDO, ACTIVO,
                new ResultadoConsulta.Encontrado(null)).motivos()).isEmpty();
    }

    @Test
    void recuperacionAActivoOHabidoEsCambioSinAviso() {
        var estado = DetectorCambioCondicionRuc.evaluar(HABIDO, SUSPENDIDO, respuesta("HABIDO", "ACTIVO"));
        var condicion = DetectorCambioCondicionRuc.evaluar(NO_HABIDO, ACTIVO, respuesta("HABIDO", "ACTIVO"));
        assertThat(estado.tipo()).isEqualTo(CAMBIO_SIN_AVISO);
        assertThat(condicion.tipo()).isEqualTo(CAMBIO_SIN_AVISO);
        assertThat(estado.motivos()).isEmpty();
        assertThat(condicion.motivos()).isEmpty();
    }

    @Test
    void variasTransicionesRelevantesConservanTodosLosMotivos() {
        var r = DetectorCambioCondicionRuc.evaluar(HABIDO, ACTIVO, respuesta("NO HABIDO", "BAJA"));
        assertThat(r.tipo()).isEqualTo(CAMBIO_RELEVANTE);
        assertThat(r.cambioCondicion()).isTrue();
        assertThat(r.cambioEstado()).isTrue();
        assertThat(r.motivos()).containsExactlyInAnyOrder(MotivoAviso.NO_HABIDO, MotivoAviso.BAJA);
    }

    @Test
    void recuperacionDeCondicionNoOcultaBajaSimultanea() {
        var r = DetectorCambioCondicionRuc.evaluar(NO_HABIDO, ACTIVO, respuesta("HABIDO", "BAJA"));
        assertThat(r.tipo()).isEqualTo(CAMBIO_RELEVANTE);
        assertThat(r.motivos()).containsExactly(MotivoAviso.BAJA);
    }

    @Test
    void faltaDeEstadoAnteriorNoInventaTransicionPeroPermiteCompararCondicion() {
        var sinBase = DetectorCambioCondicionRuc.evaluar(HABIDO, null, respuesta("HABIDO", "BAJA"));
        var conCondicion = DetectorCambioCondicionRuc.evaluar(HABIDO, null, respuesta("NO HABIDO", "BAJA"));
        assertThat(sinBase.tipo()).isEqualTo(SIN_BASE_COMPARACION);
        assertThat(sinBase.motivos()).isEmpty();
        assertThat(conCondicion.motivos()).containsExactly(MotivoAviso.NO_HABIDO);
    }

    @Test
    void estadoAnteriorDesconocidoNoEsFalloDelProveedorNiTransicionConfirmada() {
        var r = DetectorCambioCondicionRuc.evaluar(HABIDO, DESCONOCIDO, respuesta("HABIDO", "BAJA"));
        assertThat(r.tipo()).isEqualTo(SIN_BASE_COMPARACION);
        assertThat(r.motivos()).isEmpty();
    }

    @Test
    void estadoObservadoDesconocidoEsRespuestaValidaSinAviso() {
        var r = DetectorCambioCondicionRuc.evaluar(HABIDO, ACTIVO, respuesta("HABIDO", "DESCONOCIDO"));
        assertThat(r.tipo()).isEqualTo(CAMBIO_SIN_AVISO);
        assertThat(r.cambioEstado()).isTrue();
        assertThat(r.motivos()).isEmpty();
    }

    @Test
    void faltaDeCondicionAnteriorNoInventaTransicionPeroPermiteCompararEstado() {
        var r = DetectorCambioCondicionRuc.evaluar(null, ACTIVO, respuesta("NO HABIDO", "SUSPENDIDO"));
        assertThat(r.tipo()).isEqualTo(CAMBIO_RELEVANTE);
        assertThat(r.motivos()).containsExactly(MotivoAviso.SUSPENDIDO);
    }

    @Test
    void motivosDeLaEvaluacionNoSePuedenModificar() {
        var r = DetectorCambioCondicionRuc.evaluar(HABIDO, ACTIVO, respuesta("NO HABIDO", "BAJA"));
        assertThatThrownBy(() -> r.motivos().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}

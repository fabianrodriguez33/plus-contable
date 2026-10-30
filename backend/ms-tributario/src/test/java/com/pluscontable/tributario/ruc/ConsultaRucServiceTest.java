package com.pluscontable.tributario.ruc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.model.Cliente;
import jakarta.validation.ValidationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConsultaRucServiceTest {

    private static final String RUC = "20123456786";

    /** Doble de pruebas: registra los RUC consultados y responde lo que se le indique. */
    private static class Doble implements ConsultaRuc {
        final List<String> consultados = new ArrayList<>();
        Function<String, ResultadoConsulta> respuesta = r -> new ResultadoConsulta.NoDisponible("sin configurar");

        @Override
        public ResultadoConsulta consultar(String ruc) {
            consultados.add(ruc);
            return respuesta.apply(ruc);
        }
    }

    private static DatosRuc datos(String estado, String condicion, String direccion) {
        return new DatosRuc(RUC, "EMPRESA SAC", estado, condicion, direccion, "150101", "SUNAT",
                LocalDate.of(2026, 10, 29));
    }

    private final Doble doble = new Doble();
    private final ConsultaRucService servicio = new ConsultaRucService(doble);

    @ParameterizedTest
    @ValueSource(strings = {"", "123", "2012345678", "201234567890", "2012345678a", "20123456789"})
    void unRucMalFormadoSeRechazaSinLlamarAlProveedor(String ruc) {
        assertThatThrownBy(() -> servicio.consultar(ruc)).isInstanceOf(ValidationException.class);
        assertThat(doble.consultados).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"10123456781", "15123456782", "17123456785"})
    void losRucFueraDelPrefijo20NoSeConsultanYPermitenIngresoManual(String ruc) {
        var r = servicio.consultar(ruc);

        assertThat(r.resultado()).isEqualTo(ConsultaRucDto.Resultado.FUERA_DE_COBERTURA);
        assertThat(r.mensaje()).contains("manualmente");
        assertThat(r.datos()).isNull();
        assertThat(doble.consultados).isEmpty();
    }

    @Test
    void unRucActivoYHabidoDevuelveLaSugerenciaSinAvisos() {
        doble.respuesta = r -> new ResultadoConsulta.Encontrado(datos("ACTIVO", "HABIDO", "AV. UNO 1"));

        var r = servicio.consultar(RUC);

        assertThat(r.resultado()).isEqualTo(ConsultaRucDto.Resultado.ENCONTRADO);
        assertThat(r.avisos()).isEmpty();
        assertThat(r.datos().razonSocial()).isEqualTo("EMPRESA SAC");
        assertThat(r.datos().direccion()).isEqualTo("AV. UNO 1");
        assertThat(r.datos().condicionHabido()).isEqualTo(Cliente.CondicionHabido.HABIDO);
        assertThat(r.datos().fechaDatos()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(doble.consultados).containsExactly(RUC);
    }

    @Test
    void unEstadoDistintoDeActivoAvisaYNoBloquea() {
        doble.respuesta = r -> new ResultadoConsulta.Encontrado(datos("BAJA DEFINITIVA", "HABIDO", "AV. UNO 1"));

        var r = servicio.consultar(RUC);

        assertThat(r.resultado()).isEqualTo(ConsultaRucDto.Resultado.ENCONTRADO);
        assertThat(r.avisos()).singleElement().asString().contains("BAJA DEFINITIVA");
        assertThat(r.datos()).isNotNull();
    }

    @Test
    void unaCondicionNoHabidoAvisaYSeMapeaAlEnumDelSistema() {
        doble.respuesta = r -> new ResultadoConsulta.Encontrado(datos("ACTIVO", "NO HABIDO", "AV. UNO 1"));

        var r = servicio.consultar(RUC);

        assertThat(r.avisos()).singleElement().asString().contains("NO HABIDO");
        assertThat(r.datos().condicionHabido()).isEqualTo(Cliente.CondicionHabido.NO_HABIDO);
    }

    @Test
    void unaCondicionDesconocidaNoSeMapeaYQuedaParaIngresoManual() {
        doble.respuesta = r -> new ResultadoConsulta.Encontrado(datos("ACTIVO", "NO HALLADO", "AV. UNO 1"));

        var r = servicio.consultar(RUC);

        assertThat(r.datos().condicionHabido()).isNull();
        assertThat(r.datos().condicion()).isEqualTo("NO HALLADO");
        assertThat(r.avisos()).singleElement().asString().contains("NO HALLADO");
    }

    @Test
    void siFaltanEstadoYCondicionAvisaDeAmbos() {
        doble.respuesta = r -> new ResultadoConsulta.Encontrado(datos(null, null, null));

        var r = servicio.consultar(RUC);

        assertThat(r.avisos()).hasSize(2);
        assertThat(r.datos().direccion()).isNull();
    }

    @Test
    void unaDireccionMasLargaQueLaColumnaSeRecortaYAvisa() {
        String larga = "A".repeat(300);
        doble.respuesta = r -> new ResultadoConsulta.Encontrado(datos("ACTIVO", "HABIDO", larga));

        var r = servicio.consultar(RUC);

        assertThat(r.datos().direccion()).hasSize(250);
        assertThat(r.avisos()).singleElement().asString().contains("300").contains("250");
    }

    @Test
    void unRucNoEncontradoSeDistingueDelServicioCaido() {
        doble.respuesta = r -> new ResultadoConsulta.NoEncontrado();
        var noEncontrado = servicio.consultar(RUC);

        doble.respuesta = r -> new ResultadoConsulta.NoDisponible("tiempo agotado");
        var caido = servicio.consultar(RUC);

        assertThat(noEncontrado.resultado()).isEqualTo(ConsultaRucDto.Resultado.NO_ENCONTRADO);
        assertThat(caido.resultado()).isEqualTo(ConsultaRucDto.Resultado.NO_DISPONIBLE);
        assertThat(noEncontrado.mensaje()).isNotEqualTo(caido.mensaje());
        assertThat(caido.mensaje()).contains("manualmente").doesNotContain("tiempo agotado");
        assertThat(noEncontrado.datos()).isNull();
        assertThat(caido.datos()).isNull();
    }
}

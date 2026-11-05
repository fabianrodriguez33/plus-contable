package com.pluscontable.tributario.obligacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.obligacion.GeneradorObligaciones.Omision;
import com.pluscontable.tributario.obligacion.GeneradorObligaciones.ReglasMes;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** HU-07: qué tipos se generan en el mes según los datos del cliente. */
class TiposDelMesTest {
    /** ESSALUD encendido, para probar su código; el apagado se prueba aparte. */
    private static ReglasMes reglas(boolean planilla, boolean asegurados, String motivoSinQuinta) {
        return new ReglasMes(planilla, asegurados, true, motivoSinQuinta);
    }

    private static List<TipoObligacion> omitidas(GeneradorObligaciones.TiposDelMes t) {
        return t.omitidas().stream().map(Omision::tipo).toList();
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void igvYRentaSeGeneranSiempre(boolean planilla, boolean asegurados) {
        var t = GeneradorObligaciones.tiposDelMes(reglas(planilla, asegurados, null));

        assertThat(t.generar()).contains(TipoObligacion.IGV, TipoObligacion.RENTA);
    }

    @Test
    void afpNuncaSeGeneraYSiempreSeInformaConSuMotivo() {
        var t = GeneradorObligaciones.tiposDelMes(reglas(true, true, null));

        assertThat(t.generar()).doesNotContain(TipoObligacion.AFP);
        assertThat(t.omitidas()).containsExactly(new Omision(TipoObligacion.AFP, GeneradorObligaciones.MOTIVO_AFP));
    }

    @Test
    void conPlanillaYRemuneracionSeGeneranTodosMenosAfp() {
        var t = GeneradorObligaciones.tiposDelMes(reglas(true, true, null));

        assertThat(t.generar()).containsExactly(TipoObligacion.IGV, TipoObligacion.RENTA, TipoObligacion.PLAME,
                TipoObligacion.ESSALUD, TipoObligacion.RETENCION_QUINTA);
    }

    @Test
    void sinPlanillaNoSeGeneranPlameNiEssalud() {
        var t = GeneradorObligaciones.tiposDelMes(reglas(false, false, "x"));

        assertThat(t.generar()).containsExactly(TipoObligacion.IGV, TipoObligacion.RENTA);
        assertThat(t.omitidas()).contains(
                new Omision(TipoObligacion.PLAME, GeneradorObligaciones.MOTIVO_SIN_PLANILLA),
                new Omision(TipoObligacion.ESSALUD, GeneradorObligaciones.MOTIVO_SIN_PLANILLA));
    }

    @Test
    void conPlanillaSinRemuneracionSeGeneraPlameYNoEssalud() {
        var t = GeneradorObligaciones.tiposDelMes(reglas(true, false, "x"));

        assertThat(t.generar()).containsExactly(TipoObligacion.IGV, TipoObligacion.RENTA, TipoObligacion.PLAME);
        assertThat(t.omitidas()).contains(
                new Omision(TipoObligacion.ESSALUD, GeneradorObligaciones.MOTIVO_SIN_ASEGURADOS));
    }

    @Test
    void laQuintaDependeSoloDeSuMotivoNoDeLaPlanillaNiDeLasRemuneraciones() {
        var con = GeneradorObligaciones.tiposDelMes(reglas(false, false, null));
        var sin = GeneradorObligaciones.tiposDelMes(reglas(true, true, "porque sí"));

        assertThat(con.generar()).contains(TipoObligacion.RETENCION_QUINTA);
        assertThat(sin.generar()).doesNotContain(TipoObligacion.RETENCION_QUINTA);
        assertThat(sin.omitidas()).contains(new Omision(TipoObligacion.RETENCION_QUINTA, "porque sí"));
    }

    @Test
    void todoTipoQueNoSeGeneraTieneUnMotivoYNingunoSeRepite() {
        var t = GeneradorObligaciones.tiposDelMes(reglas(false, false, "x"));

        assertThat(omitidas(t)).doesNotHaveDuplicates().doesNotContainAnyElementsOf(t.generar());
        assertThat(t.omitidas()).allSatisfy(o -> assertThat(o.motivo()).isNotBlank());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void conEssaludApagadoNuncaSeGeneraYElMotivoEsElPendiente(boolean planilla, boolean asegurados) {
        var t = GeneradorObligaciones.tiposDelMes(new ReglasMes(planilla, asegurados, false, null));

        assertThat(t.generar()).doesNotContain(TipoObligacion.ESSALUD);
        assertThat(t.omitidas()).contains(
                new Omision(TipoObligacion.ESSALUD, "Pendiente de confirmar la regla del estudio"));
    }

    @Test
    void conEssaludApagadoIgvRentaYPlameSiguenIgual() {
        var t = GeneradorObligaciones.tiposDelMes(new ReglasMes(true, true, false, null));

        assertThat(t.generar()).containsExactly(TipoObligacion.IGV, TipoObligacion.RENTA, TipoObligacion.PLAME,
                TipoObligacion.RETENCION_QUINTA);
    }

    @Test
    void exigeLasReglas() {
        assertThatThrownBy(() -> GeneradorObligaciones.tiposDelMes(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void solicitudesDelMesUsanLaMismaFechaParaCadaTipoElegido() {
        var s = GeneradorObligaciones.solicitudesDelMes(7L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 20),
                List.of(TipoObligacion.IGV, TipoObligacion.RENTA));

        assertThat(s).extracting(x -> x.clave().tipoObligacion())
                .containsExactly(TipoObligacion.IGV, TipoObligacion.RENTA);
        assertThat(s).allSatisfy(x -> assertThat(x.fechaVencimiento()).isEqualTo(LocalDate.of(2026, 11, 20)));
    }
}

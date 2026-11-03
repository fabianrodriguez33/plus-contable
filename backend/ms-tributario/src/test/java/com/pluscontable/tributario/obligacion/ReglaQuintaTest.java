package com.pluscontable.tributario.obligacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.pluscontable.tributario.model.Trabajador;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** HU-07: la quinta categoría viaja apagada; los montos de estas pruebas son FICTICIOS, no la UIT real. */
class ReglaQuintaTest {
    private final ParametrosTributarios parametros = new ParametrosTributarios();

    private static Trabajador con(String remuneracion) {
        Trabajador t = new Trabajador();
        t.setRemuneracion(remuneracion == null ? null : new BigDecimal(remuneracion));
        return t;
    }

    private void activarConValoresFicticios() {
        parametros.setQuintaActiva(true);
        parametros.setQuintaSueldosAlAnio(new BigDecimal("10"));
        parametros.getUit().put(2026, new BigDecimal("1000"));
    }

    @Test
    void porDefectoNoTieneNingunMontoYEstaApagada() {
        assertThat(parametros.isQuintaActiva()).isFalse();
        assertThat(parametros.getUit()).isEmpty();
        assertThat(parametros.uitDe(2026)).isEmpty();
        assertThat(parametros.sueldosAlAnio()).isEmpty();
        assertThat(parametros.getQuintaUmbralUit()).isEqualByComparingTo("7");
    }

    @Test
    void apagadaSiempreEstaPendienteAunqueHayaUitYTrabajadores() {
        parametros.getUit().put(2026, new BigDecimal("1000"));
        parametros.setQuintaSueldosAlAnio(new BigDecimal("10"));

        assertThat(ReglaQuinta.motivoDeOmision(parametros, 2026, List.of(con("999999"))))
                .contains(ReglaQuinta.MOTIVO_PENDIENTE);
    }

    @Test
    void encendidaSinFormulaSigueEstandoPendiente() {
        parametros.setQuintaActiva(true);
        parametros.getUit().put(2026, new BigDecimal("1000"));

        assertThat(ReglaQuinta.motivoDeOmision(parametros, 2026, List.of(con("999999"))))
                .contains(ReglaQuinta.MOTIVO_PENDIENTE);
    }

    @Test
    void sinUitDelAnioDiceCualFalta() {
        activarConValoresFicticios();

        assertThat(ReglaQuinta.motivoDeOmision(parametros, 2027, List.of(con("999999"))))
                .contains("Falta la UIT de 2027");
    }

    @Test
    void sinTrabajadoresNoCorresponde() {
        activarConValoresFicticios();

        assertThat(ReglaQuinta.motivoDeOmision(parametros, 2026, List.of()))
                .contains(GeneradorObligaciones.MOTIVO_SIN_PLANILLA);
    }

    @Test
    void justoEnElUmbralNoCorrespondeYUnCentimoPorEncimaSi() {
        activarConValoresFicticios();

        assertThat(ReglaQuinta.motivoDeOmision(parametros, 2026, List.of(con("700.00")))).isPresent();
        assertThat(ReglaQuinta.motivoDeOmision(parametros, 2026, List.of(con("700.01")))).isEmpty();
    }

    @Test
    void bastaConUnTrabajadorPorEncimaDelUmbralYLaRemuneracionNulaNoCuenta() {
        activarConValoresFicticios();

        assertThat(ReglaQuinta.motivoDeOmision(parametros, 2026, List.of(con(null), con("100"), con("800"))))
                .isEmpty();
        assertThat(ReglaQuinta.motivoDeOmision(parametros, 2026, List.of(con(null)))).isPresent();
    }
}

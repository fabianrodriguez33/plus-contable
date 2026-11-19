package com.pluscontable.tributario.compras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.compras.ReglaClasificacionCompra.Estado;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** HU-25 · T215: reglas de la clasificación de compras (decisiones de Beto del 18/11). */
class ReglaClasificacionCompraTest {

    @Test
    void elCatalogoTieneSoloLosDosTiposAcordadosConBeto() {
        assertThat(ReglaClasificacionCompra.Tipo.values())
                .containsExactly(ReglaClasificacionCompra.Tipo.MERCADERIA_O_GASTO, ReglaClasificacionCompra.Tipo.ACTIVO_FIJO);
    }

    @Test
    void elEstadoSaleDeLaPropuestaDelClienteYDeLaValidacionDelContador() {
        assertThat(ReglaClasificacionCompra.estado(false, false)).isEqualTo(Estado.SIN_CLASIFICAR);
        assertThat(ReglaClasificacionCompra.estado(true, false)).isEqualTo(Estado.PROPUESTA);
        assertThat(ReglaClasificacionCompra.estado(true, true)).isEqualTo(Estado.VALIDADA);
    }

    @Test
    void elUltimoDiaCalendarioDelMes() {
        assertThat(ReglaClasificacionCompra.ultimoDiaDelMes(LocalDate.of(2026, 11, 18))).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(ReglaClasificacionCompra.ultimoDiaDelMes(LocalDate.of(2028, 2, 3))).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(ReglaClasificacionCompra.ultimoDiaDelMes(LocalDate.of(2026, 12, 31))).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void avisaDesdeCincoDiasAntesDelUltimoDiaDelMesYSigueHastaQueSeValide() {
        for (Estado e : new Estado[] {Estado.SIN_CLASIFICAR, Estado.PROPUESTA}) {
            assertThat(ReglaClasificacionCompra.alertaCierre(e, LocalDate.of(2026, 11, 24), 5)).as(e + " 24/11").isFalse();
            assertThat(ReglaClasificacionCompra.alertaCierre(e, LocalDate.of(2026, 11, 25), 5)).as(e + " 25/11").isTrue();   // 5 días antes del 30
            assertThat(ReglaClasificacionCompra.alertaCierre(e, LocalDate.of(2026, 11, 30), 5)).as(e + " 30/11").isTrue();
        }
        // en el mes siguiente la cuenta empieza de nuevo: el 1/12 faltan 30 días para el último día
        assertThat(ReglaClasificacionCompra.alertaCierre(Estado.SIN_CLASIFICAR, LocalDate.of(2026, 12, 1), 5)).isFalse();
        assertThat(ReglaClasificacionCompra.alertaCierre(Estado.SIN_CLASIFICAR, LocalDate.of(2026, 12, 26), 5)).isTrue();   // 5 días antes del 31
    }

    @Test
    void unaValidadaNuncaAvisa() {
        assertThat(ReglaClasificacionCompra.alertaCierre(Estado.VALIDADA, LocalDate.of(2026, 11, 30), 5)).isFalse();
    }

    @Test
    void losDiasSonConfigurables() {
        assertThat(ReglaClasificacionCompra.alertaCierre(Estado.SIN_CLASIFICAR, LocalDate.of(2026, 11, 20), 10)).isTrue();
        assertThat(ReglaClasificacionCompra.alertaCierre(Estado.SIN_CLASIFICAR, LocalDate.of(2026, 11, 29), 0)).isFalse();
        assertThat(ReglaClasificacionCompra.alertaCierre(Estado.SIN_CLASIFICAR, LocalDate.of(2026, 11, 30), 0)).isTrue();
    }

    @Test
    void argumentosInvalidos() {
        assertThatThrownBy(() -> ReglaClasificacionCompra.alertaCierre(Estado.SIN_CLASIFICAR, LocalDate.of(2026, 11, 30), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReglaClasificacionCompra.alertaCierre(Estado.SIN_CLASIFICAR, null, 5)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ReglaClasificacionCompra.alertaCierre(null, LocalDate.of(2026, 11, 30), 5)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void laDescripcionEsObligatoriaYTieneMaximo() {
        assertThat(ReglaClasificacionCompra.descripcionValida("Laptop para contabilidad")).isNull();
        assertThat(ReglaClasificacionCompra.descripcionValida(null)).contains("obligatoria");
        assertThat(ReglaClasificacionCompra.descripcionValida("   ")).contains("obligatoria");
        assertThat(ReglaClasificacionCompra.descripcionValida("x".repeat(300))).isNull();
        assertThat(ReglaClasificacionCompra.descripcionValida("x".repeat(301))).contains("300");
    }
}

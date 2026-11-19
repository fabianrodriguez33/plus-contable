package com.pluscontable.tributario.compras;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** HU-25 · T215: reglas de la clasificación de compras. */
class ReglaClasificacionCompraTest {
    private static final LocalDate NOVIEMBRE = LocalDate.of(2026, 11, 1);

    @Test
    void elCatalogoTieneSoloLosDosTiposAcordadosConBeto() {
        assertThat(ReglaClasificacionCompra.Tipo.values())
                .containsExactly(ReglaClasificacionCompra.Tipo.MERCADERIA_O_GASTO, ReglaClasificacionCompra.Tipo.ACTIVO_FIJO);
    }

    @Test
    void elCierreEsElUltimoDiaDelMesDelPeriodo() {
        assertThat(ReglaClasificacionCompra.cierre(NOVIEMBRE)).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(ReglaClasificacionCompra.cierre(LocalDate.of(2028, 2, 1))).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(ReglaClasificacionCompra.cierre(LocalDate.of(2026, 12, 1))).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void sinClasificarAvisaDesdeCincoDiasAntesDelCierre() {
        assertThat(ReglaClasificacionCompra.alertaCierre(false, NOVIEMBRE, LocalDate.of(2026, 11, 24), 5)).isFalse();
        assertThat(ReglaClasificacionCompra.alertaCierre(false, NOVIEMBRE, LocalDate.of(2026, 11, 25), 5)).isTrue();   // 5 días antes
        assertThat(ReglaClasificacionCompra.alertaCierre(false, NOVIEMBRE, LocalDate.of(2026, 11, 30), 5)).isTrue();   // el cierre
        assertThat(ReglaClasificacionCompra.alertaCierre(false, NOVIEMBRE, LocalDate.of(2026, 12, 3), 5)).isTrue();    // sigue pendiente
    }

    @Test
    void losDiasSonConfigurables() {
        assertThat(ReglaClasificacionCompra.alertaCierre(false, NOVIEMBRE, LocalDate.of(2026, 11, 20), 10)).isTrue();
        assertThat(ReglaClasificacionCompra.alertaCierre(false, NOVIEMBRE, LocalDate.of(2026, 11, 29), 0)).isFalse();
        assertThat(ReglaClasificacionCompra.alertaCierre(false, NOVIEMBRE, LocalDate.of(2026, 11, 30), 0)).isTrue();
    }

    @Test
    void unaCompraClasificadaNuncaAvisa() {
        assertThat(ReglaClasificacionCompra.alertaCierre(true, NOVIEMBRE, LocalDate.of(2026, 11, 30), 5)).isFalse();
    }

    @Test
    void argumentosInvalidos() {
        assertThatThrownBy(() -> ReglaClasificacionCompra.alertaCierre(false, NOVIEMBRE, LocalDate.of(2026, 11, 30), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReglaClasificacionCompra.alertaCierre(false, NOVIEMBRE, null, 5)).isInstanceOf(NullPointerException.class);
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

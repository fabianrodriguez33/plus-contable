package com.pluscontable.tributario.vencimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.model.Obligacion.Estado;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** HU-16 · T46: reglas de VENCIDA SIN PAGO. */
class ReglaVencidaSinPagoTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 18);

    @Test
    void ayerSinConstanciaEstaVencida() {
        assertThat(ReglaVencidaSinPago.corresponde(Estado.DECLARADO, HOY.minusDays(1), HOY, false)).isTrue();
        assertThat(ReglaVencidaSinPago.corresponde(Estado.PENDIENTE, HOY.minusDays(30), HOY, false)).isTrue();
    }

    @Test
    void elDiaDeLaFechaLimiteYAntesNoEstaVencida() {
        assertThat(ReglaVencidaSinPago.corresponde(Estado.DECLARADO, HOY, HOY, false)).isFalse();
        assertThat(ReglaVencidaSinPago.corresponde(Estado.DECLARADO, HOY.plusDays(1), HOY, false)).isFalse();
    }

    @Test
    void siExisteConstanciaNoSeMarca() {
        assertThat(ReglaVencidaSinPago.corresponde(Estado.DECLARADO, HOY.minusDays(5), HOY, true)).isFalse();
        assertThat(ReglaVencidaSinPago.corresponde(Estado.PAGO_PARCIAL, HOY.minusDays(5), HOY, true)).isFalse();
    }

    @Test
    void pagadaNuncaSeMarca() {
        assertThat(ReglaVencidaSinPago.corresponde(Estado.PAGADO, HOY.minusDays(5), HOY, false)).isFalse();
    }

    @Test
    void cambiaSoloAlPasarLaFechaLimite() {
        LocalDate limite = LocalDate.of(2026, 11, 20);
        assertThat(ReglaVencidaSinPago.corresponde(Estado.DECLARADO, limite, limite, false)).isFalse();
        assertThat(ReglaVencidaSinPago.corresponde(Estado.DECLARADO, limite, limite.plusDays(1), false)).isTrue();
    }

    @Test
    void diasVencidaYArgumentosInvalidos() {
        assertThat(ReglaVencidaSinPago.diasVencida(HOY.minusDays(7), HOY)).isEqualTo(7);
        assertThat(ReglaVencidaSinPago.diasVencida(HOY.plusDays(2), HOY)).isZero();
        assertThat(ReglaVencidaSinPago.corresponde(Estado.DECLARADO, null, HOY, false)).isFalse();
        assertThatThrownBy(() -> ReglaVencidaSinPago.corresponde(null, HOY, HOY, false)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ReglaVencidaSinPago.corresponde(Estado.DECLARADO, HOY, null, false))
                .isInstanceOf(NullPointerException.class);
    }
}

package com.pluscontable.tributario.vencimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.model.Obligacion.Estado;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** HU-16 · T46: definición de «vencida» de Beto (18/11): fecha pasada y saldo pendiente; las sin declarar van aparte. */
class ReglaObligacionVencidaTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 18);

    @Test
    void declaradaSinPagoOConPagoParcialConFechaPasadaEstaVencida() {
        assertThat(ReglaObligacionVencida.vencida(Estado.DECLARADO, HOY.minusDays(1), HOY)).isTrue();
        assertThat(ReglaObligacionVencida.vencida(Estado.PAGO_PARCIAL, HOY.minusDays(30), HOY)).isTrue();
    }

    @Test
    void conPagoParcialSigueVencidaMientrasQuedeSaldo_yLaPagadaNunca() {
        assertThat(ReglaObligacionVencida.vencida(Estado.PAGO_PARCIAL, HOY.minusDays(5), HOY)).isTrue();
        assertThat(ReglaObligacionVencida.vencida(Estado.PAGADO, HOY.minusDays(5), HOY)).isFalse();
        assertThat(ReglaObligacionVencida.sinDeclarar(Estado.PAGADO, HOY.minusDays(5), HOY)).isFalse();
    }

    @Test
    void elDiaDeLaFechaLimiteYAntesNoEstaVencida() {
        for (Estado e : Estado.values()) {
            assertThat(ReglaObligacionVencida.vencida(e, HOY, HOY)).as(e + " hoy").isFalse();
            assertThat(ReglaObligacionVencida.vencida(e, HOY.plusDays(1), HOY)).as(e + " futura").isFalse();
            assertThat(ReglaObligacionVencida.sinDeclarar(e, HOY, HOY)).as(e + " hoy").isFalse();
        }
    }

    @Test
    void laSinDeclararConFechaPasadaNoEsVencidaSinoPendienteDeAtencion() {
        assertThat(ReglaObligacionVencida.vencida(Estado.PENDIENTE, HOY.minusDays(3), HOY)).isFalse();
        assertThat(ReglaObligacionVencida.sinDeclarar(Estado.PENDIENTE, HOY.minusDays(3), HOY)).isTrue();
        assertThat(ReglaObligacionVencida.sinDeclarar(Estado.DECLARADO, HOY.minusDays(3), HOY)).isFalse();
        assertThat(ReglaObligacionVencida.sinDeclarar(Estado.PAGO_PARCIAL, HOY.minusDays(3), HOY)).isFalse();
    }

    @Test
    void cambiaSoloAlPasarLaFechaLimite() {
        LocalDate limite = LocalDate.of(2026, 11, 20);
        assertThat(ReglaObligacionVencida.vencida(Estado.DECLARADO, limite, limite)).isFalse();
        assertThat(ReglaObligacionVencida.vencida(Estado.DECLARADO, limite, limite.plusDays(1))).isTrue();
        assertThat(ReglaObligacionVencida.sinDeclarar(Estado.PENDIENTE, limite, limite)).isFalse();
        assertThat(ReglaObligacionVencida.sinDeclarar(Estado.PENDIENTE, limite, limite.plusDays(1))).isTrue();
    }

    @Test
    void diasVencidaYArgumentosInvalidos() {
        assertThat(ReglaObligacionVencida.diasVencida(HOY.minusDays(7), HOY)).isEqualTo(7);
        assertThat(ReglaObligacionVencida.diasVencida(HOY.plusDays(2), HOY)).isZero();
        assertThat(ReglaObligacionVencida.vencida(Estado.DECLARADO, null, HOY)).isFalse();
        assertThat(ReglaObligacionVencida.sinDeclarar(Estado.PENDIENTE, null, HOY)).isFalse();
        assertThatThrownBy(() -> ReglaObligacionVencida.vencida(null, HOY, HOY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ReglaObligacionVencida.vencida(Estado.DECLARADO, HOY, null)).isInstanceOf(NullPointerException.class);
    }
}

package com.pluscontable.tributario.obligacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Hu07ComponentesTest {
    private static final String CSV = "periodo_tributario,ultimo_digito_ruc,fecha_vencimiento\n";

    @Test
    void validaClienteTipoYPeriodo() {
        ObligacionInputValidator.validar(1L, TipoObligacion.IGV, LocalDate.of(2026, 1, 1));
        assertThatThrownBy(() -> ObligacionInputValidator.validar(0L, TipoObligacion.IGV,
                LocalDate.of(2026, 1, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObligacionInputValidator.validar(1L, null,
                LocalDate.of(2026, 1, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ObligacionInputValidator.validar(1L, TipoObligacion.IGV,
                LocalDate.of(2026, 1, 2))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conservaLosSeisTiposAcordados() {
        assertThat(TipoObligacion.values()).extracting(Enum::name)
                .containsExactly("IGV", "RENTA", "PLAME", "ESSALUD", "RETENCION_QUINTA", "AFP");
    }

    @Test
    void leeCsvSinAsignarUnTipo() throws Exception {
        var filas = CronogramaCsvReader.leer(new StringReader(CSV + "2026-01,0,2026-02-16\n"));
        assertThat(filas).containsExactly(new CronogramaTributario(YearMonth.of(2026, 1), "0",
                LocalDate.of(2026, 2, 16)));
    }

    @Test
    void rechazaPeriodoYGrupoDuplicadosEnCsv() {
        assertThatThrownBy(() -> CronogramaCsvReader.leer(new StringReader(CSV
                + "2026-01,0,2026-02-16\n2026-01,0,2026-02-17\n")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicados");
    }

    @Test
    void preparaSoloClavesNuevas() {
        var clave = new GeneradorObligaciones.Clave(1L, TipoObligacion.IGV, LocalDate.of(2026, 1, 1));
        var otra = new GeneradorObligaciones.Clave(1L, TipoObligacion.RENTA, LocalDate.of(2026, 1, 1));
        var fecha = LocalDate.of(2026, 2, 16);
        var solicitudes = List.of(new GeneradorObligaciones.Solicitud(clave, fecha),
                new GeneradorObligaciones.Solicitud(clave, fecha),
                new GeneradorObligaciones.Solicitud(otra, fecha));
        assertThat(GeneradorObligaciones.preparar(solicitudes, Set.of(clave)))
                .containsExactly(new GeneradorObligaciones.Solicitud(otra, fecha));
    }
}

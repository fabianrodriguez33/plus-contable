package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.DeudaVencidaDto;
import com.pluscontable.tributario.dto.MorosoDto;
import com.pluscontable.tributario.model.EstadoCobro;
import com.pluscontable.tributario.repository.CobroHonorarioRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** HU-31 · Tarea 154 (unitarias): monto y días de atraso, quién entra en la lista y en qué orden. */
class MorosoServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 11);

    private final CobroHonorarioRepository cobros = mock(CobroHonorarioRepository.class);
    private final MorosoService service = new MorosoService(cobros, Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC));

    private static DeudaVencidaDto deuda(long id, String monto, long cobros, String desde) {
        return new DeudaVencidaDto(id, "2010000000" + id, "Cliente " + id, new BigDecimal(monto), cobros, LocalDate.parse(desde));
    }

    @Test
    void criterio1_cadaMorosoTraeSuMontoVencidoYLosDiasDesdeSuCobroVencidoMasAntiguo() {
        when(cobros.deudasVencidas(any(), eq(HOY))).thenReturn(List.of(deuda(4, "750.00", 2, "2026-10-15")));

        assertThat(service.listar()).singleElement().satisfies(m -> {
            assertThat(m.clienteId()).isEqualTo(4L);
            assertThat(m.razonSocial()).isEqualTo("Cliente 4");
            assertThat(m.montoVencido()).isEqualByComparingTo("750.00");
            assertThat(m.cobrosVencidos()).isEqualTo(2);
            assertThat(m.vencidoDesde()).isEqualTo(LocalDate.of(2026, 10, 15));
            assertThat(m.diasAtraso()).isEqualTo(27);
        });
    }

    @Test
    void criterio1_unCobroQueVencioAyerTieneUnDiaDeAtraso() {
        when(cobros.deudasVencidas(any(), eq(HOY))).thenReturn(List.of(deuda(4, "10.00", 1, "2026-11-10")));

        assertThat(service.listar().get(0).diasAtraso()).isEqualTo(1);
    }

    @Test
    void criterio2_buscaSoloCobrosConSaldoYConFechaAnteriorAHoy() {
        when(cobros.deudasVencidas(any(), any())).thenReturn(List.of());

        assertThat(service.listar()).isEmpty();
        // PAGADO no cuenta; el corte es «antes de hoy» (hoy todavía no está vencido, lo filtra la consulta con fecha < hoy)
        verify(cobros).deudasVencidas(List.of(EstadoCobro.PENDIENTE, EstadoCobro.PAGO_PARCIAL), HOY);
    }

    @Test
    void hoyEsElDeLimaAunqueElServidorCorraEnUtc() {
        // 12/11 02:00 en UTC = 11/11 21:00 en Lima: para Lima todavía es 11/11
        MorosoService enLima = new MorosoService(cobros, Clock.fixed(Instant.parse("2026-11-12T02:00:00Z"), MorosoService.ZONA));
        when(cobros.deudasVencidas(any(), any())).thenReturn(List.of(deuda(4, "10.00", 1, "2026-11-10")));

        assertThat(enLima.listar().get(0).diasAtraso()).isEqualTo(1);
        verify(cobros).deudasVencidas(any(), eq(LocalDate.of(2026, 11, 11)));
    }

    @Test
    void elServicioQueCreaSpringUsaLaZonaDeLima() {
        assertThat(new MorosoService(cobros).reloj().getZone()).isEqualTo(ZoneId.of("America/Lima"));
    }

    @Test
    void losMasAtrasadosPrimeroYAIgualAtrasoElDeMayorMonto() {
        when(cobros.deudasVencidas(any(), eq(HOY))).thenReturn(List.of(
                deuda(1, "100.00", 1, "2026-11-01"),
                deuda(2, "900.00", 1, "2026-11-01"),
                deuda(3, "50.00", 1, "2026-09-01")));

        assertThat(service.listar()).extracting(MorosoDto::clienteId).containsExactly(3L, 2L, 1L);
    }
}

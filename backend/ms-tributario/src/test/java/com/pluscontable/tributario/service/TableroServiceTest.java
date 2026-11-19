package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.PagadoPorObligacionDto;
import com.pluscontable.tributario.dto.TableroFilaDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.PagoObligacionRepository;
import com.pluscontable.tributario.repository.TableroRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

/** HU-14 · Tarea 32 (unitarias): estado, montos, saldo y «vencida» de cada fila, orden, rechazos y el «hoy» de Lima. */
class TableroServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 11);
    private static final LocalDate MES = LocalDate.of(2026, 11, 1);

    private final TableroRepository tablero = mock(TableroRepository.class);
    private final PagoObligacionRepository pagos = mock(PagoObligacionRepository.class);
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    // 11/11 15:00 en Lima
    private final TableroService service = new TableroService(tablero, pagos, clientes,
            Clock.fixed(Instant.parse("2026-11-11T20:00:00Z"), TableroService.ZONA));

    @BeforeEach
    void preparar() {
        Cliente a = cliente(1L, "20100070970", "Aaa SAC");
        Cliente b = cliente(2L, "20100070971", "Bbb SAC");
        when(clientes.existsById(any())).thenReturn(true);
        when(clientes.findAllById(any())).thenReturn(List.of(a, b));
        when(pagos.sumasPagadas(any())).thenReturn(List.of());
    }

    private static Cliente cliente(long id, String ruc, String razon) {
        Cliente c = new Cliente();
        c.setId(id);
        c.setRuc(ruc);
        c.setRazonSocial(razon);
        return c;
    }

    private static Obligacion obligacion(long id, long cliente, TipoObligacion tipo, LocalDate vence, Obligacion.Estado estado, String monto) {
        Obligacion o = new Obligacion();
        o.setId(id);
        o.setClienteId(cliente);
        o.setTipoObligacion(tipo);
        o.setPeriodo(MES);
        o.setFechaVencimiento(vence);
        o.setEstado(estado);
        o.setMontoDeterminado(monto == null ? null : new BigDecimal(monto));
        return o;
    }

    @SuppressWarnings("unchecked")
    private void hay(Obligacion... lista) {
        when(tablero.findAll(any(Specification.class))).thenReturn(List.of(lista));
    }

    @Test
    void cadaFilaTraeSuEstadoRealSuMontoSuFechaLimiteLoPagadoYElSaldo() {
        hay(obligacion(10, 1, TipoObligacion.IGV, HOY.plusDays(3), Obligacion.Estado.PAGO_PARCIAL, "1000.00"));
        when(pagos.sumasPagadas(any())).thenReturn(List.of(new PagadoPorObligacionDto(10L, new BigDecimal("400.00"))));

        TableroFilaDto f = service.listar(null, null, false).get(0);

        assertThat(f.estado()).isEqualTo(Obligacion.Estado.PAGO_PARCIAL);
        assertThat(f.montoDeterminado()).isEqualByComparingTo("1000.00");
        assertThat(f.montoPagado()).isEqualByComparingTo("400.00");
        assertThat(f.saldo()).isEqualByComparingTo("600.00");
        assertThat(f.fechaVencimiento()).isEqualTo(HOY.plusDays(3));
        assertThat(f.razonSocial()).isEqualTo("Aaa SAC");
        assertThat(f.ruc()).isEqualTo("20100070970");
        assertThat(f.tipoObligacion()).isEqualTo(TipoObligacion.IGV);
    }

    @Test
    void sinPagosLoPagadoEsCeroYElSaldoEsElMontoYSinMontoDeterminadoElSaldoQuedaVacio() {
        hay(obligacion(10, 1, TipoObligacion.IGV, HOY, Obligacion.Estado.DECLARADO, "250.00"),
                obligacion(11, 1, TipoObligacion.RENTA, HOY, Obligacion.Estado.PENDIENTE, null));

        List<TableroFilaDto> filas = service.listar(null, null, false);

        assertThat(filas.get(0).montoPagado()).isEqualByComparingTo("0");
        assertThat(filas.get(0).saldo()).isEqualByComparingTo("250.00");
        assertThat(filas.get(1).montoDeterminado()).isNull();
        assertThat(filas.get(1).saldo()).isNull();
    }

    @Test
    void vencidaSoloSiTieneSaldoPendienteYSuFechaEsAnteriorAHoy() {
        LocalDate ayer = HOY.minusDays(1);
        hay(obligacion(1, 1, TipoObligacion.IGV, ayer, Obligacion.Estado.PENDIENTE, null),
                obligacion(2, 1, TipoObligacion.RENTA, ayer, Obligacion.Estado.PAGO_PARCIAL, "100.00"),
                obligacion(3, 1, TipoObligacion.PLAME, ayer, Obligacion.Estado.PAGADO, "100.00"),
                obligacion(4, 1, TipoObligacion.ESSALUD, ayer, Obligacion.Estado.DECLARADO, "100.00"),
                obligacion(5, 1, TipoObligacion.AFP, HOY, Obligacion.Estado.PENDIENTE, null),
                obligacion(6, 1, TipoObligacion.RETENCION_QUINTA, HOY, Obligacion.Estado.PAGO_PARCIAL, "100.00"));

        List<TableroFilaDto> filas = service.listar(null, null, false);

        // declarada sin pago (4) y pago parcial (2) vencen; la sin declarar (1) va aparte; pagada (3) nunca
        assertThat(filas).filteredOn(TableroFilaDto::vencida).extracting(TableroFilaDto::obligacionId).containsExactlyInAnyOrder(2L, 4L);
        assertThat(filas).filteredOn(TableroFilaDto::sinDeclarar).extracting(TableroFilaDto::obligacionId).containsExactly(1L);
    }

    @Test
    void hoyEsElDeLimaAunqueElServidorCorraEnUtc() {
        // 12/11 02:00 UTC = 11/11 21:00 en Lima: lo que vence el 11/11 todavía no está vencido
        TableroService enLima = new TableroService(tablero, pagos, clientes,
                Clock.fixed(Instant.parse("2026-11-12T02:00:00Z"), TableroService.ZONA));
        hay(obligacion(1, 1, TipoObligacion.IGV, HOY, Obligacion.Estado.PENDIENTE, null),
                obligacion(2, 1, TipoObligacion.RENTA, HOY.minusDays(1), Obligacion.Estado.PENDIENTE, null));

        List<TableroFilaDto> filas = enLima.listar(null, null, false);

        assertThat(filas).filteredOn(TableroFilaDto::sinDeclarar).extracting(TableroFilaDto::obligacionId).containsExactly(2L);
    }

    @Test
    void elServicioQueCreaSpringUsaLaZonaDeLimaAunqueElServidorEsteEnOtraZona() {
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC")); // un servidor en UTC
            assertThat(new TableroService(tablero, pagos, clientes).reloj().getZone()).isEqualTo(ZoneId.of("America/Lima"));
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }

    @Test
    void lasFilasVanPorFechaLimiteLuegoClienteTributoYNumero() {
        hay(obligacion(4, 2, TipoObligacion.IGV, HOY, Obligacion.Estado.PENDIENTE, null),
                obligacion(3, 1, TipoObligacion.RENTA, HOY, Obligacion.Estado.PENDIENTE, null),
                obligacion(2, 1, TipoObligacion.IGV, HOY, Obligacion.Estado.PENDIENTE, null),
                obligacion(1, 2, TipoObligacion.PLAME, HOY.minusDays(5), Obligacion.Estado.PENDIENTE, null));

        assertThat(service.listar(null, null, false)).extracting(TableroFilaDto::obligacionId).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void sinObligacionesDevuelveVacioSinConsultarPagosNiClientes() {
        hay();

        assertThat(service.listar(null, MES, true)).isEmpty();
        verify(pagos, never()).sumasPagadas(any());
        verify(clientes, never()).findAllById(any());
    }

    @Test
    void rechazaUnClienteNoPositivoUnPeriodoQueNoEsPrimerDiaYUnClienteInexistente() {
        assertThatThrownBy(() -> service.listar(0L, null, false)).isInstanceOf(ValidationException.class).hasMessageContaining("clienteId");
        assertThatThrownBy(() -> service.listar(-3L, null, false)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.listar(null, MES.plusDays(1), false)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("primer día del mes");
        verifyNoInteractions(tablero);

        when(clientes.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> service.listar(99L, null, false)).isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(tablero);
    }
}

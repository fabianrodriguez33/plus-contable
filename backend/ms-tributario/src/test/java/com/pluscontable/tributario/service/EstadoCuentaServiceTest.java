package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.EstadoCuentaDto;
import com.pluscontable.tributario.dto.PagoHonorarioDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.CobroHonorario;
import com.pluscontable.tributario.model.EstadoCobro;
import com.pluscontable.tributario.model.MedioPago;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.CobroHonorarioRepository;
import com.pluscontable.tributario.repository.PagoHonorarioRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** HU-30 · Tarea 148 (unitarias): las reglas del estado de cuenta (movimientos del periodo, saldos, sin movimientos y rechazos). */
class EstadoCuentaServiceTest {

    private static final long CLIENTE = 7L;
    private static final LocalDate DESDE = LocalDate.of(2026, 11, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 11, 30);

    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final CobroHonorarioRepository cobros = mock(CobroHonorarioRepository.class);
    private final PagoHonorarioRepository pagos = mock(PagoHonorarioRepository.class);
    private final EstadoCuentaService service = new EstadoCuentaService(clientes, cobros, pagos);

    @BeforeEach
    void preparar() {
        Cliente c = new Cliente();
        c.setId(CLIENTE);
        c.setRazonSocial("Acme SAC");
        when(clientes.findById(CLIENTE)).thenReturn(Optional.of(c));
        when(cobros.cobradoAntesDe(CLIENTE, DESDE)).thenReturn(BigDecimal.ZERO);
        when(pagos.pagadoAntesDe(CLIENTE, DESDE)).thenReturn(BigDecimal.ZERO);
        when(cobros.findByClienteIdAndFechaCobroBetweenOrderByFechaCobroAscIdAsc(CLIENTE, DESDE, HASTA)).thenReturn(List.of());
        when(pagos.delPeriodo(CLIENTE, DESDE, HASTA)).thenReturn(List.of());
    }

    private static CobroHonorario cobro(long id, String fecha, String monto) {
        CobroHonorario c = new CobroHonorario();
        c.setId(id);
        c.setClienteId(CLIENTE);
        c.setFechaCobro(LocalDate.parse(fecha));
        c.setMonto(new BigDecimal(monto));
        c.setEstado(EstadoCobro.PENDIENTE);
        return c;
    }

    private static PagoHonorarioDto pago(long id, String fecha, String monto) {
        return new PagoHonorarioDto(id, 1L, LocalDate.of(2026, 11, 15), LocalDate.parse(fecha), new BigDecimal(monto), MedioPago.EFECTIVO, null);
    }

    @Test
    void criterio1_muestraLosCobrosYPagosDelPeriodoConSusTotalesYElSaldo() {
        when(cobros.findByClienteIdAndFechaCobroBetweenOrderByFechaCobroAscIdAsc(CLIENTE, DESDE, HASTA))
                .thenReturn(List.of(cobro(1, "2026-11-15", "250.00"), cobro(2, "2026-11-30", "250.00")));
        when(pagos.delPeriodo(CLIENTE, DESDE, HASTA)).thenReturn(List.of(pago(9, "2026-11-16", "300.00")));

        EstadoCuentaDto r = service.emitir(CLIENTE, DESDE, HASTA);

        assertThat(r.razonSocial()).isEqualTo("Acme SAC");
        assertThat(r.cobros()).extracting(c -> c.fechaCobro().toString()).containsExactly("2026-11-15", "2026-11-30");
        assertThat(r.pagos()).singleElement().satisfies(p -> assertThat(p.monto()).isEqualByComparingTo("300.00"));
        assertThat(r.totalCobrado()).isEqualByComparingTo("500.00");
        assertThat(r.totalPagado()).isEqualByComparingTo("300.00");
        assertThat(r.saldo()).isEqualByComparingTo("200.00");
        assertThat(r.sinMovimientos()).isFalse();
    }

    @Test
    void criterio1_elSaldoParteDeLoQueSeDebiaAntesDelPeriodo() {
        when(cobros.cobradoAntesDe(CLIENTE, DESDE)).thenReturn(new BigDecimal("1000.00"));
        when(pagos.pagadoAntesDe(CLIENTE, DESDE)).thenReturn(new BigDecimal("600.00"));
        when(cobros.findByClienteIdAndFechaCobroBetweenOrderByFechaCobroAscIdAsc(CLIENTE, DESDE, HASTA))
                .thenReturn(List.of(cobro(1, "2026-11-15", "500.00")));
        when(pagos.delPeriodo(CLIENTE, DESDE, HASTA)).thenReturn(List.of(pago(9, "2026-11-20", "700.00")));

        EstadoCuentaDto r = service.emitir(CLIENTE, DESDE, HASTA);

        assertThat(r.saldoAnterior()).isEqualByComparingTo("400.00");
        assertThat(r.saldo()).isEqualByComparingTo("200.00"); // 400 + 500 - 700
    }

    @Test
    void criterio2_usaElClienteYElPeriodoElegidosYUnSoloDiaEsUnPeriodoValido() {
        LocalDate dia = LocalDate.of(2026, 11, 15);
        when(cobros.cobradoAntesDe(CLIENTE, dia)).thenReturn(BigDecimal.ZERO);
        when(pagos.pagadoAntesDe(CLIENTE, dia)).thenReturn(BigDecimal.ZERO);
        when(cobros.findByClienteIdAndFechaCobroBetweenOrderByFechaCobroAscIdAsc(CLIENTE, dia, dia))
                .thenReturn(List.of(cobro(1, "2026-11-15", "250.00")));
        when(pagos.delPeriodo(CLIENTE, dia, dia)).thenReturn(List.of());

        EstadoCuentaDto r = service.emitir(CLIENTE, dia, dia);

        assertThat(r.desde()).isEqualTo(dia);
        assertThat(r.hasta()).isEqualTo(dia);
        assertThat(r.cobros()).hasSize(1);
        assertThat(r.saldo()).isEqualByComparingTo("250.00");
    }

    @Test
    void criterio3_sinCobrosNiPagosEnElPeriodoLoIndicaYConservaElSaldoAnterior() {
        when(cobros.cobradoAntesDe(CLIENTE, DESDE)).thenReturn(new BigDecimal("300.00"));

        EstadoCuentaDto r = service.emitir(CLIENTE, DESDE, HASTA);

        assertThat(r.sinMovimientos()).isTrue();
        assertThat(r.cobros()).isEmpty();
        assertThat(r.pagos()).isEmpty();
        assertThat(r.totalCobrado()).isEqualByComparingTo("0");
        assertThat(r.saldo()).isEqualByComparingTo("300.00");
    }

    @Test
    void conSoloUnPagoEnElPeriodoNoEsSinMovimientos() {
        when(pagos.delPeriodo(CLIENTE, DESDE, HASTA)).thenReturn(List.of(pago(9, "2026-11-05", "100.00")));

        assertThat(service.emitir(CLIENTE, DESDE, HASTA).sinMovimientos()).isFalse();
    }

    @Test
    void faltanFechasODesdePosteriorAHastaDa400ConTodosLosErroresSinConsultar() {
        assertThatThrownBy(() -> service.emitir(CLIENTE, null, null))
                .isInstanceOf(ValidationException.class).hasMessage("Faltan o son inválidos: desde, hasta");
        assertThatThrownBy(() -> service.emitir(CLIENTE, HASTA, DESDE))
                .isInstanceOf(ValidationException.class).hasMessage("Faltan o son inválidos: desde (no puede ser posterior a hasta)");
        verifyNoInteractions(clientes, cobros, pagos);
    }

    @Test
    void unClienteInexistenteDa404() {
        when(clientes.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.emitir(99L, DESDE, HASTA)).isInstanceOf(EntityNotFoundException.class);
    }
}

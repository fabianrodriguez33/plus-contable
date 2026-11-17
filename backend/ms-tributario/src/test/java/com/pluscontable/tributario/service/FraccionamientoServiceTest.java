package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.calendario.CalendarioService;
import com.pluscontable.tributario.dto.CalendarioCuotaDto;
import com.pluscontable.tributario.dto.CuotaDto;
import com.pluscontable.tributario.dto.FraccionamientoDto;
import com.pluscontable.tributario.dto.FraccionamientoEntradaDto;
import com.pluscontable.tributario.model.CuotaFraccionamiento;
import com.pluscontable.tributario.model.EstadoCuota;
import com.pluscontable.tributario.model.Fraccionamiento;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import com.pluscontable.tributario.repository.CuotaFraccionamientoRepository;
import com.pluscontable.tributario.repository.FraccionamientoRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

/** HU-17 · Tarea 24 (unitarias): registrar el fraccionamiento, sus cuotas, el riesgo, el calendario y el pago de una cuota. */
class FraccionamientoServiceTest {

    private static final long CLIENTE = 7L;
    private static final LocalDate HOY = LocalDate.of(2026, 11, 9);

    private final FraccionamientoRepository fraccionamientos = mock(FraccionamientoRepository.class);
    private final CuotaFraccionamientoRepository cuotas = mock(CuotaFraccionamientoRepository.class);
    private final ClienteService clientes = mock(ClienteService.class);
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneOffset.UTC);
    private final FraccionamientoService service = new FraccionamientoService(fraccionamientos, cuotas, clientes, 36, reloj);

    @BeforeEach
    void preparar() {
        when(fraccionamientos.save(any(Fraccionamiento.class))).thenAnswer(inv -> {
            Fraccionamiento f = inv.getArgument(0);
            f.setId(21L);
            return f;
        });
        when(cuotas.saveAll(any())).thenAnswer(inv -> {
            List<CuotaFraccionamiento> lista = new ArrayList<>(inv.getArgument(0));
            long id = 100;
            for (CuotaFraccionamiento c : lista) c.setId(id++);
            return lista;
        });
    }

    private static FraccionamientoEntradaDto entrada(TipoObligacion tributo, String monto, Integer cuotas, LocalDate primera) {
        return new FraccionamientoEntradaDto(tributo, monto == null ? null : new BigDecimal(monto), cuotas, primera);
    }

    private void noSeGuarda(FraccionamientoEntradaDto dto, String... partes) {
        assertThatThrownBy(() -> service.registrar(CLIENTE, dto))
                .isInstanceOf(ValidationException.class)
                .hasMessageStartingWith("Faltan o son inválidos: ")
                .satisfies(e -> {
                    for (String parte : partes) assertThat(e.getMessage()).contains(parte);
                });
        verify(fraccionamientos, never()).save(any());
        verify(cuotas, never()).saveAll(any());
    }

    private static CuotaFraccionamiento cuota(long id, long fraccionamiento, int numero, LocalDate fecha, EstadoCuota estado) {
        CuotaFraccionamiento c = new CuotaFraccionamiento();
        c.setId(id);
        c.setFraccionamientoId(fraccionamiento);
        c.setNumero(numero);
        c.setFechaVencimiento(fecha);
        c.setMonto(new BigDecimal("100.00"));
        c.setEstado(estado);
        return c;
    }

    // ---- registrar (tareas 19 y 20)

    @Test
    void registraElFraccionamientoYGeneraLasCuotasMensualesConSuMonto() {
        FraccionamientoDto r = service.registrar(CLIENTE, entrada(TipoObligacion.IGV, "1200.00", 4, LocalDate.of(2026, 12, 15)));

        ArgumentCaptor<Fraccionamiento> f = ArgumentCaptor.forClass(Fraccionamiento.class);
        verify(fraccionamientos).save(f.capture());
        assertThat(f.getValue().getClienteId()).isEqualTo(CLIENTE);
        assertThat(f.getValue().getTributo()).isEqualTo(TipoObligacion.IGV);
        assertThat(f.getValue().getNumeroCuotas()).isEqualTo(4);
        assertThat(r.id()).isEqualTo(21L);
        assertThat(r.cuotas()).hasSize(4);
        assertThat(r.cuotas()).extracting(CuotaDto::numero).containsExactly(1, 2, 3, 4);
        assertThat(r.cuotas()).extracting(CuotaDto::fechaVencimiento).containsExactly(
                LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 15), LocalDate.of(2027, 2, 15), LocalDate.of(2027, 3, 15));
        assertThat(r.cuotas()).extracting(CuotaDto::monto).allSatisfy(m -> assertThat(m).isEqualByComparingTo("300.00"));
        assertThat(r.cuotas()).extracting(CuotaDto::estado).containsOnly(EstadoCuota.PENDIENTE);
    }

    @Test
    void laUltimaCuotaAjustaLosCentavosYLaSumaEsElMontoTotal() {
        FraccionamientoDto r = service.registrar(CLIENTE, entrada(TipoObligacion.RENTA, "1000.00", 3, LocalDate.of(2026, 12, 1)));

        assertThat(r.cuotas()).extracting(c -> c.monto().toPlainString()).containsExactly("333.33", "333.33", "333.34");
        BigDecimal suma = r.cuotas().stream().map(CuotaDto::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(suma).isEqualByComparingTo("1000.00");
    }

    @Test
    void unMontoConMasDeDosDecimalesSeRedondeaAlCentimoAntesDeRepartir() {
        FraccionamientoDto r = service.registrar(CLIENTE, entrada(TipoObligacion.IGV, "100.005", 2, LocalDate.of(2026, 12, 1)));

        assertThat(r.montoTotal()).isEqualByComparingTo("100.01");
        BigDecimal suma = r.cuotas().stream().map(CuotaDto::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(suma).isEqualByComparingTo("100.01");
    }

    @Test
    void siElDiaNoExisteEnUnMesLaCuotaVaElUltimoDiaYLaSiguienteVuelveAlDiaOriginal() {
        FraccionamientoDto r = service.registrar(CLIENTE, entrada(TipoObligacion.IGV, "400.00", 4, LocalDate.of(2026, 12, 31)));

        assertThat(r.cuotas()).extracting(CuotaDto::fechaVencimiento).containsExactly(
                LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31));
    }

    @Test
    void laFechaDeLaPrimeraCuotaPuedeSerPasadaPorqueElFraccionamientoYaEstaVigente() {
        FraccionamientoDto r = service.registrar(CLIENTE, entrada(TipoObligacion.IGV, "300.00", 3, LocalDate.of(2026, 9, 10)));

        assertThat(r.cuotas()).extracting(CuotaDto::enRiesgo).containsExactly(true, true, false);
    }

    @ParameterizedTest
    @EnumSource(value = TipoObligacion.class, names = {"ESSALUD", "RETENCION_QUINTA", "AFP"})
    void essaludQuintaYAfpTambienSePuedenFraccionar(TipoObligacion tributo) {
        FraccionamientoDto r = service.registrar(CLIENTE, entrada(tributo, "500.00", 5, LocalDate.of(2026, 12, 1)));

        assertThat(r.tributo()).isEqualTo(tributo);
        assertThat(r.cuotas()).hasSize(5);
    }

    @Test
    void sinMontoNoSeGuarda() {
        noSeGuarda(entrada(TipoObligacion.IGV, null, 3, LocalDate.of(2026, 12, 1)), "montoTotal");
    }

    @Test
    void sinNumeroDeCuotasNoSeGuarda() {
        noSeGuarda(entrada(TipoObligacion.IGV, "300.00", null, LocalDate.of(2026, 12, 1)), "numeroCuotas");
    }

    @Test
    void sinFechaDeLaPrimeraCuotaNoSeGuarda() {
        noSeGuarda(entrada(TipoObligacion.IGV, "300.00", 3, null), "fechaPrimeraCuota");
    }

    @Test
    void sinNadaListaTodosLosErroresJuntosYSinCuerpoTambien() {
        noSeGuarda(entrada(null, null, null, null), "tributo", "montoTotal", "numeroCuotas", "fechaPrimeraCuota");
        noSeGuarda(null, "tributo", "montoTotal", "numeroCuotas", "fechaPrimeraCuota");
    }

    @Test
    void elMontoDebeSerMayorQueCeroYNoDemasiadoGrande() {
        noSeGuarda(entrada(TipoObligacion.IGV, "0", 3, LocalDate.of(2026, 12, 1)), "montoTotal (debe ser mayor que cero)");
        noSeGuarda(entrada(TipoObligacion.IGV, "-10.00", 3, LocalDate.of(2026, 12, 1)), "montoTotal (debe ser mayor que cero)");
        noSeGuarda(entrada(TipoObligacion.IGV, "1000000000000.00", 3, LocalDate.of(2026, 12, 1)), "montoTotal (demasiado grande)");
    }

    @Test
    void elMontoDebeAlcanzarParaCuotasDeAlMenosUnCentimo() {
        noSeGuarda(entrada(TipoObligacion.IGV, "0.01", 2, LocalDate.of(2026, 12, 1)), "montoTotal (no alcanza para cuotas de al menos 0.01)");
    }

    @Test
    void elNumeroDeCuotasVaDeDosAlMaximoConfigurado() {
        noSeGuarda(entrada(TipoObligacion.IGV, "300.00", 1, LocalDate.of(2026, 12, 1)), "numeroCuotas (de 2 a 36)");
        noSeGuarda(entrada(TipoObligacion.IGV, "300.00", 37, LocalDate.of(2026, 12, 1)), "numeroCuotas (de 2 a 36)");

        assertThat(service.registrar(CLIENTE, entrada(TipoObligacion.IGV, "360.00", 36, LocalDate.of(2026, 12, 1))).cuotas()).hasSize(36);
        FraccionamientoService corto = new FraccionamientoService(fraccionamientos, cuotas, clientes, 12, reloj);
        assertThatThrownBy(() -> corto.registrar(CLIENTE, entrada(TipoObligacion.IGV, "300.00", 13, LocalDate.of(2026, 12, 1))))
                .hasMessageContaining("numeroCuotas (de 2 a 12)");
    }

    @Test
    void siElClienteNoExisteResponde404YNoGuarda() {
        when(clientes.buscar(CLIENTE)).thenThrow(new EntityNotFoundException("Cliente no encontrado: " + CLIENTE));

        assertThatThrownBy(() -> service.registrar(CLIENTE, entrada(TipoObligacion.IGV, "300.00", 3, LocalDate.of(2026, 12, 1))))
                .isInstanceOf(EntityNotFoundException.class);
        verify(fraccionamientos, never()).save(any());
    }

    // ---- listar y riesgo (criterio 4)

    @Test
    void listarDevuelveLosFraccionamientosConSusCuotasYMarcaElRiesgo() {
        Fraccionamiento f = new Fraccionamiento();
        f.setId(21L);
        f.setClienteId(CLIENTE);
        f.setTributo(TipoObligacion.IGV);
        f.setMontoTotal(new BigDecimal("300.00"));
        f.setNumeroCuotas(3);
        f.setFechaPrimeraCuota(LocalDate.of(2026, 10, 10));
        when(fraccionamientos.findByClienteIdOrderByRegistradoEnDescIdDesc(CLIENTE)).thenReturn(List.of(f));
        when(cuotas.findByFraccionamientoIdInOrderByFraccionamientoIdAscNumeroAsc(anyCollection())).thenReturn(List.of(
                cuota(1, 21, 1, LocalDate.of(2026, 10, 10), EstadoCuota.PENDIENTE),   // pasada y sin pago: en riesgo
                cuota(2, 21, 2, LocalDate.of(2026, 11, 9), EstadoCuota.PENDIENTE),    // vence hoy: aún no
                cuota(3, 21, 3, LocalDate.of(2026, 12, 9), EstadoCuota.PENDIENTE)));

        List<FraccionamientoDto> r = service.listar(CLIENTE);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).cuotas()).extracting(CuotaDto::enRiesgo).containsExactly(true, false, false);
    }

    @Test
    void unaCuotaPagadaConFechaPasadaNoEstaEnRiesgo() {
        CuotaFraccionamiento pagada = cuota(1, 21, 1, LocalDate.of(2026, 10, 10), EstadoCuota.PAGADA);
        pagada.setFechaPago(LocalDate.of(2026, 10, 9));

        assertThat(CuotaDto.from(pagada, HOY).enRiesgo()).isFalse();
        assertThat(CuotaDto.from(cuota(2, 21, 2, LocalDate.of(2026, 10, 10), EstadoCuota.PENDIENTE), HOY).enRiesgo()).isTrue();
    }

    @Test
    void sinFraccionamientosListarDevuelveListaVacia() {
        when(fraccionamientos.findByClienteIdOrderByRegistradoEnDescIdDesc(CLIENTE)).thenReturn(List.of());

        assertThat(service.listar(CLIENTE)).isEmpty();
    }

    // ---- calendario (criterio 3)

    @Test
    void elCalendarioDevuelveLasCuotasConDiasRestantesYSiEstanVencidas() {
        when(cuotas.delClienteEntre(CLIENTE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31))).thenReturn(List.of(
                cuota(1, 21, 1, LocalDate.of(2026, 11, 5), EstadoCuota.PENDIENTE),
                cuota(2, 21, 2, LocalDate.of(2026, 11, 9), EstadoCuota.PENDIENTE),
                cuota(3, 21, 3, LocalDate.of(2026, 12, 9), EstadoCuota.PENDIENTE)));

        List<CalendarioCuotaDto> r = service.calendario(CLIENTE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31));

        assertThat(r).extracting(CalendarioCuotaDto::diasRestantes).containsExactly(-4L, 0L, 30L);
        assertThat(r).extracting(CalendarioCuotaDto::vencida).containsExactly(true, false, false);
        assertThat(r).extracting(CalendarioCuotaDto::fraccionamientoId).containsOnly(21L);
        assertThat(r.get(0).cuota().enRiesgo()).isTrue();
    }

    @Test
    void sinFechasElCalendarioPideTodasLasCuotasDelCliente() {
        when(cuotas.delClienteEntre(any(), any(), any())).thenReturn(List.of());

        service.calendario(CLIENTE, null, null);

        verify(cuotas).delClienteEntre(CLIENTE, LocalDate.of(1900, 1, 1), LocalDate.of(2999, 12, 31));
    }

    @Test
    void elCalendarioFiltraPorEstadoDeLaCuota() {
        when(cuotas.delClienteEntre(any(), any(), any())).thenReturn(List.of(
                cuota(1, 21, 1, LocalDate.of(2026, 11, 5), EstadoCuota.PAGADA),
                cuota(2, 21, 2, LocalDate.of(2026, 11, 20), EstadoCuota.PENDIENTE)));

        assertThat(service.calendario(CLIENTE, null, null, EstadoCuota.PAGADA, null))
                .extracting(c -> c.cuota().numero()).containsExactly(1);
        assertThat(service.calendario(CLIENTE, null, null, EstadoCuota.PENDIENTE, null))
                .extracting(c -> c.cuota().numero()).containsExactly(2);
    }

    @Test
    void elCalendarioSeparaLasCuotasProximasDeLasVencidas() {
        when(cuotas.delClienteEntre(any(), any(), any())).thenReturn(List.of(
                cuota(1, 21, 1, LocalDate.of(2026, 11, 8), EstadoCuota.PENDIENTE),
                cuota(2, 21, 2, LocalDate.of(2026, 11, 9), EstadoCuota.PENDIENTE),
                cuota(3, 21, 3, LocalDate.of(2026, 12, 9), EstadoCuota.PENDIENTE)));

        assertThat(service.calendario(CLIENTE, null, null, null, CalendarioService.Situacion.VENCIDO))
                .extracting(c -> c.cuota().numero()).containsExactly(1);
        assertThat(service.calendario(CLIENTE, null, null, null, CalendarioService.Situacion.PROXIMO))
                .extracting(c -> c.cuota().numero()).containsExactly(2, 3);
        assertThat(service.calendario(CLIENTE, null, null, EstadoCuota.PENDIENTE, CalendarioService.Situacion.VENCIDO))
                .extracting(c -> c.cuota().enRiesgo()).containsExactly(true);
    }

    @Test
    void elCalendarioRechazaDesdeMayorQueHasta() {
        assertThatThrownBy(() -> service.calendario(CLIENTE, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 1)))
                .isInstanceOf(ValidationException.class).hasMessage("desde no puede superar hasta");
        verify(cuotas, never()).delClienteEntre(any(), any(), any());
    }

    // ---- marcar una cuota como pagada

    @Test
    void pagarUnaCuotaPendienteLaPasaAPagadaConLaFechaDeHoyYQuitaElRiesgo() {
        CuotaFraccionamiento c = cuota(5, 21, 1, LocalDate.of(2026, 10, 10), EstadoCuota.PENDIENTE);
        when(cuotas.findById(5L)).thenReturn(Optional.of(c));
        when(cuotas.save(any(CuotaFraccionamiento.class))).thenAnswer(inv -> inv.getArgument(0));

        CuotaDto r = service.pagarCuota(5L);

        assertThat(r.estado()).isEqualTo(EstadoCuota.PAGADA);
        assertThat(r.fechaPago()).isEqualTo(HOY);
        assertThat(r.enRiesgo()).isFalse();
        assertThat(c.getEstado()).isEqualTo(EstadoCuota.PAGADA);
    }

    @Test
    void pagarUnaCuotaYaPagadaResponde400YConservaSuFecha() {
        CuotaFraccionamiento c = cuota(5, 21, 1, LocalDate.of(2026, 10, 10), EstadoCuota.PAGADA);
        c.setFechaPago(LocalDate.of(2026, 10, 9));
        when(cuotas.findById(5L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.pagarCuota(5L)).isInstanceOf(ValidationException.class).hasMessage("La cuota ya fue pagada");
        verify(cuotas, never()).save(any());
        assertThat(c.getFechaPago()).isEqualTo(LocalDate.of(2026, 10, 9));
    }

    @Test
    void pagarUnaCuotaInexistenteResponde404() {
        when(cuotas.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pagarCuota(99L)).isInstanceOf(EntityNotFoundException.class).hasMessage("Cuota no encontrada");
    }
}

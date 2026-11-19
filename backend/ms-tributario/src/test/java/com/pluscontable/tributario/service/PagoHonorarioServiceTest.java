package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.CobroHonorarioDto;
import com.pluscontable.tributario.dto.PagoHonorarioEntradaDto;
import com.pluscontable.tributario.model.CobroHonorario;
import com.pluscontable.tributario.model.EstadoCobro;
import com.pluscontable.tributario.model.MedioPago;
import com.pluscontable.tributario.model.PagoHonorario;
import com.pluscontable.tributario.repository.CobroHonorarioRepository;
import com.pluscontable.tributario.repository.PagoHonorarioRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** HU-29 · Tarea 142 (unitarias): los 4 criterios del pago de honorarios, con sus límites y rechazos. */
class PagoHonorarioServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 11);
    private static final long CLIENTE = 7L;
    private static final long COBRO = 1L;

    private final CobroHonorarioRepository cobros = mock(CobroHonorarioRepository.class);
    private final PagoHonorarioRepository pagos = mock(PagoHonorarioRepository.class);
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC);
    private final List<PagoHonorario> guardados = new ArrayList<>();
    private PagoHonorarioService service;
    private CobroHonorario cobro;

    @BeforeEach
    void preparar() {
        service = new PagoHonorarioService(cobros, pagos, reloj);
        cobro = new CobroHonorario();
        cobro.setId(COBRO);
        cobro.setClienteId(CLIENTE);
        cobro.setFechaCobro(LocalDate.of(2026, 11, 15));
        cobro.setMonto(new BigDecimal("500.00"));
        when(cobros.buscarParaActualizar(COBRO)).thenReturn(Optional.of(cobro));
        when(cobros.save(any())).thenAnswer(a -> a.getArgument(0));
        when(pagos.save(any())).thenAnswer(a -> {
            guardados.add(a.getArgument(0));
            return a.getArgument(0);
        });
    }

    private static PagoHonorarioEntradaDto pago(String monto) {
        return new PagoHonorarioEntradaDto(HOY, new BigDecimal(monto), MedioPago.YAPE_PLIN, null);
    }

    @Test
    void criterio1_guardaElPagoConFechaMontoMedioYQuienLoRegistro() {
        service.registrar(CLIENTE, COBRO, new PagoHonorarioEntradaDto(HOY.minusDays(3), new BigDecimal("120.50"), MedioPago.EFECTIVO, null), "contador1");

        assertThat(guardados).singleElement().satisfies(p -> {
            assertThat(p.getCobroId()).isEqualTo(COBRO);
            assertThat(p.getFechaPago()).isEqualTo(HOY.minusDays(3));
            assertThat(p.getMonto()).isEqualByComparingTo("120.50");
            assertThat(p.getMedioPago()).isEqualTo(MedioPago.EFECTIVO);
            assertThat(p.getRegistradoPor()).isEqualTo("contador1");
        });
    }

    @Test
    void criterio2_unPagoPorElTotalDejaElCobroPagadoSinSaldo() {
        CobroHonorarioDto r = service.registrar(CLIENTE, COBRO, pago("500"), "contador1");

        assertThat(r.estado()).isEqualTo(EstadoCobro.PAGADO);
        assertThat(r.saldo()).isEqualByComparingTo("0");
        assertThat(r.pagado()).isEqualByComparingTo("500.00");
    }

    @Test
    void criterio3_unPagoParcialDejaSaldoYOtroPagoPorElRestoLoCompleta() {
        CobroHonorarioDto primero = service.registrar(CLIENTE, COBRO, pago("200.00"), "contador1");
        assertThat(primero.estado()).isEqualTo(EstadoCobro.PAGO_PARCIAL);
        assertThat(primero.saldo()).isEqualByComparingTo("300.00");

        CobroHonorarioDto segundo = service.registrar(CLIENTE, COBRO, pago("300.00"), "contador1");
        assertThat(segundo.estado()).isEqualTo(EstadoCobro.PAGADO);
        assertThat(segundo.saldo()).isEqualByComparingTo("0");
        assertThat(guardados).hasSize(2);
    }

    @Test
    void criterio4_unCobroYaPagadoNoAdmiteOtroPago() {
        cobro.setPagado(new BigDecimal("500.00"));
        cobro.setEstado(EstadoCobro.PAGADO);

        assertThatThrownBy(() -> service.registrar(CLIENTE, COBRO, pago("1.00"), "contador1"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Solo se registran pagos sobre un cobro pendiente");
        verify(pagos, never()).save(any());
    }

    @Test
    void criterio4_unCobroInexistenteODeOtroClienteDa404() {
        assertThatThrownBy(() -> service.registrar(CLIENTE, 99L, pago("1.00"), "contador1"))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.registrar(8L, COBRO, pago("1.00"), "contador1"))
                .isInstanceOf(EntityNotFoundException.class);
        verify(pagos, never()).save(any());
    }

    @Test
    void unMontoMayorAlSaldoSeRechazaConElSaldoEnElMensaje() {
        cobro.setPagado(new BigDecimal("200.00"));
        cobro.setEstado(EstadoCobro.PAGO_PARCIAL);

        assertThatThrownBy(() -> service.registrar(CLIENTE, COBRO, pago("300.01"), "contador1"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("supera el saldo pendiente de 300.00");
        verify(pagos, never()).save(any());
    }

    @Test
    void reuneTodosLosErroresYRechazaFechaFuturaMontoCeroOConMasDeDosDecimales() {
        assertThatThrownBy(() -> service.registrar(CLIENTE, COBRO, new PagoHonorarioEntradaDto(null, null, null, null), "c"))
                .hasMessage("Faltan o son inválidos: fechaPago, monto, medioPago");
        assertThatThrownBy(() -> service.registrar(CLIENTE, COBRO, null, "c"))
                .hasMessage("Faltan o son inválidos: fechaPago, monto, medioPago");
        assertThatThrownBy(() -> service.registrar(CLIENTE, COBRO,
                new PagoHonorarioEntradaDto(HOY.plusDays(1), BigDecimal.ZERO, MedioPago.OTRO, "Plin empresarial"), "c"))
                .hasMessage("Faltan o son inválidos: fechaPago (no puede ser futura), monto (debe ser mayor que cero)");
        assertThatThrownBy(() -> service.registrar(CLIENTE, COBRO, pago("10.005"), "c"))
                .hasMessageContaining("monto (máximo 2 decimales)");
        verify(pagos, never()).save(any());
    }

    @Test
    void conOtroSeExigeEspecificarElMedioYSeGuardaSinEspacios() {
        assertThatThrownBy(() -> service.registrar(CLIENTE, COBRO, new PagoHonorarioEntradaDto(HOY, BigDecimal.TEN, MedioPago.OTRO, "  "), "c"))
                .hasMessage("Faltan o son inválidos: medioPagoDetalle (especifique el medio de pago)");
        assertThatThrownBy(() -> service.registrar(CLIENTE, COBRO, new PagoHonorarioEntradaDto(HOY, BigDecimal.TEN, MedioPago.OTRO, null), "c"))
                .hasMessage("Faltan o son inválidos: medioPagoDetalle (especifique el medio de pago)");
        assertThatThrownBy(() -> service.registrar(CLIENTE, COBRO,
                new PagoHonorarioEntradaDto(HOY, BigDecimal.TEN, MedioPago.OTRO, "x".repeat(101)), "c"))
                .hasMessageContaining("medioPagoDetalle (máximo 100 caracteres)");
        verify(pagos, never()).save(any());

        service.registrar(CLIENTE, COBRO, new PagoHonorarioEntradaDto(HOY, BigDecimal.TEN, MedioPago.OTRO, "  Tarjeta de crédito "), "c");
        assertThat(guardados).singleElement().satisfies(p -> {
            assertThat(p.getMedioPago()).isEqualTo(MedioPago.OTRO);
            assertThat(p.getMedioPagoDetalle()).isEqualTo("Tarjeta de crédito");
        });
    }

    @Test
    void conUnMedioDeLaListaElDetalleSeIgnora() {
        service.registrar(CLIENTE, COBRO, new PagoHonorarioEntradaDto(HOY, BigDecimal.TEN, MedioPago.TRANSFERENCIA, "BCP"), "c");

        assertThat(guardados).singleElement().satisfies(p -> assertThat(p.getMedioPagoDetalle()).isNull());
    }

    @Test
    void unMontoConCerosDeMasNoCuentaComoDecimalesDeMas() {
        assertThat(service.registrar(CLIENTE, COBRO, pago("500.000"), "c").estado()).isEqualTo(EstadoCobro.PAGADO);
    }

    /** 20:00 del 11/11 en Lima ya es el 12/11 en UTC: un pago con fecha del 12 sigue siendo futuro. */
    @Test
    void conLaNocheDeLimaHoyEsElDiaDeLimaYUnPagoDeMananaSeRechaza() {
        PagoHonorarioService enLima = new PagoHonorarioService(cobros, pagos,
                Clock.fixed(Instant.parse("2026-11-12T01:00:00Z"), PagoHonorarioService.ZONA));

        assertThatThrownBy(() -> enLima.registrar(CLIENTE, COBRO,
                new PagoHonorarioEntradaDto(HOY.plusDays(1), new BigDecimal("100"), MedioPago.EFECTIVO, null), "contador1"))
                .isInstanceOf(ValidationException.class).hasMessageContaining("fechaPago");

        enLima.registrar(CLIENTE, COBRO, pago("100"), "contador1");
        assertThat(guardados).singleElement().satisfies(p -> assertThat(p.getRegistradoEn()).isEqualTo(LocalDateTime.of(2026, 11, 11, 20, 0)));
    }
}

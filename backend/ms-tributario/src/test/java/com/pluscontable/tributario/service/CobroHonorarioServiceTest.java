package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.ProgramacionCobroDto;
import com.pluscontable.tributario.dto.ProgramacionCobroEntradaDto;
import com.pluscontable.tributario.model.AcuerdoServicio;
import com.pluscontable.tributario.model.CobroHonorario;
import com.pluscontable.tributario.model.EstadoCobro;
import com.pluscontable.tributario.model.Periodicidad;
import com.pluscontable.tributario.model.ProgramacionCobro;
import com.pluscontable.tributario.repository.AcuerdoRepository;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.CobroHonorarioRepository;
import com.pluscontable.tributario.repository.ProgramacionCobroRepository;
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

/** HU-28 · Tarea 133 (unitarias): fechas y montos de los cobros, acuerdo obligatorio y reprogramación sin duplicar. */
class CobroHonorarioServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 9);
    private static final long CLIENTE = 7L;

    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final AcuerdoRepository acuerdos = mock(AcuerdoRepository.class);
    private final ProgramacionCobroRepository programaciones = mock(ProgramacionCobroRepository.class);
    private final CobroHonorarioRepository cobros = mock(CobroHonorarioRepository.class);
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneOffset.UTC);
    private final List<CobroHonorario> guardados = new ArrayList<>();
    private CobroHonorarioService service;

    @BeforeEach
    void preparar() {
        service = new CobroHonorarioService(clientes, acuerdos, programaciones, cobros, reloj);
        when(clientes.existsById(CLIENTE)).thenReturn(true);
        when(acuerdos.findByClienteId(CLIENTE)).thenReturn(Optional.of(acuerdo("500.00", LocalDate.of(2026, 1, 31))));
        when(programaciones.findByClienteId(CLIENTE)).thenReturn(Optional.empty());
        when(programaciones.save(any())).thenAnswer(a -> {
            ProgramacionCobro p = a.getArgument(0);
            p.setId(3L);
            return p;
        });
        when(cobros.save(any())).thenAnswer(a -> {
            guardados.add(a.getArgument(0));
            return a.getArgument(0);
        });
    }

    private static AcuerdoServicio acuerdo(String honorario, LocalDate inicio) {
        AcuerdoServicio a = new AcuerdoServicio();
        a.setClienteId(CLIENTE);
        a.setHonorarioMensual(new BigDecimal(honorario));
        a.setFechaInicio(inicio);
        a.setHonorarioDesde(inicio);
        return a;
    }

    private static ProgramacionCobro programacion(Periodicidad p) {
        ProgramacionCobro pc = new ProgramacionCobro();
        pc.setId(3L);
        pc.setClienteId(CLIENTE);
        pc.setPeriodicidad(p);
        return pc;
    }

    private static ProgramacionCobroEntradaDto con(Periodicidad p) {
        return new ProgramacionCobroEntradaDto(p);
    }

    @Test
    void mensualCaeElDiaDelAcuerdoOElUltimoDelMesSiEseDiaNoExiste() {
        List<LocalDate> fechas = CobroHonorarioService.fechasDeCobro(Periodicidad.MENSUAL, 31, HOY);

        assertThat(fechas).hasSize(12)
                .startsWith(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28))
                .endsWith(LocalDate.of(2027, 10, 31));
    }

    @Test
    void mensualConUnDiaYaPasadoEsteMesEmpiezaElMesSiguiente() {
        List<LocalDate> fechas = CobroHonorarioService.fechasDeCobro(Periodicidad.MENSUAL, 5, HOY);

        assertThat(fechas).hasSize(12).startsWith(LocalDate.of(2026, 12, 5)).endsWith(LocalDate.of(2027, 11, 5));
    }

    @Test
    void quincenalCaeLosDias15YUltimoDeCadaMes() {
        List<LocalDate> fechas = CobroHonorarioService.fechasDeCobro(Periodicidad.QUINCENAL, 31, HOY);

        assertThat(fechas).hasSize(24)
                .startsWith(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 30))
                .contains(LocalDate.of(2027, 2, 15), LocalDate.of(2027, 2, 28))
                .endsWith(LocalDate.of(2027, 10, 31));
    }

    @Test
    void elMontoQuincenalEsLaMitadYElCentimoImparVaAFinDeMes() {
        BigDecimal honorario = new BigDecimal("100.01");
        BigDecimal q1 = CobroHonorarioService.monto(Periodicidad.QUINCENAL, honorario, LocalDate.of(2026, 12, 15));
        BigDecimal q2 = CobroHonorarioService.monto(Periodicidad.QUINCENAL, honorario, LocalDate.of(2026, 12, 31));

        assertThat(q1).isEqualByComparingTo("50.00");
        assertThat(q2).isEqualByComparingTo("50.01");
        assertThat(q1.add(q2)).isEqualByComparingTo(honorario);
        assertThat(CobroHonorarioService.monto(Periodicidad.MENSUAL, honorario, LocalDate.of(2026, 12, 31))).isEqualByComparingTo("100.01");
    }

    @Test
    void conAcuerdoGeneraDoceCobrosMensualesPendientesConElHonorario() {
        ProgramacionCobroDto r = service.programar(CLIENTE, con(Periodicidad.MENSUAL));

        assertThat(r.periodicidad()).isEqualTo(Periodicidad.MENSUAL);
        assertThat(r.cobrosGenerados()).isEqualTo(12);
        assertThat(r.cobrosYaExistentes()).isZero();
        assertThat(guardados).hasSize(12).allSatisfy(c -> {
            assertThat(c.getClienteId()).isEqualTo(CLIENTE);
            assertThat(c.getProgramacionId()).isEqualTo(3L);
            assertThat(c.getEstado()).isEqualTo(EstadoCobro.PENDIENTE);
            assertThat(c.getMonto()).isEqualByComparingTo("500.00");
        });
        assertThat(guardados.get(0).getFechaCobro()).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    void quincenalGeneraVeinticuatroCobrosDeMedioHonorario() {
        ProgramacionCobroDto r = service.programar(CLIENTE, con(Periodicidad.QUINCENAL));

        assertThat(r.cobrosGenerados()).isEqualTo(24);
        assertThat(guardados).allSatisfy(c -> assertThat(c.getMonto()).isEqualByComparingTo("250.00"));
    }

    @Test
    void sinAcuerdoPideRegistrarloPrimeroYNoGuardaNada() {
        when(acuerdos.findByClienteId(CLIENTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.programar(CLIENTE, con(Periodicidad.MENSUAL)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Registre primero el acuerdo de honorarios del cliente");
        verify(programaciones, never()).save(any());
        verify(cobros, never()).save(any());
    }

    @Test
    void sinPeriodicidadDa400AntesDeConsultarNada() {
        assertThatThrownBy(() -> service.programar(CLIENTE, con(null)))
                .isInstanceOf(ValidationException.class).hasMessage("Faltan o son inválidos: periodicidad");
        assertThatThrownBy(() -> service.programar(CLIENTE, null))
                .isInstanceOf(ValidationException.class).hasMessage("Faltan o son inválidos: periodicidad");
        verify(acuerdos, never()).findByClienteId(anyLong());
    }

    @Test
    void elClienteInexistenteDa404() {
        when(clientes.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.programar(99L, con(Periodicidad.MENSUAL))).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.pendientes(99L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void volverAProgramarConLaMismaPeriodicidadNoDuplicaNiBorra() {
        when(programaciones.findByClienteId(CLIENTE)).thenReturn(Optional.of(programacion(Periodicidad.MENSUAL)));
        when(cobros.existsByClienteIdAndFechaCobro(eq(CLIENTE), any())).thenReturn(true);

        ProgramacionCobroDto r = service.programar(CLIENTE, con(Periodicidad.MENSUAL));

        assertThat(r.cobrosGenerados()).isZero();
        assertThat(r.cobrosYaExistentes()).isEqualTo(12);
        verify(cobros, never()).save(any());
        verify(cobros, never()).deleteByClienteIdAndEstadoAndFechaCobroGreaterThanEqual(any(), any(), any());
    }

    @Test
    void cambiarLaPeriodicidadReemplazaLosPendientesDesdeHoyParaNoCobrarDoble() {
        when(programaciones.findByClienteId(CLIENTE)).thenReturn(Optional.of(programacion(Periodicidad.MENSUAL)));

        ProgramacionCobroDto r = service.programar(CLIENTE, con(Periodicidad.QUINCENAL));

        verify(cobros).deleteByClienteIdAndEstadoAndFechaCobroGreaterThanEqual(CLIENTE, EstadoCobro.PENDIENTE, HOY);
        verify(cobros).flush();
        assertThat(r.periodicidad()).isEqualTo(Periodicidad.QUINCENAL);
        assertThat(r.cobrosGenerados()).isEqualTo(24);
    }

    @Test
    void noSePuedeCambiarLaPeriodicidadConUnCobroVencidoDelMesSinPagar() {
        when(programaciones.findByClienteId(CLIENTE)).thenReturn(Optional.of(programacion(Periodicidad.MENSUAL)));
        when(cobros.existsByClienteIdAndEstadoAndFechaCobroBetween(
                CLIENTE, EstadoCobro.PENDIENTE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8))).thenReturn(true);

        assertThatThrownBy(() -> service.programar(CLIENTE, con(Periodicidad.QUINCENAL)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("cobro vencido de este mes");
        verify(cobros, never()).deleteByClienteIdAndEstadoAndFechaCobroGreaterThanEqual(any(), any(), any());
        verify(cobros, never()).save(any());
        verify(programaciones, never()).save(any());
    }

    @Test
    void tampocoSePuedeCambiarDeQuincenalAMensualConUnCobroVencidoDelMesSinPagar() {
        when(programaciones.findByClienteId(CLIENTE)).thenReturn(Optional.of(programacion(Periodicidad.QUINCENAL)));
        when(cobros.existsByClienteIdAndEstadoAndFechaCobroBetween(
                CLIENTE, EstadoCobro.PENDIENTE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8))).thenReturn(true);

        assertThatThrownBy(() -> service.programar(CLIENTE, con(Periodicidad.MENSUAL)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("cobro vencido de este mes");
        verify(cobros, never()).deleteByClienteIdAndEstadoAndFechaCobroGreaterThanEqual(any(), any(), any());
        verify(cobros, never()).save(any());
        verify(programaciones, never()).save(any());
    }

    @Test
    void unHonorarioDeUnCentimoNoSePuedeCobrarPorQuincenasPeroSiPorMes() {
        when(acuerdos.findByClienteId(CLIENTE)).thenReturn(Optional.of(acuerdo("0.01", LocalDate.of(2026, 1, 31))));

        assertThatThrownBy(() -> service.programar(CLIENTE, con(Periodicidad.QUINCENAL)))
                .isInstanceOf(ValidationException.class).hasMessage("El honorario mensual debe ser al menos 0.02 para cobrarlo por quincenas");
        verify(cobros, never()).save(any());

        assertThat(service.programar(CLIENTE, con(Periodicidad.MENSUAL)).cobrosGenerados()).isEqualTo(12);
    }

    @Test
    void conCentimoImparCadaMesQuincenalSumaExactoElHonorario() {
        when(acuerdos.findByClienteId(CLIENTE)).thenReturn(Optional.of(acuerdo("100.01", LocalDate.of(2026, 1, 31))));

        service.programar(CLIENTE, con(Periodicidad.QUINCENAL));

        assertThat(guardados).hasSize(24).allSatisfy(c -> assertThat(c.getMonto()).isGreaterThanOrEqualTo(new BigDecimal("0.01")));
        assertThat(guardados.stream().filter(c -> c.getFechaCobro().getMonthValue() == 11 && c.getFechaCobro().getYear() == 2026)
                .map(CobroHonorario::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.01");
        for (int i = 0; i < guardados.size(); i += 2) { // cada par es un mes: día 15 y fin de mes
            assertThat(guardados.get(i).getMonto().add(guardados.get(i + 1).getMonto())).isEqualByComparingTo("100.01");
        }
    }

    @Test
    void alReemplazarSoloSeBorranLosPendientesParaNoPerderCobrosConPagoHU29() {
        when(programaciones.findByClienteId(CLIENTE)).thenReturn(Optional.of(programacion(Periodicidad.MENSUAL)));

        service.programar(CLIENTE, con(Periodicidad.QUINCENAL));

        verify(cobros).deleteByClienteIdAndEstadoAndFechaCobroGreaterThanEqual(eq(CLIENTE), eq(EstadoCobro.PENDIENTE), any());
        verify(cobros, never()).deleteAll();
        verify(cobros, never()).deleteById(any());
    }

    @Test
    void unAcuerdoQueEmpiezaEnElFuturoCobraDesdeSuInicio() {
        when(acuerdos.findByClienteId(CLIENTE)).thenReturn(Optional.of(acuerdo("800.00", LocalDate.of(2027, 1, 10))));

        service.programar(CLIENTE, con(Periodicidad.MENSUAL));

        assertThat(guardados).hasSize(12);
        assertThat(guardados.get(0).getFechaCobro()).isEqualTo(LocalDate.of(2027, 1, 10));
        assertThat(guardados.get(11).getFechaCobro()).isEqualTo(LocalDate.of(2027, 12, 10));
    }
}

package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.model.Trabajador;
import com.pluscontable.tributario.obligacion.CronogramaConsultaService;
import com.pluscontable.tributario.obligacion.GeneradorObligaciones;
import com.pluscontable.tributario.obligacion.GeneradorObligaciones.Omision;
import com.pluscontable.tributario.obligacion.ParametrosTributarios;
import com.pluscontable.tributario.obligacion.ReglaQuinta;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import com.pluscontable.tributario.repository.ObligacionRepository;
import com.pluscontable.tributario.repository.TrabajadorRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** HU-07 (tareas 58, 59 y 60): programar el mes con la fecha del cronograma, o bloquear si falta. */
class ProgramacionMensualServiceTest {
    private static final long CLIENTE = 7L;
    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    private static final LocalDate FECHA = LocalDate.of(2026, 11, 20);

    private final CronogramaConsultaService cronograma = mock(CronogramaConsultaService.class);
    private final ObligacionRepository obligaciones = mock(ObligacionRepository.class);
    private final TrabajadorRepository trabajadores = mock(TrabajadorRepository.class);
    private final ParametrosTributarios parametros = new ParametrosTributarios();
    private final ProgramacionMensualService service =
            new ProgramacionMensualService(cronograma, obligaciones, trabajadores, parametros);

    @BeforeEach
    void guardarDevuelveLoGuardado() {
        when(obligaciones.saveAndFlush(any(Obligacion.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static Obligacion existente(TipoObligacion tipo, LocalDate periodo) {
        Obligacion o = new Obligacion();
        o.setClienteId(CLIENTE);
        o.setTipoObligacion(tipo);
        o.setPeriodo(periodo);
        o.setFechaVencimiento(periodo.plusMonths(1).plusDays(19));
        return o;
    }

    private static Trabajador trabajador(LocalDate ingreso, String remuneracion) {
        Trabajador t = new Trabajador();
        t.setClienteId(CLIENTE);
        t.setFechaIngreso(ingreso);
        t.setRemuneracion(remuneracion == null ? null : new BigDecimal(remuneracion));
        return t;
    }

    private void conTrabajadores(Trabajador... lista) {
        when(trabajadores.findByClienteIdOrderById(CLIENTE)).thenReturn(List.of(lista));
    }

    private List<TipoObligacion> tiposGuardados(int veces) {
        ArgumentCaptor<Obligacion> guardadas = ArgumentCaptor.forClass(Obligacion.class);
        verify(obligaciones, times(veces)).saveAndFlush(guardadas.capture());
        return guardadas.getAllValues().stream().map(Obligacion::getTipoObligacion).toList();
    }

    @Test
    void conPlanillaGeneraIgvRentaYPlameConLaFechaDelCronogramaYOmiteEssaludQuintaYAfp() {
        when(cronograma.buscar(CLIENTE, OCTUBRE)).thenReturn(Optional.of(FECHA));
        conTrabajadores(trabajador(LocalDate.of(2026, 1, 5), "1500.00"));

        var r = service.programar(CLIENTE, OCTUBRE);

        ArgumentCaptor<Obligacion> guardadas = ArgumentCaptor.forClass(Obligacion.class);
        verify(obligaciones, times(3)).saveAndFlush(guardadas.capture());
        assertThat(guardadas.getAllValues()).extracting(Obligacion::getTipoObligacion)
                .containsExactlyInAnyOrder(TipoObligacion.IGV, TipoObligacion.RENTA, TipoObligacion.PLAME);
        assertThat(guardadas.getAllValues()).allSatisfy(o -> {
            assertThat(o.getClienteId()).isEqualTo(CLIENTE);
            assertThat(o.getPeriodo()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(o.getFechaVencimiento()).isEqualTo(FECHA);
            assertThat(o.getEstado()).isEqualTo(Obligacion.Estado.PENDIENTE);
        });
        assertThat(r.creadas()).hasSize(3);
        assertThat(r.yaExistian()).isEmpty();
        assertThat(r.noGeneradas()).containsExactlyInAnyOrder(
                new Omision(TipoObligacion.ESSALUD, GeneradorObligaciones.MOTIVO_ESSALUD_PENDIENTE),
                new Omision(TipoObligacion.RETENCION_QUINTA, ReglaQuinta.MOTIVO_PENDIENTE),
                new Omision(TipoObligacion.AFP, GeneradorObligaciones.MOTIVO_AFP));
        assertThat(r.fechaVencimiento()).isEqualTo(FECHA);
        assertThat(r.periodo()).isEqualTo(OCTUBRE);
    }

    @Test
    void sinTrabajadoresGeneraSoloIgvYRentaYDiceElMotivoDeLoDemas() {
        when(cronograma.buscar(CLIENTE, OCTUBRE)).thenReturn(Optional.of(FECHA));

        var r = service.programar(CLIENTE, OCTUBRE);

        assertThat(tiposGuardados(2)).containsExactlyInAnyOrder(TipoObligacion.IGV, TipoObligacion.RENTA);
        assertThat(r.noGeneradas()).containsExactlyInAnyOrder(
                new Omision(TipoObligacion.PLAME, GeneradorObligaciones.MOTIVO_SIN_PLANILLA),
                new Omision(TipoObligacion.ESSALUD, GeneradorObligaciones.MOTIVO_ESSALUD_PENDIENTE),
                new Omision(TipoObligacion.RETENCION_QUINTA, ReglaQuinta.MOTIVO_PENDIENTE),
                new Omision(TipoObligacion.AFP, GeneradorObligaciones.MOTIVO_AFP));
    }

    @Test
    void conEssaludEncendidoUnTrabajadorSinRemuneracionGeneraPlamePeroNoEssalud() {
        when(cronograma.buscar(CLIENTE, OCTUBRE)).thenReturn(Optional.of(FECHA));
        parametros.setEssaludActivo(true);
        conTrabajadores(trabajador(LocalDate.of(2026, 1, 5), null), trabajador(LocalDate.of(2026, 2, 1), "0"));

        var r = service.programar(CLIENTE, OCTUBRE);

        assertThat(tiposGuardados(3)).containsExactlyInAnyOrder(TipoObligacion.IGV, TipoObligacion.RENTA,
                TipoObligacion.PLAME);
        assertThat(r.noGeneradas()).contains(
                new Omision(TipoObligacion.ESSALUD, GeneradorObligaciones.MOTIVO_SIN_ASEGURADOS));
    }

    @Test
    void conEssaludEncendidoYRemuneracionSeGeneraEssalud() {
        when(cronograma.buscar(CLIENTE, OCTUBRE)).thenReturn(Optional.of(FECHA));
        conTrabajadores(trabajador(LocalDate.of(2026, 1, 5), "1500.00"));
        parametros.setEssaludActivo(true);

        var r = service.programar(CLIENTE, OCTUBRE);

        assertThat(tiposGuardados(4)).contains(TipoObligacion.ESSALUD);
        assertThat(r.noGeneradas()).extracting(Omision::tipo)
                .containsExactlyInAnyOrder(TipoObligacion.RETENCION_QUINTA, TipoObligacion.AFP);
    }

    @Test
    void unTrabajadorQueIngresaDespuesDelPeriodoNoCuenta() {
        when(cronograma.buscar(CLIENTE, OCTUBRE)).thenReturn(Optional.of(FECHA));
        conTrabajadores(trabajador(LocalDate.of(2026, 11, 1), "1500.00"));

        service.programar(CLIENTE, OCTUBRE);

        assertThat(tiposGuardados(2)).containsExactlyInAnyOrder(TipoObligacion.IGV, TipoObligacion.RENTA);
    }

    @Test
    void unTrabajadorQueIngresaElUltimoDiaDelPeriodoSiCuenta() {
        when(cronograma.buscar(CLIENTE, OCTUBRE)).thenReturn(Optional.of(FECHA));
        conTrabajadores(trabajador(LocalDate.of(2026, 10, 31), "1500.00"));

        service.programar(CLIENTE, OCTUBRE);

        assertThat(tiposGuardados(3)).contains(TipoObligacion.PLAME).doesNotContain(TipoObligacion.ESSALUD);
    }

    @Test
    void conLaQuintaActivaPeroSinUitDelAnioSeOmiteConSuMotivo() {
        when(cronograma.buscar(CLIENTE, OCTUBRE)).thenReturn(Optional.of(FECHA));
        conTrabajadores(trabajador(LocalDate.of(2026, 1, 5), "1500.00"));
        parametros.setQuintaActiva(true);
        parametros.setQuintaSueldosAlAnio(new BigDecimal("10"));

        var r = service.programar(CLIENTE, OCTUBRE);

        assertThat(tiposGuardados(3)).doesNotContain(TipoObligacion.RETENCION_QUINTA);
        assertThat(r.noGeneradas()).contains(new Omision(TipoObligacion.RETENCION_QUINTA, "Falta la UIT de 2026"));
    }

    // Valores FICTICIOS de prueba (no son la UIT ni la fórmula real): UIT 1000, umbral 7, 10 sueldos al año.
    private void quintaActivaConValoresFicticios() {
        parametros.setQuintaActiva(true);
        parametros.setQuintaSueldosAlAnio(new BigDecimal("10"));
        parametros.getUit().put(2026, new BigDecimal("1000"));
    }

    @Test
    void conLaQuintaActivaYUnTrabajadorPorEncimaDelUmbralSeGeneraLaQuinta() {
        when(cronograma.buscar(CLIENTE, OCTUBRE)).thenReturn(Optional.of(FECHA));
        quintaActivaConValoresFicticios();
        conTrabajadores(trabajador(LocalDate.of(2026, 1, 5), "700.01"));

        var r = service.programar(CLIENTE, OCTUBRE);

        assertThat(tiposGuardados(4)).contains(TipoObligacion.RETENCION_QUINTA);
        assertThat(r.noGeneradas()).extracting(Omision::tipo)
                .containsExactlyInAnyOrder(TipoObligacion.ESSALUD, TipoObligacion.AFP);
    }

    @Test
    void conLaQuintaActivaYJustoEnElUmbralNoCorrespondeLaQuinta() {
        when(cronograma.buscar(CLIENTE, OCTUBRE)).thenReturn(Optional.of(FECHA));
        quintaActivaConValoresFicticios();
        conTrabajadores(trabajador(LocalDate.of(2026, 1, 5), "700.00"));

        var r = service.programar(CLIENTE, OCTUBRE);

        assertThat(tiposGuardados(3)).doesNotContain(TipoObligacion.RETENCION_QUINTA);
        assertThat(r.noGeneradas()).contains(
                new Omision(TipoObligacion.RETENCION_QUINTA, "Ningún trabajador supera las 7 UIT proyectadas"));
    }

    @Test
    void sinCronogramaDelPeriodoBloqueaYDiceQueFalta() {
        when(cronograma.buscar(CLIENTE, YearMonth.of(2027, 1))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.programar(CLIENTE, YearMonth.of(2027, 1)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Falta el cronograma de 2027-01");
        verify(obligaciones, never()).saveAndFlush(any());
    }

    @Test
    void conUnClienteInexistenteNoProgramaNada() {
        when(cronograma.buscar(CLIENTE, OCTUBRE)).thenThrow(new EntityNotFoundException("Cliente no encontrado"));

        assertThatThrownBy(() -> service.programar(CLIENTE, OCTUBRE)).isInstanceOf(EntityNotFoundException.class);
        verify(obligaciones, never()).saveAndFlush(any());
    }

    @Test
    void loQueYaEstaProgramadoNoSeDuplicaYSeInforma() {
        when(cronograma.buscar(CLIENTE, OCTUBRE)).thenReturn(Optional.of(FECHA));
        conTrabajadores(trabajador(LocalDate.of(2026, 1, 5), "1500.00"));
        when(obligaciones.findByClienteIdOrderByPeriodoDesc(CLIENTE)).thenReturn(List.of(
                existente(TipoObligacion.IGV, LocalDate.of(2026, 10, 1)),
                existente(TipoObligacion.RENTA, LocalDate.of(2026, 10, 1))));

        var r = service.programar(CLIENTE, OCTUBRE);

        assertThat(r.creadas()).extracting(o -> o.tipoObligacion()).containsExactly(TipoObligacion.PLAME);
        assertThat(r.yaExistian()).containsExactlyInAnyOrder(TipoObligacion.IGV, TipoObligacion.RENTA);
        verify(obligaciones, times(1)).saveAndFlush(any());
    }

    @Test
    void lasObligacionesDeOtroPeriodoNoCuentanComoYaProgramadas() {
        when(cronograma.buscar(CLIENTE, OCTUBRE)).thenReturn(Optional.of(FECHA));
        when(obligaciones.findByClienteIdOrderByPeriodoDesc(CLIENTE)).thenReturn(List.of(
                existente(TipoObligacion.IGV, LocalDate.of(2026, 9, 1))));

        var r = service.programar(CLIENTE, OCTUBRE);

        assertThat(r.creadas()).hasSize(2);
        assertThat(r.yaExistian()).isEmpty();
    }

    @Test
    void programarDeNuevoUnMesCompletoNoCreaNada() {
        when(cronograma.buscar(CLIENTE, OCTUBRE)).thenReturn(Optional.of(FECHA));
        LocalDate periodo = LocalDate.of(2026, 10, 1);
        when(obligaciones.findByClienteIdOrderByPeriodoDesc(CLIENTE)).thenReturn(List.of(
                existente(TipoObligacion.IGV, periodo), existente(TipoObligacion.RENTA, periodo)));

        var r = service.programar(CLIENTE, OCTUBRE);

        assertThat(r.creadas()).isEmpty();
        assertThat(r.yaExistian()).hasSize(2);
        verify(obligaciones, never()).saveAndFlush(any());
    }

    @Test
    void exigeClienteYPeriodo() {
        assertThatThrownBy(() -> service.programar(null, OCTUBRE)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.programar(CLIENTE, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.programar(0L, OCTUBRE)).isInstanceOf(ValidationException.class);
    }
}

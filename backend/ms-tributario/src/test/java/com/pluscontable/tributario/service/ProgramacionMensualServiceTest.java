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
import com.pluscontable.tributario.obligacion.CronogramaConsultaService;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import com.pluscontable.tributario.repository.ObligacionRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
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
    private final ProgramacionMensualService service = new ProgramacionMensualService(cronograma, obligaciones);

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

    @Test
    void generaIgvRentaPlameEssaludYQuintaConLaFechaDelCronogramaYNoGeneraAfp() {
        when(cronograma.buscar(CLIENTE, OCTUBRE)).thenReturn(Optional.of(FECHA));

        var r = service.programar(CLIENTE, OCTUBRE);

        ArgumentCaptor<Obligacion> guardadas = ArgumentCaptor.forClass(Obligacion.class);
        verify(obligaciones, times(5)).saveAndFlush(guardadas.capture());
        assertThat(guardadas.getAllValues()).extracting(Obligacion::getTipoObligacion)
                .containsExactlyInAnyOrder(TipoObligacion.IGV, TipoObligacion.RENTA, TipoObligacion.PLAME,
                        TipoObligacion.ESSALUD, TipoObligacion.RETENCION_QUINTA);
        assertThat(guardadas.getAllValues()).allSatisfy(o -> {
            assertThat(o.getClienteId()).isEqualTo(CLIENTE);
            assertThat(o.getPeriodo()).isEqualTo(LocalDate.of(2026, 10, 1));
            assertThat(o.getFechaVencimiento()).isEqualTo(FECHA);
            assertThat(o.getEstado()).isEqualTo(Obligacion.Estado.PENDIENTE);
        });
        assertThat(r.creadas()).hasSize(5);
        assertThat(r.yaExistian()).isEmpty();
        assertThat(r.noGeneradas()).containsExactly(TipoObligacion.AFP);
        assertThat(r.fechaVencimiento()).isEqualTo(FECHA);
        assertThat(r.periodo()).isEqualTo(OCTUBRE);
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
        when(obligaciones.findByClienteIdOrderByPeriodoDesc(CLIENTE)).thenReturn(List.of(
                existente(TipoObligacion.IGV, LocalDate.of(2026, 10, 1)),
                existente(TipoObligacion.RENTA, LocalDate.of(2026, 10, 1))));

        var r = service.programar(CLIENTE, OCTUBRE);

        assertThat(r.creadas()).extracting(o -> o.tipoObligacion())
                .containsExactlyInAnyOrder(TipoObligacion.PLAME, TipoObligacion.ESSALUD, TipoObligacion.RETENCION_QUINTA);
        assertThat(r.yaExistian()).containsExactlyInAnyOrder(TipoObligacion.IGV, TipoObligacion.RENTA);
        verify(obligaciones, times(3)).saveAndFlush(any());
    }

    @Test
    void lasObligacionesDeOtroPeriodoNoCuentanComoYaProgramadas() {
        when(cronograma.buscar(CLIENTE, OCTUBRE)).thenReturn(Optional.of(FECHA));
        when(obligaciones.findByClienteIdOrderByPeriodoDesc(CLIENTE)).thenReturn(List.of(
                existente(TipoObligacion.IGV, LocalDate.of(2026, 9, 1))));

        var r = service.programar(CLIENTE, OCTUBRE);

        assertThat(r.creadas()).hasSize(5);
        assertThat(r.yaExistian()).isEmpty();
    }

    @Test
    void programarDeNuevoUnMesCompletoNoCreaNada() {
        when(cronograma.buscar(CLIENTE, OCTUBRE)).thenReturn(Optional.of(FECHA));
        LocalDate periodo = LocalDate.of(2026, 10, 1);
        when(obligaciones.findByClienteIdOrderByPeriodoDesc(CLIENTE)).thenReturn(List.of(
                existente(TipoObligacion.IGV, periodo), existente(TipoObligacion.RENTA, periodo),
                existente(TipoObligacion.PLAME, periodo), existente(TipoObligacion.ESSALUD, periodo),
                existente(TipoObligacion.RETENCION_QUINTA, periodo)));

        var r = service.programar(CLIENTE, OCTUBRE);

        assertThat(r.creadas()).isEmpty();
        assertThat(r.yaExistian()).hasSize(5);
        verify(obligaciones, never()).saveAndFlush(any());
    }

    @Test
    void exigeClienteYPeriodo() {
        assertThatThrownBy(() -> service.programar(null, OCTUBRE)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.programar(CLIENTE, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.programar(0L, OCTUBRE)).isInstanceOf(ValidationException.class);
    }
}

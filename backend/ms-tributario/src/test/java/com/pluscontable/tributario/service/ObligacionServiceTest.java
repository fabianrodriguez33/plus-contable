package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.CambioVencimientoDto;
import com.pluscontable.tributario.dto.DeclaracionDto;
import com.pluscontable.tributario.dto.ObligacionDto;
import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.model.Obligacion.Estado;
import com.pluscontable.tributario.model.VencimientoHistorial;
import com.pluscontable.tributario.repository.ObligacionRepository;
import com.pluscontable.tributario.repository.VencimientoHistorialRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** HU-09 · Tarea 64 y HU-10 · Tarea 70 (unitarias). */
class ObligacionServiceTest {

    private static final LocalDate PERIODO = LocalDate.of(2026, 9, 1);
    private static final LocalDate FECHA = LocalDate.of(2026, 10, 15);

    private final ObligacionRepository obligaciones = mock(ObligacionRepository.class);
    private final VencimientoHistorialRepository historial = mock(VencimientoHistorialRepository.class);
    private final ObligacionService service = new ObligacionService(obligaciones, historial);
    private Obligacion pendiente;

    @BeforeEach
    void setUp() {
        pendiente = new Obligacion();
        pendiente.setId(3L);
        pendiente.setClienteId(1L);
        pendiente.setPeriodo(PERIODO);
        pendiente.setFechaVencimiento(LocalDate.of(2026, 10, 20));
        when(obligaciones.findById(3L)).thenReturn(Optional.of(pendiente));
        when(obligaciones.save(any(Obligacion.class))).thenAnswer(inv -> inv.getArgument(0));
        when(obligaciones.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- HU-09 ----

    @Test
    void conNumeroDeFormularioPasaADeclarado() {
        ObligacionDto o = service.declarar(3L, new DeclaracionDto(FECHA, " 0621 ", new BigDecimal("1250.00")));

        assertThat(o.estado()).isEqualTo(Estado.DECLARADO);
        assertThat(o.numeroFormulario()).isEqualTo("0621");
        assertThat(o.fechaDeclaracion()).isEqualTo(FECHA);
        assertThat(o.montoDeterminado()).isEqualByComparingTo("1250");
    }

    @Test
    void sinNumeroDeFormularioNoSeAcepta() {
        assertThatThrownBy(() -> service.declarar(3L, new DeclaracionDto(FECHA, "  ", BigDecimal.TEN)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("numeroFormulario");
        assertThat(pendiente.getEstado()).isEqualTo(Estado.PENDIENTE);
        verify(obligaciones, never()).save(any());
    }

    @Test
    void sinDatosSenalaTodosLosCamposFaltantes() {
        assertThatThrownBy(() -> service.declarar(3L, new DeclaracionDto(null, null, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContainingAll("fechaDeclaracion", "numeroFormulario", "montoDeterminado");
    }

    @Test
    void fechaFuturaOAnteriorAlPeriodoSeRechaza() {
        assertThatThrownBy(() -> service.declarar(3L, new DeclaracionDto(LocalDate.now().plusDays(1), "621", BigDecimal.ONE)))
                .hasMessageContaining("futura");
        assertThatThrownBy(() -> service.declarar(3L, new DeclaracionDto(PERIODO.minusDays(1), "621", BigDecimal.ONE)))
                .hasMessageContaining("anterior al periodo");
    }

    @Test
    void montoNegativoSeRechaza() {
        assertThatThrownBy(() -> service.declarar(3L, new DeclaracionDto(FECHA, "621", new BigDecimal("-1"))))
                .hasMessageContaining("montoDeterminado");
    }

    @Test
    void obligacionYaDeclaradaNoSeVuelveADeclarar() {
        pendiente.setEstado(Estado.DECLARADO);
        pendiente.setNumeroFormulario("0621");

        assertThatThrownBy(() -> service.declarar(3L, new DeclaracionDto(FECHA, "9999", BigDecimal.ONE)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ya fue declarada");
        assertThat(pendiente.getNumeroFormulario()).isEqualTo("0621");
    }

    @Test
    void laFechaYElFormularioQuedanConsultables() {
        service.declarar(3L, new DeclaracionDto(FECHA, "0621", BigDecimal.TEN));

        ObligacionDto consultada = service.buscar(3L);

        assertThat(consultada.fechaDeclaracion()).isEqualTo(FECHA);
        assertThat(consultada.numeroFormulario()).isEqualTo("0621");
    }

    @Test
    void obligacionInexistente() {
        assertThatThrownBy(() -> service.declarar(99L, new DeclaracionDto(FECHA, "621", BigDecimal.ONE)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ---- HU-10 ----

    private Obligacion otra(long id, LocalDate periodo) {
        Obligacion o = new Obligacion();
        o.setId(id);
        o.setClienteId(1L);
        o.setPeriodo(periodo);
        o.setFechaVencimiento(periodo.plusMonths(1).plusDays(19));
        return o;
    }

    @Test
    void cambiaElVencimientoDeUnaObligacionYGuardaLaAnterior() {
        when(obligaciones.findAllById(List.of(3L))).thenReturn(List.of(pendiente));
        LocalDate nueva = LocalDate.of(2026, 10, 27);

        List<ObligacionDto> r = service.cambiarVencimiento(new CambioVencimientoDto(List.of(3L), nueva));

        assertThat(r).singleElement().satisfies(o -> assertThat(o.fechaVencimiento()).isEqualTo(nueva));
        ArgumentCaptor<VencimientoHistorial> h = ArgumentCaptor.forClass(VencimientoHistorial.class);
        verify(historial).save(h.capture());
        assertThat(h.getValue().getObligacionId()).isEqualTo(3L);
        assertThat(h.getValue().getFechaAnterior()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(h.getValue().getFechaNueva()).isEqualTo(nueva);
    }

    @Test
    void cambiaElVencimientoDeVariasALaVez() {
        Obligacion agosto = otra(4L, LocalDate.of(2026, 8, 1));
        when(obligaciones.findAllById(List.of(3L, 4L))).thenReturn(List.of(pendiente, agosto));
        LocalDate nueva = LocalDate.of(2026, 10, 30);

        List<ObligacionDto> r = service.cambiarVencimiento(new CambioVencimientoDto(List.of(3L, 4L, 3L), nueva));

        assertThat(r).extracting(ObligacionDto::fechaVencimiento).containsOnly(nueva);
        verify(historial, times(2)).save(any());
    }

    @Test
    void rechazaFechaAnteriorAlPeriodoTributarioSinCambiarNinguna() {
        Obligacion octubre = otra(4L, LocalDate.of(2026, 10, 1));
        when(obligaciones.findAllById(List.of(3L, 4L))).thenReturn(List.of(pendiente, octubre));

        assertThatThrownBy(() -> service.cambiarVencimiento(new CambioVencimientoDto(List.of(3L, 4L), LocalDate.of(2026, 9, 30))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("anterior al periodo 2026-10");
        assertThat(pendiente.getFechaVencimiento()).isEqualTo(LocalDate.of(2026, 10, 20));
        verify(historial, never()).save(any());
        verify(obligaciones, never()).saveAll(any());
    }

    @Test
    void obligacionDeclaradaNoCambiaDeVencimiento() {
        pendiente.setEstado(Estado.DECLARADO);
        when(obligaciones.findAllById(List.of(3L))).thenReturn(List.of(pendiente));

        assertThatThrownBy(() -> service.cambiarVencimiento(new CambioVencimientoDto(List.of(3L), LocalDate.of(2026, 10, 27))))
                .hasMessageContaining("ya declarada");
    }

    @Test
    void mismaFechaNoGeneraHistorial() {
        when(obligaciones.findAllById(List.of(3L))).thenReturn(List.of(pendiente));

        service.cambiarVencimiento(new CambioVencimientoDto(List.of(3L), LocalDate.of(2026, 10, 20)));

        verify(historial, never()).save(any());
    }

    @Test
    void sinObligacionesNiFechaSenalaAmbos() {
        assertThatThrownBy(() -> service.cambiarVencimiento(new CambioVencimientoDto(List.of(), null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContainingAll("obligacionIds", "nuevaFecha");
    }

    @Test
    void obligacionInexistenteEnElLote() {
        when(obligaciones.findAllById(List.of(3L, 99L))).thenReturn(List.of(pendiente));

        assertThatThrownBy(() -> service.cambiarVencimiento(new CambioVencimientoDto(List.of(3L, 99L), LocalDate.of(2026, 10, 27))))
                .isInstanceOf(EntityNotFoundException.class);
        verify(historial, never()).save(any());
    }
}

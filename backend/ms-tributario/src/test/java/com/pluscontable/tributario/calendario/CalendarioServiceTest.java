package com.pluscontable.tributario.calendario;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pluscontable.tributario.dto.ObligacionDto;
import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import com.pluscontable.tributario.service.ObligacionService;
import jakarta.validation.ValidationException;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarioServiceTest {
    private final ObligacionService obligaciones = mock(ObligacionService.class);
    private final CalendarioService calendario = new CalendarioService(obligaciones,
            Clock.fixed(Instant.parse("2026-10-29T15:00:00Z"), ZoneId.of("America/Lima")));
    private static final LocalDate PERIODO = LocalDate.of(2026, 10, 1);

    private ObligacionDto dato(long id, int dia, Obligacion.Estado estado) {
        return new ObligacionDto(id, 4L, TipoObligacion.IGV, PERIODO, LocalDate.of(2026, 10, dia), estado,
                null, null, null);
    }

    @Test void consultaOrdenadaConDiasCalculados() {
        when(obligaciones.listar(4L)).thenReturn(List.of(
                dato(3, 31, Obligacion.Estado.PENDIENTE),
                dato(1, 27, Obligacion.Estado.PENDIENTE),
                dato(2, 29, Obligacion.Estado.DECLARADO)));
        var lista = calendario.listar(4L, null, null, null, null, null);
        assertEquals(List.of(1L, 2L, 3L), lista.stream().map(o -> o.obligacion().id()).toList());
        assertEquals(List.of(-2L, 0L, 2L), lista.stream().map(CalendarioObligacionDto::diasRestantes).toList());
        assertEquals(List.of(true, false, false), lista.stream().map(CalendarioObligacionDto::vencimientoPasado).toList());
        assertEquals(TipoObligacion.IGV, lista.getFirst().obligacion().tipoObligacion());
    }

    @Test void filtraProximosConHorizonteExplicito() {
        when(obligaciones.listar(4L)).thenReturn(List.of(dato(1, 27, Obligacion.Estado.PENDIENTE),
                dato(2, 29, Obligacion.Estado.PENDIENTE), dato(3, 31, Obligacion.Estado.PENDIENTE)));
        var lista = calendario.listar(4L, null, LocalDate.of(2026, 10, 30), null,
                Obligacion.Estado.PENDIENTE, CalendarioService.Situacion.PROXIMO);
        assertEquals(List.of(2L), lista.stream().map(o -> o.obligacion().id()).toList());
    }

    @Test void filtraVencidosPorFechaPeriodoYEstado() {
        when(obligaciones.listar(4L)).thenReturn(List.of(dato(1, 27, Obligacion.Estado.PENDIENTE),
                dato(2, 28, Obligacion.Estado.DECLARADO), dato(3, 31, Obligacion.Estado.PENDIENTE)));
        var lista = calendario.listar(4L, LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28),
                PERIODO, Obligacion.Estado.PENDIENTE, CalendarioService.Situacion.VENCIDO);
        assertEquals(List.of(1L), lista.stream().map(o -> o.obligacion().id()).toList());
    }

    @Test void validaFiltros() {
        assertThrows(ValidationException.class, () -> calendario.listar(0L, null, null, null, null, null));
        assertThrows(ValidationException.class, () -> calendario.listar(4L,
                LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 29), null, null, null));
        assertThrows(ValidationException.class, () -> calendario.listar(4L, null, null,
                LocalDate.of(2026, 10, 2), null, null));
        assertThrows(ValidationException.class, () -> calendario.listar(4L, null, null,
                null, null, CalendarioService.Situacion.PROXIMO));
        verifyNoInteractions(obligaciones);
    }
}

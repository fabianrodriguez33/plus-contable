package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.CrearObligacionDto;
import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.ObligacionRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class ObligacionProgramacionServiceTest {
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final ObligacionRepository obligaciones = mock(ObligacionRepository.class);
    private final ObligacionProgramacionService service = new ObligacionProgramacionService(clientes, obligaciones);
    private static final CrearObligacionDto SOLICITUD = new CrearObligacionDto(7L, TipoObligacion.IGV,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 16));

    @Test
    void guardaObligacionPendienteSinTocarDeclaracion() {
        when(clientes.existsById(7L)).thenReturn(true);
        when(obligaciones.saveAndFlush(any(Obligacion.class))).thenAnswer(i -> i.getArgument(0));

        var resultado = service.crear(SOLICITUD);

        ArgumentCaptor<Obligacion> captor = ArgumentCaptor.forClass(Obligacion.class);
        verify(obligaciones).saveAndFlush(captor.capture());
        assertThat(resultado.tipoObligacion()).isEqualTo(TipoObligacion.IGV);
        assertThat(resultado.estado()).isEqualTo(Obligacion.Estado.PENDIENTE);
        assertThat(captor.getValue().getFechaDeclaracion()).isNull();
        assertThat(captor.getValue().getFechaVencimiento()).isEqualTo(SOLICITUD.fechaVencimiento());
    }

    @Test
    void noGuardaDuplicado() {
        when(clientes.existsById(7L)).thenReturn(true);
        when(obligaciones.existsByClienteIdAndTipoObligacionAndPeriodo(7L, TipoObligacion.IGV,
                SOLICITUD.periodo())).thenReturn(true);

        assertThatThrownBy(() -> service.crear(SOLICITUD)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
        verify(obligaciones, never()).saveAndFlush(any());
    }

    @Test
    void clienteDebeExistir() {
        assertThatThrownBy(() -> service.crear(SOLICITUD)).isInstanceOf(EntityNotFoundException.class);
        verify(obligaciones, never()).saveAndFlush(any());
    }

    @Test
    void requiereVencimientoExplicito() {
        var sinFecha = new CrearObligacionDto(7L, TipoObligacion.IGV, SOLICITUD.periodo(), null);
        assertThatThrownBy(() -> service.crear(sinFecha)).isInstanceOf(ValidationException.class);
        verify(obligaciones, never()).saveAndFlush(any());
    }

    @Test
    void noAdmiteVencimientoAnteriorAlPeriodoComoHu10() {
        var anterior = new CrearObligacionDto(7L, TipoObligacion.IGV, SOLICITUD.periodo(),
                SOLICITUD.periodo().minusDays(1));
        assertThatThrownBy(() -> service.crear(anterior)).isInstanceOf(ValidationException.class);
        verify(obligaciones, never()).saveAndFlush(any());
    }
}

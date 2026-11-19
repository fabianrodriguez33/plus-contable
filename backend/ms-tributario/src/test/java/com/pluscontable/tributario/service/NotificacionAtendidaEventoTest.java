package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.model.EstadoNotificacion;
import com.pluscontable.tributario.model.NotificacionSol;
import com.pluscontable.tributario.model.TipoNotificacion;
import com.pluscontable.tributario.repository.NotificacionSolRepository;
import jakarta.validation.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** HU-18/HU-19: al atender una notificación se publica el evento que HU-19 usa para retirar sus avisos; si ya estaba atendida no. */
class NotificacionAtendidaEventoTest {

    private final NotificacionSolRepository repositorio = mock(NotificacionSolRepository.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final NotificacionSolService service = new NotificacionSolService(repositorio, mock(ClienteService.class),
            Clock.fixed(Instant.parse("2026-11-17T15:00:00Z"), ZoneOffset.UTC));

    private NotificacionSol pendiente(EstadoNotificacion estado) {
        NotificacionSol n = new NotificacionSol();
        n.setId(40L);
        n.setClienteId(7L);
        n.setTipo(TipoNotificacion.CARTA_INDUCTIVA);
        n.setFechaRecepcion(LocalDate.of(2026, 11, 10));
        n.setPlazoRespuesta(LocalDate.of(2026, 11, 20));
        n.setEstado(estado);
        return n;
    }

    @Test
    void alAtenderSePublicaElEventoConSuNumero() {
        service.setEventos(eventos);
        when(repositorio.findById(40L)).thenReturn(Optional.of(pendiente(EstadoNotificacion.PENDIENTE)));
        when(repositorio.save(any())).thenAnswer(a -> a.getArgument(0));

        service.marcarAtendida(40L);

        verify(eventos).publishEvent(new NotificacionAtendidaEvento(40L));
    }

    @Test
    void unaYaAtendidaDa400SinPublicarNada() {
        service.setEventos(eventos);
        when(repositorio.findById(40L)).thenReturn(Optional.of(pendiente(EstadoNotificacion.ATENDIDA)));

        assertThatThrownBy(() -> service.marcarAtendida(40L)).isInstanceOf(ValidationException.class);

        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void sinPublicadorAtenderSigueFuncionando() {
        when(repositorio.findById(40L)).thenReturn(Optional.of(pendiente(EstadoNotificacion.PENDIENTE)));
        when(repositorio.save(any())).thenAnswer(a -> a.getArgument(0));

        service.marcarAtendida(40L);

        verify(repositorio).save(any());
    }
}

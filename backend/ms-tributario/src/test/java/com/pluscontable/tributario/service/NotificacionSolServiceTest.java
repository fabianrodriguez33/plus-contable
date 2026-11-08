package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.NotificacionSolDto;
import com.pluscontable.tributario.dto.NotificacionSolEntradaDto;
import com.pluscontable.tributario.model.EstadoNotificacion;
import com.pluscontable.tributario.model.NotificacionSol;
import com.pluscontable.tributario.model.TipoNotificacion;
import com.pluscontable.tributario.repository.NotificacionSolRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

/** HU-18 · Tarea 70 (unitarias de registrar): tipo, fecha de recepción y plazo; sin plazo no se guarda. */
class NotificacionSolServiceTest {

    private static final long CLIENTE = 7L;
    private static final LocalDate HOY = LocalDate.of(2026, 11, 9);

    private final NotificacionSolRepository repositorio = mock(NotificacionSolRepository.class);
    private final ClienteService clientes = mock(ClienteService.class);
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneOffset.UTC);
    private final NotificacionSolService service = new NotificacionSolService(repositorio, clientes, reloj);

    @BeforeEach
    void preparar() {
        when(repositorio.save(any(NotificacionSol.class))).thenAnswer(inv -> {
            NotificacionSol n = inv.getArgument(0);
            n.setId(55L);
            return n;
        });
    }

    private static NotificacionSolEntradaDto entrada(TipoNotificacion tipo, LocalDate recepcion, LocalDate plazo) {
        return new NotificacionSolEntradaDto(tipo, recepcion, plazo);
    }

    private NotificacionSol guardada() {
        ArgumentCaptor<NotificacionSol> c = ArgumentCaptor.forClass(NotificacionSol.class);
        verify(repositorio).save(c.capture());
        return c.getValue();
    }

    private void noSeGuarda(NotificacionSolEntradaDto dto, String... partes) {
        assertThatThrownBy(() -> service.registrar(CLIENTE, dto))
                .isInstanceOf(ValidationException.class)
                .hasMessageStartingWith("Faltan o son inválidos: ")
                .satisfies(e -> {
                    for (String parte : partes) assertThat(e.getMessage()).contains(parte);
                });
        verify(repositorio, never()).save(any());
    }

    @Test
    void registraLaNotificacionPendienteConSusDatosYDevuelveElDto() {
        NotificacionSolDto r = service.registrar(CLIENTE,
                entrada(TipoNotificacion.ESQUELA, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 20)));

        NotificacionSol n = guardada();
        assertThat(n.getClienteId()).isEqualTo(CLIENTE);
        assertThat(n.getTipo()).isEqualTo(TipoNotificacion.ESQUELA);
        assertThat(n.getFechaRecepcion()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(n.getPlazoRespuesta()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(n.getFechaAtencion()).isNull();
        assertThat(r.id()).isEqualTo(55L);
        assertThat(r.estado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(r.plazoRespuesta()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @ParameterizedTest
    @EnumSource(TipoNotificacion.class)
    void aceptaLosTresTiposEsquelaCartaInductivaYComunicado(TipoNotificacion tipo) {
        NotificacionSolDto r = service.registrar(CLIENTE, entrada(tipo, HOY, HOY.plusDays(10)));

        assertThat(r.tipo()).isEqualTo(tipo);
    }

    @Test
    void sinPlazoDeRespuestaNoSeGuarda() {
        noSeGuarda(entrada(TipoNotificacion.CARTA_INDUCTIVA, HOY, null), "plazoRespuesta");
    }

    @Test
    void sinTipoNiFechaDeRecepcionNiPlazoListaTodosLosErroresJuntos() {
        noSeGuarda(entrada(null, null, null), "tipo", "fechaRecepcion", "plazoRespuesta");
    }

    @Test
    void sinCuerpoTambienSeRechazaConLosTresCampos() {
        noSeGuarda(null, "tipo", "fechaRecepcion", "plazoRespuesta");
    }

    @Test
    void laFechaDeRecepcionNoPuedeSerFutura() {
        noSeGuarda(entrada(TipoNotificacion.COMUNICADO, HOY.plusDays(1), HOY.plusDays(15)), "fechaRecepcion (no puede ser futura)");
    }

    @Test
    void laFechaDeRecepcionDeHoyEsValida() {
        service.registrar(CLIENTE, entrada(TipoNotificacion.COMUNICADO, HOY, HOY.plusDays(15)));

        assertThat(guardada().getFechaRecepcion()).isEqualTo(HOY);
    }

    @Test
    void elPlazoNoPuedeSerAnteriorALaRecepcion() {
        noSeGuarda(entrada(TipoNotificacion.ESQUELA, HOY, HOY.minusDays(1)), "plazoRespuesta (no puede ser anterior a la fecha de recepción)");
    }

    @Test
    void elPlazoIgualALaRecepcionEsValido() {
        service.registrar(CLIENTE, entrada(TipoNotificacion.ESQUELA, HOY, HOY));

        assertThat(guardada().getPlazoRespuesta()).isEqualTo(HOY);
    }

    @Test
    void siElClienteNoExisteResponde404YNoGuarda() {
        when(clientes.buscar(CLIENTE)).thenThrow(new EntityNotFoundException("Cliente no encontrado: " + CLIENTE));

        assertThatThrownBy(() -> service.registrar(CLIENTE, entrada(TipoNotificacion.ESQUELA, HOY, HOY.plusDays(5))))
                .isInstanceOf(EntityNotFoundException.class);
        verify(repositorio, never()).save(any());
    }

    @Test
    void conClienteInexistenteYDatosInvalidosGanaElErrorDelCliente() {
        when(clientes.buscar(CLIENTE)).thenThrow(new EntityNotFoundException("Cliente no encontrado: " + CLIENTE));

        assertThatThrownBy(() -> service.registrar(CLIENTE, entrada(null, null, null)))
                .isInstanceOf(EntityNotFoundException.class);
    }
}

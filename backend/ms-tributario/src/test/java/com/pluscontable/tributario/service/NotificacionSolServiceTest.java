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
import java.util.List;
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

    // ---- tarea 70: límites y rechazos que faltaban

    @Test
    void sinPlazoElMensajeNombraSoloElPlazo() {
        noSeGuardaConMensaje(entrada(TipoNotificacion.ESQUELA, HOY, null), "Faltan o son inválidos: plazoRespuesta");
    }

    @Test
    void sinTipoElMensajeNombraSoloElTipo() {
        noSeGuardaConMensaje(entrada(null, HOY, HOY.plusDays(5)), "Faltan o son inválidos: tipo");
    }

    @Test
    void sinFechaDeRecepcionNoSeComparaElPlazoYElMensajeNombraSoloLaFecha() {
        noSeGuardaConMensaje(entrada(TipoNotificacion.COMUNICADO, null, HOY.minusDays(30)), "Faltan o son inválidos: fechaRecepcion");
    }

    @Test
    void recepcionFuturaYPlazoAnteriorALaRecepcionListanLosDosErroresJuntos() {
        noSeGuardaConMensaje(entrada(TipoNotificacion.ESQUELA, HOY.plusDays(3), HOY),
                "Faltan o son inválidos: fechaRecepcion (no puede ser futura), "
                        + "plazoRespuesta (no puede ser anterior a la fecha de recepción)");
    }

    @Test
    void unaNotificacionConElPlazoYaVencidoSiSePuedeRegistrarMientrasNoSeaAnteriorALaRecepcion() {
        service.registrar(CLIENTE, entrada(TipoNotificacion.CARTA_INDUCTIVA, HOY.minusDays(20), HOY.minusDays(5)));

        assertThat(guardada().getPlazoRespuesta()).isEqualTo(HOY.minusDays(5));
    }

    @Test
    void laFechaDeRecepcionDeMuchoAntesTambienEsValida() {
        service.registrar(CLIENTE, entrada(TipoNotificacion.ESQUELA, LocalDate.of(2025, 1, 31), HOY.plusDays(1)));

        assertThat(guardada().getFechaRecepcion()).isEqualTo(LocalDate.of(2025, 1, 31));
    }

    private void noSeGuardaConMensaje(NotificacionSolEntradaDto dto, String mensaje) {
        assertThatThrownBy(() -> service.registrar(CLIENTE, dto))
                .isInstanceOf(ValidationException.class).hasMessage(mensaje);
        verify(repositorio, never()).save(any());
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

    // ---- tarea 67: listar (pendientes del cliente)

    private NotificacionSol conPlazo(long id, EstadoNotificacion estado, LocalDate plazo) {
        NotificacionSol n = new NotificacionSol();
        n.setId(id);
        n.setClienteId(CLIENTE);
        n.setTipo(TipoNotificacion.COMUNICADO);
        n.setFechaRecepcion(HOY.minusDays(10));
        n.setPlazoRespuesta(plazo);
        n.setEstado(estado);
        if (estado == EstadoNotificacion.ATENDIDA) n.setFechaAtencion(HOY.minusDays(1));
        return n;
    }

    @Test
    void pendientesConsultaSoloLasPendientesDelClienteEnElOrdenDelRepositorio() {
        when(repositorio.findByClienteIdAndEstadoOrderByPlazoRespuestaAscIdAsc(CLIENTE, EstadoNotificacion.PENDIENTE))
                .thenReturn(List.of(conPlazo(2L, EstadoNotificacion.PENDIENTE, HOY.plusDays(3)),
                        conPlazo(1L, EstadoNotificacion.PENDIENTE, HOY.plusDays(9))));

        List<NotificacionSolDto> r = service.pendientes(CLIENTE);

        verify(clientes).buscar(CLIENTE);
        assertThat(r).extracting(NotificacionSolDto::id).containsExactly(2L, 1L);
        assertThat(r).allSatisfy(d -> assertThat(d.estado()).isEqualTo(EstadoNotificacion.PENDIENTE));
        assertThat(r.get(0).plazoRespuesta()).isEqualTo(HOY.plusDays(3));
        assertThat(r.get(0).clienteId()).isEqualTo(CLIENTE);
        assertThat(r.get(0).tipo()).isEqualTo(TipoNotificacion.COMUNICADO);
        assertThat(r.get(0).fechaRecepcion()).isEqualTo(HOY.minusDays(10));
        verify(repositorio, never()).findByClienteIdOrderByPlazoRespuestaAscIdAsc(any());
    }

    @Test
    void listarConEstadoAtendidaPideSoloLasAtendidas() {
        when(repositorio.findByClienteIdAndEstadoOrderByPlazoRespuestaAscIdAsc(CLIENTE, EstadoNotificacion.ATENDIDA))
                .thenReturn(List.of(conPlazo(5L, EstadoNotificacion.ATENDIDA, HOY.minusDays(2))));

        List<NotificacionSolDto> r = service.listar(CLIENTE, EstadoNotificacion.ATENDIDA);

        assertThat(r).hasSize(1);
        assertThat(r.get(0).estado()).isEqualTo(EstadoNotificacion.ATENDIDA);
        assertThat(r.get(0).fechaAtencion()).isEqualTo(HOY.minusDays(1));
        verify(repositorio, never()).findByClienteIdAndEstadoOrderByPlazoRespuestaAscIdAsc(CLIENTE, EstadoNotificacion.PENDIENTE);
    }

    @Test
    void listarSinEstadoDevuelveTodasLasDelClienteSinFiltrar() {
        when(repositorio.findByClienteIdOrderByPlazoRespuestaAscIdAsc(CLIENTE))
                .thenReturn(List.of(conPlazo(5L, EstadoNotificacion.ATENDIDA, HOY.minusDays(2)),
                        conPlazo(6L, EstadoNotificacion.PENDIENTE, HOY.plusDays(2))));

        List<NotificacionSolDto> r = service.listar(CLIENTE, null);

        assertThat(r).extracting(NotificacionSolDto::id).containsExactly(5L, 6L);
        verify(repositorio, never()).findByClienteIdAndEstadoOrderByPlazoRespuestaAscIdAsc(any(), any());
    }

    @Test
    void unClienteSinNotificacionesDevuelveUnaListaVacia() {
        when(repositorio.findByClienteIdAndEstadoOrderByPlazoRespuestaAscIdAsc(CLIENTE, EstadoNotificacion.PENDIENTE))
                .thenReturn(List.of());

        assertThat(service.pendientes(CLIENTE)).isEmpty();
    }

    @Test
    void listarConUnClienteInexistenteResponde404YNoConsultaLasNotificaciones() {
        when(clientes.buscar(CLIENTE)).thenThrow(new EntityNotFoundException("Cliente no encontrado: " + CLIENTE));

        assertThatThrownBy(() -> service.pendientes(CLIENTE)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.listar(CLIENTE, null)).isInstanceOf(EntityNotFoundException.class);
        verify(repositorio, never()).findByClienteIdOrderByPlazoRespuestaAscIdAsc(any());
        verify(repositorio, never()).findByClienteIdAndEstadoOrderByPlazoRespuestaAscIdAsc(any(), any());
    }

    // ---- tarea 68: marcar como atendida

    private NotificacionSol existente(EstadoNotificacion estado) {
        NotificacionSol n = new NotificacionSol();
        n.setId(55L);
        n.setClienteId(CLIENTE);
        n.setTipo(TipoNotificacion.ESQUELA);
        n.setFechaRecepcion(HOY.minusDays(5));
        n.setPlazoRespuesta(HOY.plusDays(10));
        n.setEstado(estado);
        when(repositorio.findById(55L)).thenReturn(java.util.Optional.of(n));
        return n;
    }

    @Test
    void marcarAtendidaPasaAAtendidaConLaFechaDeHoyYDevuelveElDto() {
        existente(EstadoNotificacion.PENDIENTE);

        NotificacionSolDto r = service.marcarAtendida(55L);

        NotificacionSol n = guardada();
        assertThat(n.getEstado()).isEqualTo(EstadoNotificacion.ATENDIDA);
        assertThat(n.getFechaAtencion()).isEqualTo(HOY);
        assertThat(r.estado()).isEqualTo(EstadoNotificacion.ATENDIDA);
        assertThat(r.fechaAtencion()).isEqualTo(HOY);
        assertThat(r.plazoRespuesta()).isEqualTo(HOY.plusDays(10)); // el resto no cambia
    }

    @Test
    void sePuedeMarcarAtendidaDespuesDelPlazoYLoDemasNoCambia() {
        NotificacionSol n = existente(EstadoNotificacion.PENDIENTE);
        n.setPlazoRespuesta(HOY.minusDays(3)); // ya venció: igual se puede dejar constancia de que se atendió

        NotificacionSolDto r = service.marcarAtendida(55L);

        assertThat(r.estado()).isEqualTo(EstadoNotificacion.ATENDIDA);
        assertThat(r.fechaAtencion()).isEqualTo(HOY);
        assertThat(r.clienteId()).isEqualTo(CLIENTE);
        assertThat(r.tipo()).isEqualTo(TipoNotificacion.ESQUELA);
        assertThat(r.fechaRecepcion()).isEqualTo(HOY.minusDays(5));
        assertThat(r.plazoRespuesta()).isEqualTo(HOY.minusDays(3));
    }

    @Test
    void siLaNotificacionNoExisteResponde404YNoGuarda() {
        when(repositorio.findById(99L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.marcarAtendida(99L))
                .isInstanceOf(EntityNotFoundException.class).hasMessage("Notificación no encontrada");
        verify(repositorio, never()).save(any());
    }

    @Test
    void siYaEstabaAtendidaResponde400NoGuardaYConservaLaFechaOriginal() {
        NotificacionSol n = existente(EstadoNotificacion.ATENDIDA);
        n.setFechaAtencion(HOY.minusDays(2));

        assertThatThrownBy(() -> service.marcarAtendida(55L))
                .isInstanceOf(ValidationException.class).hasMessage("La notificación ya fue atendida");
        verify(repositorio, never()).save(any());
        assertThat(n.getFechaAtencion()).isEqualTo(HOY.minusDays(2));
    }
}

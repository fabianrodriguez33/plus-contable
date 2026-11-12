package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.SolicitudEspecialDto;
import com.pluscontable.tributario.dto.SolicitudEspecialEntradaDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.EstadoSolicitud;
import com.pluscontable.tributario.model.SolicitudEspecial;
import com.pluscontable.tributario.model.TipoSolicitud;
import com.pluscontable.tributario.repository.SolicitudEspecialRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** HU-26 · Tarea 187 (unitarias): registrar el pedido con su fecha de entrega, sin fecha no se guarda, pendientes y atención. */
class SolicitudEspecialServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 11);
    private static final long CLIENTE = 7L;

    private final SolicitudEspecialRepository repo = mock(SolicitudEspecialRepository.class);
    private final ClienteService clientes = mock(ClienteService.class);
    private final SolicitudEspecialService service = new SolicitudEspecialService(repo, clientes,
            Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void preparar() {
        when(clientes.buscar(CLIENTE)).thenReturn(new Cliente());
        when(clientes.buscar(99L)).thenThrow(new EntityNotFoundException("Cliente no encontrado: 99"));
        when(repo.save(any())).thenAnswer(a -> {
            SolicitudEspecial s = a.getArgument(0);
            if (s.getId() == null) s.setId(5L);
            return s;
        });
    }

    private static SolicitudEspecial guardada(long cliente, EstadoSolicitud estado) {
        SolicitudEspecial s = new SolicitudEspecial();
        s.setId(5L);
        s.setClienteId(cliente);
        s.setTipo(TipoSolicitud.TRAMITE);
        s.setFechaEntrega(HOY.plusDays(3));
        s.setEstado(estado);
        if (estado == EstadoSolicitud.ATENDIDA) s.setFechaAtencion(HOY.minusDays(1));
        return s;
    }

    @Test
    void criterio1_guardaElPedidoConTipoFechaDeEntregaDescripcionYQuienLoRegistroComoPendiente() {
        SolicitudEspecialDto r = service.registrar(CLIENTE,
                new SolicitudEspecialEntradaDto(TipoSolicitud.CONSTANCIA, "  Constancia de no adeudo ", HOY.plusDays(5)), "contador1");

        ArgumentCaptor<SolicitudEspecial> s = ArgumentCaptor.forClass(SolicitudEspecial.class);
        verify(repo).save(s.capture());
        assertThat(s.getValue().getClienteId()).isEqualTo(CLIENTE);
        assertThat(s.getValue().getTipo()).isEqualTo(TipoSolicitud.CONSTANCIA);
        assertThat(s.getValue().getDescripcion()).isEqualTo("Constancia de no adeudo");
        assertThat(s.getValue().getFechaEntrega()).isEqualTo(HOY.plusDays(5));
        assertThat(s.getValue().getRegistradaPor()).isEqualTo("contador1");
        assertThat(r.estado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        assertThat(r.fechaAtencion()).isNull();
    }

    @Test
    void criterio1_laDescripcionEsOpcionalYHoyEsUnaFechaDeEntregaValida() {
        SolicitudEspecialDto r = service.registrar(CLIENTE, new SolicitudEspecialEntradaDto(TipoSolicitud.CERTIFICACION, "   ", HOY), "c");

        assertThat(r.descripcion()).isNull();
        assertThat(r.fechaEntrega()).isEqualTo(HOY);
    }

    @Test
    void criterio2_sinFechaDeEntregaNoSeGuardaYSeReunenTodosLosErrores() {
        assertThatThrownBy(() -> service.registrar(CLIENTE, new SolicitudEspecialEntradaDto(TipoSolicitud.TRAMITE, null, null), "c"))
                .isInstanceOf(ValidationException.class).hasMessage("Faltan o son inválidos: fechaEntrega");
        assertThatThrownBy(() -> service.registrar(CLIENTE, null, "c"))
                .hasMessage("Faltan o son inválidos: tipo, fechaEntrega");
        assertThatThrownBy(() -> service.registrar(CLIENTE,
                new SolicitudEspecialEntradaDto(TipoSolicitud.TRAMITE, "x".repeat(501), HOY.minusDays(1)), "c"))
                .hasMessage("Faltan o son inválidos: fechaEntrega (no puede ser anterior a hoy), descripcion (máximo 500 caracteres)");
        verify(repo, never()).save(any());
    }

    @Test
    void unaDescripcionDe500CaracteresSiSeAcepta() {
        assertThat(service.registrar(CLIENTE, new SolicitudEspecialEntradaDto(TipoSolicitud.TRAMITE, "x".repeat(500), HOY), "c")
                .descripcion()).hasSize(500);
    }

    @Test
    void unClienteInexistenteDa404SinGuardar() {
        assertThatThrownBy(() -> service.registrar(99L, new SolicitudEspecialEntradaDto(TipoSolicitud.TRAMITE, null, HOY), "c"))
                .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.pendientes(99L)).isInstanceOf(EntityNotFoundException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void criterio3_losPendientesSonLosPendienteDelClienteEnElOrdenDelRepositorio() {
        when(repo.findByClienteIdAndEstadoOrderByFechaEntregaAscIdAsc(CLIENTE, EstadoSolicitud.PENDIENTE))
                .thenReturn(List.of(guardada(CLIENTE, EstadoSolicitud.PENDIENTE)));

        assertThat(service.pendientes(CLIENTE)).singleElement().satisfies(d -> assertThat(d.id()).isEqualTo(5L));
    }

    @Test
    void marcarAtendidaLaSacaDePendientesConLaFechaDeHoy() {
        SolicitudEspecial s = guardada(CLIENTE, EstadoSolicitud.PENDIENTE);
        when(repo.findById(5L)).thenReturn(Optional.of(s));

        SolicitudEspecialDto r = service.marcarAtendida(CLIENTE, 5L);

        assertThat(r.estado()).isEqualTo(EstadoSolicitud.ATENDIDA);
        assertThat(r.fechaAtencion()).isEqualTo(HOY);
    }

    @Test
    void marcarAtendidaUnaYaAtendidaDa400YUnaDeOtroClienteOInexistenteDa404() {
        when(repo.findById(5L)).thenReturn(Optional.of(guardada(CLIENTE, EstadoSolicitud.ATENDIDA)));
        assertThatThrownBy(() -> service.marcarAtendida(CLIENTE, 5L)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.marcarAtendida(8L, 5L)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.marcarAtendida(CLIENTE, 77L)).isInstanceOf(EntityNotFoundException.class);
        verify(repo, never()).save(any());
    }
}

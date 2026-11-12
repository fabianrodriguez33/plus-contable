package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pluscontable.tributario.dto.HistorialItemDto;
import com.pluscontable.tributario.dto.HistorialItemDto.Origen;
import com.pluscontable.tributario.dto.IncidenciaDto;
import com.pluscontable.tributario.model.EstadoSolicitud;
import com.pluscontable.tributario.model.SolicitudEspecial;
import com.pluscontable.tributario.model.TipoIncidencia;
import com.pluscontable.tributario.model.TipoSolicitud;
import com.pluscontable.tributario.repository.SolicitudEspecialRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** HU-27 · Tarea 194 (unitarias): junta incidencias y solicitudes, el orden, los datos de cada registro y el historial vacío. */
class HistorialServiceTest {

    private static final long CLIENTE = 7L;

    private final IncidenciaService incidencias = mock(IncidenciaService.class);
    private final SolicitudEspecialRepository solicitudes = mock(SolicitudEspecialRepository.class);
    private final HistorialService service = new HistorialService(incidencias, solicitudes);

    private static IncidenciaDto incidencia(long id, TipoIncidencia tipo, String fecha, String registradaEn) {
        return new IncidenciaDto(id, tipo, LocalDate.parse(fecha), "Incidencia " + id, "contador1", LocalDateTime.parse(registradaEn), 1);
    }

    private static SolicitudEspecial solicitud(long id, String registradaEn, EstadoSolicitud estado) {
        SolicitudEspecial s = new SolicitudEspecial();
        s.setId(id);
        s.setClienteId(CLIENTE);
        s.setTipo(TipoSolicitud.CONSTANCIA);
        s.setDescripcion("Solicitud " + id);
        s.setFechaEntrega(LocalDate.of(2026, 12, 1));
        s.setEstado(estado);
        s.setRegistradaEn(LocalDateTime.parse(registradaEn));
        return s;
    }

    @Test
    void criterio1_juntaIncidenciasYSolicitudesDeLaMasRecienteALaMasAntigua() {
        when(incidencias.delCliente(CLIENTE)).thenReturn(List.of(
                incidencia(1, TipoIncidencia.OTRO, "2026-11-10", "2026-11-10T09:00:00"),
                incidencia(2, TipoIncidencia.CAIDA_PORTAL_SOL, "2026-11-01", "2026-11-01T08:00:00")));
        when(solicitudes.findByClienteId(CLIENTE)).thenReturn(List.of(
                solicitud(5, "2026-11-05T10:00:00", EstadoSolicitud.ATENDIDA),
                solicitud(6, "2026-11-11T12:00:00", EstadoSolicitud.PENDIENTE)));

        assertThat(service.delCliente(CLIENTE)).extracting(h -> h.origen() + "-" + h.id())
                .containsExactly("SOLICITUD-6", "INCIDENCIA-1", "SOLICITUD-5", "INCIDENCIA-2");
    }

    @Test
    void criterio1_aIgualFechaVaPrimeroLoRegistradoDespues() {
        when(incidencias.delCliente(CLIENTE)).thenReturn(List.of(incidencia(1, TipoIncidencia.OTRO, "2026-11-10", "2026-11-10T09:00:00")));
        when(solicitudes.findByClienteId(CLIENTE)).thenReturn(List.of(solicitud(5, "2026-11-10T15:30:00", EstadoSolicitud.PENDIENTE)));

        assertThat(service.delCliente(CLIENTE)).extracting(HistorialItemDto::origen).containsExactly(Origen.SOLICITUD, Origen.INCIDENCIA);
    }

    @Test
    void criterio2_cadaRegistroTraeTipoFechaYDescripcionMasLoPropioDeSuOrigen() {
        when(incidencias.delCliente(CLIENTE)).thenReturn(List.of(incidencia(2, TipoIncidencia.CAIDA_PORTAL_SOL, "2026-11-01", "2026-11-01T08:00:00")));
        when(solicitudes.findByClienteId(CLIENTE)).thenReturn(List.of(solicitud(5, "2026-11-05T10:00:00", EstadoSolicitud.ATENDIDA)));

        List<HistorialItemDto> h = service.delCliente(CLIENTE);

        assertThat(h.get(0)).satisfies(s -> {
            assertThat(s.tipo()).isEqualTo("CONSTANCIA");
            assertThat(s.fecha()).isEqualTo(LocalDate.of(2026, 11, 5)); // el día en que se pidió
            assertThat(s.descripcion()).isEqualTo("Solicitud 5");
            assertThat(s.estado()).isEqualTo("ATENDIDA");
            assertThat(s.fechaEntrega()).isEqualTo(LocalDate.of(2026, 12, 1));
            assertThat(s.general()).isFalse();
        });
        assertThat(h.get(1)).satisfies(i -> {
            assertThat(i.tipo()).isEqualTo("CAIDA_PORTAL_SOL");
            assertThat(i.fecha()).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(i.descripcion()).isEqualTo("Incidencia 2");
            assertThat(i.general()).isTrue();
            assertThat(i.estado()).isNull();
            assertThat(i.fechaEntrega()).isNull();
        });
    }

    @Test
    void criterio3_sinIncidenciasNiSolicitudesElHistorialEstaVacio() {
        when(incidencias.delCliente(CLIENTE)).thenReturn(List.of());
        when(solicitudes.findByClienteId(CLIENTE)).thenReturn(List.of());

        assertThat(service.delCliente(CLIENTE)).isEmpty();
    }

    @Test
    void unClienteInexistenteDa404() {
        when(incidencias.delCliente(99L)).thenThrow(new EntityNotFoundException("Cliente no encontrado: 99"));

        assertThatThrownBy(() -> service.delCliente(99L)).isInstanceOf(EntityNotFoundException.class);
    }
}

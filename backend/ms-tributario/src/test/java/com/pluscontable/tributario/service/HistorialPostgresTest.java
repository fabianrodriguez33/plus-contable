package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pluscontable.tributario.dto.HistorialItemDto;
import com.pluscontable.tributario.dto.IncidenciaEntradaDto;
import com.pluscontable.tributario.dto.SolicitudEspecialDto;
import com.pluscontable.tributario.dto.SolicitudEspecialEntradaDto;
import com.pluscontable.tributario.model.TipoIncidencia;
import com.pluscontable.tributario.model.TipoSolicitud;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-27 · Tarea 195: el historial de punta a punta contra PostgreSQL de pruebas, con incidencias (HU-23) y solicitudes (HU-26)
 * guardadas por sus servicios (los permisos por rol se prueban en HistorialControllerTest). Clientes ficticios que se borran.
 */
@SpringBootTest
class HistorialPostgresTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired HistorialService service;
    @Autowired IncidenciaService incidencias;
    @Autowired SolicitudEspecialService solicitudes;

    private final List<String> rucs = new ArrayList<>();
    private final List<Long> incidenciasCreadas = new ArrayList<>();
    private final LocalDate hoy = LocalDate.now();

    @AfterEach
    void limpiar() {
        // borrar el cliente solo quita la asociación: la incidencia quedaría suelta y molestaría a otras pruebas (como HU-23)
        for (Long id : incidenciasCreadas) jdbc.update("DELETE FROM tributario_schema.incidencias WHERE id = ?", id);
        for (String ruc : rucs) jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
    }

    private long cliente(boolean activo) {
        String ruc = RucFicticio.nuevo();
        rucs.add(ruc);
        jdbc.update("INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario, activo) VALUES (?, 'Cliente ficticio (historial)', 'MYPE', ?)",
                ruc, activo);
        return jdbc.queryForObject("SELECT id FROM tributario_schema.clientes WHERE ruc = ?", Long.class, ruc);
    }

    private void incidencia(Long cliente, TipoIncidencia tipo, LocalDate fecha, String descripcion) {
        incidenciasCreadas.add(incidencias.registrar(new IncidenciaEntradaDto(tipo, cliente, fecha, descripcion, null), "contador1").id());
    }

    @Test
    void criterios1y2_muestraSusIncidenciasYSolicitudesConTipoFechaYDescripcionDeLaMasRecienteALaMasAntigua() {
        long id = cliente(true);
        long otro = cliente(true);
        incidencia(id, TipoIncidencia.CLIENTE_NO_ENVIA_DOCUMENTOS, hoy.minusDays(10), "No envió sus compras");
        incidencia(otro, TipoIncidencia.OTRO, hoy.minusDays(1), "De otro cliente");          // no aparece
        SolicitudEspecialDto atendida = solicitudes.registrar(id, new SolicitudEspecialEntradaDto(TipoSolicitud.CONSTANCIA, "No adeudo", hoy), "contador1");
        solicitudes.marcarAtendida(id, atendida.id());
        incidencia(id, TipoIncidencia.PROBLEMA_SIRE, hoy.minusDays(3), "SIRE no cargaba");

        List<HistorialItemDto> h = service.delCliente(id);

        assertThat(h).extracting(HistorialItemDto::descripcion).containsExactly("No adeudo", "SIRE no cargaba", "No envió sus compras");
        assertThat(h.get(0)).satisfies(s -> {
            assertThat(s.origen()).isEqualTo(HistorialItemDto.Origen.SOLICITUD);
            assertThat(s.tipo()).isEqualTo("CONSTANCIA");
            assertThat(s.fecha()).isEqualTo(hoy);
            assertThat(s.estado()).isEqualTo("ATENDIDA");      // las atendidas también quedan en el historial
        });
        assertThat(h.get(2)).satisfies(i -> {
            assertThat(i.tipo()).isEqualTo("CLIENTE_NO_ENVIA_DOCUMENTOS");
            assertThat(i.fecha()).isEqualTo(hoy.minusDays(10));
        });
    }

    @Test
    void lasIncidenciasGeneralesDeLaCarteraTambienEntranEnElHistorialDelCliente() {
        long id = cliente(true);
        incidencia(null, TipoIncidencia.CAIDA_PORTAL_SOL, hoy, "SOL caído toda la mañana");

        List<HistorialItemDto> h = service.delCliente(id);

        assertThat(h).anySatisfy(i -> {
            assertThat(i.descripcion()).isEqualTo("SOL caído toda la mañana");
            assertThat(i.general()).isTrue();
        });
    }

    @Test
    void criterio3_unClienteSinRegistrosTieneElHistorialVacio() {
        long id = cliente(false); // inactivo: tampoco recibe las incidencias generales nuevas

        assertThat(service.delCliente(id)).isEmpty();
    }
}

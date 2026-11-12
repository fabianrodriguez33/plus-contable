package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.dto.SolicitudEspecialDto;
import com.pluscontable.tributario.dto.SolicitudEspecialEntradaDto;
import com.pluscontable.tributario.model.EstadoSolicitud;
import com.pluscontable.tributario.model.TipoSolicitud;
import jakarta.validation.ValidationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-26 · Tareas 186 y 188: la tabla {@code solicitudes_especiales} y las solicitudes de punta a punta contra PostgreSQL de
 * pruebas (los permisos por rol se prueban en SolicitudEspecialControllerTest). Clientes ficticios que se borran al terminar.
 */
@SpringBootTest
class SolicitudEspecialPostgresTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired SolicitudEspecialService service;

    private final List<String> rucs = new ArrayList<>();
    private final LocalDate hoy = LocalDate.now();

    @AfterEach
    void limpiar() {
        for (String ruc : rucs) jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
    }

    private long cliente() {
        String ruc = RucFicticio.nuevo();
        rucs.add(ruc);
        jdbc.update("INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario, activo) VALUES (?, 'Cliente ficticio (solicitudes)', 'MYPE', true)", ruc);
        return jdbc.queryForObject("SELECT id FROM tributario_schema.clientes WHERE ruc = ?", Long.class, ruc);
    }

    private int filas(long cliente) {
        return jdbc.queryForObject("SELECT count(*) FROM tributario_schema.solicitudes_especiales WHERE cliente_id = ?", Integer.class, cliente);
    }

    @Test
    void criterios1y3_elPedidoSeGuardaConSuFechaYQuedaEnPendientesOrdenadoPorEntrega() {
        long id = cliente();
        service.registrar(id, new SolicitudEspecialEntradaDto(TipoSolicitud.TRAMITE, "Baja de RUC", hoy.plusDays(10)), "contador1");
        service.registrar(id, new SolicitudEspecialEntradaDto(TipoSolicitud.CONSTANCIA, null, hoy.plusDays(2)), "contador1");

        List<SolicitudEspecialDto> pendientes = service.pendientes(id);

        assertThat(pendientes).extracting(SolicitudEspecialDto::tipo).containsExactly(TipoSolicitud.CONSTANCIA, TipoSolicitud.TRAMITE);
        assertThat(pendientes).allSatisfy(s -> assertThat(s.estado()).isEqualTo(EstadoSolicitud.PENDIENTE));
        assertThat(jdbc.queryForObject("SELECT registrada_por FROM tributario_schema.solicitudes_especiales WHERE cliente_id = ? AND tipo = 'TRAMITE'",
                String.class, id)).isEqualTo("contador1");
    }

    @Test
    void criterio2_sinFechaDeEntregaNoSeGuardaNiEnLaBase() {
        long id = cliente();

        assertThatThrownBy(() -> service.registrar(id, new SolicitudEspecialEntradaDto(TipoSolicitud.TRAMITE, "x", null), "c"))
                .isInstanceOf(ValidationException.class);
        assertThat(filas(id)).isZero();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO tributario_schema.solicitudes_especiales (cliente_id, tipo, registrada_por) VALUES (?, 'TRAMITE', 'x')", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void atenderLaSacaDePendientesYNoSePuedeAtenderDosVeces() {
        long id = cliente();
        SolicitudEspecialDto s = service.registrar(id, new SolicitudEspecialEntradaDto(TipoSolicitud.CERTIFICACION, null, hoy), "c");

        SolicitudEspecialDto atendida = service.marcarAtendida(id, s.id());

        assertThat(atendida.fechaAtencion()).isEqualTo(hoy);
        assertThat(service.pendientes(id)).isEmpty();
        assertThatThrownBy(() -> service.marcarAtendida(id, s.id())).isInstanceOf(ValidationException.class);
        assertThat(filas(id)).isEqualTo(1);
    }

    @Test
    void laTablaRechazaTiposYEstadosFueraDeListaYUnaAtencionSinFecha() {
        long id = cliente();
        String insertar = "INSERT INTO tributario_schema.solicitudes_especiales (cliente_id, tipo, fecha_entrega, estado, fecha_atencion, registrada_por) VALUES (?, ?, ?, ?, ?, 'x')";

        assertThatThrownBy(() -> jdbc.update(insertar, id, "CARTA", hoy, "PENDIENTE", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, id, "TRAMITE", hoy, "ANULADA", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, id, "TRAMITE", hoy, "ATENDIDA", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, id, "TRAMITE", hoy, "PENDIENTE", hoy)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(filas(id)).isZero();
    }

    @Test
    void borrarElClienteBorraSusSolicitudes() {
        long id = cliente();
        service.registrar(id, new SolicitudEspecialEntradaDto(TipoSolicitud.TRAMITE, null, hoy), "c");

        jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", id);

        assertThat(filas(id)).isZero();
    }
}

package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.model.EstadoNotificacion;
import com.pluscontable.tributario.model.NotificacionSol;
import com.pluscontable.tributario.model.TipoNotificacion;
import com.pluscontable.tributario.repository.NotificacionSolRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HU-18 · Tarea 69: la tabla {@code notificaciones_sol} que crea schema.sql (requiere PostgreSQL de pruebas).
 * Usa un cliente ficticio que se borra al terminar; sus notificaciones se borran en cascada.
 */
@SpringBootTest
class NotificacionSolEsquemaPostgresTest {

    private static final LocalDate RECEPCION = LocalDate.of(2026, 11, 5);

    @Autowired JdbcTemplate jdbc;
    @Autowired NotificacionSolRepository repositorio;

    private String ruc;
    private long cliente;

    @BeforeEach
    void crearCliente() {
        ruc = RucFicticio.nuevo();
        jdbc.update("INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario) VALUES (?, ?, 'MYPE')",
                ruc, "Cliente ficticio (notificaciones)");
        cliente = jdbc.queryForObject("SELECT id FROM tributario_schema.clientes WHERE ruc = ?", Long.class, ruc);
    }

    @AfterEach
    void borrarCliente() {
        jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
    }

    private int insertar(String tipo, LocalDate recepcion, LocalDate plazo) {
        return jdbc.update("INSERT INTO tributario_schema.notificaciones_sol (cliente_id, tipo, fecha_recepcion, plazo_respuesta) VALUES (?, ?, ?, ?)",
                cliente, tipo, recepcion, plazo);
    }

    private int filas() {
        return jdbc.queryForObject("SELECT count(*) FROM tributario_schema.notificaciones_sol WHERE cliente_id = ?", Integer.class, cliente);
    }

    @Test
    void laTablaTieneLasColumnasDelDiseno() {
        List<String> columnas = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'tributario_schema' AND table_name = 'notificaciones_sol'
                """, String.class);

        assertThat(columnas).containsExactlyInAnyOrder("id", "cliente_id", "tipo", "fecha_recepcion", "plazo_respuesta",
                "estado", "fecha_atencion", "registrado_en");
    }

    @Test
    void unaFilaNuevaNaceConEstadoPendienteYSinFechaDeAtencion() {
        insertar("ESQUELA", RECEPCION, RECEPCION.plusDays(15));

        assertThat(jdbc.queryForObject("SELECT estado FROM tributario_schema.notificaciones_sol WHERE cliente_id = ?", String.class, cliente))
                .isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject("SELECT fecha_atencion FROM tributario_schema.notificaciones_sol WHERE cliente_id = ?", LocalDate.class, cliente))
                .isNull();
    }

    @Test
    void soloAdmiteEsquelaCartaInductivaYComunicado() {
        assertThat(insertar("ESQUELA", RECEPCION, RECEPCION)).isEqualTo(1);
        assertThat(insertar("CARTA_INDUCTIVA", RECEPCION, RECEPCION)).isEqualTo(1);
        assertThat(insertar("COMUNICADO", RECEPCION, RECEPCION)).isEqualTo(1);

        assertThatThrownBy(() -> insertar("OTRO", RECEPCION, RECEPCION)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(filas()).isEqualTo(3);
    }

    @Test
    void sinPlazoDeRespuestaNoSeGuarda() {
        assertThatThrownBy(() -> insertar("ESQUELA", RECEPCION, null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(filas()).isZero();
    }

    @Test
    void elPlazoNoPuedeSerAnteriorALaRecepcion() {
        assertThatThrownBy(() -> insertar("ESQUELA", RECEPCION, RECEPCION.minusDays(1))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(filas()).isZero();
    }

    @Test
    void elEstadoSoloAdmitePendienteOAtendida() {
        insertar("ESQUELA", RECEPCION, RECEPCION.plusDays(5));

        assertThatThrownBy(() -> jdbc.update("UPDATE tributario_schema.notificaciones_sol SET estado = 'VENCIDA' WHERE cliente_id = ?", cliente))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.update("UPDATE tributario_schema.notificaciones_sol SET estado = 'ATENDIDA' WHERE cliente_id = ?", cliente)).isEqualTo(1);
    }

    @Test
    void laEntidadGuardaYLeeLaNotificacionConSusEnumeraciones() {
        NotificacionSol n = new NotificacionSol();
        n.setClienteId(cliente);
        n.setTipo(TipoNotificacion.CARTA_INDUCTIVA);
        n.setFechaRecepcion(RECEPCION);
        n.setPlazoRespuesta(RECEPCION.plusDays(30));

        NotificacionSol guardada = repositorio.saveAndFlush(n);
        NotificacionSol leida = repositorio.findById(guardada.getId()).orElseThrow();

        assertThat(leida.getTipo()).isEqualTo(TipoNotificacion.CARTA_INDUCTIVA);
        assertThat(leida.getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(leida.getPlazoRespuesta()).isEqualTo(RECEPCION.plusDays(30));
        assertThat(leida.getRegistradoEn()).isNotNull();
    }

    @Test
    void alBorrarElClienteSeBorranSusNotificaciones() {
        insertar("ESQUELA", RECEPCION, RECEPCION.plusDays(5));
        assertThat(filas()).isEqualTo(1);

        jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", cliente);

        assertThat(filas()).isZero();
    }
}

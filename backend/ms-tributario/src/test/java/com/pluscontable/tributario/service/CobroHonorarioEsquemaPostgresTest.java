package com.pluscontable.tributario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.dto.CobroHonorarioDto;
import com.pluscontable.tributario.dto.ProgramacionCobroDto;
import com.pluscontable.tributario.dto.ProgramacionCobroEntradaDto;
import com.pluscontable.tributario.model.Periodicidad;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
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
 * HU-28 · Tareas 132 y 134: las tablas {@code programaciones_cobro} y {@code cobros_honorarios} que crea schema.sql y la
 * programación de punta a punta contra PostgreSQL de pruebas. Usa clientes ficticios que se borran al terminar (lo demás en cascada).
 */
@SpringBootTest
class CobroHonorarioEsquemaPostgresTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired CobroHonorarioService service;

    private final List<String> rucs = new ArrayList<>();

    @AfterEach
    void limpiar() {
        for (String ruc : rucs) {
            jdbc.update("DELETE FROM tributario_schema.clientes WHERE ruc = ?", ruc);
        }
    }

    private long crearCliente() {
        String ruc = RucFicticio.nuevo();
        rucs.add(ruc);
        jdbc.update("INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario, activo) VALUES (?, ?, 'MYPE', true)",
                ruc, "Cliente ficticio (cobros)");
        return jdbc.queryForObject("SELECT id FROM tributario_schema.clientes WHERE ruc = ?", Long.class, ruc);
    }

    private void crearAcuerdo(long clienteId, String honorario) {
        LocalDate inicio = LocalDate.of(2026, 1, 15);
        jdbc.update("INSERT INTO tributario_schema.acuerdos_servicio (cliente_id, honorario_mensual, honorario_desde, fecha_inicio) VALUES (?, ?, ?, ?)",
                clienteId, new BigDecimal(honorario), inicio, inicio);
    }

    private long programacionMensual(long clienteId) {
        jdbc.update("INSERT INTO tributario_schema.programaciones_cobro (cliente_id, periodicidad) VALUES (?, 'MENSUAL')", clienteId);
        return jdbc.queryForObject("SELECT id FROM tributario_schema.programaciones_cobro WHERE cliente_id = ?", Long.class, clienteId);
    }

    private int contar(long clienteId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.cobros_honorarios WHERE cliente_id = ?", Integer.class, clienteId);
    }

    @Test
    void programarGuardaLosCobrosYSeVenComoPendientesDelCliente() {
        long cliente = crearCliente();
        crearAcuerdo(cliente, "450.00");

        ProgramacionCobroDto r = service.programar(cliente, new ProgramacionCobroEntradaDto(Periodicidad.MENSUAL));

        assertThat(r.cobrosGenerados()).isEqualTo(12);
        assertThat(contar(cliente)).isEqualTo(12);
        List<CobroHonorarioDto> pendientes = service.pendientes(cliente);
        assertThat(pendientes).hasSize(12).allSatisfy(c -> {
            assertThat(c.monto()).isEqualByComparingTo("450.00");
            assertThat(c.fechaCobro().getDayOfMonth()).isEqualTo(15);
        });
        assertThat(pendientes).extracting(CobroHonorarioDto::fechaCobro).isSorted();
    }

    @Test
    void volverAProgramarNoDuplicaYCambiarAQuincenalNoCobraDobleNiChocaEnElDia15() {
        long cliente = crearCliente();
        crearAcuerdo(cliente, "450.00");
        service.programar(cliente, new ProgramacionCobroEntradaDto(Periodicidad.MENSUAL));

        ProgramacionCobroDto otraVez = service.programar(cliente, new ProgramacionCobroEntradaDto(Periodicidad.MENSUAL));
        assertThat(otraVez.cobrosGenerados()).isZero();
        assertThat(contar(cliente)).isEqualTo(12);

        // Los cobros mensuales caen el 15, igual que la primera quincena: se reemplazan sin violar la unicidad.
        ProgramacionCobroDto quincenal = service.programar(cliente, new ProgramacionCobroEntradaDto(Periodicidad.QUINCENAL));
        assertThat(quincenal.cobrosGenerados()).isEqualTo(24);
        assertThat(contar(cliente)).isEqualTo(24);
        assertThat(jdbc.queryForObject("SELECT periodicidad FROM tributario_schema.programaciones_cobro WHERE cliente_id = ?",
                String.class, cliente)).isEqualTo("QUINCENAL");
        assertThat(jdbc.queryForObject("SELECT SUM(monto) FROM tributario_schema.cobros_honorarios WHERE cliente_id = ?",
                BigDecimal.class, cliente)).isEqualByComparingTo("5400.00");
    }

    @Test
    void sinAcuerdoNoSeGuardaNiLaProgramacionNiLosCobros() {
        long cliente = crearCliente();

        assertThatThrownBy(() -> service.programar(cliente, new ProgramacionCobroEntradaDto(Periodicidad.MENSUAL)))
                .isInstanceOf(ValidationException.class);
        assertThat(contar(cliente)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.programaciones_cobro WHERE cliente_id = ?",
                Integer.class, cliente)).isZero();
    }

    @Test
    void laTablaRechazaFechasRepetidasMontosNoPositivosYValoresFueraDeLista() {
        long cliente = crearCliente();
        long prog = programacionMensual(cliente);
        String insertar = "INSERT INTO tributario_schema.cobros_honorarios (cliente_id, programacion_id, fecha_cobro, monto, estado) VALUES (?, ?, ?, ?, ?)";
        LocalDate fecha = LocalDate.of(2026, 12, 15);

        jdbc.update(insertar, cliente, prog, fecha, new BigDecimal("100.00"), "PENDIENTE");
        assertThatThrownBy(() -> jdbc.update(insertar, cliente, prog, fecha, new BigDecimal("100.00"), "PENDIENTE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, cliente, prog, fecha.plusDays(1), BigDecimal.ZERO, "PENDIENTE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(insertar, cliente, prog, fecha.plusDays(2), new BigDecimal("1.00"), "INVENTADO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE tributario_schema.programaciones_cobro SET periodicidad = 'SEMANAL' WHERE id = ?", prog))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO tributario_schema.programaciones_cobro (cliente_id, periodicidad) VALUES (?, 'QUINCENAL')", cliente))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void borrarElClienteBorraSuProgramacionYSusCobros() {
        long cliente = crearCliente();
        crearAcuerdo(cliente, "300.00");
        service.programar(cliente, new ProgramacionCobroEntradaDto(Periodicidad.QUINCENAL));

        jdbc.update("DELETE FROM tributario_schema.clientes WHERE id = ?", cliente);

        assertThat(contar(cliente)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.programaciones_cobro WHERE cliente_id = ?",
                Integer.class, cliente)).isZero();
    }
}

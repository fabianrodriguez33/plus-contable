package com.pluscontable.tributario.obligacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pluscontable.tributario.dto.CrearObligacionDto;
import com.pluscontable.tributario.service.ObligacionProgramacionService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** Se ejecuta únicamente contra la base de pruebas indicada por el POM. */
@SpringBootTest
class Hu07PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObligacionProgramacionService programacion;

    @Test
    @Transactional
    void cargaCronogramaYGuardaObligacionSinTocarDeclaracion() {
        Long clienteId = jdbc.queryForObject("""
                INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario)
                VALUES ('20999999999', 'Cliente HU-07 de prueba', 'GENERAL') RETURNING id
                """, Long.class);

        var creada = programacion.crear(new CrearObligacionDto(clienteId, TipoObligacion.IGV,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 16)));

        assertThat(creada.id()).isNotNull();
        assertThat(creada.estado().name()).isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM tributario_schema.obligaciones
                WHERE cliente_id = ? AND tipo_obligacion = 'IGV' AND periodo = DATE '2026-01-01'
                """, Integer.class, clienteId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM tributario_schema.cronograma_tributario
                WHERE periodo_tributario BETWEEN DATE '2026-01-01' AND DATE '2026-12-01'
                """, Integer.class)).isGreaterThanOrEqualTo(84);
    }

    @Test
    @Transactional
    void indiceImpideDuplicarClienteTipoYPeriodo() {
        Long clienteId = jdbc.queryForObject("""
                INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario)
                VALUES ('20999999998', 'Cliente HU-07 de unicidad', 'GENERAL') RETURNING id
                """, Long.class);
        String sql = """
                INSERT INTO tributario_schema.obligaciones
                    (cliente_id, tipo_obligacion, periodo, fecha_vencimiento)
                VALUES (?, 'IGV', DATE '2026-01-01', DATE '2026-02-16')
                """;
        jdbc.update(sql, clienteId);
        assertThatThrownBy(() -> jdbc.update(sql, clienteId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

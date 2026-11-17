package com.pluscontable.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** HU-38 (tarea 94): tras arrancar con schema.sql existen el rol CLIENTE_MYPE y usuarios.cliente_id (requiere PostgreSQL). */
@SpringBootTest
class ClienteMypeEsquemaPostgresTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void elRolClienteMypeSeSiembra() {
        List<String> roles = jdbc.queryForList("SELECT nombre FROM auth_schema.roles", String.class);

        assertThat(roles).contains("ADMIN", "CONTADOR", "CLIENTE_MYPE");
    }

    @Test
    void usuariosTieneLaColumnaClienteIdSinClaveForanea() {
        Integer columnas = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'auth_schema' AND table_name = 'usuarios' AND column_name = 'cliente_id'
                """, Integer.class);
        Integer foraneas = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_schema = 'auth_schema' AND table_name = 'usuarios' AND constraint_type = 'FOREIGN KEY'
                """, Integer.class);

        assertThat(columnas).isEqualTo(1);
        assertThat(foraneas).isZero();
    }
}

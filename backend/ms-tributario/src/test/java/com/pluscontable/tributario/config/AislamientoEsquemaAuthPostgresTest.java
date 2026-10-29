package com.pluscontable.tributario.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * HT-01 (tarea 5, PARCIAL): MODELO de permisos en PostgreSQL, no el despliegue actual.
 *
 * <p>Qué prueba: que un rol con acceso solo a {@code tributario_schema} puede leer ese esquema y recibe
 * "permission denied" al leer {@code auth_schema}. Es la configuración que habría que desplegar (un usuario
 * por servicio, tarea 4).
 *
 * <p>Qué NO prueba: que hoy esté desplegada. Ambos servicios se conectan como {@code postgres}
 * (superusuario) con las mismas variables DB_USER y DB_PASSWORD, y la base no rechazaría esa consulta.
 *
 * <p>Todo ocurre en una transacción que se revierte: el rol, el esquema auxiliar y la tabla de apoyo no
 * quedan en la base. Requiere que el usuario de pruebas pueda crear roles (en la base desechable lo es);
 * si no puede, la prueba se omite en vez de fallar.
 */
@SpringBootTest
class AislamientoEsquemaAuthPostgresTest {

    private static final String ROL = "rol_tributario_prueba";

    @Autowired JdbcTemplate jdbc;

    @Test
    @Transactional
    void unRolLimitadoAlEsquemaTributarioNoPuedeLeerAuthSchema() {
        Boolean puedeCrearRoles = jdbc.queryForObject(
                "SELECT rolsuper OR rolcreaterole FROM pg_roles WHERE rolname = current_user", Boolean.class);
        assumeTrue(Boolean.TRUE.equals(puedeCrearRoles), "el usuario de pruebas no puede crear roles");

        jdbc.execute("CREATE SCHEMA IF NOT EXISTS auth_schema");
        jdbc.execute("CREATE TABLE auth_schema.tabla_aislamiento_prueba (id INT)");
        jdbc.execute("CREATE ROLE " + ROL + " NOLOGIN");
        jdbc.execute("GRANT USAGE ON SCHEMA tributario_schema TO " + ROL);
        jdbc.execute("GRANT SELECT ON ALL TABLES IN SCHEMA tributario_schema TO " + ROL);

        jdbc.execute("SET LOCAL ROLE " + ROL);

        Integer clientes = jdbc.queryForObject("SELECT COUNT(*) FROM tributario_schema.clientes", Integer.class);
        assertThat(clientes).as("con el rol limitado sí se lee tributario_schema").isNotNull();

        assertThatThrownBy(() -> jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_schema.tabla_aislamiento_prueba", Integer.class))
                .satisfies(error -> {
                    Throwable causa = NestedExceptionUtils.getMostSpecificCause(error);
                    assertThat(causa).isInstanceOf(SQLException.class);
                    assertThat(((SQLException) causa).getSQLState()).as("42501 = permiso denegado").isEqualTo("42501");
                });
    }
}

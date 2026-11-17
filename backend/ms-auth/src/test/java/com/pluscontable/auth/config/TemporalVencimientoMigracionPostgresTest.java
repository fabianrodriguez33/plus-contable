package com.pluscontable.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * HU-37: las cuentas creadas antes de que existiera el vencimiento de la temporal quedaron con {@code temporal_vence_en} en null,
 * es decir, sin vencimiento. La actualización de {@code schema.sql} les pone la fecha de vencimiento AHORA (criterio más seguro:
 * no se sabe cuándo se creó la temporal, así que no se le da más tiempo; el contador genera otra con «Restablecer acceso»).
 * Se simula una base anterior y se vuelve a correr {@code schema.sql} (requiere PostgreSQL).
 */
@SpringBootTest
class TemporalVencimientoMigracionPostgresTest {

    private static final String PREFIJO = "itest-hu37-mig-";

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @BeforeEach
    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM auth_schema.usuarios WHERE username LIKE ?", PREFIJO + "%");
    }

    private void usuario(String nombre, boolean debeCambiar, String vence) {
        jdbc.update("INSERT INTO auth_schema.usuarios (username, password_hash, nombre_completo, debe_cambiar_password, temporal_vence_en) "
                + "VALUES (?, '$2a$10$hashDePruebaQueNoSirveParaEntrar', 'Cuenta de prueba', ?, " + vence + ")", PREFIJO + nombre, debeCambiar);
    }

    private LocalDateTime vence(String nombre) {
        Timestamp t = jdbc.queryForObject("SELECT temporal_vence_en FROM auth_schema.usuarios WHERE username = ?", Timestamp.class, PREFIJO + nombre);
        return t == null ? null : t.toLocalDateTime();
    }

    private void correrSchema() {
        ResourceDatabasePopulator p = new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
        p.execute(dataSource);
    }

    @Test
    void laCuentaConCambioPendienteYSinVencimientoQuedaVencidaAhora() {
        usuario("pendiente-sin-fecha", true, "NULL");
        LocalDateTime antes = LocalDateTime.now();

        correrSchema();

        LocalDateTime vence = vence("pendiente-sin-fecha");
        assertThat(vence).isNotNull();
        assertThat(vence).isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(1)).isAfter(antes.minusMinutes(1));
    }

    @Test
    void noTocaLasCuentasQueYaCambiaronSuContraseniaNiLasQueYaTienenFecha() {
        usuario("ya-cambio", false, "NULL");
        usuario("con-fecha-futura", true, "NOW() + INTERVAL '30 hours'");
        usuario("con-fecha-pasada", true, "NOW() - INTERVAL '3 days'");
        LocalDateTime futura = vence("con-fecha-futura");
        LocalDateTime pasada = vence("con-fecha-pasada");

        correrSchema();

        assertThat(vence("ya-cambio")).isNull();
        assertThat(vence("con-fecha-futura")).isEqualTo(futura);
        assertThat(vence("con-fecha-pasada")).isEqualTo(pasada);
    }

    @Test
    void esIdempotenteVolverACorrerNoMueveLaFecha() {
        usuario("pendiente-sin-fecha", true, "NULL");
        correrSchema();
        LocalDateTime primera = vence("pendiente-sin-fecha");

        correrSchema();
        correrSchema();

        assertThat(vence("pendiente-sin-fecha")).isEqualTo(primera);
    }
}

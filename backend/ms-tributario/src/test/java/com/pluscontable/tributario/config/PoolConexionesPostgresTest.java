package com.pluscontable.tributario.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * Despliegue gratis (Neon): la base se suspende por inactividad y corta las conexiones abiertas. Se carga el pool con la
 * configuracion REAL de application.yml y se comprueba que una conexion cortada desde el servidor no rompe la siguiente consulta.
 * Base: la de las pruebas (DB_URL, DB_USER y DB_PASSWORD del pom), nunca plus_contable_db.
 */
class PoolConexionesPostgresTest {
    private static final String URL = System.getenv("DB_URL");
    private static final String USUARIO = System.getenv("DB_USER");
    private static final String CLAVE = System.getenv("DB_PASSWORD");

    private static HikariDataSource pool() throws Exception {
        var entorno = new StandardEnvironment();
        for (var fuente : new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))) {
            entorno.getPropertySources().addLast(fuente);
        }
        var pool = new HikariDataSource();
        new Binder(ConfigurationPropertySources.get(entorno), new PropertySourcesPlaceholdersResolver(entorno))
                .bind("spring.datasource.hikari", Bindable.ofInstance(pool));
        pool.setJdbcUrl(URL);
        pool.setUsername(USUARIO);
        pool.setPassword(CLAVE);
        return pool;
    }

    @Test
    void lasConexionesVivenMenosQueLaSuspensionDeLaBaseYElPoolSeVaciaSolo() throws Exception {
        try (var pool = pool()) {
            assertThat(pool.getMaxLifetime()).isBetween(60_000L, 270_000L);
            assertThat(pool.getMinimumIdle()).isZero();
            assertThat(pool.getIdleTimeout()).isPositive().isLessThan(pool.getMaxLifetime());
            assertThat(pool.getValidationTimeout()).isBetween(1_000L, 10_000L);
            assertThat(pool.getConnectionTimeout()).isGreaterThanOrEqualTo(20_000L);
        }
    }

    @Test
    void unaConexionCortadaDesdeElServidorSeReemplazaSinFallarLaConsulta() throws Exception {
        try (var pool = pool(); Connection admin = DriverManager.getConnection(URL, USUARIO, CLAVE)) {
            int pid;
            try (Connection c = pool.getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT pg_backend_pid()")) {
                r.next();
                pid = r.getInt(1);
            }
            try (Statement s = admin.createStatement()) {
                s.execute("SELECT pg_terminate_backend(" + pid + ")"); // lo que hace Neon al suspender el computo
            }
            Thread.sleep(800); // Hikari no revalida una conexion usada hace menos de 500 ms

            try (Connection c = pool.getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT pg_backend_pid()")) {
                r.next();
                assertThat(r.getInt(1)).isNotEqualTo(pid);
            }
        }
    }
}

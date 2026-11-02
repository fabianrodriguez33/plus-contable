package com.pluscontable.tributario.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * HT-01 (tarea 5): con los usuarios por servicio de {@code db/roles-por-servicio.sql}, la BASE rechaza que el usuario de
 * ms-tributario consulte {@code auth_schema} (y al revés), y cada servicio puede seguir arrancando con su usuario.
 *
 * <p>Qué hace: crea una base temporal propia dentro del servidor de pruebas, le aplica los {@code schema.sql} reales de
 * ms-auth y ms-tributario como administrador (igual que un despliegue ya existente), ejecuta el script de roles y se
 * conecta con los usuarios nuevos. Al terminar elimina la base temporal y los usuarios. No toca la base indicada por
 * {@code DB_URL} más que para crear y borrar la temporal, así que sirve cualquier base de pruebas.
 *
 * <p>Requiere que el usuario de pruebas pueda crear bases y usuarios (en la base desechable lo es); si no puede, se omite.
 * Maven ejecuta las pruebas desde el módulo; una IDE puede hacerlo desde la raíz del repositorio.
 */
class AislamientoPorUsuarioPostgresTest {

    private static final String SQLSTATE_PERMISO_DENEGADO = "42501";

    @Test
    void cadaUsuarioSoloUsaSuEsquemaYLaBaseRechazaElOtro() throws Exception {
        String urlAdmin = variable("DB_URL");
        String usuarioAdmin = variable("DB_USER");
        String claveAdmin = variable("DB_PASSWORD");

        String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String baseTemporal = "pc_aislamiento_" + sufijo;
        String usuarioAuth = "ms_auth_t" + sufijo;
        String usuarioTributario = "ms_tributario_t" + sufijo;
        String claveAuth = "a-" + UUID.randomUUID();
        String claveTributario = "t-" + UUID.randomUUID();

        Path schemaAuth = archivo("backend/ms-auth/src/main/resources/schema.sql", "../ms-auth/src/main/resources/schema.sql");
        Path schemaTributario = archivo("backend/ms-tributario/src/main/resources/schema.sql", "src/main/resources/schema.sql");
        Path scriptRoles = archivo("db/roles-por-servicio.sql", "../../db/roles-por-servicio.sql");

        try (Connection servidor = DriverManager.getConnection(urlAdmin, usuarioAdmin, claveAdmin)) {
            assumeTrue(puedeCrearBasesYUsuarios(servidor), "el usuario de pruebas no puede crear bases ni usuarios");
            ejecutar(servidor, "CREATE DATABASE " + baseTemporal);
        }

        String urlTemporal = reemplazarBase(urlAdmin, baseTemporal);
        try {
            try (Connection admin = DriverManager.getConnection(urlTemporal, usuarioAdmin, claveAdmin)) {
                // despliegue "existente": esquemas y tablas creados antes por el administrador
                ScriptUtils.executeSqlScript(admin, new FileSystemResource(schemaAuth));
                ScriptUtils.executeSqlScript(admin, new FileSystemResource(schemaTributario));
                ejecutar(admin, "INSERT INTO tributario_schema.clientes (ruc, razon_social, regimen_tributario) "
                        + "VALUES ('20100070970', 'Cliente de prueba', 'GENERAL')");
                ejecutar(admin, "INSERT INTO auth_schema.usuarios (username, password_hash, nombre_completo) "
                        + "VALUES ('prueba', 'hash-de-prueba', 'Usuario de prueba')");

                aplicarRoles(admin, scriptRoles, usuarioAuth, claveAuth, usuarioTributario, claveTributario);
                aplicarRoles(admin, scriptRoles, usuarioAuth, claveAuth, usuarioTributario, claveTributario); // idempotente

                assertThat(propietarios(admin, "tributario_schema")).containsExactly(usuarioTributario);
                assertThat(propietarios(admin, "auth_schema")).containsExactly(usuarioAuth);
            }

            try (Connection comoTributario = DriverManager.getConnection(urlTemporal, usuarioTributario, claveTributario)) {
                assertThat(contar(comoTributario, "tributario_schema.clientes")).as("lee su propio esquema").isEqualTo(1);
                assertPermisoDenegado(() -> contar(comoTributario, "auth_schema.usuarios"));
                assertPermisoDenegado(() -> ejecutar(comoTributario, "CREATE TABLE auth_schema.intruso (id INT)"));
                assertPermisoDenegado(() -> ejecutar(comoTributario, "DELETE FROM auth_schema.usuarios"));
                assertThatCode(() -> ScriptUtils.executeSqlScript(comoTributario, new FileSystemResource(schemaTributario)))
                        .as("schema.sql de ms-tributario se puede ejecutar de nuevo con su usuario, como en cada arranque")
                        .doesNotThrowAnyException();
                assertThat(contar(comoTributario, "tributario_schema.clientes")).isEqualTo(1);
            }

            try (Connection comoAuth = DriverManager.getConnection(urlTemporal, usuarioAuth, claveAuth)) {
                assertThat(contar(comoAuth, "auth_schema.usuarios")).as("lee su propio esquema").isEqualTo(1);
                assertPermisoDenegado(() -> contar(comoAuth, "tributario_schema.clientes"));
                assertPermisoDenegado(() -> ejecutar(comoAuth, "DROP TABLE tributario_schema.clientes CASCADE"));
                assertThatCode(() -> ScriptUtils.executeSqlScript(comoAuth, new FileSystemResource(schemaAuth)))
                        .as("schema.sql de ms-auth se puede ejecutar de nuevo con su usuario, como en cada arranque")
                        .doesNotThrowAnyException();
            }
        } finally {
            try (Connection servidor = DriverManager.getConnection(urlAdmin, usuarioAdmin, claveAdmin)) {
                ejecutar(servidor, "DROP DATABASE IF EXISTS " + baseTemporal + " WITH (FORCE)");
                ejecutar(servidor, "DROP ROLE IF EXISTS " + usuarioAuth);
                ejecutar(servidor, "DROP ROLE IF EXISTS " + usuarioTributario);
            }
        }
    }

    @Test
    void elScriptDeRolesExigeUsuarioYContrasenaDeCadaServicio() throws Exception {
        String urlAdmin = variable("DB_URL");
        Path scriptRoles = archivo("db/roles-por-servicio.sql", "../../db/roles-por-servicio.sql");
        try (Connection c = DriverManager.getConnection(urlAdmin, variable("DB_USER"), variable("DB_PASSWORD"))) {
            c.setAutoCommit(false);
            // sin variables rolesvc.* el script debe fallar y no crear nada
            assertThatThrownBy(() -> ejecutar(c, Files.readString(scriptRoles, StandardCharsets.UTF_8)))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Falta el usuario o la contrasena");
            c.rollback();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static void aplicarRoles(Connection admin, Path script, String usuarioAuth, String claveAuth,
                                     String usuarioTributario, String claveTributario) throws Exception {
        try (PreparedStatement p = admin.prepareStatement("SELECT set_config(?, ?, false)")) {
            for (String[] par : List.of(new String[] {"rolesvc.auth_user", usuarioAuth},
                    new String[] {"rolesvc.auth_password", claveAuth},
                    new String[] {"rolesvc.tributario_user", usuarioTributario},
                    new String[] {"rolesvc.tributario_password", claveTributario})) {
                p.setString(1, par[0]);
                p.setString(2, par[1]);
                p.execute();
            }
        }
        ejecutar(admin, Files.readString(script, StandardCharsets.UTF_8));
    }

    private static List<String> propietarios(Connection c, String esquema) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "SELECT DISTINCT tableowner FROM pg_tables WHERE schemaname = ? ORDER BY 1")) {
            p.setString(1, esquema);
            try (ResultSet rs = p.executeQuery()) {
                List<String> duenos = new java.util.ArrayList<>();
                while (rs.next()) duenos.add(rs.getString(1));
                return duenos;
            }
        }
    }

    private static int contar(Connection c, String tabla) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + tabla)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void ejecutar(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static void assertPermisoDenegado(ThrowingRunnable accion) {
        assertThatThrownBy(accion::run).isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).as("42501 = permiso denegado")
                        .isEqualTo(SQLSTATE_PERMISO_DENEGADO));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static boolean puedeCrearBasesYUsuarios(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT rolsuper OR (rolcreaterole AND rolcreatedb) "
                     + "FROM pg_roles WHERE rolname = current_user")) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    private static String reemplazarBase(String url, String base) {
        int consulta = url.indexOf('?');
        String sinConsulta = consulta < 0 ? url : url.substring(0, consulta);
        return sinConsulta.substring(0, sinConsulta.lastIndexOf('/') + 1) + base + (consulta < 0 ? "" : url.substring(consulta));
    }

    private static String variable(String nombre) {
        String valor = System.getenv(nombre);
        assertThat(valor).as("variable de entorno " + nombre + " (la define el POM con test.db.*)").isNotBlank();
        return valor;
    }

    private static Path archivo(String desdeRaiz, String desdeModulo) throws IOException {
        for (String candidato : List.of(desdeModulo, desdeRaiz)) {
            Path p = Path.of(candidato);
            if (Files.exists(p)) return p;
        }
        throw new IOException("No se encontró " + desdeRaiz + " (se busca desde el módulo y desde la raíz del repositorio)");
    }
}

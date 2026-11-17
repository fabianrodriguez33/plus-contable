package com.pluscontable.tributario.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * HT-02 · Criterio 4 (T108): la llave de cifrado se guarda aparte de los datos cifrados. Se comprueba que la llave viene del
 * entorno y no de un archivo del repositorio, que ninguna tabla tiene un campo para la llave y que la plantilla de variables
 * no lleva valor. El valor de la llave no se escribe en ningún archivo de prueba: se lee de la variable de entorno que el
 * POM define solo para las pruebas.
 */
@SpringBootTest
class LlaveCifradoAparteTest {

    @Autowired Environment entorno;
    @Autowired JdbcTemplate jdbc;

    private static String leer(String ruta) throws IOException {
        return Files.readString(Path.of(ruta), StandardCharsets.UTF_8);
    }

    @Test
    void laLlaveYElIvVienenDeVariablesDeEntornoYNoDeUnValorEscritoEnLaConfiguracion() throws IOException {
        String yml = leer("src/main/resources/application.yml");

        assertThat(yml).contains("secret-key: ${AES_SECRET_KEY}").contains("legacy-iv: ${AES_IV}");
        assertThat(entorno.getProperty("app.aes.secret-key")).isEqualTo(System.getenv("AES_SECRET_KEY")).isNotBlank();
        assertThat(entorno.getProperty("app.aes.legacy-iv")).isEqualTo(System.getenv("AES_IV")).isNotBlank();
    }

    @Test
    void ningunArchivoDeConfiguracionDelServicioLlevaLaLlaveEscrita() throws IOException {
        String llave = System.getenv("AES_SECRET_KEY");
        try (var archivos = Files.walk(Path.of("src/main/resources"))) {
            for (Path p : archivos.filter(Files::isRegularFile).toList()) {
                assertThat(leer(p.toString())).as(p.toString()).doesNotContain(llave);
            }
        }
    }

    @Test
    void laPlantillaDeVariablesNoTraeValoresYElArchivoRealNoSeSube() throws IOException {
        List<String> plantilla = Files.readAllLines(Path.of("../../.env.example"), StandardCharsets.UTF_8);
        List<String> ignorados = Files.readAllLines(Path.of("../../.gitignore"), StandardCharsets.UTF_8);

        assertThat(plantilla).contains("AES_SECRET_KEY=", "AES_IV=");
        assertThat(ignorados).contains(".env");
    }

    @Test
    void ningunaTablaTieneUnCampoParaLaLlave() {
        List<String> columnas = jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'tributario_schema' AND table_name = 'credenciales_sol'
                ORDER BY ordinal_position
                """, String.class);
        List<String> raras = jdbc.queryForList("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE table_schema = 'tributario_schema'
                  AND column_name ~* '(llave|secret|aes|iv$|key)'
                  AND column_name NOT IN ('clave_sol_cifrada')
                """, String.class);

        assertThat(columnas).containsExactly("id", "cliente_id", "usuario_sol_cifrado", "clave_sol_cifrada", "actualizado_en");
        assertThat(raras).isEmpty();
    }
}

package com.pluscontable.tributario.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * HT-01 (tarea 5, PARCIAL): aislamiento de ms-tributario respecto de {@code auth_schema} EN EL CÓDIGO.
 *
 * <p>Qué prueba: ninguna entidad de ms-tributario sale de {@code tributario_schema} y ningún archivo de
 * código o recurso de ms-tributario menciona {@code auth_schema}.
 *
 * <p>Qué NO prueba: que la base de datos rechace la consulta. El despliegue actual usa el usuario
 * {@code postgres} (superusuario) para los dos servicios, así que la base no lo impediría. Eso depende de
 * crear un usuario por servicio con permisos separados (tarea 4). Ver también
 * {@code AislamientoEsquemaAuthPostgresTest}, que prueba el modelo de permisos, no el despliegue.
 */
class AislamientoEsquemaAuthCodigoTest {

    private static final String PAQUETE = "com.pluscontable.tributario";
    private static final String ESQUEMA_PROPIO = "tributario_schema";
    private static final String ESQUEMA_AJENO = "auth_schema";

    @Test
    void todasLasEntidadesPertenecenAlEsquemaTributario() throws Exception {
        var buscador = new ClassPathScanningCandidateComponentProvider(false);
        buscador.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        List<String> fuera = new ArrayList<>();
        int entidades = 0;
        for (BeanDefinition definicion : buscador.findCandidateComponents(PAQUETE)) {
            Class<?> clase = Class.forName(definicion.getBeanClassName());
            entidades++;
            Table tabla = clase.getAnnotation(Table.class);
            if (tabla == null || !ESQUEMA_PROPIO.equals(tabla.schema())) {
                fuera.add(clase.getSimpleName());
            }
        }

        assertThat(entidades).as("entidades encontradas").isGreaterThan(0);
        assertThat(fuera).as("entidades que no declaran " + ESQUEMA_PROPIO).isEmpty();
    }

    @Test
    void ningunArchivoDeMsTributarioMencionaElEsquemaDeAuth() throws IOException {
        Path raiz = ubicarCodigoFuente();
        List<String> menciones = new ArrayList<>();
        try (Stream<Path> archivos = Files.walk(raiz)) {
            for (Path archivo : archivos.filter(Files::isRegularFile).filter(AislamientoEsquemaAuthCodigoTest::esTexto).toList()) {
                String contenido = Files.readString(archivo, StandardCharsets.UTF_8);
                if (contenido.toLowerCase().contains(ESQUEMA_AJENO)) {
                    menciones.add(raiz.relativize(archivo).toString());
                }
            }
        }

        assertThat(menciones).as("archivos de ms-tributario que mencionan " + ESQUEMA_AJENO).isEmpty();
    }

    private static boolean esTexto(Path archivo) {
        String nombre = archivo.getFileName().toString();
        return nombre.endsWith(".java") || nombre.endsWith(".sql") || nombre.endsWith(".yml")
                || nombre.endsWith(".yaml") || nombre.endsWith(".properties") || nombre.endsWith(".xml");
    }

    /** Maven ejecuta las pruebas desde el módulo; una IDE puede hacerlo desde la raíz del repositorio. */
    private static Path ubicarCodigoFuente() {
        for (String candidato : List.of("src/main", "backend/ms-tributario/src/main")) {
            Path ruta = Path.of(candidato);
            if (Files.isDirectory(ruta)) {
                return ruta;
            }
        }
        throw new IllegalStateException("No se encontró src/main de ms-tributario desde " + Path.of("").toAbsolutePath());
    }
}

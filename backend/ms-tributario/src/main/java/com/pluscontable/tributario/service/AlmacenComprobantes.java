package com.pluscontable.tributario.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * HU-13: guarda en disco el comprobante (PDF) de un pago, igual que los documentos de HU-04: carpeta por cliente y
 * nombre generado. Está en {@code app.storage.dir}, dentro de la subcarpeta {@code pagos}.
 */
@Component
public class AlmacenComprobantes {

    /** Lo guardado: la ruta absoluta (para borrarlo si algo falla), la relativa a {@code app.storage.dir} y los datos del archivo. */
    public record Guardado(Path destino, String rutaRelativa, String nombreArchivo, long tamanoBytes) {
    }

    private final Path baseDir;

    public AlmacenComprobantes(@Value("${app.storage.dir}") String dir) {
        this.baseDir = Path.of(dir).toAbsolutePath().normalize();
    }

    public Guardado guardar(Long clienteId, MultipartFile archivo) {
        Path destino = baseDir.resolve("pagos").resolve(clienteId.toString()).resolve(UUID.randomUUID() + ".pdf");
        try (InputStream in = archivo.getInputStream()) {
            Files.createDirectories(destino.getParent());
            Files.copy(in, destino);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el comprobante", e);
        }
        return new Guardado(destino, baseDir.relativize(destino).toString(), nombreSeguro(archivo.getOriginalFilename()),
                archivo.getSize());
    }

    /** Sin registro el archivo quedaría huérfano: se borra sin fallar si ya no está. */
    public void borrar(Path destino) {
        try {
            Files.deleteIfExists(destino);
        } catch (IOException | RuntimeException ignorada) {
            // no se puede hacer más; el archivo queda en la carpeta del cliente
        }
    }

    private static String nombreSeguro(String original) {
        // solo el nombre, sin la ruta del equipo del usuario (sin Path.of: en Windows falla con caracteres como ':')
        String nombre = original == null ? "" : original.substring(original.replace('\\', '/').lastIndexOf('/') + 1);
        if (nombre.isBlank()) nombre = "comprobante.pdf";
        return nombre.length() > 255 ? nombre.substring(nombre.length() - 255) : nombre;
    }
}

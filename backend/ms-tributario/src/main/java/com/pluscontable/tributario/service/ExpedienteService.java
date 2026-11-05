package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.DocumentoDto;
import com.pluscontable.tributario.model.DocumentoLegal;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.DocumentoRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** HU-04 · Archivar documentos legales en el expediente digital. */
@Service
public class ExpedienteService {

    /** Igual a spring.servlet.multipart.max-file-size; se repite aquí para dar el motivo y para las pruebas. */
    public static final long TAMANO_MAXIMO = 10L * 1024 * 1024;

    private static final byte[] FIRMA_PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final DocumentoRepository documentoRepository;
    private final ClienteRepository clienteRepository;
    private final Path baseDir;

    // shortcut: disco local en app.storage.dir (./uploads); pasar a volumen u objetos si se despliega en varias instancias
    public ExpedienteService(DocumentoRepository documentoRepository, ClienteRepository clienteRepository,
                             @Value("${app.storage.dir}") String dir) {
        this.documentoRepository = documentoRepository;
        this.clienteRepository = clienteRepository;
        this.baseDir = Path.of(dir).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public List<DocumentoDto> listar(Long clienteId) {
        exigirCliente(clienteId);
        return documentoRepository.findByClienteIdOrderByTipoAscVersionDesc(clienteId).stream()
                .map(DocumentoDto::from).toList();
    }

    @Transactional
    public DocumentoDto archivar(Long clienteId, DocumentoLegal.Tipo tipo, LocalDate fechaEmision, MultipartFile archivo) {
        exigirCliente(clienteId);
        validar(tipo, fechaEmision, archivo);

        int version = documentoRepository.findTopByClienteIdAndTipoOrderByVersionDesc(clienteId, tipo)
                .map(d -> d.getVersion() + 1).orElse(1);
        Path destino = baseDir.resolve(clienteId.toString()).resolve(UUID.randomUUID() + ".pdf");
        try (InputStream in = archivo.getInputStream()) {
            Files.createDirectories(destino.getParent());
            Files.copy(in, destino);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo", e);
        }

        DocumentoLegal d = new DocumentoLegal();
        d.setClienteId(clienteId);
        d.setTipo(tipo);
        d.setVersion(version);
        d.setFechaEmision(fechaEmision);
        d.setNombreArchivo(nombreSeguro(archivo.getOriginalFilename()));
        d.setContentType("application/pdf");
        d.setTamanoBytes(archivo.getSize());
        d.setRutaAlmacenamiento(baseDir.relativize(destino).toString());
        try {
            return DocumentoDto.from(documentoRepository.save(d));
        } catch (RuntimeException e) {
            borrarSilencioso(destino); // sin registro el archivo quedaría huérfano
            throw e;
        }
    }

    /** Documento del cliente; si pertenece a otro cliente se responde igual que si no existiera. */
    @Transactional(readOnly = true)
    public DocumentoLegal buscar(Long clienteId, Long documentoId) {
        return documentoRepository.findById(documentoId)
                .filter(d -> d.getClienteId().equals(clienteId))
                .orElseThrow(() -> new EntityNotFoundException("Documento no encontrado"));
    }

    /** El archivo tal cual se subió. */
    public Resource cargar(DocumentoLegal d) {
        Path p = baseDir.resolve(d.getRutaAlmacenamiento()).normalize();
        if (!p.startsWith(baseDir) || !Files.exists(p)) {
            throw new EntityNotFoundException("Archivo no disponible");
        }
        return new PathResource(p);
    }

    private static void validar(DocumentoLegal.Tipo tipo, LocalDate fechaEmision, MultipartFile archivo) {
        List<String> errores = new ArrayList<>();
        if (tipo == null) errores.add("tipo es obligatorio (MINUTA, VIGENCIA_PODERES o FICHA_RUC)");
        if (fechaEmision == null) errores.add("fechaEmision es obligatoria");
        else if (fechaEmision.isAfter(LocalDate.now())) errores.add("fechaEmision no puede ser futura");

        if (archivo == null || archivo.isEmpty()) {
            errores.add("archivo es obligatorio");
        } else if (archivo.getSize() > TAMANO_MAXIMO) {
            errores.add("el archivo supera el máximo de 10 MB");
        } else if (!esPdf(archivo)) {
            errores.add("el archivo no es un PDF");
        }

        if (!errores.isEmpty()) {
            throw new ValidationException("Documento rechazado: " + String.join("; ", errores));
        }
    }

    /** Se exige el tipo declarado y la firma %PDF- del contenido: la extensión sola se puede falsear. */
    private static boolean esPdf(MultipartFile archivo) {
        if (!"application/pdf".equalsIgnoreCase(archivo.getContentType())) return false;
        try (InputStream in = archivo.getInputStream()) {
            return Arrays.equals(in.readNBytes(FIRMA_PDF.length), FIRMA_PDF);
        } catch (IOException e) {
            return false;
        }
    }

    private static String nombreSeguro(String original) {
        // solo el nombre, sin la ruta del equipo del usuario (sin Path.of: en Windows falla con caracteres como ':')
        String nombre = original == null ? "" : original.substring(original.replace('\\', '/').lastIndexOf('/') + 1);
        if (nombre.isBlank()) nombre = "documento.pdf";
        return nombre.length() > 255 ? nombre.substring(nombre.length() - 255) : nombre;
    }

    private static void borrarSilencioso(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // no ocultar el error original de base de datos
        }
    }

    private void exigirCliente(Long clienteId) {
        if (!clienteRepository.existsById(clienteId)) {
            throw new EntityNotFoundException("Cliente no encontrado");
        }
    }
}

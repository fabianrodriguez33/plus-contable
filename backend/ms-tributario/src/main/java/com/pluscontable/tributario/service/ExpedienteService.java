package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.DocumentoDto;
import com.pluscontable.tributario.model.DocumentoLegal;
import com.pluscontable.tributario.repository.DocumentoRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Expediente documental legal: Minuta, Copia Literal, Vigencia de Poderes y Ficha RUC. */
@Service
public class ExpedienteService {

    private static final Set<String> EXTENSIONES = Set.of("pdf", "png", "jpg", "jpeg", "doc", "docx");

    private final DocumentoRepository documentoRepository;
    private final ClienteService clienteService;
    private final Path baseDir;

    public ExpedienteService(DocumentoRepository documentoRepository, ClienteService clienteService,
                             @Value("${app.storage.dir}") String storageDir) {
        this.documentoRepository = documentoRepository;
        this.clienteService = clienteService;
        this.baseDir = Paths.get(storageDir).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public List<DocumentoDto> listar(Long clienteId) {
        clienteService.buscar(clienteId);
        return documentoRepository.findByClienteIdOrderBySubidoEnDesc(clienteId).stream()
                .map(DocumentoDto::from).toList();
    }

    @Transactional
    public DocumentoDto subir(Long clienteId, DocumentoLegal.Tipo tipo, MultipartFile archivo) {
        clienteService.buscar(clienteId);
        if (archivo == null || archivo.isEmpty()) {
            throw new ValidationException("Debe adjuntar un archivo");
        }
        String original = Paths.get(archivo.getOriginalFilename() == null ? "documento" : archivo.getOriginalFilename())
                .getFileName().toString();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!EXTENSIONES.contains(ext)) {
            throw new ValidationException("Formato no permitido. Use: " + String.join(", ", EXTENSIONES));
        }
        try {
            Path dir = baseDir.resolve("cliente-" + clienteId);
            Files.createDirectories(dir);
            Path destino = dir.resolve(UUID.randomUUID() + "." + ext).normalize();
            if (!destino.startsWith(baseDir)) {
                throw new ValidationException("Ruta de archivo inválida");
            }
            archivo.transferTo(destino);

            DocumentoLegal d = new DocumentoLegal();
            d.setClienteId(clienteId);
            d.setTipo(tipo);
            d.setNombreArchivo(original);
            d.setContentType(archivo.getContentType());
            d.setTamanoBytes(archivo.getSize());
            d.setRutaAlmacenamiento(baseDir.relativize(destino).toString());
            return DocumentoDto.from(documentoRepository.save(d));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo", e);
        }
    }

    public DocumentoLegal buscar(Long documentoId) {
        return documentoRepository.findById(documentoId)
                .orElseThrow(() -> new EntityNotFoundException("Documento no encontrado: " + documentoId));
    }

    public Resource cargar(DocumentoLegal d) {
        Path p = baseDir.resolve(d.getRutaAlmacenamiento()).normalize();
        if (!p.startsWith(baseDir) || !Files.exists(p)) {
            throw new EntityNotFoundException("Archivo no disponible en el almacenamiento");
        }
        return new PathResource(p);
    }

    @Transactional
    public void eliminar(Long clienteId, Long documentoId) {
        DocumentoLegal d = buscar(documentoId);
        if (!d.getClienteId().equals(clienteId)) {
            throw new EntityNotFoundException("Documento no encontrado: " + documentoId);
        }
        documentoRepository.delete(d);
        try {
            Files.deleteIfExists(baseDir.resolve(d.getRutaAlmacenamiento()).normalize());
        } catch (IOException ignored) {
            // el registro ya fue eliminado; el archivo huerfano no afecta la consistencia
        }
    }
}

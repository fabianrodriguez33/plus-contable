package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.DocumentoDto;
import com.pluscontable.tributario.model.DocumentoLegal;
import com.pluscontable.tributario.repository.DocumentoRepository;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** HU-04 · Archivar documentos legales en el expediente digital. */
@Service
public class ExpedienteService {

    private final DocumentoRepository documentoRepository;
    private final Path baseDir;

    // shortcut: disco local (app.storage.dir) y 10 MB de spring.servlet.multipart; pendiente de confirmar (NOTAS.md, HU-04)
    public ExpedienteService(DocumentoRepository documentoRepository, @Value("${app.storage.dir}") String dir) {
        this.documentoRepository = documentoRepository;
        this.baseDir = Path.of(dir).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public List<DocumentoDto> listar(Long clienteId) {
        return documentoRepository.findByClienteIdOrderByTipoAscVersionDesc(clienteId).stream()
                .map(DocumentoDto::from).toList();
    }

    @Transactional
    public DocumentoDto archivar(Long clienteId, DocumentoLegal.Tipo tipo, LocalDate fechaEmision, MultipartFile archivo) {
        // TODO Tarea 41: rechazar si fechaEmision es null ("fechaEmision: es obligatoria")
        // TODO Tarea 41: rechazar si no es PDF (content-type application/pdf y cabecera %PDF-), indicando el motivo
        // TODO Tarea 41: version = ultima version del tipo + 1; guardar en baseDir/<clienteId>/<uuid>.pdf
        throw new UnsupportedOperationException("HU-04 pendiente");
    }

    public Resource descargar(Long clienteId, Long documentoId) {
        // TODO Tarea 41: devolver el archivo tal cual se subio; validar que es del cliente y que no sale de baseDir
        throw new UnsupportedOperationException("HU-04 pendiente");
    }
}

package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.DocumentoFaltanteDto;
import com.pluscontable.tributario.dto.DocumentoFaltanteEntradaDto;
import com.pluscontable.tributario.dto.IncidenciaEntradaDto;
import com.pluscontable.tributario.model.DocumentoFaltante;
import com.pluscontable.tributario.model.EstadoDocumentoFaltante;
import com.pluscontable.tributario.model.TipoIncidencia;
import com.pluscontable.tributario.repository.DocumentoFaltanteRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-24 · Verificar documentos faltantes: se registra el documento que falta con su fecha límite (PENDIENTE) y la fecha en que
 * se pidió; al recibirlo pasa a ENTREGADO; si vence sin entrega se muestra SIN_RESPUESTA (no se guarda) y se puede registrar
 * una vez como incidencia de HU-23. Supuestos del diseño previo T165, pendientes de confirmar con Beto: el documento es texto
 * libre de hasta 150 caracteres y la fecha de solicitud, si no se indica, es hoy.
 */
@Service
public class DocumentoFaltanteService {

    /** «Hoy» es el de Lima, aunque el servidor corra en UTC (igual que HU-31). */
    static final ZoneId ZONA = ZoneId.of("America/Lima");
    static final int MAX_DOCUMENTO = 150;
    private static final DateTimeFormatter DD_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DocumentoFaltanteRepository documentos;
    private final ClienteService clientes;
    private final IncidenciaService incidencias;
    private final Clock clock;

    @Autowired
    public DocumentoFaltanteService(DocumentoFaltanteRepository documentos, ClienteService clientes, IncidenciaService incidencias) {
        this(documentos, clientes, incidencias, Clock.system(ZONA));
    }

    DocumentoFaltanteService(DocumentoFaltanteRepository documentos, ClienteService clientes, IncidenciaService incidencias,
                             Clock clock) {
        this.documentos = documentos;
        this.clientes = clientes;
        this.incidencias = incidencias;
        this.clock = clock;
    }

    /** Criterios 1 y 2. 400 con todos los errores juntos; 404 si el cliente no existe. */
    @Transactional
    public DocumentoFaltanteDto registrar(Long clienteId, DocumentoFaltanteEntradaDto dto, String usuario) {
        LocalDate hoy = LocalDate.now(clock);
        LocalDate solicitud = dto == null || dto.fechaSolicitud() == null ? hoy : dto.fechaSolicitud();
        validar(dto, solicitud, hoy);
        clientes.buscar(clienteId);
        DocumentoFaltante d = new DocumentoFaltante();
        d.setClienteId(clienteId);
        d.setDocumento(dto.documento().trim());
        d.setFechaSolicitud(solicitud);
        d.setFechaLimite(dto.fechaLimite());
        d.setRegistradoPor(usuario);
        return DocumentoFaltanteDto.from(documentos.save(d), hoy);
    }

    /** Criterio 5: los documentos del cliente con su estado visible, el de fecha límite más próxima primero. 404 si no existe. */
    @Transactional(readOnly = true)
    public List<DocumentoFaltanteDto> listar(Long clienteId) {
        clientes.buscar(clienteId);
        LocalDate hoy = LocalDate.now(clock);
        return documentos.findByClienteIdOrderByFechaLimiteAscIdAsc(clienteId).stream()
                .map(d -> DocumentoFaltanteDto.from(d, hoy)).toList();
    }

    /** Criterio 3: guarda la fecha de entrega de hoy y pasa a ENTREGADO, aunque ya estuviera sin respuesta. 400 si ya estaba entregado. */
    @Transactional
    public DocumentoFaltanteDto registrarEntrega(Long clienteId, Long documentoId) {
        DocumentoFaltante d = buscar(clienteId, documentoId);
        if (d.getEstado() == DocumentoFaltante.Estado.ENTREGADO) throw new ValidationException("El documento ya fue entregado");
        LocalDate hoy = LocalDate.now(clock);
        d.setEstado(DocumentoFaltante.Estado.ENTREGADO);
        d.setFechaEntrega(hoy);
        return DocumentoFaltanteDto.from(documentos.save(d), hoy);
    }

    /**
     * Criterio 4: crea la incidencia CLIENTE_NO_ENVIA_DOCUMENTOS de HU-23 para un documento SIN_RESPUESTA y la enlaza; una sola
     * vez. 400 si no está sin respuesta o ya tiene incidencia.
     */
    @Transactional
    public DocumentoFaltanteDto registrarComoIncidencia(Long clienteId, Long documentoId, String usuario) {
        DocumentoFaltante d = buscar(clienteId, documentoId);
        LocalDate hoy = LocalDate.now(clock);
        if (DocumentoFaltanteDto.estadoVisible(d, hoy) != EstadoDocumentoFaltante.SIN_RESPUESTA) {
            throw new ValidationException("Solo se registra como incidencia un documento sin respuesta");
        }
        if (d.getIncidenciaId() != null) throw new ValidationException("Ya se registró como incidencia");
        var incidencia = incidencias.registrar(new IncidenciaEntradaDto(TipoIncidencia.CLIENTE_NO_ENVIA_DOCUMENTOS, clienteId, hoy,
                "No envió «" + d.getDocumento() + "»: vencía el " + d.getFechaLimite().format(DD_MM_AAAA), null), usuario);
        d.setIncidenciaId(incidencia.id());
        return DocumentoFaltanteDto.from(documentos.save(d), hoy);
    }

    private DocumentoFaltante buscar(Long clienteId, Long documentoId) {
        return documentos.findById(documentoId)
                .filter(d -> d.getClienteId().equals(clienteId))
                .orElseThrow(() -> new EntityNotFoundException("Documento no encontrado"));
    }

    private void validar(DocumentoFaltanteEntradaDto dto, LocalDate solicitud, LocalDate hoy) {
        List<String> errores = new ArrayList<>();
        String documento = dto == null || dto.documento() == null ? "" : dto.documento().trim();
        if (documento.isEmpty()) errores.add("documento");
        else if (documento.length() > MAX_DOCUMENTO) errores.add("documento (máximo " + MAX_DOCUMENTO + " caracteres)");
        if (solicitud.isAfter(hoy)) errores.add("fechaSolicitud (no puede ser futura)");
        LocalDate limite = dto == null ? null : dto.fechaLimite();
        if (limite == null) errores.add("fechaLimite");
        else if (limite.isBefore(solicitud)) errores.add("fechaLimite (no puede ser anterior a la fecha de solicitud)");
        if (!errores.isEmpty()) throw new ValidationException("Faltan o son inválidos: " + String.join(", ", errores));
    }
}

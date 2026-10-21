package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.DocumentoDto;
import com.pluscontable.tributario.model.DocumentoLegal;
import com.pluscontable.tributario.service.ExpedienteService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tributario/clientes/{clienteId}/documentos")
public class DocumentoController {

    private final ExpedienteService service;

    public DocumentoController(ExpedienteService service) {
        this.service = service;
    }

    @GetMapping
    public List<DocumentoDto> listar(@PathVariable Long clienteId) {
        return service.listar(clienteId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoDto archivar(@PathVariable Long clienteId,
                                 @RequestParam DocumentoLegal.Tipo tipo,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaEmision,
                                 @RequestParam MultipartFile archivo) {
        return service.archivar(clienteId, tipo, fechaEmision, archivo);
    }

    @GetMapping("/{id}/archivo")
    public ResponseEntity<Resource> descargar(@PathVariable Long clienteId, @PathVariable Long id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).body(service.descargar(clienteId, id));
    }
}

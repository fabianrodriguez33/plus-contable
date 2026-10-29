package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.DocumentoDto;
import com.pluscontable.tributario.model.DocumentoLegal;
import com.pluscontable.tributario.service.ExpedienteService;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tributario")
public class DocumentoController {

    private final ExpedienteService expedienteService;

    public DocumentoController(ExpedienteService expedienteService) {
        this.expedienteService = expedienteService;
    }

    @GetMapping("/clientes/{clienteId}/documentos")
    public List<DocumentoDto> listar(@PathVariable Long clienteId) {
        return expedienteService.listar(clienteId);
    }

    @PostMapping(value = "/clientes/{clienteId}/documentos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoDto subir(@PathVariable Long clienteId, @RequestParam DocumentoLegal.Tipo tipo,
                              @RequestParam("archivo") MultipartFile archivo) {
        return expedienteService.subir(clienteId, tipo, archivo);
    }

    @GetMapping("/documentos/{id}/descargar")
    public ResponseEntity<Resource> descargar(@PathVariable Long id) {
        DocumentoLegal d = expedienteService.buscar(id);
        Resource recurso = expedienteService.cargar(d);
        MediaType tipo = d.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(d.getContentType());
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.getNombreArchivo()).build().toString())
                .body(recurso);
    }

    @DeleteMapping("/clientes/{clienteId}/documentos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long clienteId, @PathVariable Long id) {
        expedienteService.eliminar(clienteId, id);
    }
}

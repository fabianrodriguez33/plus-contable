package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.DocumentoFaltanteDto;
import com.pluscontable.tributario.dto.DocumentoFaltanteEntradaDto;
import com.pluscontable.tributario.service.DocumentoFaltanteService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HU-24: solo el estudio (CONTADOR, ADMIN) sigue los documentos faltantes; las rutas no listadas en SecurityConfig son del estudio. */
@RestController
@RequestMapping("/api/tributario/clientes/{clienteId}/documentos-faltantes")
public class DocumentoFaltanteController {

    private final DocumentoFaltanteService service;

    public DocumentoFaltanteController(DocumentoFaltanteService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentoFaltanteDto registrar(@PathVariable Long clienteId, @RequestBody(required = false) DocumentoFaltanteEntradaDto dto,
                                          Authentication autenticacion) {
        return service.registrar(clienteId, dto, autenticacion.getName());
    }

    @GetMapping
    public List<DocumentoFaltanteDto> listar(@PathVariable Long clienteId) {
        return service.listar(clienteId);
    }

    @PostMapping("/{documentoId}/entrega")
    public DocumentoFaltanteDto registrarEntrega(@PathVariable Long clienteId, @PathVariable Long documentoId) {
        return service.registrarEntrega(clienteId, documentoId);
    }

    /** El usuario de la incidencia es el del token. */
    @PostMapping("/{documentoId}/incidencia")
    public DocumentoFaltanteDto registrarComoIncidencia(@PathVariable Long clienteId, @PathVariable Long documentoId,
                                                        Authentication autenticacion) {
        return service.registrarComoIncidencia(clienteId, documentoId, autenticacion.getName());
    }
}

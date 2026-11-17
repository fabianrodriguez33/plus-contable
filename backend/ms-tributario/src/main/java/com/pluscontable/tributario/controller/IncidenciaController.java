package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.IncidenciaDto;
import com.pluscontable.tributario.dto.IncidenciaEntradaDto;
import com.pluscontable.tributario.service.IncidenciaService;
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

/** HU-23: solo el estudio (CONTADOR, ADMIN) registra y consulta incidencias; las rutas no listadas en SecurityConfig son del estudio. */
@RestController
@RequestMapping("/api/tributario")
public class IncidenciaController {

    private final IncidenciaService service;

    public IncidenciaController(IncidenciaService service) {
        this.service = service;
    }

    /** El usuario que registra es el del token (criterio 4). */
    @PostMapping("/incidencias")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidenciaDto registrar(@RequestBody IncidenciaEntradaDto dto, Authentication autenticacion) {
        return service.registrar(dto, autenticacion.getName());
    }

    @GetMapping("/clientes/{clienteId}/incidencias")
    public List<IncidenciaDto> delCliente(@PathVariable Long clienteId) {
        return service.delCliente(clienteId);
    }
}

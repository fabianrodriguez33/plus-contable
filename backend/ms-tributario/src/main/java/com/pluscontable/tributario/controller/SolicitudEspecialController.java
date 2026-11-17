package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.SolicitudEspecialDto;
import com.pluscontable.tributario.dto.SolicitudEspecialEntradaDto;
import com.pluscontable.tributario.service.SolicitudEspecialService;
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

/** HU-26: solo el estudio (CONTADOR, ADMIN) registra y sigue las solicitudes; las rutas no listadas en SecurityConfig son del estudio. */
@RestController
@RequestMapping("/api/tributario/clientes/{clienteId}/solicitudes")
public class SolicitudEspecialController {

    private final SolicitudEspecialService service;

    public SolicitudEspecialController(SolicitudEspecialService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SolicitudEspecialDto registrar(@PathVariable Long clienteId, @RequestBody(required = false) SolicitudEspecialEntradaDto dto,
                                          Authentication autenticacion) {
        return service.registrar(clienteId, dto, autenticacion.getName());
    }

    /** Los pedidos en seguimiento (PENDIENTE), el de entrega más próxima primero. */
    @GetMapping
    public List<SolicitudEspecialDto> pendientes(@PathVariable Long clienteId) {
        return service.pendientes(clienteId);
    }

    @PostMapping("/{solicitudId}/atender")
    public SolicitudEspecialDto marcarAtendida(@PathVariable Long clienteId, @PathVariable Long solicitudId) {
        return service.marcarAtendida(clienteId, solicitudId);
    }
}

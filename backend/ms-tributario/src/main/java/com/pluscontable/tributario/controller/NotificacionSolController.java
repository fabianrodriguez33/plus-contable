package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.NotificacionSolDto;
import com.pluscontable.tributario.dto.NotificacionSolEntradaDto;
import com.pluscontable.tributario.service.NotificacionSolService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HU-18: solo el estudio (CONTADOR, ADMIN) registra y atiende notificaciones; las rutas no listadas en SecurityConfig son del estudio. */
@RestController
@RequestMapping("/api/tributario")
public class NotificacionSolController {

    private final NotificacionSolService service;

    public NotificacionSolController(NotificacionSolService service) {
        this.service = service;
    }

    @PostMapping("/clientes/{clienteId}/notificaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificacionSolDto registrar(@PathVariable Long clienteId, @RequestBody NotificacionSolEntradaDto dto) {
        return service.registrar(clienteId, dto);
    }

    @PatchMapping("/notificaciones/{id}/atencion")
    public NotificacionSolDto marcarAtendida(@PathVariable Long id) {
        return service.marcarAtendida(id);
    }
}

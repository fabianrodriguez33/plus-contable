package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.ProgramacionMesDto;
import com.pluscontable.tributario.dto.ProgramarMesDto;
import com.pluscontable.tributario.service.ProgramacionMensualService;
import jakarta.validation.ValidationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tributario/obligaciones/programacion")
public class ProgramacionMensualController {
    private final ProgramacionMensualService servicio;

    public ProgramacionMensualController(ProgramacionMensualService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    public ProgramacionMesDto programar(@RequestBody ProgramarMesDto solicitud) {
        if (solicitud == null) throw new ValidationException("clienteId y periodo son obligatorios");
        return servicio.programar(solicitud.clienteId(), solicitud.periodo());
    }
}

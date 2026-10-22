package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.AcuerdoDto;
import com.pluscontable.tributario.service.AcuerdoService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tributario/clientes/{clienteId}/acuerdo")
public class AcuerdoController {

    private final AcuerdoService service;

    public AcuerdoController(AcuerdoService service) {
        this.service = service;
    }

    @GetMapping
    public AcuerdoDto vigente(@PathVariable Long clienteId) {
        return service.vigente(clienteId);
    }

    @PutMapping
    public AcuerdoDto guardar(@PathVariable Long clienteId, @RequestBody AcuerdoDto dto) {
        return service.guardar(clienteId, dto);
    }
}

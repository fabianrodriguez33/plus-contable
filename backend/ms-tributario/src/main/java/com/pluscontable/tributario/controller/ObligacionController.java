package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.CambioVencimientoDto;
import com.pluscontable.tributario.dto.DeclaracionDto;
import com.pluscontable.tributario.dto.ObligacionDto;
import com.pluscontable.tributario.service.ObligacionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tributario/obligaciones")
public class ObligacionController {

    private final ObligacionService service;

    public ObligacionController(ObligacionService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ObligacionDto buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PatchMapping("/{id}/declaracion")
    public ObligacionDto declarar(@PathVariable Long id, @RequestBody DeclaracionDto dto) {
        return service.declarar(id, dto);
    }

    @PatchMapping("/vencimiento")
    public List<ObligacionDto> cambiarVencimiento(@Valid @RequestBody CambioVencimientoDto dto) {
        return service.cambiarVencimiento(dto);
    }
}

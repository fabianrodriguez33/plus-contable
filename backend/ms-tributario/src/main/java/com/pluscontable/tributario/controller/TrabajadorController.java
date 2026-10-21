package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.TrabajadorDto;
import com.pluscontable.tributario.service.TrabajadorService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tributario/clientes/{clienteId}/trabajadores")
public class TrabajadorController {

    private final TrabajadorService service;

    public TrabajadorController(TrabajadorService service) {
        this.service = service;
    }

    @GetMapping
    public List<TrabajadorDto> listar(@PathVariable Long clienteId) {
        return service.listar(clienteId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrabajadorDto registrar(@PathVariable Long clienteId, @Valid @RequestBody TrabajadorDto dto) {
        return service.registrar(clienteId, dto);
    }
}

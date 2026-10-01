package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.ActivoFijoDto;
import com.pluscontable.tributario.service.PatrimonioLaboralService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tributario/clientes/{clienteId}/activos")
public class ActivoFijoController {

    private final PatrimonioLaboralService service;

    public ActivoFijoController(PatrimonioLaboralService service) {
        this.service = service;
    }

    @GetMapping
    public List<ActivoFijoDto> listar(@PathVariable Long clienteId) {
        return service.listarActivos(clienteId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActivoFijoDto agregar(@PathVariable Long clienteId, @Valid @RequestBody ActivoFijoDto dto) {
        return service.agregarActivo(clienteId, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long clienteId, @PathVariable Long id) {
        service.eliminarActivo(clienteId, id);
    }
}

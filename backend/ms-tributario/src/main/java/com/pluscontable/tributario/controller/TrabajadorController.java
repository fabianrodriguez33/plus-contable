package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.TrabajadorDto;
import com.pluscontable.tributario.service.PatrimonioLaboralService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clientes/{clienteId}/trabajadores")
public class TrabajadorController {

    private final PatrimonioLaboralService service;

    public TrabajadorController(PatrimonioLaboralService service) {
        this.service = service;
    }

    @GetMapping
    public List<TrabajadorDto> listar(@PathVariable Long clienteId) {
        return service.listarTrabajadores(clienteId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrabajadorDto agregar(@PathVariable Long clienteId, @Valid @RequestBody TrabajadorDto dto) {
        return service.agregarTrabajador(clienteId, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable Long clienteId, @PathVariable Long id) {
        service.eliminarTrabajador(clienteId, id);
    }
}

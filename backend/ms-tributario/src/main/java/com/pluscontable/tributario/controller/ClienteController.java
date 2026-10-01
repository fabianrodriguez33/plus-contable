package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.ClienteDto;
import com.pluscontable.tributario.dto.CredencialSolDto;
import com.pluscontable.tributario.service.ClienteService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tributario/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @GetMapping
    public List<ClienteDto> listar() {
        return clienteService.listar();
    }

    @GetMapping("/{id}")
    public ClienteDto obtener(@PathVariable Long id) {
        return clienteService.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteDto crear(@Valid @RequestBody ClienteDto dto) {
        return clienteService.crear(dto);
    }

    @PutMapping("/{id}")
    public ClienteDto actualizar(@PathVariable Long id, @Valid @RequestBody ClienteDto dto) {
        return clienteService.actualizar(id, dto);
    }

    @PutMapping("/{id}/sol")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void guardarSol(@PathVariable Long id, @Valid @RequestBody CredencialSolDto dto) {
        clienteService.guardarCredencialSol(id, dto);
    }

    @GetMapping("/{id}/sol")
    public CredencialSolDto obtenerSol(@PathVariable Long id) {
        return clienteService.obtenerCredencialSol(id);
    }
}

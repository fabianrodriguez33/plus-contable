package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.HistorialItemDto;
import com.pluscontable.tributario.service.HistorialService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** HU-27: solo el estudio (CONTADOR, ADMIN) ve el historial (criterio 4); las rutas no listadas en SecurityConfig son del estudio. */
@RestController
public class HistorialController {

    private final HistorialService service;

    public HistorialController(HistorialService service) {
        this.service = service;
    }

    @GetMapping("/api/tributario/clientes/{clienteId}/historial")
    public List<HistorialItemDto> delCliente(@PathVariable Long clienteId) {
        return service.delCliente(clienteId);
    }
}

package com.pluscontable.tributario.empresa;

import com.pluscontable.tributario.config.AccesoCliente;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Solo GET: el cliente no modifica nada (las rutas de escritura no están abiertas a CLIENTE_MYPE en SecurityConfig). */
@RestController
@RequestMapping("/api/tributario/clientes/{clienteId}/datos-empresa")
public class DatosEmpresaController {
    private final DatosEmpresaService service;

    public DatosEmpresaController(DatosEmpresaService service) {
        this.service = service;
    }

    @GetMapping
    public DatosEmpresaDto obtener(@PathVariable Long clienteId) {
        AccesoCliente.exigir(clienteId);
        return service.obtener(clienteId);
    }
}

package com.pluscontable.tributario.avisocliente;

import com.pluscontable.tributario.config.AccesoCliente;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** El cliente MYPE lee solo los avisos de su empresa (AccesoCliente); SecurityConfig abre la ruta de lectura. */
@RestController
@RequestMapping("/api/tributario/clientes/{clienteId}/avisos-pago")
public class AvisoClienteController {
    private final AvisoClienteService service;

    public AvisoClienteController(AvisoClienteService service) {
        this.service = service;
    }

    @GetMapping
    public List<AvisoClienteDto> listar(@PathVariable Long clienteId) {
        AccesoCliente.exigir(clienteId);
        return service.listar(clienteId);
    }
}

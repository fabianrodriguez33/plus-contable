package com.pluscontable.tributario.compras;

import com.pluscontable.tributario.config.AccesoCliente;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU-25. Cargar compras (POST): solo CONTADOR y ADMIN (ruta no listada en SecurityConfig). Verlas (GET): CONTADOR, ADMIN y el cliente
 * MYPE de esa empresa (AccesoCliente). Clasificar (PATCH): solo el cliente MYPE, y solo en su empresa (SecurityConfig y AccesoCliente).
 */
@RestController
@RequestMapping("/api/tributario/clientes/{clienteId}/compras")
public class CompraController {
    private final CompraService service;

    public CompraController(CompraService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompraDto registrar(@PathVariable Long clienteId, @RequestBody(required = false) CompraEntradaDto cuerpo, Authentication auth) {
        return service.registrar(clienteId, cuerpo, auth.getName());
    }

    @GetMapping
    public List<CompraDto> listar(@PathVariable Long clienteId, @RequestParam(required = false) Boolean clasificada) {
        AccesoCliente.exigir(clienteId);
        return service.listar(clienteId, clasificada);
    }

    @PatchMapping("/{compraId}/clasificacion")
    public CompraDto clasificar(@PathVariable Long clienteId, @PathVariable Long compraId,
                                @RequestBody(required = false) ClasificacionEntradaDto cuerpo, Authentication auth) {
        AccesoCliente.exigir(clienteId);
        return service.clasificar(clienteId, compraId, cuerpo, auth.getName());
    }
}

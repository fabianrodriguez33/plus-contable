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
 * HU-25. Cargar compras (POST) y validar (POST .../validacion): solo CONTADOR y ADMIN (rutas no listadas en SecurityConfig). Verlas
 * (GET): CONTADOR, ADMIN y el cliente MYPE de esa empresa (AccesoCliente). Proponer la clasificación (PATCH): solo el cliente MYPE y
 * solo en su empresa (SecurityConfig y AccesoCliente). La alerta del cierre del mes es solo del contador: al cliente le llega falsa.
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
    public List<CompraDto> listar(@PathVariable Long clienteId, @RequestParam(required = false) ReglaClasificacionCompra.Estado estado,
                                  Authentication auth) {
        AccesoCliente.exigir(clienteId);
        List<CompraDto> compras = service.listar(clienteId, estado);
        return esCliente(auth) ? compras.stream().map(CompraController::paraElCliente).toList() : compras;
    }

    @PatchMapping("/{compraId}/clasificacion")
    public CompraDto proponer(@PathVariable Long clienteId, @PathVariable Long compraId,
                              @RequestBody(required = false) ClasificacionEntradaDto cuerpo, Authentication auth) {
        AccesoCliente.exigir(clienteId);
        return paraElCliente(service.proponer(clienteId, compraId, cuerpo, auth.getName()));
    }

    @PostMapping("/{compraId}/validacion")
    public CompraDto validar(@PathVariable Long clienteId, @PathVariable Long compraId, Authentication auth) {
        return service.validar(clienteId, compraId, auth.getName());
    }

    private static boolean esCliente(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE_MYPE"));
    }

    /** La alerta y quién validó son del estudio; el cliente solo ve el estado de su compra. */
    private static CompraDto paraElCliente(CompraDto c) {
        return new CompraDto(c.id(), c.clienteId(), c.periodo(), c.proveedorRuc(), c.proveedorRazonSocial(), c.comprobante(),
                c.fechaComprobante(), c.importe(), c.estado(), c.tipo(), c.descripcion(), c.clasificadaEn(), null, c.validadaEn(), false);
    }
}

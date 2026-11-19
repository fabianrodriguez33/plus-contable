package com.pluscontable.tributario.vencimiento;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU-16: solo CONTADOR y ADMIN (la ruta no está en las lecturas abiertas al cliente de SecurityConfig). Si el cliente MYPE
 * debe ver sus vencidas, lo definen HU-32 y Julio: no se abre aquí por suposición.
 */
@RestController
@RequestMapping("/api/tributario/obligaciones-vencidas-sin-pago")
public class ObligacionesVencidasSinPagoController {
    private final ObligacionesVencidasSinPagoService service;

    public ObligacionesVencidasSinPagoController(ObligacionesVencidasSinPagoService service) {
        this.service = service;
    }

    @GetMapping
    public List<ObligacionVencidaSinPagoDto> listar(@RequestParam(required = false) Long clienteId) {
        return service.listar(clienteId);
    }
}

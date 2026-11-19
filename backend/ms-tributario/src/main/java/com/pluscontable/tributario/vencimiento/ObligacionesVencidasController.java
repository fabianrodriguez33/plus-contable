package com.pluscontable.tributario.vencimiento;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU-16: solo CONTADOR y ADMIN (la ruta no está en las lecturas abiertas al cliente de SecurityConfig). El cliente MYPE ve sus
 * vencidas, pagadas y saldos en «Mi estado de cuenta» (HU-32), no en esta lista de cartera.
 */
@RestController
@RequestMapping("/api/tributario/obligaciones-vencidas")
public class ObligacionesVencidasController {
    private final ObligacionesVencidasService service;

    public ObligacionesVencidasController(ObligacionesVencidasService service) {
        this.service = service;
    }

    @GetMapping
    public ObligacionesVencidasService.Resultado listar(@RequestParam(required = false) Long clienteId) {
        return service.listar(clienteId);
    }
}

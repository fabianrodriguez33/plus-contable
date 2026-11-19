package com.pluscontable.tributario.vencimiento;

import com.pluscontable.tributario.config.AccesoCliente;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU-16. CONTADOR y ADMIN ven la cartera (con o sin {@code clienteId}), con las vencidas y, aparte, las sin declarar. El cliente MYPE
 * también puede ver sus <b>vencidas</b> (pedido de Julio con la definición de Beto): solo de su empresa, así que debe indicar su
 * {@code clienteId} (AccesoCliente responde 403 con otra empresa o sin indicarlo), y no recibe la lista de «sin declarar», que es un
 * pendiente de atención del estudio (decisión mía, por confirmar).
 */
@RestController
@RequestMapping("/api/tributario/obligaciones-vencidas")
public class ObligacionesVencidasController {
    private final ObligacionesVencidasService service;

    public ObligacionesVencidasController(ObligacionesVencidasService service) {
        this.service = service;
    }

    @GetMapping
    public ObligacionesVencidasService.Resultado listar(@RequestParam(required = false) Long clienteId, Authentication autenticacion) {
        AccesoCliente.exigir(clienteId);
        ObligacionesVencidasService.Resultado resultado = service.listar(clienteId);
        boolean esCliente = autenticacion != null
                && autenticacion.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLIENTE_MYPE"));
        return esCliente ? new ObligacionesVencidasService.Resultado(resultado.vencidas(), List.of()) : resultado;
    }
}

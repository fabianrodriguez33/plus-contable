package com.pluscontable.tributario.alertaplazo;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** HU-19: solo el estudio (CONTADOR, ADMIN) ve las alertas; las rutas no listadas en SecurityConfig son del estudio. */
@RestController
public class AlertaPlazoController {

    private final AlertaPlazoService service;

    public AlertaPlazoController(AlertaPlazoService service) {
        this.service = service;
    }

    @GetMapping("/api/tributario/alertas-plazo")
    public List<AlertaPlazoDto> listar() {
        return service.listar();
    }
}

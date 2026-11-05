package com.pluscontable.tributario.ruc;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HU-01: sugiere razón social, dirección y condición a partir del RUC. Solo el estudio (regla general de SecurityConfig). */
@RestController
@RequestMapping("/api/tributario/ruc")
public class ConsultaRucController {

    private final ConsultaRucService service;

    public ConsultaRucController(ConsultaRucService service) {
        this.service = service;
    }

    @GetMapping("/{ruc}/consulta")
    public ConsultaRucDto consultar(@PathVariable String ruc) {
        return service.consultar(ruc);
    }
}

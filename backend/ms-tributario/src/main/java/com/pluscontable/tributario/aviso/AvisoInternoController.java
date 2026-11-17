package com.pluscontable.tributario.aviso;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** SecurityConfig permite estas rutas solo a CONTADOR y ADMIN. */
@RestController
@RequestMapping("/api/tributario/avisos")
public class AvisoInternoController {
    private final AvisoInternoService service;

    public AvisoInternoController(AvisoInternoService service) {
        this.service = service;
    }

    @GetMapping
    public List<AvisoInternoDto> bandeja(@RequestParam(required = false) Boolean leido) {
        return service.bandeja(leido);
    }

    @PatchMapping("/{id}/lectura")
    public AvisoInternoDto marcarLeido(@PathVariable Long id) {
        return service.marcarLeido(id);
    }
}

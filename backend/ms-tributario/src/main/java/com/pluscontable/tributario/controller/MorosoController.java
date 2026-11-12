package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.MorosoDto;
import com.pluscontable.tributario.service.MorosoService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU-31: solo el estudio (CONTADOR, ADMIN) ve la lista de morosos; las rutas no listadas en SecurityConfig son del estudio.
 * «Contador titular» se tomó como el estudio, igual que en HU-23 (CONTADOR y ADMIN tienen los mismos permisos): pendiente de
 * confirmar con Beto.
 */
@RestController
public class MorosoController {

    private final MorosoService service;

    public MorosoController(MorosoService service) {
        this.service = service;
    }

    @GetMapping("/api/tributario/morosos")
    public List<MorosoDto> listar() {
        return service.listar();
    }
}

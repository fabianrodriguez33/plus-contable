package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.EstadoCuentaDto;
import com.pluscontable.tributario.service.EstadoCuentaService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HU-30: solo el estudio (CONTADOR, ADMIN) emite el estado de cuenta; las rutas no listadas en SecurityConfig son del estudio. */
@RestController
public class EstadoCuentaController {

    private final EstadoCuentaService service;

    public EstadoCuentaController(EstadoCuentaService service) {
        this.service = service;
    }

    /** Las fechas son opcionales aquí para que el servicio reúna los errores en un solo 400. */
    @GetMapping("/api/tributario/clientes/{clienteId}/estado-cuenta")
    public EstadoCuentaDto emitir(@PathVariable Long clienteId,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.emitir(clienteId, desde, hasta);
    }
}

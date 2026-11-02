package com.pluscontable.tributario.calendario;

import com.pluscontable.tributario.model.Obligacion;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tributario/calendario")
public class CalendarioController {
    private final CalendarioService service;

    public CalendarioController(CalendarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<CalendarioObligacionDto> listar(
            @RequestParam Long clienteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodo,
            @RequestParam(required = false) Obligacion.Estado estado,
            @RequestParam(required = false) CalendarioService.Situacion situacion) {
        return service.listar(clienteId, desde, hasta, periodo, estado, situacion);
    }
}

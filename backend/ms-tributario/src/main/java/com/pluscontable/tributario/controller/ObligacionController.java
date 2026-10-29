package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.CambioVencimientoDto;
import com.pluscontable.tributario.dto.DeclaracionDto;
import com.pluscontable.tributario.dto.ObligacionDto;
import com.pluscontable.tributario.dto.CrearObligacionDto;
import com.pluscontable.tributario.service.ObligacionService;
import com.pluscontable.tributario.service.ObligacionProgramacionService;
import org.springframework.beans.factory.ObjectProvider;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tributario/obligaciones")
public class ObligacionController {

    private final ObligacionService service;
    private final ObjectProvider<ObligacionProgramacionService> programacion;

    public ObligacionController(ObligacionService service,
                                ObjectProvider<ObligacionProgramacionService> programacion) {
        this.service = service;
        this.programacion = programacion;
    }

    @PostMapping
    public ObligacionDto crear(@RequestBody CrearObligacionDto solicitud) {
        return programacion.getObject().crear(solicitud);
    }

    /** PROVISIONAL hasta HU-07: listado mínimo para elegir obligaciones en HU-10. */
    @GetMapping
    public List<ObligacionDto> listar(@RequestParam Long clienteId) {
        return service.listar(clienteId);
    }

    @GetMapping("/{id}")
    public ObligacionDto buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PatchMapping("/{id}/declaracion")
    public ObligacionDto declarar(@PathVariable Long id, @RequestBody DeclaracionDto dto) {
        return service.declarar(id, dto);
    }

    @PatchMapping("/vencimiento")
    public List<ObligacionDto> cambiarVencimiento(@RequestBody CambioVencimientoDto dto) {
        return service.cambiarVencimiento(dto);
    }
}

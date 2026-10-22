package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.ActivoFijoDto;
import com.pluscontable.tributario.dto.AporteDto;
import com.pluscontable.tributario.dto.ResumenSocietarioDto;
import com.pluscontable.tributario.dto.SocioDto;
import com.pluscontable.tributario.service.PatrimonioLaboralService;
import com.pluscontable.tributario.service.SocioService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** Estructura societaria (socios) y patrimonio (activos fijos) del cliente. */
@RestController
@Validated
@RequestMapping("/api/tributario/clientes/{clienteId}")
public class SocioController {

    private final SocioService socioService;
    private final PatrimonioLaboralService patrimonioService;

    public SocioController(SocioService socioService, PatrimonioLaboralService patrimonioService) {
        this.socioService = socioService;
        this.patrimonioService = patrimonioService;
    }

    @GetMapping("/socios")
    public List<SocioDto> listarSocios(@PathVariable Long clienteId) {
        return socioService.listar(clienteId);
    }

    /** Socios con capital social total, porcentaje total y diferencia respecto a 100 %. */
    @GetMapping("/socios/resumen")
    public ResumenSocietarioDto resumenSocietario(@PathVariable Long clienteId) {
        return socioService.resumen(clienteId);
    }

    @PutMapping("/socios")
    public ResumenSocietarioDto reemplazarSocios(@PathVariable Long clienteId,
                                                 @RequestBody List<@Valid SocioDto> socios) {
        return socioService.reemplazar(clienteId, socios);
    }

    @PostMapping("/socios")
    @ResponseStatus(HttpStatus.CREATED)
    public ResumenSocietarioDto registrarSocios(@PathVariable Long clienteId,
                                                @RequestBody List<@Valid SocioDto> socios) {
        return socioService.reemplazar(clienteId, socios);
    }

    @PatchMapping("/socios/{socioId}/aporte")
    public ResumenSocietarioDto actualizarAporte(@PathVariable Long clienteId, @PathVariable Long socioId,
                                                 @Valid @RequestBody AporteDto dto) {
        return socioService.actualizarAporte(clienteId, socioId, dto.aporte());
    }

    @GetMapping("/activos")
    public List<ActivoFijoDto> listarActivos(@PathVariable Long clienteId) {
        return patrimonioService.listarActivos(clienteId);
    }

    @PostMapping("/activos")
    @ResponseStatus(HttpStatus.CREATED)
    public ActivoFijoDto agregarActivo(@PathVariable Long clienteId, @Valid @RequestBody ActivoFijoDto dto) {
        return patrimonioService.agregarActivo(clienteId, dto);
    }

    @DeleteMapping("/activos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarActivo(@PathVariable Long clienteId, @PathVariable Long id) {
        patrimonioService.eliminarActivo(clienteId, id);
    }
}

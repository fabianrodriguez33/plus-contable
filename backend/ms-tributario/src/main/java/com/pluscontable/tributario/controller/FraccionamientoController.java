package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.calendario.CalendarioService;
import com.pluscontable.tributario.model.EstadoCuota;
import com.pluscontable.tributario.dto.CalendarioCuotaDto;
import com.pluscontable.tributario.dto.CuotaDto;
import com.pluscontable.tributario.dto.FraccionamientoDto;
import com.pluscontable.tributario.dto.FraccionamientoEntradaDto;
import com.pluscontable.tributario.service.FraccionamientoService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HU-17: solo el estudio (CONTADOR, ADMIN); las rutas no listadas en SecurityConfig son del estudio. */
@RestController
@RequestMapping("/api/tributario")
public class FraccionamientoController {

    private final FraccionamientoService service;

    public FraccionamientoController(FraccionamientoService service) {
        this.service = service;
    }

    @PostMapping("/clientes/{clienteId}/fraccionamientos")
    @ResponseStatus(HttpStatus.CREATED)
    public FraccionamientoDto registrar(@PathVariable Long clienteId, @RequestBody FraccionamientoEntradaDto dto) {
        return service.registrar(clienteId, dto);
    }

    @GetMapping("/clientes/{clienteId}/fraccionamientos")
    public List<FraccionamientoDto> listar(@PathVariable Long clienteId) {
        return service.listar(clienteId);
    }

    /** Las cuotas del cliente para el calendario de vencimientos; con riesgo en las pendientes de fecha pasada. */
    @GetMapping("/clientes/{clienteId}/fraccionamientos/calendario")
    public List<CalendarioCuotaDto> calendario(@PathVariable Long clienteId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                               @RequestParam(required = false) EstadoCuota estado,
                                               @RequestParam(required = false) CalendarioService.Situacion situacion) {
        return service.calendario(clienteId, desde, hasta, estado, situacion);
    }

    @PatchMapping("/cuotas/{cuotaId}/pago")
    public CuotaDto pagarCuota(@PathVariable Long cuotaId) {
        return service.pagarCuota(cuotaId);
    }
}

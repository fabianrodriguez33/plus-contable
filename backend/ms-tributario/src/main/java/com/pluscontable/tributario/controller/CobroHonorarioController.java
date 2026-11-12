package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.CobroHonorarioDto;
import com.pluscontable.tributario.dto.PagoHonorarioEntradaDto;
import com.pluscontable.tributario.dto.ProgramacionCobroDto;
import com.pluscontable.tributario.dto.ProgramacionCobroEntradaDto;
import com.pluscontable.tributario.service.CobroHonorarioService;
import com.pluscontable.tributario.service.PagoHonorarioService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HU-28 y HU-29: solo el estudio (CONTADOR, ADMIN) programa y consulta cobros; las rutas no listadas en SecurityConfig son del estudio. */
@RestController
@RequestMapping("/api/tributario/clientes/{clienteId}/cobros")
public class CobroHonorarioController {

    private final CobroHonorarioService service;
    private final PagoHonorarioService pagos;

    public CobroHonorarioController(CobroHonorarioService service, PagoHonorarioService pagos) {
        this.service = service;
        this.pagos = pagos;
    }

    @PostMapping("/programacion")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgramacionCobroDto programar(@PathVariable Long clienteId, @RequestBody(required = false) ProgramacionCobroEntradaDto dto) {
        return service.programar(clienteId, dto);
    }

    /** Los cobros pendientes del cliente (incluidos los de pago parcial), el más próximo primero (criterio 4). */
    @GetMapping
    public List<CobroHonorarioDto> pendientes(@PathVariable Long clienteId) {
        return service.pendientes(clienteId);
    }

    /** HU-29: registra un pago del cobro y devuelve el cobro con lo pagado, el saldo y el nuevo estado. */
    @PostMapping("/{cobroId}/pagos")
    @ResponseStatus(HttpStatus.CREATED)
    public CobroHonorarioDto registrarPago(@PathVariable Long clienteId, @PathVariable Long cobroId,
                                           @RequestBody(required = false) PagoHonorarioEntradaDto dto, Authentication autenticacion) {
        return pagos.registrar(clienteId, cobroId, dto, autenticacion.getName());
    }
}

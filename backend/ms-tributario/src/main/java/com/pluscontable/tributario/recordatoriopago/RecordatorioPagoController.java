package com.pluscontable.tributario.recordatoriopago;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HU-15: solo CONTADOR y ADMIN. Estas rutas no están en las lecturas abiertas al cliente de SecurityConfig (el patrón
 * {@code /obligaciones/*} solo cubre un nivel), así que un CLIENTE_MYPE recibe 403. El cliente solo recibe el resultado, como aviso de pago.
 */
@RestController
@RequestMapping("/api/tributario/obligaciones/{obligacionId}/recordatorios-pago")
public class RecordatorioPagoController {
    private final RecordatorioPagoService service;

    public RecordatorioPagoController(RecordatorioPagoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecordatorioPagoDto programar(@PathVariable Long obligacionId, @RequestBody(required = false) ProgramarRecordatorioDto cuerpo,
                                         Authentication autenticacion) {
        return service.programar(obligacionId, cuerpo == null ? null : cuerpo.fechaRecordatorio(), autenticacion.getName());
    }

    @GetMapping
    public List<RecordatorioPagoDto> listar(@PathVariable Long obligacionId) {
        return service.listar(obligacionId);
    }

    @PatchMapping("/{id}/cancelacion")
    public RecordatorioPagoDto cancelar(@PathVariable Long obligacionId, @PathVariable Long id) {
        return service.cancelar(obligacionId, id);
    }
}

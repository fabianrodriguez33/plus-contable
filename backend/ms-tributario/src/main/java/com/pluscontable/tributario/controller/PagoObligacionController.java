package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.ObligacionDto;
import com.pluscontable.tributario.dto.PagoDto;
import com.pluscontable.tributario.dto.PagoEntradaDto;
import com.pluscontable.tributario.service.PagoObligacionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * HU-13: solo el estudio (CONTADOR, ADMIN) registra y consulta pagos; las rutas no listadas en SecurityConfig son del estudio.
 * Los parámetros son opcionales aquí para que el servicio reúna todos los errores en un solo 400; el archivo PDF también
 * es opcional (alcance reducido de Beto, 10/11: el comprobante es el número de constancia).
 */
@RestController
@RequestMapping("/api/tributario/obligaciones/{obligacionId}/pagos")
public class PagoObligacionController {

    private final PagoObligacionService service;

    public PagoObligacionController(PagoObligacionService service) {
        this.service = service;
    }

    /** Los pagos de la obligación, para mostrar sus constancias. */
    @GetMapping
    public List<PagoDto> listar(@PathVariable Long obligacionId) {
        return service.listar(obligacionId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ObligacionDto registrar(@PathVariable Long obligacionId,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaPago,
                                   @RequestParam(required = false) BigDecimal montoPagado,
                                   @RequestParam(required = false) String numeroConstancia,
                                   @RequestParam(required = false) MultipartFile archivo) {
        return service.registrar(obligacionId, new PagoEntradaDto(fechaPago, montoPagado, numeroConstancia), archivo);
    }
}

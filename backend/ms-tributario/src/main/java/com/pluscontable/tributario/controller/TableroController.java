package com.pluscontable.tributario.controller;

import com.pluscontable.tributario.dto.TableroFilaDto;
import com.pluscontable.tributario.service.TableroService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HU-14: solo el estudio (CONTADOR, ADMIN) ve el tablero; las rutas no listadas en SecurityConfig son del estudio. */
@RestController
public class TableroController {

    private final TableroService service;

    public TableroController(TableroService service) {
        this.service = service;
    }

    @GetMapping("/api/tributario/tablero")
    public List<TableroFilaDto> listar(@RequestParam(required = false) Long clienteId,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodo,
                                       @RequestParam(defaultValue = "false") boolean soloDeclaradoSinPago) {
        return service.listar(clienteId, periodo, soloDeclaradoSinPago);
    }
}

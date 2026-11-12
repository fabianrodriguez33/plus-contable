package com.pluscontable.auth.controller;

import com.pluscontable.auth.dto.SolicitarRestablecimientoRequest;
import com.pluscontable.auth.dto.SolicitudPendienteDto;
import com.pluscontable.auth.service.RestablecimientoService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** HU-41: el cliente pide restablecer su contraseña (sin sesión) y el estudio ve las solicitudes (reglas de SecurityConfig). */
@RestController
@RequestMapping("/api/auth/restablecimiento")
public class RestablecimientoController {

    private final RestablecimientoService service;

    public RestablecimientoController(RestablecimientoService service) {
        this.service = service;
    }

    /** Siempre la misma respuesta, exista o no la cuenta (criterio 2). */
    @PostMapping("/solicitar")
    public Map<String, String> solicitar(@RequestBody(required = false) SolicitarRestablecimientoRequest cuerpo, HttpServletRequest http) {
        return Map.of("mensaje", service.solicitar(cuerpo == null ? null : cuerpo.correo(), http.getRemoteAddr()));
    }

    @GetMapping("/pendientes")
    public List<SolicitudPendienteDto> pendientes() {
        return service.pendientes();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> estado(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("mensaje", e.getReason() == null ? "" : e.getReason()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> cuerpoIlegible(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("mensaje", "El cuerpo de la solicitud no es válido"));
    }
}

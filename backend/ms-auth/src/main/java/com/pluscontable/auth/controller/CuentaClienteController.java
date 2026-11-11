package com.pluscontable.auth.controller;

import com.pluscontable.auth.config.SesionToken;
import com.pluscontable.auth.dto.CambiarPasswordRequest;
import com.pluscontable.auth.dto.CrearCuentaRequest;
import com.pluscontable.auth.dto.CuentaClienteResponse;
import com.pluscontable.auth.dto.LoginResponse;
import com.pluscontable.auth.service.AuthService;
import com.pluscontable.auth.service.CuentaClienteService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * HU-37: crear la cuenta de un cliente (solo CONTADOR y ADMIN, regla de SecurityConfig) y el cambio obligatorio de la
 * contraseña temporal en el primer ingreso (solo con el token restringido).
 */
@RestController
@RequestMapping("/api/auth")
public class CuentaClienteController {

    private final CuentaClienteService cuentas;
    private final AuthService auth;

    public CuentaClienteController(CuentaClienteService cuentas, AuthService auth) {
        this.cuentas = cuentas;
        this.auth = auth;
    }

    /** La contraseña temporal va solo en esta respuesta: no se guarda en cachés ni se vuelve a entregar. */
    @PostMapping("/clientes/{clienteId}/cuenta")
    public ResponseEntity<CuentaClienteResponse> crear(@PathVariable Long clienteId, @RequestBody(required = false) CrearCuentaRequest cuerpo,
                                                       Authentication autenticacion, HttpServletRequest http) {
        CuentaClienteResponse r = cuentas.crear(clienteId, cuerpo == null ? null : cuerpo.correo(),
                http.getHeader(HttpHeaders.AUTHORIZATION), autenticacion.getName(), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(r);
    }

    /** HU-37: otra contraseña temporal para la cuenta existente; solo CONTADOR y ADMIN (regla de SecurityConfig). */
    @PostMapping("/clientes/{clienteId}/cuenta/restablecer")
    public ResponseEntity<CuentaClienteResponse> restablecer(@PathVariable Long clienteId, Authentication autenticacion,
                                                             HttpServletRequest http) {
        CuentaClienteResponse r = cuentas.restablecer(clienteId, autenticacion.getName(), http.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(r);
    }

    @PostMapping("/cambiar-password")
    public ResponseEntity<LoginResponse> cambiarPassword(@RequestBody(required = false) CambiarPasswordRequest cuerpo,
                                                         Authentication autenticacion, HttpServletRequest http) {
        SesionToken sesion = autenticacion.getDetails() instanceof SesionToken s ? s : null;
        LoginResponse r = auth.cambiarPassword(autenticacion.getName(), sesion, cuerpo == null ? null : cuerpo.passwordNueva(), http.getRemoteAddr());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(r);
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

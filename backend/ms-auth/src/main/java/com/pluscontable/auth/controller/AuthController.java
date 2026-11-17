package com.pluscontable.auth.controller;

import com.pluscontable.auth.config.SesionToken;
import com.pluscontable.auth.dto.LoginRequest;
import com.pluscontable.auth.dto.LoginResponse;
import com.pluscontable.auth.dto.UserDto;
import com.pluscontable.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.security.core.Authentication;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, http.getRemoteAddr());
    }

    /** HU-36 (tarea 2): cierra la sesion del token con el que se llama; sin token valido responde 401 el filtro. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication autenticacion, HttpServletRequest http) {
        SesionToken sesion = autenticacion.getDetails() instanceof SesionToken s ? s : new SesionToken(null, null);
        authService.logout(autenticacion.getName(), sesion.jti(), sesion.expira(), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserDto me(Principal principal) {
        return authService.me(principal.getName());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of("mensaje", e.getReason() == null ? "" : e.getReason()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensaje", "Usuario y contraseña son obligatorios"));
    }
}

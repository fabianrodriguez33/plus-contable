package com.pluscontable.auth.controller;

import com.pluscontable.auth.dto.ClaveSolDto;
import com.pluscontable.auth.service.ClaveSolService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/claves-sol")
public class ClaveSolController {

    private final ClaveSolService service;

    public ClaveSolController(ClaveSolService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void guardar(@Valid @RequestBody ClaveSolDto dto, Principal principal, HttpServletRequest http) {
        service.guardar(dto, principal.getName(), http.getRemoteAddr());
    }

    @GetMapping("/{ruc}")
    public ClaveSolDto obtener(@PathVariable String ruc, Principal principal, HttpServletRequest http) {
        return service.obtener(ruc, principal.getName(), http.getRemoteAddr());
    }
}

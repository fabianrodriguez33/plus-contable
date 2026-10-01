package com.pluscontable.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ClaveSolDto(
        @Pattern(regexp = "\\d{11}", message = "El RUC debe tener 11 dígitos") String ruc,
        @NotBlank(message = "El usuario SOL es obligatorio") String usuarioSol,
        @NotBlank(message = "La clave SOL es obligatoria") String claveSol) {
}

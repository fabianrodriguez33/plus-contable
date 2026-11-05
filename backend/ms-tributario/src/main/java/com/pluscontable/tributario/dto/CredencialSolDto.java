package com.pluscontable.tributario.dto;

import jakarta.validation.constraints.NotBlank;

public record CredencialSolDto(
        @NotBlank(message = "El usuario SOL es obligatorio") String usuarioSol,
        @NotBlank(message = "La clave SOL es obligatoria") String claveSol) {
}

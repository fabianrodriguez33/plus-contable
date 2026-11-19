package com.pluscontable.auth.dto;

/**
 * {@code debeCambiarPassword}: HU-37, el token es el restringido del primer ingreso con la contraseña temporal y solo sirve
 * para cambiarla.
 */
public record LoginResponse(String token, String tipo, long expiraEnSegundos, UserDto usuario, boolean debeCambiarPassword) {

    public LoginResponse(String token, String tipo, long expiraEnSegundos, UserDto usuario) {
        this(token, tipo, expiraEnSegundos, usuario, false);
    }
}

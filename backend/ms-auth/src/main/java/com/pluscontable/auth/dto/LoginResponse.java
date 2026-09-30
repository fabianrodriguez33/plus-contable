package com.pluscontable.auth.dto;

public record LoginResponse(String token, String tipo, long expiraEnSegundos, UserDto usuario) {
}

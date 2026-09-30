package com.pluscontable.auth.dto;

import java.util.List;

public record UserDto(Long id, String username, String nombreCompleto, List<String> roles) {
}

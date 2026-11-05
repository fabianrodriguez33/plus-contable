package com.pluscontable.tributario.dto;

import java.time.YearMonth;

/** Mes tributario (AAAA-MM) cuyas obligaciones se programan para un cliente. */
public record ProgramarMesDto(Long clienteId, YearMonth periodo) {
}

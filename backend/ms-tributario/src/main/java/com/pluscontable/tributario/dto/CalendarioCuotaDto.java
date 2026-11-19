package com.pluscontable.tributario.dto;

/** Una cuota vista en el calendario de vencimientos: positivo si falta tiempo, cero si vence hoy y negativo si ya pasó. */
public record CalendarioCuotaDto(CuotaDto cuota, Long fraccionamientoId, long diasRestantes, boolean vencida) {
}

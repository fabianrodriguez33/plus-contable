package com.pluscontable.tributario.config;

/** Datos del JWT que Spring Security no guarda por sí solo; ver {@link AccesoCliente}. */
public record DatosToken(Long clienteId) {
}

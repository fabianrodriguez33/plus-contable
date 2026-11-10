package com.pluscontable.auth.dto;

/**
 * HU-37: la cuenta creada. {@code passwordTemporal} solo se entrega en esta respuesta (una sola vez): no se guarda en claro
 * ni se vuelve a mostrar.
 */
public record CuentaClienteResponse(Long usuarioId, String correo, Long clienteId, String passwordTemporal) {
}

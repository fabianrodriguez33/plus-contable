package com.pluscontable.auth.dto;

import java.time.LocalDateTime;

/**
 * HU-37: la cuenta creada o restablecida. {@code passwordTemporal} solo se entrega en esta respuesta (una sola vez): no se guarda
 * en claro ni se vuelve a mostrar. {@code venceEn} es hasta cuándo sirve la temporal (48 horas por defecto).
 */
public record CuentaClienteResponse(Long usuarioId, String correo, Long clienteId, String passwordTemporal, LocalDateTime venceEn) {

    public CuentaClienteResponse(Long usuarioId, String correo, Long clienteId, String passwordTemporal) {
        this(usuarioId, correo, clienteId, passwordTemporal, null);
    }
}

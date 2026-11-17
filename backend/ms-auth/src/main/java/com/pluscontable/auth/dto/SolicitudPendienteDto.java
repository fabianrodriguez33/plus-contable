package com.pluscontable.auth.dto;

import java.time.LocalDateTime;

/** HU-41: una solicitud de restablecimiento sin atender, para que el estudio sepa a qué cliente restablecer el acceso. */
public record SolicitudPendienteDto(Long clienteId, String correo, LocalDateTime solicitadaEn) {
}

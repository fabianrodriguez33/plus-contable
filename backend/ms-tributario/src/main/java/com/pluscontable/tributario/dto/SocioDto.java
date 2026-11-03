package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.Socio;
import java.math.BigDecimal;

/** Socio del cliente en las respuestas: aporte al capital (S/) y porcentaje de participación calculado. */
public record SocioDto(
        Long id,
        String nombreCompleto,
        String tipoDocumento,
        String numeroDocumento,
        BigDecimal aporte,
        BigDecimal porcentaje,
        String cargo) {

    public static SocioDto from(Socio s) {
        return new SocioDto(s.getId(), s.getNombreCompleto(), s.getTipoDocumento(), s.getNumeroDocumento(),
                s.getAporte(), s.getPorcentaje(), s.getCargo());
    }
}

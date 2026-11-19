package com.pluscontable.tributario.alertaplazo;

import com.pluscontable.tributario.model.TipoNotificacion;
import java.time.LocalDate;

/**
 * HU-19: una alerta del plazo de respuesta de una notificación. diasAntes es el escalón en que se generó (5, 3, 1 o 0 días
 * antes) y diasRestantes los días que faltan hoy (hoy de Lima); este último no se guarda, se calcula al consultar.
 */
public record AlertaPlazoDto(Long id, Long notificacionId, Long clienteId, String ruc, String razonSocial, TipoNotificacion tipo,
                             LocalDate plazoRespuesta, int diasAntes, LocalDate fechaAlerta, long diasRestantes) {
}

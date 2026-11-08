package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.EstadoNotificacion;
import com.pluscontable.tributario.model.NotificacionSol;
import com.pluscontable.tributario.model.TipoNotificacion;
import java.time.LocalDate;

public record NotificacionSolDto(Long id, Long clienteId, TipoNotificacion tipo, LocalDate fechaRecepcion,
                                 LocalDate plazoRespuesta, EstadoNotificacion estado, LocalDate fechaAtencion) {
    public static NotificacionSolDto from(NotificacionSol n) {
        return new NotificacionSolDto(n.getId(), n.getClienteId(), n.getTipo(), n.getFechaRecepcion(),
                n.getPlazoRespuesta(), n.getEstado(), n.getFechaAtencion());
    }
}

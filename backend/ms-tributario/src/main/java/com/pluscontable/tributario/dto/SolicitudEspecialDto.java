package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.EstadoSolicitud;
import com.pluscontable.tributario.model.SolicitudEspecial;
import com.pluscontable.tributario.model.TipoSolicitud;
import java.time.LocalDate;

public record SolicitudEspecialDto(Long id, Long clienteId, TipoSolicitud tipo, String descripcion, LocalDate fechaEntrega,
                                   EstadoSolicitud estado, LocalDate fechaAtencion) {

    public static SolicitudEspecialDto from(SolicitudEspecial s) {
        return new SolicitudEspecialDto(s.getId(), s.getClienteId(), s.getTipo(), s.getDescripcion(), s.getFechaEntrega(),
                s.getEstado(), s.getFechaAtencion());
    }
}

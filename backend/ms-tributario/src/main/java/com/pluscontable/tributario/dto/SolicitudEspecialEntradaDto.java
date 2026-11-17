package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.TipoSolicitud;
import java.time.LocalDate;

public record SolicitudEspecialEntradaDto(TipoSolicitud tipo, String descripcion, LocalDate fechaEntrega) {
}

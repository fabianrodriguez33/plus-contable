package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.TipoNotificacion;
import java.time.LocalDate;

/** Sin anotaciones de validación: NotificacionSolService valida todo junto, como el resto de los servicios. */
public record NotificacionSolEntradaDto(TipoNotificacion tipo, LocalDate fechaRecepcion, LocalDate plazoRespuesta) {
}

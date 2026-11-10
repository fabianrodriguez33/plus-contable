package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.TipoIncidencia;
import java.time.LocalDate;

/** clienteId es obligatorio salvo en las incidencias generales (CAIDA_PORTAL_SOL). */
public record IncidenciaEntradaDto(TipoIncidencia tipo, Long clienteId, LocalDate fecha, String descripcion) {
}

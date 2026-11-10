package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.Incidencia;
import com.pluscontable.tributario.model.TipoIncidencia;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record IncidenciaDto(Long id, TipoIncidencia tipo, LocalDate fecha, String descripcion, String registradaPor,
                            LocalDateTime registradaEn, int clientesAsociados, String observaciones) {

    /** Sin observaciones (la incidencia no las tiene). */
    public IncidenciaDto(Long id, TipoIncidencia tipo, LocalDate fecha, String descripcion, String registradaPor,
                         LocalDateTime registradaEn, int clientesAsociados) {
        this(id, tipo, fecha, descripcion, registradaPor, registradaEn, clientesAsociados, null);
    }

    public static IncidenciaDto from(Incidencia i, int clientesAsociados) {
        return new IncidenciaDto(i.getId(), i.getTipo(), i.getFecha(), i.getDescripcion(), i.getRegistradaPor(),
                i.getRegistradaEn(), clientesAsociados, i.getObservaciones());
    }
}

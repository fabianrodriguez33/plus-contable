package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.Incidencia;
import com.pluscontable.tributario.model.TipoIncidencia;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record IncidenciaDto(Long id, TipoIncidencia tipo, LocalDate fecha, String descripcion, String registradaPor,
                            LocalDateTime registradaEn, int clientesAsociados) {

    public static IncidenciaDto from(Incidencia i, int clientesAsociados) {
        return new IncidenciaDto(i.getId(), i.getTipo(), i.getFecha(), i.getDescripcion(), i.getRegistradaPor(),
                i.getRegistradaEn(), clientesAsociados);
    }
}

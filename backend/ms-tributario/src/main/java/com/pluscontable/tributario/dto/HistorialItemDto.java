package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.SolicitudEspecial;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * HU-27: un registro del historial del cliente, sea una incidencia (HU-23) o una solicitud especial (HU-26).
 * tipo es el del registro (TipoIncidencia o TipoSolicitud); fecha es la de la incidencia o el día en que se pidió la solicitud.
 * general solo aplica a incidencias; estado y fechaEntrega, solo a solicitudes.
 */
public record HistorialItemDto(Origen origen, Long id, String tipo, LocalDate fecha, String descripcion, boolean general,
                               String estado, LocalDate fechaEntrega, LocalDateTime registradaEn) {

    public enum Origen { INCIDENCIA, SOLICITUD }

    public static HistorialItemDto de(IncidenciaDto i) {
        return new HistorialItemDto(Origen.INCIDENCIA, i.id(), i.tipo().name(), i.fecha(), i.descripcion(), i.tipo().esGeneral(),
                null, null, i.registradaEn());
    }

    public static HistorialItemDto de(SolicitudEspecial s) {
        return new HistorialItemDto(Origen.SOLICITUD, s.getId(), s.getTipo().name(), s.getRegistradaEn().toLocalDate(),
                s.getDescripcion(), false, s.getEstado().name(), s.getFechaEntrega(), s.getRegistradaEn());
    }
}

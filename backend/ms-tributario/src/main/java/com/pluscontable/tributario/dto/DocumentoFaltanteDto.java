package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.DocumentoFaltante;
import com.pluscontable.tributario.model.EstadoDocumentoFaltante;
import java.time.LocalDate;

public record DocumentoFaltanteDto(Long id, Long clienteId, String documento, LocalDate fechaSolicitud, LocalDate fechaLimite,
                                   EstadoDocumentoFaltante estado, LocalDate fechaEntrega, Long incidenciaId) {

    /** El estado visible: un PENDIENTE cuya fecha límite ya pasó se muestra SIN_RESPUESTA. */
    public static DocumentoFaltanteDto from(DocumentoFaltante d, LocalDate hoy) {
        return new DocumentoFaltanteDto(d.getId(), d.getClienteId(), d.getDocumento(), d.getFechaSolicitud(), d.getFechaLimite(),
                estadoVisible(d, hoy), d.getFechaEntrega(), d.getIncidenciaId());
    }

    public static EstadoDocumentoFaltante estadoVisible(DocumentoFaltante d, LocalDate hoy) {
        if (d.getEstado() == DocumentoFaltante.Estado.ENTREGADO) return EstadoDocumentoFaltante.ENTREGADO;
        return d.getFechaLimite().isBefore(hoy) ? EstadoDocumentoFaltante.SIN_RESPUESTA : EstadoDocumentoFaltante.PENDIENTE;
    }
}

package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.DocumentoLegal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DocumentoDto(Long id, DocumentoLegal.Tipo tipo, int version, LocalDate fechaEmision,
                           String nombreArchivo, long tamanoBytes, LocalDateTime subidoEn) {

    public static DocumentoDto from(DocumentoLegal d) {
        return new DocumentoDto(d.getId(), d.getTipo(), d.getVersion(), d.getFechaEmision(),
                d.getNombreArchivo(), d.getTamanoBytes(), d.getSubidoEn());
    }
}

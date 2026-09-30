package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.DocumentoLegal;
import java.time.LocalDateTime;

public record DocumentoDto(Long id, Long clienteId, DocumentoLegal.Tipo tipo, String nombreArchivo,
                           String contentType, long tamanoBytes, LocalDateTime subidoEn) {

    public static DocumentoDto from(DocumentoLegal d) {
        return new DocumentoDto(d.getId(), d.getClienteId(), d.getTipo(), d.getNombreArchivo(),
                d.getContentType(), d.getTamanoBytes(), d.getSubidoEn());
    }
}

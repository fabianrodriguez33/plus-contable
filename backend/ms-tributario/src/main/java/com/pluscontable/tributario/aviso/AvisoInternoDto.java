package com.pluscontable.tributario.aviso;

import java.time.Instant;
import java.util.List;

/** Bandeja compartida del estudio: la lectura es global para cada aviso. */
public record AvisoInternoDto(Long id, String origen, String claveEvento, String mensaje,
                              Instant creadoEn, Instant leidoEn, List<Long> clienteIds) {
    public AvisoInternoDto {
        clienteIds = List.copyOf(clienteIds);
    }
}

package com.pluscontable.tributario.dto;

import java.time.LocalDate;
import java.util.List;

/** HU-10: nueva fecha límite para una o varias obligaciones. ObligacionService valida todo junto. */
public record CambioVencimientoDto(List<Long> obligacionIds, LocalDate nuevaFecha) {
}

package com.pluscontable.tributario.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/** HU-10: nueva fecha límite para una o varias obligaciones. */
public record CambioVencimientoDto(
        @NotEmpty(message = "obligacionIds: indique al menos una obligación") List<Long> obligacionIds,
        @NotNull(message = "nuevaFecha: es obligatoria") LocalDate nuevaFecha) {
}

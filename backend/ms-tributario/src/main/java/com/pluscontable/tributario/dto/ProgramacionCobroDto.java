package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.Periodicidad;
import java.util.List;

/** Resultado de programar: cuántos cobros se crearon, cuántos ya existían y los pendientes del cliente. */
public record ProgramacionCobroDto(Periodicidad periodicidad, int cobrosGenerados, int cobrosYaExistentes,
                                   List<CobroHonorarioDto> cobros) {
}

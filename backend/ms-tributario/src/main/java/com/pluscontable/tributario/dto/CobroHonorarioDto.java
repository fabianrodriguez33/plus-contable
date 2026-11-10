package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.CobroHonorario;
import com.pluscontable.tributario.model.EstadoCobro;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CobroHonorarioDto(Long id, LocalDate fechaCobro, BigDecimal monto, EstadoCobro estado) {

    public static CobroHonorarioDto from(CobroHonorario c) {
        return new CobroHonorarioDto(c.getId(), c.getFechaCobro(), c.getMonto(), c.getEstado());
    }
}

package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.Obligacion;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ObligacionDto(Long id, Long clienteId, LocalDate periodo, LocalDate fechaVencimiento,
                            Obligacion.Estado estado, LocalDate fechaDeclaracion, String numeroFormulario,
                            BigDecimal montoDeterminado) {

    public static ObligacionDto from(Obligacion o) {
        return new ObligacionDto(o.getId(), o.getClienteId(), o.getPeriodo(), o.getFechaVencimiento(), o.getEstado(),
                o.getFechaDeclaracion(), o.getNumeroFormulario(), o.getMontoDeterminado());
    }
}

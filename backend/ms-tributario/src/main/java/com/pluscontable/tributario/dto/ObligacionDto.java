package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ObligacionDto(Long id, Long clienteId, TipoObligacion tipoObligacion,
                            LocalDate periodo, LocalDate fechaVencimiento,
                            Obligacion.Estado estado, LocalDate fechaDeclaracion, String numeroFormulario,
                            BigDecimal montoDeterminado) {

    public static ObligacionDto from(Obligacion o) {
        return new ObligacionDto(o.getId(), o.getClienteId(), o.getTipoObligacion(),
                o.getPeriodo(), o.getFechaVencimiento(), o.getEstado(),
                o.getFechaDeclaracion(), o.getNumeroFormulario(), o.getMontoDeterminado());
    }
}

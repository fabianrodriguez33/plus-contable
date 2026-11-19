package com.pluscontable.tributario.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Los tres últimos campos (HU-13) solo se calculan al registrar un pago; en las demás respuestas van nulos y no se
 * envían.
 */
public record ObligacionDto(Long id, Long clienteId, TipoObligacion tipoObligacion,
                            LocalDate periodo, LocalDate fechaVencimiento,
                            Obligacion.Estado estado, LocalDate fechaDeclaracion, String numeroFormulario,
                            BigDecimal montoDeterminado,
                            @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal montoAcumuladoPagado,
                            @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal saldo,
                            @JsonInclude(JsonInclude.Include.NON_NULL) Integer numeroPagos) {

    public ObligacionDto(Long id, Long clienteId, TipoObligacion tipoObligacion,
                         LocalDate periodo, LocalDate fechaVencimiento,
                         Obligacion.Estado estado, LocalDate fechaDeclaracion, String numeroFormulario,
                         BigDecimal montoDeterminado) {
        this(id, clienteId, tipoObligacion, periodo, fechaVencimiento, estado, fechaDeclaracion, numeroFormulario,
                montoDeterminado, null, null, null);
    }

    public static ObligacionDto from(Obligacion o) {
        return new ObligacionDto(o.getId(), o.getClienteId(), o.getTipoObligacion(),
                o.getPeriodo(), o.getFechaVencimiento(), o.getEstado(),
                o.getFechaDeclaracion(), o.getNumeroFormulario(), o.getMontoDeterminado());
    }

    /** Con el acumulado de pagos, el saldo (monto determinado menos el acumulado) y el número de pagos. */
    public static ObligacionDto from(Obligacion o, BigDecimal acumulado, int numeroPagos) {
        BigDecimal determinado = o.getMontoDeterminado() == null ? BigDecimal.ZERO : o.getMontoDeterminado();
        return new ObligacionDto(o.getId(), o.getClienteId(), o.getTipoObligacion(),
                o.getPeriodo(), o.getFechaVencimiento(), o.getEstado(),
                o.getFechaDeclaracion(), o.getNumeroFormulario(), o.getMontoDeterminado(),
                acumulado, determinado.subtract(acumulado), numeroPagos);
    }
}

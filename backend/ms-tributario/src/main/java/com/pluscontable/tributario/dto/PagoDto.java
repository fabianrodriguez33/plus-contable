package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.PagoObligacion;
import java.math.BigDecimal;
import java.time.LocalDate;

/** HU-13: un pago registrado en una obligación, con el acumulado y el saldo que quedaron después de él. */
public record PagoDto(Long id, LocalDate fechaPago, BigDecimal montoPagado, String numeroConstancia,
                      BigDecimal acumuladoDespues, BigDecimal saldoDespues) {

    public static PagoDto from(PagoObligacion p) {
        return new PagoDto(p.getId(), p.getFechaPago(), p.getMontoPagado(), p.getNumeroConstancia(),
                p.getAcumuladoDespues(), p.getSaldoDespues());
    }
}

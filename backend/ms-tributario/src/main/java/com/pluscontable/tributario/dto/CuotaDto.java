package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.CuotaFraccionamiento;
import com.pluscontable.tributario.model.EstadoCuota;
import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code enRiesgo}: cuota PENDIENTE con fecha anterior a hoy; se resalta como riesgo de perder el fraccionamiento. */
public record CuotaDto(Long id, int numero, LocalDate fechaVencimiento, BigDecimal monto, EstadoCuota estado,
                       LocalDate fechaPago, boolean enRiesgo) {

    public static CuotaDto from(CuotaFraccionamiento c, LocalDate hoy) {
        boolean enRiesgo = c.getEstado() == EstadoCuota.PENDIENTE && c.getFechaVencimiento().isBefore(hoy);
        return new CuotaDto(c.getId(), c.getNumero(), c.getFechaVencimiento(), c.getMonto(), c.getEstado(),
                c.getFechaPago(), enRiesgo);
    }
}

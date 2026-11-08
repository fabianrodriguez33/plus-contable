package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.Fraccionamiento;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FraccionamientoDto(Long id, Long clienteId, TipoObligacion tributo, BigDecimal montoTotal,
                                 int numeroCuotas, LocalDate fechaPrimeraCuota, List<CuotaDto> cuotas) {

    public static FraccionamientoDto from(Fraccionamiento f, List<CuotaDto> cuotas) {
        return new FraccionamientoDto(f.getId(), f.getClienteId(), f.getTributo(), f.getMontoTotal(),
                f.getNumeroCuotas(), f.getFechaPrimeraCuota(), cuotas);
    }
}

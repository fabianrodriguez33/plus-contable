package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.obligacion.TipoObligacion;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Sin anotaciones de validación: FraccionamientoService valida todo junto, como el resto de los servicios. */
public record FraccionamientoEntradaDto(TipoObligacion tributo, BigDecimal montoTotal, Integer numeroCuotas,
                                        LocalDate fechaPrimeraCuota) {
}

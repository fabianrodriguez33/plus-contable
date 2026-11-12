package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.MedioPago;
import java.math.BigDecimal;
import java.time.LocalDate;

/** medioPagoDetalle: obligatorio solo con medioPago OTRO; con los demás medios se ignora. */
public record PagoHonorarioEntradaDto(LocalDate fechaPago, BigDecimal monto, MedioPago medioPago, String medioPagoDetalle) {
}

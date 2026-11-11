package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.MedioPago;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PagoHonorarioEntradaDto(LocalDate fechaPago, BigDecimal monto, MedioPago medioPago) {
}

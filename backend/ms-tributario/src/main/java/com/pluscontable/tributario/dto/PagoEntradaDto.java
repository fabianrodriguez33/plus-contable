package com.pluscontable.tributario.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Datos de un pago; el comprobante (PDF) llega aparte como archivo. Sin anotaciones: PagoObligacionService valida todo junto. */
public record PagoEntradaDto(LocalDate fechaPago, BigDecimal montoPagado, String numeroConstancia) {
}

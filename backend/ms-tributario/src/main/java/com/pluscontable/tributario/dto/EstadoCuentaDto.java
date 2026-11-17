package com.pluscontable.tributario.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * HU-30: estado de cuenta de honorarios de un cliente en [desde, hasta]. saldo = saldoAnterior + totalCobrado - totalPagado;
 * saldoAnterior es lo que se debía antes de desde. Un saldo negativo es a favor del cliente: los pagos entran por su fecha
 * de pago, así que un pago adelantado de un cobro posterior al periodo resta aunque su cobro no esté en el periodo.
 */
public record EstadoCuentaDto(Long clienteId, String razonSocial, LocalDate desde, LocalDate hasta, BigDecimal saldoAnterior,
                              List<CobroHonorarioDto> cobros, List<PagoHonorarioDto> pagos, BigDecimal totalCobrado,
                              BigDecimal totalPagado, BigDecimal saldo, boolean sinMovimientos) {
}

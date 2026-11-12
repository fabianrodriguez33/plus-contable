package com.pluscontable.tributario.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * HU-31: un cliente con cobros de honorarios vencidos y sin pagar del todo. montoVencido = suma de los saldos vencidos;
 * diasAtraso = días desde el cobro vencido más antiguo.
 */
public record MorosoDto(Long clienteId, String ruc, String razonSocial, BigDecimal montoVencido, long cobrosVencidos,
                        LocalDate vencidoDesde, long diasAtraso) {
}

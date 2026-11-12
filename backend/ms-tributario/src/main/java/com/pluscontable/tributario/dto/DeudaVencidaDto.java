package com.pluscontable.tributario.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** HU-31: lo que la base agrupa por cliente; los días de atraso se calculan después con la fecha de hoy. */
public record DeudaVencidaDto(Long clienteId, String ruc, String razonSocial, BigDecimal montoVencido, Long cobrosVencidos,
                              LocalDate vencidoDesde) {
}

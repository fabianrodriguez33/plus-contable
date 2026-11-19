package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * HU-14: una obligación en el tablero Declarado vs. Pagado. montoPagado es la suma de sus pagos (HU-13) y saldo = monto
 * determinado - pagado (nulo si aún no tiene monto determinado). Definición de Beto: vencida = fecha límite pasada y saldo
 * pendiente (DECLARADO sin pago o PAGO_PARCIAL); la sin declarar con fecha pasada no es vencida sino sinDeclarar (pendiente de
 * atención). Se calculan con la fecha de hoy; no se guardan.
 */
public record TableroFilaDto(Long obligacionId, Long clienteId, String ruc, String razonSocial, TipoObligacion tipoObligacion,
                             LocalDate periodo, LocalDate fechaVencimiento, Obligacion.Estado estado,
                             BigDecimal montoDeterminado, BigDecimal montoPagado, BigDecimal saldo, boolean vencida, boolean sinDeclarar) {
}

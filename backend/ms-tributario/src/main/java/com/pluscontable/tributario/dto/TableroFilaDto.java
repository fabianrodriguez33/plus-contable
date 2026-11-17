package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * HU-14: una obligación en el tablero Declarado vs. Pagado. montoPagado es la suma de sus pagos (HU-13) y saldo = monto
 * determinado - pagado (nulo si aún no tiene monto determinado). vencida se calcula con la fecha de hoy: solo las
 * PENDIENTE o con PAGO_PARCIAL cuya fecha límite ya pasó; no se guarda.
 */
public record TableroFilaDto(Long obligacionId, Long clienteId, String ruc, String razonSocial, TipoObligacion tipoObligacion,
                             LocalDate periodo, LocalDate fechaVencimiento, Obligacion.Estado estado,
                             BigDecimal montoDeterminado, BigDecimal montoPagado, BigDecimal saldo, boolean vencida) {
}

package com.pluscontable.tributario.recordatoriopago;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Un recordatorio programado por el contador; solo lo ven CONTADOR y ADMIN. {@code saldo} es el saldo pendiente de la obligación hoy
 * (monto determinado menos lo pagado; nulo si no tiene monto determinado): es lo que recibirá el cliente si se envía ahora.
 */
public record RecordatorioPagoDto(Long id, Long obligacionId, LocalDate fechaRecordatorio, String estado,
                                  String programadoPor, Instant programadoEn, Instant resueltoEn, BigDecimal saldo) {
}

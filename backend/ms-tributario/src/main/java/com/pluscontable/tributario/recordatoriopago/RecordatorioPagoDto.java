package com.pluscontable.tributario.recordatoriopago;

import java.time.Instant;
import java.time.LocalDate;

/** Un recordatorio programado por el contador; solo lo ven CONTADOR y ADMIN. */
public record RecordatorioPagoDto(Long id, Long obligacionId, LocalDate fechaRecordatorio, String estado,
                                  String programadoPor, Instant programadoEn, Instant resueltoEn) {
}

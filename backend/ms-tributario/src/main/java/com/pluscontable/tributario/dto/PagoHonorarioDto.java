package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.model.MedioPago;
import java.math.BigDecimal;
import java.time.LocalDate;

/** HU-30: un pago recibido, con la fecha del cobro al que se aplicó. */
public record PagoHonorarioDto(Long id, Long cobroId, LocalDate fechaCobro, LocalDate fechaPago, BigDecimal monto,
                               MedioPago medioPago, String medioPagoDetalle) {
}

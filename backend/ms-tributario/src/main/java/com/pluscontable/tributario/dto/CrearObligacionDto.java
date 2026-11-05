package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.obligacion.TipoObligacion;
import java.time.LocalDate;

/** La fecha se indica explícitamente hasta confirmar las reglas de Beto. */
public record CrearObligacionDto(Long clienteId, TipoObligacion tipoObligacion,
                                 LocalDate periodo, LocalDate fechaVencimiento) {
}

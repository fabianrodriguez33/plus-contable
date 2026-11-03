package com.pluscontable.tributario.dto;

import com.pluscontable.tributario.obligacion.GeneradorObligaciones.Omision;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Resultado de programar un mes: lo creado, lo que ya existía (no se duplica) y los tipos que no se generan,
 * cada uno con su motivo (por ejemplo AFP, que no tiene cronograma propio).
 */
public record ProgramacionMesDto(YearMonth periodo, LocalDate fechaVencimiento, List<ObligacionDto> creadas,
                                 List<TipoObligacion> yaExistian, List<Omision> noGeneradas) {
}

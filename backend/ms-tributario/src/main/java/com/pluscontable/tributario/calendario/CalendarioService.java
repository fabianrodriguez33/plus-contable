package com.pluscontable.tributario.calendario;

import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.service.ObligacionService;
import jakarta.validation.ValidationException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CalendarioService {
    public enum Situacion { PROXIMO, VENCIDO }

    private final ObligacionService obligaciones;
    private final Clock clock;

    @Autowired
    public CalendarioService(ObligacionService obligaciones) {
        this(obligaciones, Clock.systemDefaultZone());
    }

    CalendarioService(ObligacionService obligaciones, Clock clock) {
        this.obligaciones = obligaciones;
        this.clock = clock;
    }

    public List<CalendarioObligacionDto> listar(Long clienteId, LocalDate desde, LocalDate hasta,
                                                LocalDate periodo, Obligacion.Estado estado,
                                                Situacion situacion) {
        if (clienteId == null || clienteId <= 0) throw new ValidationException("clienteId debe ser positivo");
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ValidationException("desde no puede superar hasta");
        }
        if (periodo != null && periodo.getDayOfMonth() != 1) {
            throw new ValidationException("periodo debe ser el primer día del mes");
        }
        LocalDate hoy = LocalDate.now(clock);
        if (situacion == Situacion.PROXIMO && (hasta == null || hasta.isBefore(hoy))) {
            throw new ValidationException("PROXIMO requiere hasta igual o posterior a hoy");
        }
        return obligaciones.listar(clienteId).stream()
                .filter(o -> o.fechaVencimiento() != null)
                .filter(o -> desde == null || !o.fechaVencimiento().isBefore(desde))
                .filter(o -> hasta == null || !o.fechaVencimiento().isAfter(hasta))
                .filter(o -> periodo == null || periodo.equals(o.periodo()))
                .filter(o -> estado == null || estado == o.estado())
                .filter(o -> situacion != Situacion.PROXIMO || !o.fechaVencimiento().isBefore(hoy))
                .filter(o -> situacion != Situacion.VENCIDO || o.fechaVencimiento().isBefore(hoy))
                .map(o -> CalendarioObligacionDto.from(o, clock))
                .sorted(Comparator.comparing(CalendarioObligacionDto::fechaVencimiento)
                        .thenComparing(o -> o.obligacion().id()))
                .toList();
    }
}

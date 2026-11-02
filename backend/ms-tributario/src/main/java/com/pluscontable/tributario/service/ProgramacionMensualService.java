package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.ObligacionDto;
import com.pluscontable.tributario.dto.ProgramacionMesDto;
import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.obligacion.CronogramaConsultaService;
import com.pluscontable.tributario.obligacion.GeneradorObligaciones;
import com.pluscontable.tributario.obligacion.GeneradorObligaciones.Clave;
import com.pluscontable.tributario.obligacion.GeneradorObligaciones.Solicitud;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import com.pluscontable.tributario.repository.ObligacionRepository;
import jakarta.validation.ValidationException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HU-07: programa las obligaciones de un mes con la fecha del cronograma SUNAT del cliente. */
@Service
public class ProgramacionMensualService {
    private final CronogramaConsultaService cronograma;
    private final ObligacionRepository obligaciones;

    public ProgramacionMensualService(CronogramaConsultaService cronograma, ObligacionRepository obligaciones) {
        this.cronograma = cronograma;
        this.obligaciones = obligaciones;
    }

    @Transactional
    public ProgramacionMesDto programar(Long clienteId, YearMonth periodo) {
        if (clienteId == null || clienteId <= 0 || periodo == null) {
            throw new ValidationException("clienteId y periodo son obligatorios");
        }
        // 404 si el cliente no existe; sin cronograma del periodo no se programa nada
        LocalDate fecha = cronograma.buscar(clienteId, periodo)
                .orElseThrow(() -> new ValidationException("Falta el cronograma de " + periodo));
        LocalDate primerDia = periodo.atDay(1);

        Set<Clave> existentes = new HashSet<>();
        for (Obligacion o : obligaciones.findByClienteIdOrderByPeriodoDesc(clienteId)) {
            if (o.getTipoObligacion() != null && primerDia.equals(o.getPeriodo())) {
                existentes.add(new Clave(clienteId, o.getTipoObligacion(), primerDia));
            }
        }
        List<Solicitud> pedidas = GeneradorObligaciones.solicitudesDelMes(clienteId, primerDia, fecha);
        List<Solicitud> nuevas = GeneradorObligaciones.preparar(pedidas, existentes);

        List<ObligacionDto> creadas = nuevas.stream().map(this::guardar).toList();
        List<TipoObligacion> yaExistian = pedidas.stream().map(s -> s.clave().tipoObligacion())
                .filter(tipo -> nuevas.stream().noneMatch(n -> n.clave().tipoObligacion() == tipo)).toList();
        return new ProgramacionMesDto(periodo, fecha, creadas, yaExistian, GeneradorObligaciones.TIPOS_NO_GENERADOS);
    }

    private ObligacionDto guardar(Solicitud solicitud) {
        Obligacion o = new Obligacion();
        o.setClienteId(solicitud.clave().clienteId());
        o.setTipoObligacion(solicitud.clave().tipoObligacion());
        o.setPeriodo(solicitud.clave().periodo());
        o.setFechaVencimiento(solicitud.fechaVencimiento());
        return ObligacionDto.from(obligaciones.saveAndFlush(o));
    }
}

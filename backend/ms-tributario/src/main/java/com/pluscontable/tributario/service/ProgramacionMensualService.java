package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.ObligacionDto;
import com.pluscontable.tributario.dto.ProgramacionMesDto;
import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.model.Trabajador;
import com.pluscontable.tributario.obligacion.CronogramaConsultaService;
import com.pluscontable.tributario.obligacion.GeneradorObligaciones;
import com.pluscontable.tributario.obligacion.GeneradorObligaciones.Clave;
import com.pluscontable.tributario.obligacion.GeneradorObligaciones.ReglasMes;
import com.pluscontable.tributario.obligacion.GeneradorObligaciones.Solicitud;
import com.pluscontable.tributario.obligacion.ParametrosTributarios;
import com.pluscontable.tributario.obligacion.ReglaQuinta;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import com.pluscontable.tributario.repository.ObligacionRepository;
import com.pluscontable.tributario.repository.TrabajadorRepository;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
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
    private final TrabajadorRepository trabajadores;
    private final ParametrosTributarios parametros;

    public ProgramacionMensualService(CronogramaConsultaService cronograma, ObligacionRepository obligaciones,
                                      TrabajadorRepository trabajadores, ParametrosTributarios parametros) {
        this.cronograma = cronograma;
        this.obligaciones = obligaciones;
        this.trabajadores = trabajadores;
        this.parametros = parametros;
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
        var tipos = GeneradorObligaciones.tiposDelMes(reglasDelMes(clienteId, periodo));
        List<Solicitud> pedidas = GeneradorObligaciones.solicitudesDelMes(clienteId, primerDia, fecha, tipos.generar());
        List<Solicitud> nuevas = GeneradorObligaciones.preparar(pedidas, existentes);

        List<ObligacionDto> creadas = nuevas.stream().map(this::guardar).toList();
        List<TipoObligacion> yaExistian = pedidas.stream().map(s -> s.clave().tipoObligacion())
                .filter(tipo -> nuevas.stream().noneMatch(n -> n.clave().tipoObligacion() == tipo)).toList();
        return new ProgramacionMesDto(periodo, fecha, creadas, yaExistian, tipos.omitidas());
    }

    /** Trabajadores ya ingresados al cierre del periodo; el modelo no tiene fecha de cese. */
    private ReglasMes reglasDelMes(Long clienteId, YearMonth periodo) {
        List<Trabajador> activos = trabajadores.findByClienteIdOrderById(clienteId).stream()
                .filter(t -> t.getFechaIngreso() != null && !t.getFechaIngreso().isAfter(periodo.atEndOfMonth()))
                .toList();
        boolean conRemuneracion = activos.stream().map(Trabajador::getRemuneracion)
                .anyMatch(r -> r != null && r.compareTo(BigDecimal.ZERO) > 0);
        String sinQuinta = ReglaQuinta.motivoDeOmision(parametros, periodo.getYear(), activos).orElse(null);
        return new ReglasMes(!activos.isEmpty(), conRemuneracion, sinQuinta);
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

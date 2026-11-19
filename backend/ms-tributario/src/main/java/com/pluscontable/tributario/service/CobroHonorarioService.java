package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.CobroHonorarioDto;
import com.pluscontable.tributario.dto.ProgramacionCobroDto;
import com.pluscontable.tributario.dto.ProgramacionCobroEntradaDto;
import com.pluscontable.tributario.model.AcuerdoServicio;
import com.pluscontable.tributario.model.CobroHonorario;
import com.pluscontable.tributario.model.EstadoCobro;
import com.pluscontable.tributario.model.Periodicidad;
import com.pluscontable.tributario.model.ProgramacionCobro;
import com.pluscontable.tributario.repository.AcuerdoRepository;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.CobroHonorarioRepository;
import com.pluscontable.tributario.repository.ProgramacionCobroRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-28 · Programar el cobro periódico de honorarios. Con el acuerdo de honorarios del cliente (HU-05) genera los
 * cobros PENDIENTE de los próximos 12 meses según la periodicidad. El sistema programa los cobros; no emite comprobantes.
 */
@Service
public class CobroHonorarioService {

    static final int MESES = 12;
    private static final BigDecimal MINIMO_QUINCENAL = new BigDecimal("0.02");
    /** HU-29: un pago parcial deja el cobro pendiente de saldo; se sigue viendo entre los pendientes. */
    static final List<EstadoCobro> CON_SALDO = List.of(EstadoCobro.PENDIENTE, EstadoCobro.PAGO_PARCIAL);
    private static final List<EstadoCobro> CON_PAGO = List.of(EstadoCobro.PAGO_PARCIAL, EstadoCobro.PAGADO);

    private final ClienteRepository clientes;
    private final AcuerdoRepository acuerdos;
    private final ProgramacionCobroRepository programaciones;
    private final CobroHonorarioRepository cobros;
    private final Clock clock;

    @Autowired
    public CobroHonorarioService(ClienteRepository clientes, AcuerdoRepository acuerdos,
                                 ProgramacionCobroRepository programaciones, CobroHonorarioRepository cobros) {
        this(clientes, acuerdos, programaciones, cobros, Clock.systemDefaultZone());
    }

    CobroHonorarioService(ClienteRepository clientes, AcuerdoRepository acuerdos,
                          ProgramacionCobroRepository programaciones, CobroHonorarioRepository cobros, Clock clock) {
        this.clientes = clientes;
        this.acuerdos = acuerdos;
        this.programaciones = programaciones;
        this.cobros = cobros;
        this.clock = clock;
    }

    /**
     * 400 si falta la periodicidad o el cliente no tiene acuerdo (criterio 3); 404 si el cliente no existe. Volver a
     * programar no duplica fechas; si cambia la periodicidad, los cobros pendientes desde hoy se reemplazan por los nuevos
     * para no cobrar dos veces el mismo mes.
     */
    @Transactional
    public ProgramacionCobroDto programar(Long clienteId, ProgramacionCobroEntradaDto dto) {
        if (dto == null || dto.periodicidad() == null) {
            throw new ValidationException("Faltan o son inválidos: periodicidad");
        }
        exigirCliente(clienteId);
        AcuerdoServicio acuerdo = acuerdos.findByClienteId(clienteId)
                .orElseThrow(() -> new ValidationException("Registre primero el acuerdo de honorarios del cliente"));

        if (dto.periodicidad() == Periodicidad.QUINCENAL && acuerdo.getHonorarioMensual().compareTo(MINIMO_QUINCENAL) < 0) {
            throw new ValidationException("El honorario mensual debe ser al menos 0.02 para cobrarlo por quincenas");
        }

        LocalDate hoy = LocalDate.now(clock);
        ProgramacionCobro programacion = programaciones.findByClienteId(clienteId).orElse(null);
        if (programacion == null) {
            programacion = new ProgramacionCobro();
            programacion.setClienteId(clienteId);
        } else if (programacion.getPeriodicidad() != dto.periodicidad()) {
            // Un pendiente vencido de este mes seguiría vivo y el mes se cobraría dos veces con el nuevo esquema.
            if (cobros.existsByClienteIdAndEstadoAndFechaCobroBetween(clienteId, EstadoCobro.PENDIENTE, hoy.withDayOfMonth(1), hoy.minusDays(1))) {
                throw new ValidationException("No se puede cambiar la periodicidad: hay un cobro vencido de este mes sin pagar. "
                        + "Cambie la periodicidad desde el mes siguiente");
            }
            // HU-29: un cobro con pago de este mes o posterior no se borra (se perdería el pago) y, si queda, el mes se cobraría
            // dos veces con el nuevo esquema.
            if (cobros.existsByClienteIdAndEstadoInAndFechaCobroGreaterThanEqual(clienteId, CON_PAGO, hoy.withDayOfMonth(1))) {
                throw new ValidationException("No se puede cambiar la periodicidad: hay cobros de este mes o posteriores con pagos "
                        + "registrados. Cambie la periodicidad desde el mes siguiente");
            }
            cobros.deleteByClienteIdAndEstadoAndFechaCobroGreaterThanEqual(clienteId, EstadoCobro.PENDIENTE, hoy);
            cobros.flush(); // los borrados van antes que los nuevos: pueden caer en la misma fecha
        }
        programacion.setPeriodicidad(dto.periodicidad());
        programacion = programaciones.save(programacion);

        LocalDate desde = acuerdo.getFechaInicio().isAfter(hoy) ? acuerdo.getFechaInicio() : hoy;
        int generados = 0;
        int existentes = 0;
        for (LocalDate fecha : fechasDeCobro(dto.periodicidad(), acuerdo.getFechaInicio().getDayOfMonth(), desde)) {
            if (cobros.existsByClienteIdAndFechaCobro(clienteId, fecha)) {
                existentes++;
                continue;
            }
            CobroHonorario c = new CobroHonorario();
            c.setClienteId(clienteId);
            c.setProgramacionId(programacion.getId());
            c.setFechaCobro(fecha);
            c.setMonto(monto(dto.periodicidad(), acuerdo.getHonorarioMensual(), fecha));
            cobros.save(c);
            generados++;
        }
        return new ProgramacionCobroDto(dto.periodicidad(), generados, existentes, listarPendientes(clienteId));
    }

    /** Los cobros pendientes del cliente, el más próximo primero (criterio 4). 404 si el cliente no existe. */
    @Transactional(readOnly = true)
    public List<CobroHonorarioDto> pendientes(Long clienteId) {
        exigirCliente(clienteId);
        return listarPendientes(clienteId);
    }

    /**
     * Fechas de cobro en [desde, desde + 12 meses). MENSUAL: el día de inicio del acuerdo (el último del mes si ese día
     * no existe); QUINCENAL: los días 15 y último de cada mes.
     */
    static List<LocalDate> fechasDeCobro(Periodicidad periodicidad, int diaDelAcuerdo, LocalDate desde) {
        LocalDate hasta = desde.plusMonths(MESES);
        List<LocalDate> fechas = new ArrayList<>();
        for (YearMonth mes = YearMonth.from(desde); !mes.atDay(1).isAfter(hasta); mes = mes.plusMonths(1)) {
            List<LocalDate> delMes = periodicidad == Periodicidad.MENSUAL
                    ? List.of(mes.atDay(Math.min(diaDelAcuerdo, mes.lengthOfMonth())))
                    : List.of(mes.atDay(15), mes.atEndOfMonth());
            for (LocalDate f : delMes) {
                if (!f.isBefore(desde) && f.isBefore(hasta)) fechas.add(f);
            }
        }
        return fechas;
    }

    /** QUINCENAL: el honorario entre 2; el céntimo impar va al cobro de fin de mes para que el mes sume exacto. */
    static BigDecimal monto(Periodicidad periodicidad, BigDecimal honorarioMensual, LocalDate fecha) {
        if (periodicidad == Periodicidad.MENSUAL) {
            return honorarioMensual.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal primera = honorarioMensual.divide(BigDecimal.valueOf(2), 2, RoundingMode.DOWN);
        return fecha.getDayOfMonth() == 15 ? primera : honorarioMensual.setScale(2, RoundingMode.HALF_UP).subtract(primera);
    }

    private List<CobroHonorarioDto> listarPendientes(Long clienteId) {
        return cobros.findByClienteIdAndEstadoInOrderByFechaCobroAsc(clienteId, CON_SALDO).stream()
                .map(CobroHonorarioDto::from).toList();
    }

    private void exigirCliente(Long clienteId) {
        if (!clientes.existsById(clienteId)) {
            throw new EntityNotFoundException("Cliente no encontrado");
        }
    }
}

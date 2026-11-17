package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.PagadoPorObligacionDto;
import com.pluscontable.tributario.dto.TableroFilaDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.PagoObligacionRepository;
import com.pluscontable.tributario.repository.TableroRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-14 · Tablero Declarado vs. Pagado: las obligaciones de todos los clientes (o de uno) y de un mes (o de todos), con su
 * estado real (PENDIENTE, DECLARADO, PAGO_PARCIAL, PAGADO), su monto, su fecha límite, lo pagado y el saldo (HU-13).
 * Supuestos por confirmar con Beto: «mes» es el periodo tributario; «declarado sin pago» es el estado DECLARADO (con pago
 * parcial ya no); orden por fecha límite, cliente y tributo.
 */
@Service
public class TableroService {

    /** «Hoy» es el de Lima, aunque el servidor corra en UTC (igual que HU-31). */
    static final ZoneId ZONA = ZoneId.of("America/Lima");

    private final TableroRepository tablero;
    private final PagoObligacionRepository pagos;
    private final ClienteRepository clientes;
    private final Clock clock;

    @Autowired
    public TableroService(TableroRepository tablero, PagoObligacionRepository pagos, ClienteRepository clientes) {
        this(tablero, pagos, clientes, Clock.system(ZONA));
    }

    TableroService(TableroRepository tablero, PagoObligacionRepository pagos, ClienteRepository clientes, Clock clock) {
        this.tablero = tablero;
        this.pagos = pagos;
        this.clientes = clientes;
        this.clock = clock;
    }

    Clock reloj() {
        return clock;
    }

    /** 400 si clienteId no es positivo o periodo no es el primer día del mes; 404 si el cliente indicado no existe. */
    @Transactional(readOnly = true)
    public List<TableroFilaDto> listar(Long clienteId, LocalDate periodo, boolean soloDeclaradoSinPago) {
        if (clienteId != null && clienteId <= 0) throw new ValidationException("clienteId debe ser positivo");
        if (periodo != null && periodo.getDayOfMonth() != 1) throw new ValidationException("periodo debe ser el primer día del mes");
        if (clienteId != null && !clientes.existsById(clienteId)) throw new EntityNotFoundException("Cliente no encontrado: " + clienteId);
        LocalDate hoy = LocalDate.now(clock);

        Specification<Obligacion> filtro = (o, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (clienteId != null) p.add(cb.equal(o.get("clienteId"), clienteId));
            if (periodo != null) p.add(cb.equal(o.get("periodo"), periodo));
            if (soloDeclaradoSinPago) p.add(cb.equal(o.get("estado"), Obligacion.Estado.DECLARADO));
            return cb.and(p.toArray(Predicate[]::new));
        };
        List<Obligacion> obligaciones = tablero.findAll(filtro);
        if (obligaciones.isEmpty()) return List.of();

        Map<Long, BigDecimal> pagado = pagos.sumasPagadas(obligaciones.stream().map(Obligacion::getId).toList()).stream()
                .collect(Collectors.toMap(PagadoPorObligacionDto::obligacionId, PagadoPorObligacionDto::pagado));
        Map<Long, Cliente> porId = clientes.findAllById(obligaciones.stream().map(Obligacion::getClienteId).distinct().toList())
                .stream().collect(Collectors.toMap(Cliente::getId, Function.identity()));

        return obligaciones.stream()
                .map(o -> fila(o, porId.get(o.getClienteId()), pagado.getOrDefault(o.getId(), BigDecimal.ZERO), hoy))
                .sorted(Comparator.comparing(TableroFilaDto::fechaVencimiento)
                        .thenComparing(TableroFilaDto::razonSocial)
                        .thenComparing(f -> f.tipoObligacion() == null ? "" : f.tipoObligacion().name())
                        .thenComparing(TableroFilaDto::obligacionId))
                .toList();
    }

    private static TableroFilaDto fila(Obligacion o, Cliente c, BigDecimal pagado, LocalDate hoy) {
        BigDecimal saldo = o.getMontoDeterminado() == null ? null : o.getMontoDeterminado().subtract(pagado);
        boolean conSaldoPendiente = o.getEstado() == Obligacion.Estado.PENDIENTE || o.getEstado() == Obligacion.Estado.PAGO_PARCIAL;
        return new TableroFilaDto(o.getId(), o.getClienteId(), c.getRuc(), c.getRazonSocial(), o.getTipoObligacion(),
                o.getPeriodo(), o.getFechaVencimiento(), o.getEstado(), o.getMontoDeterminado(), pagado, saldo,
                conSaldoPendiente && o.getFechaVencimiento().isBefore(hoy));
    }
}

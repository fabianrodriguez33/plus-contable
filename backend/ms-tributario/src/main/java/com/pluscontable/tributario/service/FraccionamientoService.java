package com.pluscontable.tributario.service;

import com.pluscontable.tributario.calendario.CalendarioService;
import com.pluscontable.tributario.dto.CalendarioCuotaDto;
import com.pluscontable.tributario.dto.CuotaDto;
import com.pluscontable.tributario.dto.FraccionamientoDto;
import com.pluscontable.tributario.dto.FraccionamientoEntradaDto;
import com.pluscontable.tributario.model.CuotaFraccionamiento;
import com.pluscontable.tributario.model.EstadoCuota;
import com.pluscontable.tributario.model.Fraccionamiento;
import com.pluscontable.tributario.repository.CuotaFraccionamientoRepository;
import com.pluscontable.tributario.repository.FraccionamientoRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-17 · Registrar el fraccionamiento tributario vigente de un cliente y generar sus cuotas. Las cuotas aparecen en el
 * calendario y la PENDIENTE con fecha pasada se marca en riesgo de perder el fraccionamiento.
 */
@Service
public class FraccionamientoService {

    private static final int MINIMO_CUOTAS = 2;
    private static final BigDecimal MAXIMO_MONTO = new BigDecimal("999999999999.99");
    private static final LocalDate DESDE_POR_DEFECTO = LocalDate.of(1900, 1, 1);
    private static final LocalDate HASTA_POR_DEFECTO = LocalDate.of(2999, 12, 31);

    private final FraccionamientoRepository fraccionamientos;
    private final CuotaFraccionamientoRepository cuotas;
    private final ClienteService clientes;
    private final int maximoCuotas;
    private final Clock clock;

    // shortcut: el máximo de cuotas es una propiedad hasta que exista parametros_sistema (diseño de HU-12)
    @Autowired
    public FraccionamientoService(FraccionamientoRepository fraccionamientos, CuotaFraccionamientoRepository cuotas,
                                  ClienteService clientes, @Value("${app.fraccionamiento.max-cuotas:36}") int maximoCuotas) {
        this(fraccionamientos, cuotas, clientes, maximoCuotas, Clock.systemDefaultZone());
    }

    FraccionamientoService(FraccionamientoRepository fraccionamientos, CuotaFraccionamientoRepository cuotas,
                           ClienteService clientes, int maximoCuotas, Clock clock) {
        this.fraccionamientos = fraccionamientos;
        this.cuotas = cuotas;
        this.clientes = clientes;
        this.maximoCuotas = maximoCuotas;
        this.clock = clock;
    }

    /**
     * 404 si el cliente no existe; 400 con todos los errores juntos si falta el monto, el número de cuotas o la fecha.
     * Las cuotas son mensuales (el mismo día del mes, el último si ese día no existe) y la última ajusta los centavos.
     */
    @Transactional
    public FraccionamientoDto registrar(Long clienteId, FraccionamientoEntradaDto dto) {
        clientes.buscar(clienteId);
        validar(dto);

        Fraccionamiento f = new Fraccionamiento();
        f.setClienteId(clienteId);
        f.setTributo(dto.tributo());
        f.setMontoTotal(dto.montoTotal().setScale(2, RoundingMode.HALF_UP));
        f.setNumeroCuotas(dto.numeroCuotas());
        f.setFechaPrimeraCuota(dto.fechaPrimeraCuota());
        Fraccionamiento guardado = fraccionamientos.save(f);

        List<CuotaFraccionamiento> generadas = generarCuotas(guardado);
        List<CuotaFraccionamiento> cuotasGuardadas = cuotas.saveAll(generadas);
        LocalDate hoy = LocalDate.now(clock);
        return FraccionamientoDto.from(guardado, cuotasGuardadas.stream().map(c -> CuotaDto.from(c, hoy)).toList());
    }

    @Transactional(readOnly = true)
    public List<FraccionamientoDto> listar(Long clienteId) {
        clientes.buscar(clienteId);
        List<Fraccionamiento> lista = fraccionamientos.findByClienteIdOrderByRegistradoEnDescIdDesc(clienteId);
        if (lista.isEmpty()) return List.of();
        Map<Long, List<CuotaFraccionamiento>> porFraccionamiento = cuotas
                .findByFraccionamientoIdInOrderByFraccionamientoIdAscNumeroAsc(lista.stream().map(Fraccionamiento::getId).toList())
                .stream().collect(Collectors.groupingBy(CuotaFraccionamiento::getFraccionamientoId));
        LocalDate hoy = LocalDate.now(clock);
        return lista.stream().map(f -> FraccionamientoDto.from(f,
                porFraccionamiento.getOrDefault(f.getId(), List.of()).stream().map(c -> CuotaDto.from(c, hoy)).toList())).toList();
    }

    /** Las cuotas del cliente con vencimiento entre las fechas (todas si no se indican), más próximas primero, para el calendario. */
    @Transactional(readOnly = true)
    public List<CalendarioCuotaDto> calendario(Long clienteId, LocalDate desde, LocalDate hasta) {
        return calendario(clienteId, desde, hasta, null, null);
    }

    /**
     * Igual que el anterior, con los filtros del calendario de obligaciones: por estado de la cuota y por situación
     * (PROXIMO: vence hoy o después; VENCIDO: la fecha ya pasó, esté pagada o no).
     */
    @Transactional(readOnly = true)
    public List<CalendarioCuotaDto> calendario(Long clienteId, LocalDate desde, LocalDate hasta,
                                               EstadoCuota estado, CalendarioService.Situacion situacion) {
        clientes.buscar(clienteId);
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ValidationException("desde no puede superar hasta");
        }
        LocalDate hoy = LocalDate.now(clock);
        return cuotas.delClienteEntre(clienteId, desde == null ? DESDE_POR_DEFECTO : desde, hasta == null ? HASTA_POR_DEFECTO : hasta)
                .stream()
                .filter(c -> estado == null || c.getEstado() == estado)
                .filter(c -> situacion != CalendarioService.Situacion.PROXIMO || !c.getFechaVencimiento().isBefore(hoy))
                .filter(c -> situacion != CalendarioService.Situacion.VENCIDO || c.getFechaVencimiento().isBefore(hoy))
                .map(c -> new CalendarioCuotaDto(CuotaDto.from(c, hoy), c.getFraccionamientoId(),
                        ChronoUnit.DAYS.between(hoy, c.getFechaVencimiento()), c.getFechaVencimiento().isBefore(hoy)))
                .toList();
    }

    /** Marca la cuota como pagada con la fecha de hoy (así deja de estar en riesgo); 404 si no existe y 400 si ya estaba pagada. */
    @Transactional
    public CuotaDto pagarCuota(Long cuotaId) {
        CuotaFraccionamiento c = cuotas.findById(cuotaId).orElseThrow(() -> new EntityNotFoundException("Cuota no encontrada"));
        if (c.getEstado() == EstadoCuota.PAGADA) {
            throw new ValidationException("La cuota ya fue pagada");
        }
        c.setEstado(EstadoCuota.PAGADA);
        c.setFechaPago(LocalDate.now(clock));
        return CuotaDto.from(cuotas.save(c), LocalDate.now(clock));
    }

    private void validar(FraccionamientoEntradaDto dto) {
        if (dto == null) {
            throw new ValidationException("Faltan o son inválidos: tributo, montoTotal, numeroCuotas, fechaPrimeraCuota");
        }
        List<String> errores = new ArrayList<>();
        if (dto.tributo() == null) errores.add("tributo");
        if (dto.montoTotal() == null) errores.add("montoTotal");
        else if (dto.montoTotal().signum() <= 0) errores.add("montoTotal (debe ser mayor que cero)");
        else if (dto.montoTotal().compareTo(MAXIMO_MONTO) > 0) errores.add("montoTotal (demasiado grande)");
        if (dto.numeroCuotas() == null) errores.add("numeroCuotas");
        else if (dto.numeroCuotas() < MINIMO_CUOTAS || dto.numeroCuotas() > maximoCuotas) {
            errores.add("numeroCuotas (de " + MINIMO_CUOTAS + " a " + maximoCuotas + ")");
        }
        if (dto.montoTotal() != null && dto.montoTotal().signum() > 0 && dto.numeroCuotas() != null
                && dto.numeroCuotas() >= MINIMO_CUOTAS && dto.numeroCuotas() <= maximoCuotas
                && dto.montoTotal().setScale(2, RoundingMode.HALF_UP)
                        .divide(BigDecimal.valueOf(dto.numeroCuotas()), 2, RoundingMode.DOWN).signum() == 0) {
            errores.add("montoTotal (no alcanza para cuotas de al menos 0.01)");
        }
        if (dto.fechaPrimeraCuota() == null) errores.add("fechaPrimeraCuota");
        if (!errores.isEmpty()) {
            throw new ValidationException("Faltan o son inválidos: " + String.join(", ", errores));
        }
    }

    private static List<CuotaFraccionamiento> generarCuotas(Fraccionamiento f) {
        int n = f.getNumeroCuotas();
        BigDecimal base = f.getMontoTotal().divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
        BigDecimal acumulado = BigDecimal.ZERO;
        List<CuotaFraccionamiento> lista = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            BigDecimal monto = i == n ? f.getMontoTotal().subtract(acumulado) : base; // la última ajusta los centavos
            acumulado = acumulado.add(monto);
            CuotaFraccionamiento c = new CuotaFraccionamiento();
            c.setFraccionamientoId(f.getId());
            c.setNumero(i);
            c.setFechaVencimiento(f.getFechaPrimeraCuota().plusMonths(i - 1L)); // plusMonths respeta el último día del mes
            c.setMonto(monto);
            lista.add(c);
        }
        return lista;
    }
}

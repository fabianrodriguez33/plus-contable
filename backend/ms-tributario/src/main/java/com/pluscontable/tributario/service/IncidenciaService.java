package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.IncidenciaDto;
import com.pluscontable.tributario.dto.IncidenciaEntradaDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.Incidencia;
import com.pluscontable.tributario.model.IncidenciaCliente;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.IncidenciaClienteRepository;
import com.pluscontable.tributario.repository.IncidenciaRepository;
import jakarta.validation.ValidationException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-23 · Registrar una incidencia del servicio contable. La incidencia de un cliente queda en su historial; la general
 * (caída del portal SOL) se asocia a toda la cartera activa. El usuario que la registra sale del token.
 */
@Service
public class IncidenciaService {

    /** «Hoy» es el de Lima, aunque el servidor corra en UTC (igual que HU-31). */
    static final ZoneId ZONA = ZoneId.of("America/Lima");
    static final int MAX_DESCRIPCION = 1000;
    static final int MAX_USUARIO = 60;
    static final int MAX_OBSERVACIONES = 1000;

    private final IncidenciaRepository incidencias;
    private final IncidenciaClienteRepository asociaciones;
    private final ClienteRepository clientesRepo;
    private final ClienteService clientes;
    private final Clock clock;

    @Autowired
    public IncidenciaService(IncidenciaRepository incidencias, IncidenciaClienteRepository asociaciones,
                             ClienteRepository clientesRepo, ClienteService clientes) {
        this(incidencias, asociaciones, clientesRepo, clientes, Clock.system(ZONA));
    }

    IncidenciaService(IncidenciaRepository incidencias, IncidenciaClienteRepository asociaciones,
                      ClienteRepository clientesRepo, ClienteService clientes, Clock clock) {
        this.incidencias = incidencias;
        this.asociaciones = asociaciones;
        this.clientesRepo = clientesRepo;
        this.clientes = clientes;
        this.clock = clock;
    }

    /**
     * 400 con todos los errores juntos si falta o es inválido algún dato; 404 si el cliente no existe. La incidencia
     * general se asocia a los clientes con activo = true al momento de registrar (los que ingresen después no).
     */
    @Transactional
    public IncidenciaDto registrar(IncidenciaEntradaDto dto, String usuario) {
        validar(dto, usuario);
        List<Long> afectados;
        if (dto.tipo().esGeneral()) {
            afectados = clientesRepo.findByActivoTrue().stream().map(Cliente::getId).toList();
        } else {
            afectados = List.of(clientes.buscar(dto.clienteId()).getId());
        }

        Incidencia i = new Incidencia();
        i.setTipo(dto.tipo());
        i.setFecha(dto.fecha());
        i.setDescripcion(dto.descripcion().trim());
        i.setObservaciones(dto.observaciones() == null || dto.observaciones().isBlank() ? null : dto.observaciones().trim());
        i.setRegistradaPor(usuario.trim());
        i.setRegistradaEn(LocalDateTime.now(clock));
        Incidencia guardada = incidencias.save(i);

        asociaciones.saveAll(afectados.stream().map(c -> new IncidenciaCliente(guardada.getId(), c)).toList());
        return IncidenciaDto.from(guardada, afectados.size());
    }

    /** El historial del cliente: sus incidencias y las generales, la más reciente primero. 404 si el cliente no existe. */
    @Transactional(readOnly = true)
    public List<IncidenciaDto> delCliente(Long clienteId) {
        clientes.buscar(clienteId);
        List<Incidencia> lista = incidencias.findByCliente(clienteId);
        if (lista.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> cantidades = new HashMap<>();
        for (Object[] fila : asociaciones.contarPorIncidencia(lista.stream().map(Incidencia::getId).toList())) {
            cantidades.put((Long) fila[0], ((Number) fila[1]).intValue());
        }
        return lista.stream().map(i -> IncidenciaDto.from(i, cantidades.getOrDefault(i.getId(), 0))).toList();
    }

    private void validar(IncidenciaEntradaDto dto, String usuario) {
        if (usuario == null || usuario.isBlank() || usuario.trim().length() > MAX_USUARIO) {
            throw new ValidationException("No se pudo identificar al usuario que registra la incidencia");
        }
        if (dto == null) {
            throw new ValidationException("Faltan o son inválidos: tipo, fecha, descripcion");
        }
        List<String> errores = new ArrayList<>();
        if (dto.tipo() == null) {
            errores.add("tipo");
        } else if (dto.tipo().esGeneral()) {
            if (dto.clienteId() != null) errores.add("clienteId (una incidencia general no lleva cliente)");
        } else if (dto.clienteId() == null) {
            errores.add("clienteId");
        } else if (dto.clienteId() <= 0) {
            errores.add("clienteId (debe ser positivo)");
        }
        if (dto.fecha() == null) {
            errores.add("fecha");
        } else if (dto.fecha().isAfter(LocalDate.now(clock))) {
            errores.add("fecha (no puede ser futura)");
        }
        if (dto.descripcion() == null || dto.descripcion().isBlank()) {
            errores.add("descripcion");
        } else if (dto.descripcion().trim().length() > MAX_DESCRIPCION) {
            errores.add("descripcion (máximo " + MAX_DESCRIPCION + " caracteres)");
        }
        if (dto.observaciones() != null && dto.observaciones().trim().length() > MAX_OBSERVACIONES) {
            errores.add("observaciones (máximo " + MAX_OBSERVACIONES + " caracteres)");
        }
        if (!errores.isEmpty()) {
            throw new ValidationException("Faltan o son inválidos: " + String.join(", ", errores));
        }
    }
}

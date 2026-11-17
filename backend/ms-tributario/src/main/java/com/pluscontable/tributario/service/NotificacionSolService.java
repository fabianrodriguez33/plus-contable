package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.NotificacionSolDto;
import com.pluscontable.tributario.dto.NotificacionSolEntradaDto;
import com.pluscontable.tributario.model.EstadoNotificacion;
import com.pluscontable.tributario.model.NotificacionSol;
import com.pluscontable.tributario.repository.NotificacionSolRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HU-18 · Registrar una notificación del Buzón SOL con su tipo, fecha de recepción y plazo de respuesta. */
@Service
public class NotificacionSolService {

    private final NotificacionSolRepository notificaciones;
    private final ClienteService clientes;
    private final Clock clock;

    @Autowired
    public NotificacionSolService(NotificacionSolRepository notificaciones, ClienteService clientes) {
        this(notificaciones, clientes, Clock.systemDefaultZone());
    }

    NotificacionSolService(NotificacionSolRepository notificaciones, ClienteService clientes, Clock clock) {
        this.notificaciones = notificaciones;
        this.clientes = clientes;
        this.clock = clock;
    }

    /** Lanza 404 si el cliente no existe y 400 con todos los errores juntos si faltan datos; sin plazo no se guarda. */
    @Transactional
    public NotificacionSolDto registrar(Long clienteId, NotificacionSolEntradaDto dto) {
        clientes.buscar(clienteId);
        validar(dto);
        NotificacionSol n = new NotificacionSol();
        n.setClienteId(clienteId);
        n.setTipo(dto.tipo());
        n.setFechaRecepcion(dto.fechaRecepcion());
        n.setPlazoRespuesta(dto.plazoRespuesta());
        n.setEstado(EstadoNotificacion.PENDIENTE);
        return NotificacionSolDto.from(notificaciones.save(n));
    }

    /** Las notificaciones pendientes del cliente, la de plazo más próximo primero (HU-18, criterio 4). */
    @Transactional(readOnly = true)
    public List<NotificacionSolDto> pendientes(Long clienteId) {
        return listar(clienteId, EstadoNotificacion.PENDIENTE);
    }

    /**
     * Las notificaciones del cliente, de plazo más próximo a más lejano; con {@code estado} solo las de ese estado.
     * Lanza 404 si el cliente no existe.
     */
    @Transactional(readOnly = true)
    public List<NotificacionSolDto> listar(Long clienteId, EstadoNotificacion estado) {
        clientes.buscar(clienteId);
        List<NotificacionSol> lista = estado == null
                ? notificaciones.findByClienteIdOrderByPlazoRespuestaAscIdAsc(clienteId)
                : notificaciones.findByClienteIdAndEstadoOrderByPlazoRespuestaAscIdAsc(clienteId, estado);
        return lista.stream().map(NotificacionSolDto::from).toList();
    }

    /** Pasa una notificación pendiente a ATENDIDA con la fecha de hoy; 404 si no existe y 400 si ya estaba atendida. */
    @Transactional
    public NotificacionSolDto marcarAtendida(Long notificacionId) {
        NotificacionSol n = notificaciones.findById(notificacionId)
                .orElseThrow(() -> new EntityNotFoundException("Notificación no encontrada"));
        if (n.getEstado() == EstadoNotificacion.ATENDIDA) {
            throw new ValidationException("La notificación ya fue atendida");
        }
        n.setEstado(EstadoNotificacion.ATENDIDA);
        n.setFechaAtencion(LocalDate.now(clock));
        return NotificacionSolDto.from(notificaciones.save(n));
    }

    private void validar(NotificacionSolEntradaDto dto) {
        if (dto == null) {
            throw new ValidationException("Faltan o son inválidos: tipo, fechaRecepcion, plazoRespuesta");
        }
        List<String> errores = new ArrayList<>();
        if (dto.tipo() == null) errores.add("tipo");
        if (dto.fechaRecepcion() == null) {
            errores.add("fechaRecepcion");
        } else if (dto.fechaRecepcion().isAfter(LocalDate.now(clock))) {
            errores.add("fechaRecepcion (no puede ser futura)");
        }
        if (dto.plazoRespuesta() == null) {
            errores.add("plazoRespuesta");
        } else if (dto.fechaRecepcion() != null && dto.plazoRespuesta().isBefore(dto.fechaRecepcion())) {
            errores.add("plazoRespuesta (no puede ser anterior a la fecha de recepción)");
        }
        if (!errores.isEmpty()) {
            throw new ValidationException("Faltan o son inválidos: " + String.join(", ", errores));
        }
    }
}

package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.SolicitudEspecialDto;
import com.pluscontable.tributario.dto.SolicitudEspecialEntradaDto;
import com.pluscontable.tributario.model.EstadoSolicitud;
import com.pluscontable.tributario.model.SolicitudEspecial;
import com.pluscontable.tributario.repository.SolicitudEspecialRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU-26 · Atender una solicitud especial del cliente: se registra el pedido (constancia, certificación o trámite) con su fecha
 * de entrega y queda en seguimiento como PENDIENTE hasta que el estudio lo marca atendido. Supuestos del diseño previo T182,
 * pendientes de confirmar con Beto: descripción opcional de hasta 500 caracteres y fecha de entrega no anterior a hoy.
 */
@Service
public class SolicitudEspecialService {

    static final int MAX_DESCRIPCION = 500;

    private final SolicitudEspecialRepository solicitudes;
    private final ClienteService clientes;
    private final Clock clock;

    @Autowired
    public SolicitudEspecialService(SolicitudEspecialRepository solicitudes, ClienteService clientes) {
        this(solicitudes, clientes, Clock.systemDefaultZone());
    }

    SolicitudEspecialService(SolicitudEspecialRepository solicitudes, ClienteService clientes, Clock clock) {
        this.solicitudes = solicitudes;
        this.clientes = clientes;
        this.clock = clock;
    }

    /** 400 con todos los errores juntos (sin fecha de entrega no se guarda: criterio 2); 404 si el cliente no existe. */
    @Transactional
    public SolicitudEspecialDto registrar(Long clienteId, SolicitudEspecialEntradaDto dto, String usuario) {
        validar(dto);
        clientes.buscar(clienteId);
        SolicitudEspecial s = new SolicitudEspecial();
        s.setClienteId(clienteId);
        s.setTipo(dto.tipo());
        s.setDescripcion(dto.descripcion() == null || dto.descripcion().isBlank() ? null : dto.descripcion().trim());
        s.setFechaEntrega(dto.fechaEntrega());
        s.setRegistradaPor(usuario);
        return SolicitudEspecialDto.from(solicitudes.save(s));
    }

    /** Los pedidos en seguimiento del cliente, el de entrega más próxima primero (criterio 3). 404 si el cliente no existe. */
    @Transactional(readOnly = true)
    public List<SolicitudEspecialDto> pendientes(Long clienteId) {
        clientes.buscar(clienteId);
        return solicitudes.findByClienteIdAndEstadoOrderByFechaEntregaAscIdAsc(clienteId, EstadoSolicitud.PENDIENTE).stream()
                .map(SolicitudEspecialDto::from).toList();
    }

    /** Sale de la lista de pendientes con la fecha de hoy. 404 si no existe o es de otro cliente; 400 si ya estaba atendida. */
    @Transactional
    public SolicitudEspecialDto marcarAtendida(Long clienteId, Long solicitudId) {
        SolicitudEspecial s = solicitudes.findById(solicitudId)
                .filter(x -> x.getClienteId().equals(clienteId))
                .orElseThrow(() -> new EntityNotFoundException("Solicitud no encontrada"));
        if (s.getEstado() == EstadoSolicitud.ATENDIDA) throw new ValidationException("La solicitud ya está atendida");
        s.setEstado(EstadoSolicitud.ATENDIDA);
        s.setFechaAtencion(LocalDate.now(clock));
        return SolicitudEspecialDto.from(solicitudes.save(s));
    }

    private void validar(SolicitudEspecialEntradaDto dto) {
        List<String> errores = new ArrayList<>();
        if (dto == null || dto.tipo() == null) errores.add("tipo");
        LocalDate entrega = dto == null ? null : dto.fechaEntrega();
        if (entrega == null) errores.add("fechaEntrega");
        else if (entrega.isBefore(LocalDate.now(clock))) errores.add("fechaEntrega (no puede ser anterior a hoy)");
        if (dto != null && dto.descripcion() != null && dto.descripcion().trim().length() > MAX_DESCRIPCION) {
            errores.add("descripcion (máximo " + MAX_DESCRIPCION + " caracteres)");
        }
        if (!errores.isEmpty()) throw new ValidationException("Faltan o son inválidos: " + String.join(", ", errores));
    }
}

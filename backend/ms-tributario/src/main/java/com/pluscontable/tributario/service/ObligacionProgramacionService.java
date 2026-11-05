package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.CrearObligacionDto;
import com.pluscontable.tributario.dto.ObligacionDto;
import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.obligacion.ObligacionInputValidator;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.ObligacionRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Alta HU-07 sin cambiar los flujos de declaración y vencimiento existentes. */
@Service
public class ObligacionProgramacionService {
    private final ClienteRepository clientes;
    private final ObligacionRepository obligaciones;

    public ObligacionProgramacionService(ClienteRepository clientes, ObligacionRepository obligaciones) {
        this.clientes = clientes;
        this.obligaciones = obligaciones;
    }

    @Transactional
    public ObligacionDto crear(CrearObligacionDto solicitud) {
        if (solicitud == null) throw new ValidationException("Datos de obligación obligatorios");
        try {
            ObligacionInputValidator.validar(solicitud.clienteId(), solicitud.tipoObligacion(), solicitud.periodo());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }
        if (solicitud.fechaVencimiento() == null || solicitud.fechaVencimiento().isBefore(solicitud.periodo())) {
            throw new ValidationException("fecha_vencimiento es obligatoria y no puede ser anterior al periodo");
        }
        if (!clientes.existsById(solicitud.clienteId())) {
            throw new EntityNotFoundException("Cliente no encontrado");
        }
        if (obligaciones.existsByClienteIdAndTipoObligacionAndPeriodo(
                solicitud.clienteId(), solicitud.tipoObligacion(), solicitud.periodo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe esta obligación para el cliente, tipo y período");
        }
        Obligacion nueva = new Obligacion();
        nueva.setClienteId(solicitud.clienteId());
        nueva.setTipoObligacion(solicitud.tipoObligacion());
        nueva.setPeriodo(solicitud.periodo());
        nueva.setFechaVencimiento(solicitud.fechaVencimiento());
        return ObligacionDto.from(obligaciones.saveAndFlush(nueva));
    }
}

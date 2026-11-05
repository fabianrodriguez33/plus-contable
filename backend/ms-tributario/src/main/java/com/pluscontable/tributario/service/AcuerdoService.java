package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.AcuerdoDto;
import com.pluscontable.tributario.model.AcuerdoServicio;
import com.pluscontable.tributario.model.HonorarioHistorial;
import com.pluscontable.tributario.repository.AcuerdoRepository;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.HonorarioHistorialRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HU-05 · Registrar el acuerdo de servicio y de honorarios. */
@Service
public class AcuerdoService {

    private final AcuerdoRepository acuerdoRepository;
    private final HonorarioHistorialRepository historialRepository;
    private final ClienteRepository clienteRepository;

    public AcuerdoService(AcuerdoRepository acuerdoRepository, HonorarioHistorialRepository historialRepository,
                          ClienteRepository clienteRepository) {
        this.acuerdoRepository = acuerdoRepository;
        this.historialRepository = historialRepository;
        this.clienteRepository = clienteRepository;
    }

    @Transactional(readOnly = true)
    public AcuerdoDto vigente(Long clienteId) {
        exigirCliente(clienteId);
        var acuerdo = acuerdoRepository.findByClienteId(clienteId)
                .orElseThrow(() -> new EntityNotFoundException("El cliente no tiene acuerdo de servicio"));
        return aDto(acuerdo);
    }

    /** Crea el acuerdo o lo actualiza; si cambia el honorario, el anterior pasa al historial con su vigencia. */
    @Transactional
    public AcuerdoDto guardar(Long clienteId, AcuerdoDto dto) {
        exigirCliente(clienteId);
        validar(dto);

        AcuerdoServicio a = acuerdoRepository.findByClienteId(clienteId).orElse(null);
        if (a == null) {
            a = new AcuerdoServicio();
            a.setClienteId(clienteId);
            a.setHonorarioMensual(dto.honorarioMensual());
            a.setHonorarioDesde(dto.fechaInicio());
        } else if (a.getHonorarioMensual().compareTo(dto.honorarioMensual()) != 0) {
            LocalDate desde = dto.honorarioDesde() != null ? dto.honorarioDesde() : LocalDate.now();
            if (!desde.isAfter(a.getHonorarioDesde())) {
                throw new ValidationException("honorarioDesde debe ser posterior a " + a.getHonorarioDesde()
                        + ", inicio del honorario actual");
            }
            HonorarioHistorial h = new HonorarioHistorial();
            h.setAcuerdoId(a.getId());
            h.setMonto(a.getHonorarioMensual());
            h.setVigenteDesde(a.getHonorarioDesde());
            h.setVigenteHasta(desde.minusDays(1));
            historialRepository.save(h);
            a.setHonorarioMensual(dto.honorarioMensual());
            a.setHonorarioDesde(desde);
        }
        a.setServiciosContratados(vacio(dto.serviciosContratados()) ? null : dto.serviciosContratados().trim());
        a.setFechaInicio(dto.fechaInicio());
        return aDto(acuerdoRepository.save(a));
    }

    private AcuerdoDto aDto(AcuerdoServicio a) {
        return AcuerdoDto.from(a, a.getId() == null ? List.of()
                : historialRepository.findByAcuerdoIdOrderByVigenteDesdeDesc(a.getId()));
    }

    private static void validar(AcuerdoDto dto) {
        List<String> errores = new ArrayList<>();
        if (dto.honorarioMensual() == null) errores.add("honorarioMensual");
        else if (dto.honorarioMensual().compareTo(BigDecimal.ZERO) <= 0) errores.add("honorarioMensual (debe ser mayor que cero)");
        if (dto.fechaInicio() == null) errores.add("fechaInicio");
        if (!errores.isEmpty()) {
            throw new ValidationException("Faltan o son inválidos: " + String.join(", ", errores));
        }
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private void exigirCliente(Long clienteId) {
        if (!clienteRepository.existsById(clienteId)) {
            throw new EntityNotFoundException("Cliente no encontrado");
        }
    }
}

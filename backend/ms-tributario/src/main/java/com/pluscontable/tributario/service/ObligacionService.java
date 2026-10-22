package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.CambioVencimientoDto;
import com.pluscontable.tributario.dto.DeclaracionDto;
import com.pluscontable.tributario.dto.ObligacionDto;
import com.pluscontable.tributario.model.Obligacion;
import com.pluscontable.tributario.repository.ObligacionRepository;
import com.pluscontable.tributario.repository.VencimientoHistorialRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HU-09 (declaración) y HU-10 (variar vencimiento) sobre la obligación provisional de HU-07. */
@Service
public class ObligacionService {

    private final ObligacionRepository obligacionRepository;
    private final VencimientoHistorialRepository historialRepository;

    public ObligacionService(ObligacionRepository obligacionRepository,
                             VencimientoHistorialRepository historialRepository) {
        this.obligacionRepository = obligacionRepository;
        this.historialRepository = historialRepository;
    }

    @Transactional(readOnly = true)
    public ObligacionDto buscar(Long obligacionId) {
        return ObligacionDto.from(obtener(obligacionId));
    }

    /** PENDIENTE → DECLARADO con fecha, número de formulario y monto; HU-13 parte de este estado. */
    @Transactional
    public ObligacionDto declarar(Long obligacionId, DeclaracionDto dto) {
        Obligacion o = obtener(obligacionId);
        if (o.getEstado() != Obligacion.Estado.PENDIENTE) {
            throw new ValidationException("La obligación ya fue declarada");
        }

        List<String> errores = new ArrayList<>();
        if (dto.fechaDeclaracion() == null) errores.add("fechaDeclaracion");
        else if (dto.fechaDeclaracion().isAfter(LocalDate.now())) errores.add("fechaDeclaracion (no puede ser futura)");
        else if (dto.fechaDeclaracion().isBefore(o.getPeriodo())) errores.add("fechaDeclaracion (anterior al periodo)");
        if (dto.numeroFormulario() == null || dto.numeroFormulario().isBlank()) errores.add("numeroFormulario");
        else if (dto.numeroFormulario().trim().length() > 30) errores.add("numeroFormulario (máximo 30 caracteres)");
        if (dto.montoDeterminado() == null) errores.add("montoDeterminado");
        else if (dto.montoDeterminado().compareTo(BigDecimal.ZERO) < 0) errores.add("montoDeterminado (no puede ser negativo)");
        if (!errores.isEmpty()) {
            throw new ValidationException("Faltan o son inválidos: " + String.join(", ", errores));
        }

        o.setFechaDeclaracion(dto.fechaDeclaracion());
        o.setNumeroFormulario(dto.numeroFormulario().trim());
        o.setMontoDeterminado(dto.montoDeterminado());
        o.setEstado(Obligacion.Estado.DECLARADO);
        return ObligacionDto.from(obligacionRepository.save(o));
    }

    @Transactional
    public List<ObligacionDto> cambiarVencimiento(CambioVencimientoDto dto) {
        // TODO Tarea 69: todas o ninguna; rechazar nuevaFecha anterior al periodo de cualquier obligacion;
        //  por cada una guardar VencimientoHistorial(fechaAnterior, fechaNueva) y actualizar
        throw new UnsupportedOperationException("HU-10 pendiente");
    }

    private Obligacion obtener(Long obligacionId) {
        return obligacionRepository.findById(obligacionId)
                .orElseThrow(() -> new EntityNotFoundException("Obligación no encontrada"));
    }
}

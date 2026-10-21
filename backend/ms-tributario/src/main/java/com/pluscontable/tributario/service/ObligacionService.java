package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.CambioVencimientoDto;
import com.pluscontable.tributario.dto.DeclaracionDto;
import com.pluscontable.tributario.dto.ObligacionDto;
import com.pluscontable.tributario.repository.ObligacionRepository;
import com.pluscontable.tributario.repository.VencimientoHistorialRepository;
import jakarta.persistence.EntityNotFoundException;
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
        return obligacionRepository.findById(obligacionId).map(ObligacionDto::from)
                .orElseThrow(() -> new EntityNotFoundException("Obligación no encontrada"));
    }

    @Transactional
    public ObligacionDto declarar(Long obligacionId, DeclaracionDto dto) {
        // TODO Tarea 63: solo desde PENDIENTE; guardar fecha, formulario y monto; estado -> DECLARADO
        throw new UnsupportedOperationException("HU-09 pendiente");
    }

    @Transactional
    public List<ObligacionDto> cambiarVencimiento(CambioVencimientoDto dto) {
        // TODO Tarea 69: todas o ninguna; rechazar nuevaFecha anterior al periodo de cualquier obligacion;
        //  por cada una guardar VencimientoHistorial(fechaAnterior, fechaNueva) y actualizar
        throw new UnsupportedOperationException("HU-10 pendiente");
    }
}

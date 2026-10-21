package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.AcuerdoDto;
import com.pluscontable.tributario.repository.AcuerdoRepository;
import com.pluscontable.tributario.repository.HonorarioHistorialRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HU-05 · Registrar el acuerdo de servicio y de honorarios. */
@Service
public class AcuerdoService {

    private final AcuerdoRepository acuerdoRepository;
    private final HonorarioHistorialRepository historialRepository;

    public AcuerdoService(AcuerdoRepository acuerdoRepository, HonorarioHistorialRepository historialRepository) {
        this.acuerdoRepository = acuerdoRepository;
        this.historialRepository = historialRepository;
    }

    @Transactional(readOnly = true)
    public AcuerdoDto vigente(Long clienteId) {
        var acuerdo = acuerdoRepository.findByClienteId(clienteId)
                .orElseThrow(() -> new EntityNotFoundException("El cliente no tiene acuerdo de servicio"));
        return AcuerdoDto.from(acuerdo, historialRepository.findByAcuerdoIdOrderByVigenteDesdeDesc(acuerdo.getId()));
    }

    @Transactional
    public AcuerdoDto guardar(Long clienteId, AcuerdoDto dto) {
        // TODO Tarea 47: si no existe acuerdo, crearlo (validar que el cliente existe)
        // TODO Tarea 47: si el honorario cambia, guardar el anterior en historial
        //  (vigenteDesde = inicio del monto anterior, vigenteHasta = dia previo al nuevo) y actualizar
        throw new UnsupportedOperationException("HU-05 pendiente");
    }
}

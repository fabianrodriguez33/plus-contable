package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.ActivoFijoDto;
import com.pluscontable.tributario.model.ActivoFijo;
import com.pluscontable.tributario.repository.ActivoFijoRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatrimonioLaboralService {

    private final ActivoFijoRepository activoRepository;
    private final ClienteService clienteService;

    public PatrimonioLaboralService(ActivoFijoRepository activoRepository, ClienteService clienteService) {
        this.activoRepository = activoRepository;
        this.clienteService = clienteService;
    }

    // ---- Activos fijos ----

    @Transactional(readOnly = true)
    public List<ActivoFijoDto> listarActivos(Long clienteId) {
        clienteService.buscar(clienteId);
        return activoRepository.findByClienteIdOrderById(clienteId).stream().map(ActivoFijoDto::from).toList();
    }

    @Transactional
    public ActivoFijoDto agregarActivo(Long clienteId, ActivoFijoDto dto) {
        clienteService.buscar(clienteId);
        ActivoFijo a = new ActivoFijo();
        a.setClienteId(clienteId);
        a.setCodigo(dto.codigo() == null || dto.codigo().isBlank() ? null : dto.codigo().trim());
        a.setDescripcion(dto.descripcion().trim());
        a.setCategoria(dto.categoria());
        a.setValorAdquisicion(dto.valorAdquisicion());
        a.setFechaAdquisicion(dto.fechaAdquisicion());
        return ActivoFijoDto.from(activoRepository.save(a));
    }

    @Transactional
    public void eliminarActivo(Long clienteId, Long activoId) {
        ActivoFijo a = activoRepository.findById(activoId)
                .filter(x -> x.getClienteId().equals(clienteId))
                .orElseThrow(() -> new EntityNotFoundException("Activo no encontrado: " + activoId));
        activoRepository.delete(a);
    }
}

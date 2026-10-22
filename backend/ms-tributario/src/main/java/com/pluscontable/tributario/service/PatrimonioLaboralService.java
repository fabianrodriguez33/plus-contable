package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.ActivoFijoDto;
import com.pluscontable.tributario.dto.TrabajadorDto;
import com.pluscontable.tributario.model.ActivoFijo;
import com.pluscontable.tributario.model.Trabajador;
import com.pluscontable.tributario.repository.ActivoFijoRepository;
import com.pluscontable.tributario.repository.TrabajadorRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatrimonioLaboralService {

    private final TrabajadorRepository trabajadorRepository;
    private final ActivoFijoRepository activoRepository;
    private final ClienteService clienteService;

    public PatrimonioLaboralService(TrabajadorRepository trabajadorRepository, ActivoFijoRepository activoRepository,
                                    ClienteService clienteService) {
        this.trabajadorRepository = trabajadorRepository;
        this.activoRepository = activoRepository;
        this.clienteService = clienteService;
    }

    // ---- Trabajadores ----

    @Transactional(readOnly = true)
    public List<TrabajadorDto> listarTrabajadores(Long clienteId) {
        clienteService.buscar(clienteId);
        return trabajadorRepository.findByClienteIdOrderById(clienteId).stream().map(TrabajadorDto::from).toList();
    }

    @Transactional
    public TrabajadorDto agregarTrabajador(Long clienteId, TrabajadorDto dto) {
        clienteService.buscar(clienteId);
        if (dto.extranjero() && (dto.nacionalidad() == null || dto.nacionalidad().isBlank())) {
            throw new ValidationException("Indique la nacionalidad del trabajador extranjero");
        }
        if (dto.sistemaPensionario() == Trabajador.SistemaPensionario.AFP
                && (dto.nombreAfp() == null || dto.nombreAfp().isBlank())) {
            throw new ValidationException("Indique el nombre de la AFP del trabajador");
        }
        Trabajador t = new Trabajador();
        t.setClienteId(clienteId);
        t.setNombreCompleto(dto.nombreCompleto().trim());
        t.setTipoDocumento(dto.tipoDocumento() == null || dto.tipoDocumento().isBlank()
                ? (dto.extranjero() ? "CE" : "DNI") : dto.tipoDocumento());
        t.setNumeroDocumento(dto.numeroDocumento().trim());
        t.setExtranjero(dto.extranjero());
        t.setNacionalidad(dto.extranjero() ? dto.nacionalidad() : "Peruana");
        t.setRegimenLaboral(dto.regimenLaboral());
        t.setSistemaPensionario(dto.sistemaPensionario());
        t.setNombreAfp(dto.sistemaPensionario() == Trabajador.SistemaPensionario.AFP ? dto.nombreAfp() : null);
        t.setFechaIngreso(dto.fechaIngreso());
        t.setRemuneracion(dto.remuneracion());
        t.setRegistroSunafil(dto.registroSunafil());
        t.setRegistroMtpe(dto.registroMtpe());
        t.setEssalud(dto.essalud());
        t.setQuintaCategoria(dto.quintaCategoria());
        return TrabajadorDto.from(trabajadorRepository.save(t));
    }

    @Transactional
    public void eliminarTrabajador(Long clienteId, Long trabajadorId) {
        Trabajador t = trabajadorRepository.findById(trabajadorId)
                .filter(x -> x.getClienteId().equals(clienteId))
                .orElseThrow(() -> new EntityNotFoundException("Trabajador no encontrado: " + trabajadorId));
        trabajadorRepository.delete(t);
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

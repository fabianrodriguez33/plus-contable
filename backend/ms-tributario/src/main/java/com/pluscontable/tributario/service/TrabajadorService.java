package com.pluscontable.tributario.service;

import com.pluscontable.tributario.dto.TrabajadorDto;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.TrabajadorRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HU-03 · Registrar la nómina de trabajadores. */
@Service
public class TrabajadorService {

    private final TrabajadorRepository trabajadorRepository;
    private final ClienteRepository clienteRepository;

    public TrabajadorService(TrabajadorRepository trabajadorRepository, ClienteRepository clienteRepository) {
        this.trabajadorRepository = trabajadorRepository;
        this.clienteRepository = clienteRepository;
    }

    @Transactional(readOnly = true)
    public List<TrabajadorDto> listar(Long clienteId) {
        exigirCliente(clienteId);
        return trabajadorRepository.findByClienteIdOrderById(clienteId).stream().map(TrabajadorDto::from).toList();
    }

    @Transactional
    public TrabajadorDto registrar(Long clienteId, TrabajadorDto dto) {
        exigirCliente(clienteId);
        // TODO Tarea 35: reunir TODOS los campos faltantes y lanzar una sola ValidationException con la lista:
        //  - AFP -> nombreAfp
        //  - extranjero -> nacionalidad, tipoDocumento distinto de DNI, tipoContrato, fechaInicioContrato
        // TODO Tarea 35: mapear dto -> Trabajador y guardar
        throw new UnsupportedOperationException("HU-03 pendiente");
    }

    private void exigirCliente(Long clienteId) {
        if (!clienteRepository.existsById(clienteId)) {
            throw new EntityNotFoundException("Cliente no encontrado");
        }
    }
}

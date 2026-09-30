package com.pluscontable.tributario.service;

import com.pluscontable.tributario.config.AesUtil;
import com.pluscontable.tributario.dto.ClienteDto;
import com.pluscontable.tributario.dto.CredencialSolDto;
import com.pluscontable.tributario.model.Cliente;
import com.pluscontable.tributario.model.CredencialSol;
import com.pluscontable.tributario.repository.ClienteRepository;
import com.pluscontable.tributario.repository.CredencialSolRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClienteService {

    private static final Pattern RUC = Pattern.compile("^[0-9]{11}$");

    private final ClienteRepository clienteRepository;
    private final CredencialSolRepository credencialRepository;
    private final AesUtil aesUtil;

    public ClienteService(ClienteRepository clienteRepository, CredencialSolRepository credencialRepository,
                          AesUtil aesUtil) {
        this.clienteRepository = clienteRepository;
        this.credencialRepository = credencialRepository;
        this.aesUtil = aesUtil;
    }

    @Transactional(readOnly = true)
    public List<ClienteDto> listar() {
        return clienteRepository.findAll().stream()
                .map(c -> ClienteDto.from(c, credencialRepository.findByClienteId(c.getId()).isPresent()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClienteDto obtener(Long id) {
        Cliente c = buscar(id);
        return ClienteDto.from(c, credencialRepository.findByClienteId(id).isPresent());
    }

    @Transactional
    public ClienteDto crear(ClienteDto dto) {
        validarRuc(dto.ruc());
        if (clienteRepository.existsByRuc(dto.ruc())) {
            throw new ValidationException("Ya existe un cliente registrado con el RUC " + dto.ruc());
        }
        Cliente c = new Cliente();
        aplicar(c, dto);
        c = clienteRepository.save(c);
        guardarSolSiViene(c.getId(), dto);
        return obtener(c.getId());
    }

    @Transactional
    public ClienteDto actualizar(Long id, ClienteDto dto) {
        validarRuc(dto.ruc());
        Cliente c = buscar(id);
        if (!c.getRuc().equals(dto.ruc()) && clienteRepository.existsByRuc(dto.ruc())) {
            throw new ValidationException("Ya existe un cliente registrado con el RUC " + dto.ruc());
        }
        aplicar(c, dto);
        clienteRepository.save(c);
        guardarSolSiViene(id, dto);
        return obtener(id);
    }

    @Transactional
    public void guardarCredencialSol(Long clienteId, CredencialSolDto dto) {
        buscar(clienteId);
        CredencialSol cred = credencialRepository.findByClienteId(clienteId).orElseGet(() -> {
            CredencialSol n = new CredencialSol();
            n.setClienteId(clienteId);
            return n;
        });
        cred.setUsuarioSolCifrado(aesUtil.encrypt(dto.usuarioSol()));
        cred.setClaveSolCifrada(aesUtil.encrypt(dto.claveSol()));
        cred.setActualizadoEn(LocalDateTime.now());
        credencialRepository.save(cred);
    }

    /** Recupera las credenciales descifradas solo en memoria para la respuesta; nunca se persisten en claro. */
    @Transactional(readOnly = true)
    public CredencialSolDto obtenerCredencialSol(Long clienteId) {
        buscar(clienteId);
        CredencialSol cred = credencialRepository.findByClienteId(clienteId)
                .orElseThrow(() -> new EntityNotFoundException("El cliente no tiene Clave SOL registrada"));
        return new CredencialSolDto(aesUtil.decrypt(cred.getUsuarioSolCifrado()),
                aesUtil.decrypt(cred.getClaveSolCifrada()));
    }

    public Cliente buscar(Long id) {
        return clienteRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado: " + id));
    }

    private void guardarSolSiViene(Long clienteId, ClienteDto dto) {
        boolean tieneUsuario = dto.usuarioSol() != null && !dto.usuarioSol().isBlank();
        boolean tieneClave = dto.claveSol() != null && !dto.claveSol().isBlank();
        if (tieneUsuario && tieneClave) {
            guardarCredencialSol(clienteId, new CredencialSolDto(dto.usuarioSol(), dto.claveSol()));
        } else if (tieneUsuario != tieneClave) {
            throw new ValidationException("Para registrar la Clave SOL debe indicar usuario SOL y clave SOL");
        }
    }

    private void aplicar(Cliente c, ClienteDto dto) {
        c.setRuc(dto.ruc());
        c.setRazonSocial(dto.razonSocial().trim());
        c.setRegimenTributario(dto.regimenTributario());
        c.setCondicionHabido(dto.condicionHabido() == null ? Cliente.CondicionHabido.HABIDO : dto.condicionHabido());
        c.setDireccion(dto.direccion());
    }

    private void validarRuc(String ruc) {
        if (ruc == null || !RUC.matcher(ruc).matches()) {
            throw new ValidationException("El RUC debe tener exactamente 11 dígitos numéricos");
        }
    }
}

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClienteService {

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

    /** Largo de las columnas usuario_sol_cifrado y clave_sol_cifrada (CredencialSol). */
    static final int COLUMNA_CIFRADA = 300;
    public static final int MAX_SOL_BYTES = AesUtil.maxBytesCifrables(COLUMNA_CIFRADA);

    /** 400 si el usuario o la clave, ya cifrados, no caben en su columna; 404 si el cliente no existe. */
    @Transactional
    public void guardarCredencialSol(Long clienteId, CredencialSolDto dto) {
        validarLargoSol(dto);
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

    private static void validarLargoSol(CredencialSolDto dto) {
        List<String> largos = new java.util.ArrayList<>();
        if (dto.usuarioSol() != null && dto.usuarioSol().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_SOL_BYTES) {
            largos.add("usuarioSol");
        }
        if (dto.claveSol() != null && dto.claveSol().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_SOL_BYTES) {
            largos.add("claveSol");
        }
        if (!largos.isEmpty()) {
            throw new ValidationException("Son demasiado largos: " + String.join(" y ", largos) + " (máximo " + MAX_SOL_BYTES
                    + " bytes cada uno; una letra con tilde o ñ ocupa 2)");
        }
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
        c.setTelefono(dto.telefono());
        c.setEmail(dto.email() == null || dto.email().isBlank() ? null : dto.email().trim());
        if (dto.buenContribuyente() != null) {
            c.setBuenContribuyente(dto.buenContribuyente());
        }
    }

    private void validarRuc(String ruc) {
        String motivo = RucValidator.motivoInvalido(ruc);
        if (motivo != null) {
            throw new ValidationException(motivo);
        }
    }
}

package com.pluscontable.auth.service;

import com.pluscontable.auth.dto.ClaveSolDto;
import com.pluscontable.auth.model.BitacoraSeguridad;
import com.pluscontable.auth.model.ClaveSolCifrada;
import com.pluscontable.auth.repository.BitacoraRepository;
import com.pluscontable.auth.repository.ClaveSolRepository;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ClaveSolService {

    private final ClaveSolRepository repository;
    private final BitacoraRepository bitacora;
    private final EncryptionService encryption;

    public ClaveSolService(ClaveSolRepository repository, BitacoraRepository bitacora, EncryptionService encryption) {
        this.repository = repository;
        this.bitacora = bitacora;
        this.encryption = encryption;
    }

    @Transactional
    public void guardar(ClaveSolDto dto, String username, String ip) {
        ClaveSolCifrada c = repository.findByRuc(dto.ruc()).orElseGet(ClaveSolCifrada::new);
        c.setRuc(dto.ruc());
        c.setUsuarioSolCifrado(encryption.encrypt(dto.usuarioSol()));
        c.setClaveSolCifrada(encryption.encrypt(dto.claveSol()));
        c.setActualizadoEn(LocalDateTime.now());
        repository.save(c);
        bitacora.save(new BitacoraSeguridad(null, username, "CLAVE_SOL_GUARDADA", "RUC " + dto.ruc(), ip));
    }

    @Transactional
    public ClaveSolDto obtener(String ruc, String username, String ip) {
        ClaveSolCifrada c = repository.findByRuc(ruc)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No hay clave SOL registrada para el RUC"));
        bitacora.save(new BitacoraSeguridad(null, username, "CLAVE_SOL_CONSULTADA", "RUC " + ruc, ip));
        return new ClaveSolDto(ruc, encryption.decrypt(c.getUsuarioSolCifrado()), encryption.decrypt(c.getClaveSolCifrada()));
    }
}

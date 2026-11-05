package com.pluscontable.tributario.service;

import com.pluscontable.tributario.config.AesUtil;
import com.pluscontable.tributario.model.CredencialSol;
import com.pluscontable.tributario.repository.CredencialSolRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pasa las credenciales SOL guardadas en CBC (sin prefijo) al formato "v2:" con GCM.
 * Es todo o nada: primero reescribe en memoria todas las filas y solo guarda si ninguna falla.
 * Es idempotente: las columnas que ya tienen "v2:" no se tocan.
 */
@Service
public class CredencialSolMigrador {

    private final CredencialSolRepository repository;
    private final AesUtil aesUtil;

    public CredencialSolMigrador(CredencialSolRepository repository, AesUtil aesUtil) {
        this.repository = repository;
        this.aesUtil = aesUtil;
    }

    /** @return cantidad de filas modificadas */
    @Transactional
    public int migrar() {
        List<CredencialSol> modificadas = new ArrayList<>();
        for (CredencialSol cred : repository.findAll()) {
            boolean cambio = false;
            if (aesUtil.esLegado(cred.getUsuarioSolCifrado())) {
                cred.setUsuarioSolCifrado(aesUtil.encrypt(aesUtil.decrypt(cred.getUsuarioSolCifrado())));
                cambio = true;
            }
            if (aesUtil.esLegado(cred.getClaveSolCifrada())) {
                cred.setClaveSolCifrada(aesUtil.encrypt(aesUtil.decrypt(cred.getClaveSolCifrada())));
                cambio = true;
            }
            if (cambio) {
                modificadas.add(cred);
            }
        }
        repository.saveAll(modificadas);
        return modificadas.size();
    }
}

package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.DocumentoFaltante;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoFaltanteRepository extends JpaRepository<DocumentoFaltante, Long> {

    /** HU-24: los documentos del cliente, el de fecha límite más próxima primero. */
    List<DocumentoFaltante> findByClienteIdOrderByFechaLimiteAscIdAsc(Long clienteId);
}

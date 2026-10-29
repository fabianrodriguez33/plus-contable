package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.DocumentoLegal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoRepository extends JpaRepository<DocumentoLegal, Long> {
    List<DocumentoLegal> findByClienteIdOrderByTipoAscVersionDesc(Long clienteId);

    Optional<DocumentoLegal> findTopByClienteIdAndTipoOrderByVersionDesc(Long clienteId, DocumentoLegal.Tipo tipo);
}

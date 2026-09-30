package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.DocumentoLegal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoRepository extends JpaRepository<DocumentoLegal, Long> {
    List<DocumentoLegal> findByClienteIdOrderBySubidoEnDesc(Long clienteId);
}

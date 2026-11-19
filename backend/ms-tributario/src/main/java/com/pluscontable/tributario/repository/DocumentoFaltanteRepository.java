package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.DocumentoFaltante;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentoFaltanteRepository extends JpaRepository<DocumentoFaltante, Long> {

    /** HU-24: los documentos del cliente, el de fecha límite más próxima primero. */
    List<DocumentoFaltante> findByClienteIdOrderByFechaLimiteAscIdAsc(Long clienteId);

    /** Bloquea el documento mientras se entrega o se crea su incidencia, para que dos pedidos a la vez no creen dos (igual que HU-29). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DocumentoFaltante d where d.id = :id")
    Optional<DocumentoFaltante> buscarParaActualizar(@Param("id") Long id);
}

package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.IncidenciaCliente;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidenciaClienteRepository extends JpaRepository<IncidenciaCliente, IncidenciaCliente.Clave> {

    /** Pares {incidenciaId, cantidad de clientes asociados} de las incidencias indicadas. */
    @Query("""
            SELECT ic.incidenciaId, COUNT(ic) FROM IncidenciaCliente ic
            WHERE ic.incidenciaId IN :ids GROUP BY ic.incidenciaId
            """)
    List<Object[]> contarPorIncidencia(@Param("ids") Collection<Long> ids);
}

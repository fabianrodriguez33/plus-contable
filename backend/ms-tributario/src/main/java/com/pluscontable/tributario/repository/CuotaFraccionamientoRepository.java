package com.pluscontable.tributario.repository;

import com.pluscontable.tributario.model.CuotaFraccionamiento;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CuotaFraccionamientoRepository extends JpaRepository<CuotaFraccionamiento, Long> {

    List<CuotaFraccionamiento> findByFraccionamientoIdOrderByNumero(Long fraccionamientoId);

    List<CuotaFraccionamiento> findByFraccionamientoIdInOrderByFraccionamientoIdAscNumeroAsc(Collection<Long> fraccionamientoIds);

    /** Cuotas de todos los fraccionamientos de un cliente con vencimiento en el rango, para el calendario. */
    @Query("""
            select c from CuotaFraccionamiento c, Fraccionamiento f
            where c.fraccionamientoId = f.id and f.clienteId = :clienteId
              and c.fechaVencimiento between :desde and :hasta
            order by c.fechaVencimiento, c.id
            """)
    List<CuotaFraccionamiento> delClienteEntre(@Param("clienteId") Long clienteId, @Param("desde") LocalDate desde,
                                               @Param("hasta") LocalDate hasta);
}

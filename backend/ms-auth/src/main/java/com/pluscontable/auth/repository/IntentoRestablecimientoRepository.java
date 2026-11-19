package com.pluscontable.auth.repository;

import com.pluscontable.auth.model.IntentoRestablecimiento;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface IntentoRestablecimientoRepository extends JpaRepository<IntentoRestablecimiento, Long> {

    long countByIpAndFechaAfter(String ip, LocalDateTime desde);

    long countByCorreoHuellaAndFechaAfter(String correoHuella, LocalDateTime desde);

    @Transactional
    @Modifying
    @Query("delete from IntentoRestablecimiento i where i.fecha < :antes")
    int borrarAnterioresA(@Param("antes") LocalDateTime antes);
}

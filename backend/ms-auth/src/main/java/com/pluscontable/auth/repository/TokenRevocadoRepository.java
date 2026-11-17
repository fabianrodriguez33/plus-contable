package com.pluscontable.auth.repository;

import com.pluscontable.auth.model.TokenRevocado;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenRevocadoRepository extends JpaRepository<TokenRevocado, String> {

    /** Borra los que ya vencieron: un token vencido se rechaza igual por su exp. */
    long deleteByExpiraEnBefore(LocalDateTime ahora);
}

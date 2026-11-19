package com.pluscontable.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** HU-36 (tarea 2): un token cuya sesion se cerro; se identifica por su jti. */
@Entity
@Table(name = "tokens_revocados", schema = "auth_schema")
public class TokenRevocado {
    @Id
    @Column(length = 64)
    private String jti;

    @Column(nullable = false, length = 60)
    private String username;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "revocado_en", nullable = false)
    private LocalDateTime revocadoEn = LocalDateTime.now();

    public TokenRevocado() { }

    public TokenRevocado(String jti, String username, LocalDateTime expiraEn) {
        this.jti = jti;
        this.username = username;
        this.expiraEn = expiraEn;
    }

    public String getJti() { return jti; }
    public String getUsername() { return username; }
    public LocalDateTime getExpiraEn() { return expiraEn; }
    public LocalDateTime getRevocadoEn() { return revocadoEn; }
}

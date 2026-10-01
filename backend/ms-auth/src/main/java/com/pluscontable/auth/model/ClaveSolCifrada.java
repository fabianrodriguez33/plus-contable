package com.pluscontable.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Clave SOL de un cliente, almacenada unicamente cifrada (AES-256-GCM). */
@Entity
@Table(name = "claves_sol", schema = "auth_schema")
public class ClaveSolCifrada {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 11)
    private String ruc;

    @Column(name = "usuario_sol_cifrado", nullable = false, length = 500)
    private String usuarioSolCifrado;

    @Column(name = "clave_sol_cifrada", nullable = false, length = 500)
    private String claveSolCifrada;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn = LocalDateTime.now();

    public Long getId() { return id; }
    public String getRuc() { return ruc; }
    public void setRuc(String ruc) { this.ruc = ruc; }
    public String getUsuarioSolCifrado() { return usuarioSolCifrado; }
    public void setUsuarioSolCifrado(String v) { this.usuarioSolCifrado = v; }
    public String getClaveSolCifrada() { return claveSolCifrada; }
    public void setClaveSolCifrada(String v) { this.claveSolCifrada = v; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public void setActualizadoEn(LocalDateTime v) { this.actualizadoEn = v; }
}

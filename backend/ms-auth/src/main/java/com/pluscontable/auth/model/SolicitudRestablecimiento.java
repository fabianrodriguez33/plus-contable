package com.pluscontable.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** HU-41: pedido del cliente para restablecer su contraseña; queda pendiente hasta que el estudio restablece el acceso. */
@Entity
@Table(name = "solicitudes_restablecimiento", schema = "auth_schema")
public class SolicitudRestablecimiento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "solicitada_en", nullable = false)
    private LocalDateTime solicitadaEn = LocalDateTime.now();

    @Column(length = 60)
    private String ip;

    @Column(name = "atendida_en")
    private LocalDateTime atendidaEn;

    @Column(name = "atendida_por", length = 60)
    private String atendidaPor;

    public SolicitudRestablecimiento() { }

    public SolicitudRestablecimiento(Long usuarioId, String ip) {
        this.usuarioId = usuarioId;
        this.ip = ip;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public LocalDateTime getSolicitadaEn() { return solicitadaEn; }
    public String getIp() { return ip; }
    public LocalDateTime getAtendidaEn() { return atendidaEn; }
    public String getAtendidaPor() { return atendidaPor; }

    public void atender(String quien) {
        this.atendidaEn = LocalDateTime.now();
        this.atendidaPor = quien;
    }
}

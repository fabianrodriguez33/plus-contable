package com.pluscontable.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bitacora_seguridad", schema = "auth_schema")
public class BitacoraSeguridad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(length = 60)
    private String username;

    @Column(nullable = false, length = 40)
    private String evento;

    @Column(length = 300)
    private String detalle;

    @Column(length = 60)
    private String ip;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    public BitacoraSeguridad() { }

    public BitacoraSeguridad(Long usuarioId, String username, String evento, String detalle, String ip) {
        this.usuarioId = usuarioId;
        this.username = username;
        this.evento = evento;
        this.detalle = detalle;
        this.ip = ip;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getUsername() { return username; }
    public String getEvento() { return evento; }
    public String getDetalle() { return detalle; }
    public String getIp() { return ip; }
    public LocalDateTime getFecha() { return fecha; }
}

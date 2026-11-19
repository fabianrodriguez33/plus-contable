package com.pluscontable.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** HU-41: un pedido de restablecimiento, exista o no la cuenta; del correo solo se guarda su huella SHA-256. */
@Entity
@Table(name = "intentos_restablecimiento", schema = "auth_schema")
public class IntentoRestablecimiento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correo_huella", nullable = false, length = 64)
    private String correoHuella;

    @Column(nullable = false, length = 60)
    private String ip;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    public IntentoRestablecimiento() { }

    public IntentoRestablecimiento(String correoHuella, String ip) {
        this.correoHuella = correoHuella;
        this.ip = ip;
    }

    public String getCorreoHuella() { return correoHuella; }
    public String getIp() { return ip; }
    public LocalDateTime getFecha() { return fecha; }
}

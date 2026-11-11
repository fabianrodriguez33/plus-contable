package com.pluscontable.auth.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "usuarios", schema = "auth_schema")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(length = 150)
    private String email;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "intentos_fallidos", nullable = false)
    private int intentosFallidos = 0;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();

    /** Empresa a la que pertenece la cuenta de un CLIENTE_MYPE; null para el estudio. */
    @Column(name = "cliente_id")
    private Long clienteId;

    /** HU-37: verdadero mientras la contraseña sea la temporal que mostró el alta; el cliente debe cambiarla al entrar. */
    @Column(name = "debe_cambiar_password", nullable = false)
    private boolean debeCambiarPassword = false;

    /** HU-37: cuándo el cliente cambió la contraseña temporal por la suya; hasta entonces la temporal sigue sirviendo para entrar. */
    @Column(name = "temporal_usada_en")
    private LocalDateTime temporalUsadaEn;

    /** HU-37: hasta cuándo sirve la contraseña temporal para entrar; pasada esa hora hay que pedir una nueva al estudio. */
    @Column(name = "temporal_vence_en")
    private LocalDateTime temporalVenceEn;

    /**
     * HU-37: versión de las sesiones de la cuenta. Todo token lleva la versión con la que se emitió (claim sv); al restablecer el
     * acceso sube y los tokens anteriores dejan de valer en ms-auth.
     */
    @Column(name = "sesion_version", nullable = false)
    private int sesionVersion = 0;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "usuario_roles", schema = "auth_schema",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id"))
    private Set<Rol> roles = new HashSet<>();

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getNombreCompleto() { return nombreCompleto; }
    public void setNombreCompleto(String nombreCompleto) { this.nombreCompleto = nombreCompleto; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public int getIntentosFallidos() { return intentosFallidos; }
    public void setIntentosFallidos(int intentosFallidos) { this.intentosFallidos = intentosFallidos; }
    public LocalDateTime getBloqueadoHasta() { return bloqueadoHasta; }
    public void setBloqueadoHasta(LocalDateTime bloqueadoHasta) { this.bloqueadoHasta = bloqueadoHasta; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public boolean isDebeCambiarPassword() { return debeCambiarPassword; }
    public void setDebeCambiarPassword(boolean debeCambiarPassword) { this.debeCambiarPassword = debeCambiarPassword; }
    public LocalDateTime getTemporalUsadaEn() { return temporalUsadaEn; }
    public void setTemporalUsadaEn(LocalDateTime temporalUsadaEn) { this.temporalUsadaEn = temporalUsadaEn; }
    public LocalDateTime getTemporalVenceEn() { return temporalVenceEn; }
    public void setTemporalVenceEn(LocalDateTime temporalVenceEn) { this.temporalVenceEn = temporalVenceEn; }
    public int getSesionVersion() { return sesionVersion; }
    public void setSesionVersion(int sesionVersion) { this.sesionVersion = sesionVersion; }
    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
    public Set<Rol> getRoles() { return roles; }
    public void setRoles(Set<Rol> roles) { this.roles = roles; }
}

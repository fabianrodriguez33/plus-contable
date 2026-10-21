package com.pluscontable.tributario.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "clientes", schema = "tributario_schema")
@Getter
@Setter
public class Cliente {
    public enum Regimen { MYPE, REMIPE, GENERAL }

    public enum CondicionHabido { HABIDO, NO_HABIDO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 11)
    private String ruc;

    @Column(name = "razon_social", nullable = false, length = 200)
    private String razonSocial;

    @Enumerated(EnumType.STRING)
    @Column(name = "regimen_tributario", nullable = false, length = 20)
    private Regimen regimenTributario;

    @Enumerated(EnumType.STRING)
    @Column(name = "condicion_habido", nullable = false, length = 20)
    private CondicionHabido condicionHabido = CondicionHabido.HABIDO;

    @Column(length = 250)
    private String direccion;

    @Column(length = 20)
    private String telefono;

    @Column(length = 150)
    private String email;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();
}

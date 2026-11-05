package com.pluscontable.tributario.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "credenciales_sol", schema = "tributario_schema")
@Getter
@Setter
public class CredencialSol {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false, unique = true)
    private Long clienteId;

    @Column(name = "usuario_sol_cifrado", nullable = false, length = 300)
    private String usuarioSolCifrado;

    @Column(name = "clave_sol_cifrada", nullable = false, length = 300)
    private String claveSolCifrada;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn = LocalDateTime.now();
}

package com.pluscontable.tributario.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** HU-28: la programación del cobro de honorarios de un cliente (una por cliente). */
@Entity
@Table(name = "programaciones_cobro", schema = "tributario_schema")
@Getter
@Setter
public class ProgramacionCobro {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false, unique = true)
    private Long clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Periodicidad periodicidad;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn = LocalDateTime.now();
}

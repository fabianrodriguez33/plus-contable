package com.pluscontable.tributario.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "activos_fijos", schema = "tributario_schema")
@Getter
@Setter
public class ActivoFijo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(length = 40)
    private String codigo;

    @Column(nullable = false, length = 250)
    private String descripcion;

    @Column(length = 80)
    private String categoria;

    @Column(name = "valor_adquisicion", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorAdquisicion;

    @Column(name = "fecha_adquisicion")
    private LocalDate fechaAdquisicion;
}

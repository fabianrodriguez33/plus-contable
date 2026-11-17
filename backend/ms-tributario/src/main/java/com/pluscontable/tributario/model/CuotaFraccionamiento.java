package com.pluscontable.tributario.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** HU-17: una cuota del fraccionamiento, con su fecha de vencimiento y su monto. */
@Entity
@Table(name = "cuotas_fraccionamiento", schema = "tributario_schema")
@Getter
@Setter
public class CuotaFraccionamiento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fraccionamiento_id", nullable = false)
    private Long fraccionamientoId;

    @Column(nullable = false)
    private int numero;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(nullable = false)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCuota estado = EstadoCuota.PENDIENTE;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;
}

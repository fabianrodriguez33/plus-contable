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

/** HU-28: un cobro de honorarios programado (uno por cliente y fecha). */
@Entity
@Table(name = "cobros_honorarios", schema = "tributario_schema")
@Getter
@Setter
public class CobroHonorario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "programacion_id", nullable = false)
    private Long programacionId;

    @Column(name = "fecha_cobro", nullable = false)
    private LocalDate fechaCobro;

    @Column(nullable = false)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCobro estado = EstadoCobro.PENDIENTE;

    /** HU-29: la suma de los pagos registrados del cobro. */
    @Column(nullable = false)
    private BigDecimal pagado = BigDecimal.ZERO;

    public BigDecimal saldo() {
        return monto.subtract(pagado);
    }
}

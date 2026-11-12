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
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** HU-29: un pago recibido de un cobro de honorarios (un cobro admite varios pagos parciales). */
@Entity
@Table(name = "pagos_honorarios", schema = "tributario_schema")
@Getter
@Setter
public class PagoHonorario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cobro_id", nullable = false)
    private Long cobroId;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @Column(nullable = false)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(name = "medio_pago", nullable = false, length = 20)
    private MedioPago medioPago;

    /** Solo con OTRO: qué medio fue (decisión de Beto del 11/11). */
    @Column(name = "medio_pago_detalle", length = 100)
    private String medioPagoDetalle;

    @Column(name = "registrado_por", nullable = false, length = 60)
    private String registradoPor;

    @Column(name = "registrado_en", nullable = false)
    private LocalDateTime registradoEn = LocalDateTime.now();
}

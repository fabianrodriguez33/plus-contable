package com.pluscontable.tributario.model;

import com.pluscontable.tributario.obligacion.TipoObligacion;
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

/** HU-17: fraccionamiento tributario vigente de un cliente (monto total, número de cuotas y fecha de la primera). */
@Entity
@Table(name = "fraccionamientos", schema = "tributario_schema")
@Getter
@Setter
public class Fraccionamiento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    /** Cualquier tributo de TipoObligacion, incluidos ESSALUD, quinta y AFP (decisión de Beto, 06/11). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoObligacion tributo;

    @Column(name = "monto_total", nullable = false)
    private BigDecimal montoTotal;

    @Column(name = "numero_cuotas", nullable = false)
    private int numeroCuotas;

    @Column(name = "fecha_primera_cuota", nullable = false)
    private LocalDate fechaPrimeraCuota;

    @Column(name = "registrado_en", nullable = false)
    private LocalDateTime registradoEn = LocalDateTime.now();
}

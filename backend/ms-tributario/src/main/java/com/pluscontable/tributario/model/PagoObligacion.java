package com.pluscontable.tributario.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * HU-13: un pago de una obligación declarada, con su comprobante (PDF en disco). Guarda el acumulado y el saldo que
 * quedaron después de este pago; el acumulado de la obligación es la suma de {@code montoPagado}.
 */
@Entity
@Table(name = "pagos_obligacion", schema = "tributario_schema")
@Getter
@Setter
public class PagoObligacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "obligacion_id", nullable = false)
    private Long obligacionId;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @Column(name = "monto_pagado", nullable = false)
    private BigDecimal montoPagado;

    @Column(name = "numero_constancia", nullable = false, length = 30)
    private String numeroConstancia;

    @Column(name = "nombre_archivo", nullable = false)
    private String nombreArchivo;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "tamano_bytes", nullable = false)
    private long tamanoBytes;

    @Column(name = "ruta_almacenamiento", nullable = false, length = 400)
    private String rutaAlmacenamiento;

    @Column(name = "acumulado_despues", nullable = false)
    private BigDecimal acumuladoDespues;

    @Column(name = "saldo_despues", nullable = false)
    private BigDecimal saldoDespues;

    @Column(name = "registrado_en", nullable = false)
    private LocalDateTime registradoEn = LocalDateTime.now();
}

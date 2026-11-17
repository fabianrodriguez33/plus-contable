package com.pluscontable.tributario.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** HU-26: pedido especial del cliente (constancia, certificación o trámite) con su fecha de entrega. */
@Entity
@Table(name = "solicitudes_especiales", schema = "tributario_schema")
@Getter
@Setter
public class SolicitudEspecial {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoSolicitud tipo;

    @Column(length = 500)
    private String descripcion;

    @Column(name = "fecha_entrega", nullable = false)
    private LocalDate fechaEntrega;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    @Column(name = "fecha_atencion")
    private LocalDate fechaAtencion;

    /** Usuario del token que la registró. */
    @Column(name = "registrada_por", nullable = false, length = 60)
    private String registradaPor;

    @Column(name = "registrada_en", nullable = false)
    private LocalDateTime registradaEn = LocalDateTime.now();
}

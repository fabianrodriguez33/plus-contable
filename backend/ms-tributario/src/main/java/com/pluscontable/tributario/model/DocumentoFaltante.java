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

/** HU-24: un documento que el cliente debe entregar, con la fecha en que se le pidió y su fecha límite. */
@Entity
@Table(name = "documentos_faltantes", schema = "tributario_schema")
@Getter
@Setter
public class DocumentoFaltante {

    /** Lo que se guarda; SIN_RESPUESTA se calcula al consultar (ver EstadoDocumentoFaltante). */
    public enum Estado { PENDIENTE, ENTREGADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(nullable = false, length = 150)
    private String documento;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDate fechaSolicitud;

    @Column(name = "fecha_limite", nullable = false)
    private LocalDate fechaLimite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.PENDIENTE;

    @Column(name = "fecha_entrega")
    private LocalDate fechaEntrega;

    /** La incidencia de HU-23 con la que se registró que el cliente no lo envió; a lo sumo una. */
    @Column(name = "incidencia_id")
    private Long incidenciaId;

    @Column(name = "registrado_por", nullable = false, length = 60)
    private String registradoPor;

    @Column(name = "registrado_en", nullable = false)
    private LocalDateTime registradoEn = LocalDateTime.now();
}

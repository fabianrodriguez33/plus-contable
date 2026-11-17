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

/** HU-18: notificación del Buzón SOL de un cliente, con su plazo de respuesta. */
@Entity
@Table(name = "notificaciones_sol", schema = "tributario_schema")
@Getter
@Setter
public class NotificacionSol {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoNotificacion tipo;

    @Column(name = "fecha_recepcion", nullable = false)
    private LocalDate fechaRecepcion;

    /** Fecha límite para responder. */
    @Column(name = "plazo_respuesta", nullable = false)
    private LocalDate plazoRespuesta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoNotificacion estado = EstadoNotificacion.PENDIENTE;

    @Column(name = "fecha_atencion")
    private LocalDate fechaAtencion;

    @Column(name = "registrado_en", nullable = false)
    private LocalDateTime registradoEn = LocalDateTime.now();
}

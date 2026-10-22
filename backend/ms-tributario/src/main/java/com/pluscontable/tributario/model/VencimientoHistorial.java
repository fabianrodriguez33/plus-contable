package com.pluscontable.tributario.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** HU-10: fecha de vencimiento anterior de una obligación. */
@Entity
@Table(name = "vencimientos_historial", schema = "tributario_schema")
@Getter
@Setter
public class VencimientoHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "obligacion_id", nullable = false)
    private Long obligacionId;

    @Column(name = "fecha_anterior", nullable = false)
    private LocalDate fechaAnterior;

    @Column(name = "fecha_nueva", nullable = false)
    private LocalDate fechaNueva;

    @Column(name = "cambiado_en", nullable = false)
    private LocalDateTime cambiadoEn = LocalDateTime.now();
}

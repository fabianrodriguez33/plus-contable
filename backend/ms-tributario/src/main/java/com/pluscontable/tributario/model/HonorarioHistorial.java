package com.pluscontable.tributario.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** HU-05: honorario reemplazado, con su periodo de vigencia. */
@Entity
@Table(name = "honorarios_historial", schema = "tributario_schema")
@Getter
@Setter
public class HonorarioHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "acuerdo_id", nullable = false)
    private Long acuerdoId;

    @Column(nullable = false)
    private BigDecimal monto;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "vigente_hasta", nullable = false)
    private LocalDate vigenteHasta;
}

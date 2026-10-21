package com.pluscontable.tributario.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** HU-05: acuerdo vigente del cliente (uno por cliente). Los montos anteriores van a {@link HonorarioHistorial}. */
@Entity
@Table(name = "acuerdos_servicio", schema = "tributario_schema")
@Getter
@Setter
public class AcuerdoServicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false, unique = true)
    private Long clienteId;

    @Column(name = "servicios_contratados", length = 1000)
    private String serviciosContratados;

    @Column(name = "honorario_mensual", nullable = false)
    private BigDecimal honorarioMensual;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;
}

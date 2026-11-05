package com.pluscontable.tributario.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** HU-03: trabajador del expediente laboral. Nacional y extranjero comparten tabla; {@code extranjero} los diferencia. */
@Entity
@Table(name = "trabajadores", schema = "tributario_schema")
@Getter
@Setter
public class Trabajador {
    public enum TipoDocumento { DNI, CE, PASAPORTE, PTP }

    public enum SistemaPensionario { ONP, AFP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "nombre_completo", nullable = false, length = 200)
    private String nombreCompleto;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 10)
    private TipoDocumento tipoDocumento;

    @Column(name = "numero_documento", nullable = false, length = 20)
    private String numeroDocumento;

    @Column(nullable = false)
    private boolean extranjero;

    @Column(length = 60)
    private String nacionalidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "sistema_pensionario", nullable = false, length = 10)
    private SistemaPensionario sistemaPensionario;

    @Column(name = "nombre_afp", length = 60)
    private String nombreAfp;

    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    private BigDecimal remuneracion;

    // datos de contrato del extranjero
    @Column(name = "tipo_contrato", length = 40)
    private String tipoContrato;

    @Column(name = "fecha_inicio_contrato")
    private LocalDate fechaInicioContrato;

    @Column(name = "fecha_fin_contrato")
    private LocalDate fechaFinContrato;
}

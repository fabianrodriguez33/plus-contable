package com.pluscontable.tributario.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "trabajadores", schema = "tributario_schema")
@Getter
@Setter
public class Trabajador {
    public enum RegimenLaboral { MYPE, REMIPE, GENERAL }

    public enum SistemaPensionario { AFP, ONP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "nombre_completo", nullable = false, length = 200)
    private String nombreCompleto;

    @Column(name = "tipo_documento", nullable = false, length = 10)
    private String tipoDocumento = "DNI";

    @Column(name = "numero_documento", nullable = false, length = 20)
    private String numeroDocumento;

    @Column(nullable = false)
    private boolean extranjero;

    @Column(length = 60)
    private String nacionalidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "regimen_laboral", nullable = false, length = 20)
    private RegimenLaboral regimenLaboral;

    @Enumerated(EnumType.STRING)
    @Column(name = "sistema_pensionario", nullable = false, length = 10)
    private SistemaPensionario sistemaPensionario;

    @Column(name = "nombre_afp", length = 60)
    private String nombreAfp;

    @Column(name = "fecha_ingreso")
    private LocalDate fechaIngreso;

    @Column(precision = 12, scale = 2)
    private BigDecimal remuneracion;

    @Column(name = "registro_sunafil", nullable = false)
    private boolean registroSunafil;

    @Column(name = "registro_mtpe", nullable = false)
    private boolean registroMtpe;

    @Column(nullable = false)
    private boolean essalud;

    @Column(name = "quinta_categoria", nullable = false)
    private boolean quintaCategoria;
}

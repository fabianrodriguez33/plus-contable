package com.pluscontable.tributario.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * PROVISIONAL: la obligación programada es de HU-07 (Ricardo) y aún no está en el repositorio.
 * Solo fechaDeclaracion, numeroFormulario y montoDeterminado son de HU-09. Reemplazar al integrar HU-07.
 */
@Entity
@Table(name = "obligaciones", schema = "tributario_schema")
@Getter
@Setter
public class Obligacion {
    // DECLARADO lo consume HU-13 (constancia de pago); no renombrar
    public enum Estado { PENDIENTE, DECLARADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    /** Primer día del mes tributario. */
    @Column(nullable = false)
    private LocalDate periodo;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.PENDIENTE;

    @Column(name = "fecha_declaracion")
    private LocalDate fechaDeclaracion;

    @Column(name = "numero_formulario", length = 30)
    private String numeroFormulario;

    @Column(name = "monto_determinado")
    private BigDecimal montoDeterminado;
}

package com.pluscontable.tributario.model;

import jakarta.persistence.*;
import com.pluscontable.tributario.obligacion.TipoObligacion;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * HU-07 amplía la obligación provisional de develop. Las columnas de declaración
 * de HU-09 y la fecha utilizada por HU-10 permanecen intactas.
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

    /** Nulo solo para filas anteriores a HU-07 hasta que se clasifiquen. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_obligacion", length = 30)
    private TipoObligacion tipoObligacion;

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

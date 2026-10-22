package com.pluscontable.tributario.pago;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Datos inmutables de un pago registrado, aún sin persistencia ni estado_pago. */
public final class RegistroPago {

    private final Long obligacionId;
    private final LocalDate fechaPago;
    private final BigDecimal montoPagado;
    private final String numeroOperacion;
    private final String referenciaConstancia;
    private final String usuarioRegistro;
    private final LocalDateTime registradoEn;

    private RegistroPago(Long obligacionId, LocalDate fechaPago, BigDecimal montoPagado,
                         String numeroOperacion, String referenciaConstancia,
                         String usuarioRegistro, LocalDateTime registradoEn) {
        this.obligacionId = obligacionId;
        this.fechaPago = fechaPago;
        this.montoPagado = montoPagado;
        this.numeroOperacion = numeroOperacion;
        this.referenciaConstancia = referenciaConstancia;
        this.usuarioRegistro = usuarioRegistro;
        this.registradoEn = registradoEn;
    }

    public static RegistroPago crear(Long obligacionId, boolean declarada, LocalDate fechaPago,
                                     BigDecimal montoPagado, String numeroOperacion,
                                     String referenciaConstancia, String usuarioRegistro, Clock clock) {
        RegistroPagoValidator.validar(obligacionId, declarada);
        RegistroPagoValidator.validarDatos(fechaPago, montoPagado, usuarioRegistro);
        if (clock == null) {
            throw new IllegalArgumentException("clock es obligatorio");
        }
        return new RegistroPago(obligacionId, fechaPago, montoPagado, numeroOperacion,
                referenciaConstancia, usuarioRegistro, LocalDateTime.now(clock));
    }

    public Long obligacionId() { return obligacionId; }
    public LocalDate fechaPago() { return fechaPago; }
    public BigDecimal montoPagado() { return montoPagado; }
    public String numeroOperacion() { return numeroOperacion; }
    public String referenciaConstancia() { return referenciaConstancia; }
    public String usuarioRegistro() { return usuarioRegistro; }
    public LocalDateTime registradoEn() { return registradoEn; }
}

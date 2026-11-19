package com.pluscontable.tributario.compras;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Una compra del cliente. {@code estado}: SIN_CLASIFICAR, PROPUESTA (el cliente propuso; pendiente de validación del contador) o
 * VALIDADA. {@code alertaCierre} es el aviso cerca del cierre del mes y es <b>solo para el contador</b>: al cliente siempre le llega falso.
 */
public record CompraDto(Long id, Long clienteId, LocalDate periodo, String proveedorRuc, String proveedorRazonSocial,
                        String comprobante, LocalDate fechaComprobante, BigDecimal importe, ReglaClasificacionCompra.Estado estado,
                        ReglaClasificacionCompra.Tipo tipo, String descripcion, Instant clasificadaEn, String validadaPor,
                        Instant validadaEn, boolean alertaCierre) {
}

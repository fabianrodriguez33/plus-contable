package com.pluscontable.tributario.compras;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Una compra del cliente. {@code clasificada} es falsa mientras esté pendiente; {@code alertaCierre} la marca cerca del cierre del mes. */
public record CompraDto(Long id, Long clienteId, LocalDate periodo, String proveedorRuc, String proveedorRazonSocial,
                        String comprobante, LocalDate fechaComprobante, BigDecimal importe, boolean clasificada,
                        ReglaClasificacionCompra.Tipo tipo, String descripcion, Instant clasificadaEn, boolean alertaCierre) {
}

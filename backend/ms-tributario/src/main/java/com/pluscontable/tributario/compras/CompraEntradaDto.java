package com.pluscontable.tributario.compras;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Lo que carga el contador, con el SIRE como referencia. El periodo es el primer día del mes tributario. */
public record CompraEntradaDto(LocalDate periodo, String proveedorRuc, String proveedorRazonSocial, String comprobante,
                               LocalDate fechaComprobante, BigDecimal importe) {
}

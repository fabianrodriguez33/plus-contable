package com.pluscontable.tributario.compras;

/** Lo que envía el cliente al clasificar una compra: el tipo del catálogo y una descripción. */
public record ClasificacionEntradaDto(ReglaClasificacionCompra.Tipo tipo, String descripcion) {
}

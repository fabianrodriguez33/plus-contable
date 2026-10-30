package com.pluscontable.tributario.ruc;

import java.time.LocalDate;

/** Datos de un RUC con nombres neutros, sin depender del proveedor. Cualquier campo puede venir nulo. */
public record DatosRuc(String ruc, String razonSocial, String estado, String condicion, String direccion,
                       String ubigeo, String fuente, LocalDate fechaDatos) {
}

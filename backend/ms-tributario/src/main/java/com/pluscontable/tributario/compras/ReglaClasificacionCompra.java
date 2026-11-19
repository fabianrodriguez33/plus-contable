package com.pluscontable.tributario.compras;

import java.time.LocalDate;
import java.util.Objects;

/**
 * HU-25 · reglas puras de la clasificación de compras. Una compra sin clasificar queda <b>pendiente</b>; cerca del cierre del mes avisa.
 * Supuesto mío, por confirmar con Beto: el «cierre» de una compra es el último día del mes de su periodo; el aviso empieza
 * {@code diasAntesCierre} días antes (propuesta de Beto: 5, configurable) y sigue mientras no se clasifique.
 */
public final class ReglaClasificacionCompra {

    /** Catálogo acordado con Beto el 18/11: «Mercadería o gasto» y «Activo fijo». */
    public enum Tipo { MERCADERIA_O_GASTO, ACTIVO_FIJO }

    public static final int DESCRIPCION_MAXIMO = 300;

    private ReglaClasificacionCompra() {
    }

    /** Último día del mes del periodo (el periodo es el primer día del mes). */
    public static LocalDate cierre(LocalDate periodo) {
        Objects.requireNonNull(periodo, "periodo");
        return periodo.withDayOfMonth(periodo.lengthOfMonth());
    }

    /** Avisa solo si falta clasificar y ya se está dentro de los días previos al cierre (o el cierre ya pasó). */
    public static boolean alertaCierre(boolean clasificada, LocalDate periodo, LocalDate hoy, int diasAntesCierre) {
        Objects.requireNonNull(hoy, "hoy");
        if (diasAntesCierre < 0) throw new IllegalArgumentException("diasAntesCierre no puede ser negativo");
        return !clasificada && !hoy.isBefore(cierre(periodo).minusDays(diasAntesCierre));
    }

    /** La descripción es obligatoria al clasificar (T210). */
    public static String descripcionValida(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) return "La descripción es obligatoria";
        if (descripcion.trim().length() > DESCRIPCION_MAXIMO) return "La descripción admite hasta " + DESCRIPCION_MAXIMO + " caracteres";
        return null;
    }
}

package com.pluscontable.tributario.compras;

import java.time.LocalDate;
import java.util.Objects;

/**
 * HU-25 · reglas puras de la clasificación de compras (decisiones de Beto, 18/11). El <b>cliente propone</b> una clasificación y queda
 * <b>pendiente de validación del contador</b>, que la valida. Una compra sin validar queda pendiente y, cerca del cierre del mes,
 * <b>avisa solo al contador</b>: desde {@code diasAntesCierre} días antes del último día calendario del mes (propuesta de Beto: 5,
 * configurable) y hasta que se valide.
 */
public final class ReglaClasificacionCompra {

    /** Catálogo acordado con Beto el 18/11: «Mercadería o gasto» y «Activo fijo». */
    public enum Tipo { MERCADERIA_O_GASTO, ACTIVO_FIJO }

    /** SIN_CLASIFICAR: el cliente no propuso nada · PROPUESTA: pendiente de validación del contador · VALIDADA: cerrada. */
    public enum Estado { SIN_CLASIFICAR, PROPUESTA, VALIDADA }

    public static final int DESCRIPCION_MAXIMO = 300;

    private ReglaClasificacionCompra() {
    }

    public static Estado estado(boolean conPropuesta, boolean validada) {
        if (validada) return Estado.VALIDADA;
        return conPropuesta ? Estado.PROPUESTA : Estado.SIN_CLASIFICAR;
    }

    /** Último día calendario del mes de la fecha dada. */
    public static LocalDate ultimoDiaDelMes(LocalDate fecha) {
        Objects.requireNonNull(fecha, "fecha");
        return fecha.withDayOfMonth(fecha.lengthOfMonth());
    }

    /** Avisa al contador mientras la compra no esté validada y ya se esté a {@code diasAntesCierre} días del último día del mes (o en él). */
    public static boolean alertaCierre(Estado estado, LocalDate hoy, int diasAntesCierre) {
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(hoy, "hoy");
        if (diasAntesCierre < 0) throw new IllegalArgumentException("diasAntesCierre no puede ser negativo");
        return estado != Estado.VALIDADA && !hoy.isBefore(ultimoDiaDelMes(hoy).minusDays(diasAntesCierre));
    }

    /** La descripción es obligatoria al proponer la clasificación (T210). */
    public static String descripcionValida(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) return "La descripción es obligatoria";
        if (descripcion.trim().length() > DESCRIPCION_MAXIMO) return "La descripción admite hasta " + DESCRIPCION_MAXIMO + " caracteres";
        return null;
    }
}

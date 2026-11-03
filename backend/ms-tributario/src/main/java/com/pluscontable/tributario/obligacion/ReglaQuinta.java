package com.pluscontable.tributario.obligacion;

import com.pluscontable.tributario.model.Trabajador;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Retención de quinta categoría: se evalúa con la remuneración de los trabajadores registrados, sin casilla manual.
 * Está apagada mientras Beto no confirme la unidad de la remuneración y la fórmula de proyección anual.
 */
public final class ReglaQuinta {
    public static final String MOTIVO_PENDIENTE = "Pendiente de regla de proyección";

    private ReglaQuinta() {
    }

    /** Devuelve el motivo por el que la quinta no se genera, o vacío si corresponde generarla. */
    public static Optional<String> motivoDeOmision(ParametrosTributarios parametros, int anio,
                                                   List<Trabajador> trabajadores) {
        if (!parametros.isQuintaActiva() || parametros.sueldosAlAnio().isEmpty()) {
            return Optional.of(MOTIVO_PENDIENTE);
        }
        if (trabajadores.isEmpty()) return Optional.of(GeneradorObligaciones.MOTIVO_SIN_PLANILLA);
        Optional<BigDecimal> uit = parametros.uitDe(anio);
        if (uit.isEmpty()) return Optional.of("Falta la UIT de " + anio);
        BigDecimal umbral = uit.get().multiply(parametros.getQuintaUmbralUit());
        BigDecimal sueldos = parametros.sueldosAlAnio().get();
        boolean alguno = trabajadores.stream().map(Trabajador::getRemuneracion)
                .anyMatch(r -> r != null && r.multiply(sueldos).compareTo(umbral) > 0);
        return alguno ? Optional.empty()
                : Optional.of("Ningún trabajador supera las "
                        + parametros.getQuintaUmbralUit().stripTrailingZeros().toPlainString() + " UIT proyectadas");
    }
}

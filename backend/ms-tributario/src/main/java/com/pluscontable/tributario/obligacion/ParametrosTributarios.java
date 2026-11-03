package com.pluscontable.tributario.obligacion;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Parámetros tributarios que cambian por año. No llevan ningún monto por defecto: la UIT se carga en
 * {@code app.tributario.uit.<año>} desde la fuente oficial (decreto supremo del MEF de cada año).
 */
@Component
@ConfigurationProperties(prefix = "app.tributario")
@Getter
@Setter
public class ParametrosTributarios {
    /** ESSALUD: apagado hasta que se confirme la regla del estudio (falta el campo asegurado). */
    private boolean essaludActivo = false;

    /** UIT por año, en soles. */
    private Map<Integer, BigDecimal> uit = new HashMap<>();

    /** Retención de quinta: permanece apagada hasta que Beto confirme la unidad y la fórmula de proyección. */
    private boolean quintaActiva = false;

    /** Múltiplo de UIT anual desde el cual se evalúa la retención (7, confirmado por Beto). */
    private BigDecimal quintaUmbralUit = new BigDecimal("7");

    /** Sueldos que se proyectan al año. Sin valor hasta que Beto confirme la fórmula. */
    private BigDecimal quintaSueldosAlAnio;

    public Optional<BigDecimal> uitDe(int anio) {
        return Optional.ofNullable(uit.get(anio));
    }

    public Optional<BigDecimal> sueldosAlAnio() {
        return Optional.ofNullable(quintaSueldosAlAnio);
    }
}

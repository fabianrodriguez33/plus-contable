package com.pluscontable.tributario.cierre;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Clasifica hallazgos ya verificados; no consulta datos, autoriza usuarios ni cierra expedientes. */
public final class EvaluadorImpedimentosCierre {

    public enum Hallazgo {
        OBLIGACION_SIN_DECLARAR,
        DECLARACION_PENDIENTE,
        FRACCIONAMIENTO_ACTIVO,
        HONORARIOS_PENDIENTES,
        DOCUMENTOS_FALTANTES,
        OBLIGACION_EN_REGULARIZACION
    }

    public enum Resultado { BLOQUEO, ADVERTENCIA, SIN_IMPEDIMENTOS }

    private static final Set<Hallazgo> BLOQUEOS = EnumSet.of(
            Hallazgo.OBLIGACION_SIN_DECLARAR,
            Hallazgo.DECLARACION_PENDIENTE,
            Hallazgo.FRACCIONAMIENTO_ACTIVO);

    private EvaluadorImpedimentosCierre() {
    }

    /**
     * La entrada contiene únicamente hallazgos confirmados por sus fuentes. Un conjunto vacío significa
     * que no se proporcionaron hallazgos, no que se completaron todas las consultas necesarias para cerrar.
     */
    public static Evaluacion evaluar(Set<Hallazgo> hallazgos) {
        Objects.requireNonNull(hallazgos, "hallazgos");
        EnumSet<Hallazgo> bloqueos = EnumSet.noneOf(Hallazgo.class);
        EnumSet<Hallazgo> advertencias = EnumSet.noneOf(Hallazgo.class);
        for (Hallazgo hallazgo : hallazgos) {
            Objects.requireNonNull(hallazgo, "Un hallazgo no puede ser nulo");
            (BLOQUEOS.contains(hallazgo) ? bloqueos : advertencias).add(hallazgo);
        }
        Resultado resultado = !bloqueos.isEmpty() ? Resultado.BLOQUEO
                : !advertencias.isEmpty() ? Resultado.ADVERTENCIA : Resultado.SIN_IMPEDIMENTOS;
        return new Evaluacion(resultado, bloqueos, advertencias);
    }

    public record Evaluacion(Resultado resultado, Set<Hallazgo> bloqueos, Set<Hallazgo> advertencias) {
        public Evaluacion {
            bloqueos = Set.copyOf(bloqueos);
            advertencias = Set.copyOf(advertencias);
        }
    }
}
